package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.starter.model.ClaudeMessage;
import io.github.claudetoolkit.ui.chat.ChatSession;
import io.github.claudetoolkit.ui.chat.ChatSessionService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.prompt.PromptService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AI 채팅 인터페이스 컨트롤러 (v2.7.0 — DB 세션 기반).
 *
 * <p>v2.7.0 변경:
 * <ul>
 *   <li>대화 히스토리를 {@link ChatSessionService} 기반 H2 DB로 영속화</li>
 *   <li>사용자별 다중 세션 지원</li>
 *   <li>세션 CRUD 엔드포인트 추가</li>
 * </ul>
 *
 * <p>2단계 SSE 스트리밍은 유지:
 * <ol>
 *   <li>POST /chat/send — 메시지를 세션에 저장 (pending 정보를 HTTP 세션에 기록)</li>
 *   <li>GET  /chat/stream — EventSource로 실시간 스트리밍 + 하트비트</li>
 * </ol>
 */
@Controller
@RequestMapping("/chat")
public class ChatController {

    private static final String PENDING_KEY = "chat_pending";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ClaudeClient        claudeClient;
    private final ToolkitSettings     settings;
    private final PromptService       promptService;
    private final ChatSessionService  sessionService;
    /** v4.4.x — 사용자 메시지에서 DB 테이블/컬럼 + 코드 자동 추출 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.claudetoolkit.ui.chat.ChatContextEnricher contextEnricher;

    public ChatController(ClaudeClient claudeClient, ToolkitSettings settings,
                          PromptService promptService, ChatSessionService sessionService) {
        this.claudeClient   = claudeClient;
        this.settings       = settings;
        this.promptService  = promptService;
        this.sessionService = sessionService;
    }

    // ── 세션 CRUD ─────────────────────────────────────────────────────────────

    /** 현재 사용자의 세션 목록 조회 */
    @GetMapping("/sessions")
    @ResponseBody
    public List<Map<String, Object>> listSessions(Principal principal) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (principal == null) return result;
        List<ChatSession> sessions = sessionService.listByUser(principal.getName());
        for (ChatSession s : sessions) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id",        s.getId());
            m.put("title",     s.getTitle());
            m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().format(TS_FMT) : null);
            m.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().format(TS_FMT) : null);
            result.add(m);
        }
        return result;
    }

    /** 새 세션 생성 */
    @PostMapping("/sessions/new")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createSession(Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (principal == null) { resp.put("success", false); return ResponseEntity.ok(resp); }
        ChatSession s = sessionService.create(principal.getName(), "새 대화");
        resp.put("success", true);
        resp.put("id",      s.getId());
        resp.put("title",   s.getTitle());
        return ResponseEntity.ok(resp);
    }

    /** 세션 제목 변경 */
    @PostMapping("/sessions/{id}/rename")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> renameSession(
            @PathVariable Long id, @RequestParam String title, Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (!checkOwner(id, principal)) {
            resp.put("success", false);
            resp.put("error", "권한이 없습니다.");
            return ResponseEntity.ok(resp);
        }
        sessionService.rename(id, title);
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    /** 세션 삭제 */
    @PostMapping("/sessions/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable Long id, Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (!checkOwner(id, principal)) {
            resp.put("success", false);
            resp.put("error", "권한이 없습니다.");
            return ResponseEntity.ok(resp);
        }
        sessionService.delete(id);
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    /** 세션 메시지 전체 삭제 (세션은 유지) */
    @PostMapping("/sessions/{id}/clear")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearSession(@PathVariable Long id, Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (!checkOwner(id, principal)) {
            resp.put("success", false);
            resp.put("error", "권한이 없습니다.");
            return ResponseEntity.ok(resp);
        }
        sessionService.clearMessages(id);
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    /** 세션의 메시지 목록 조회 */
    @GetMapping("/sessions/{id}/messages")
    @ResponseBody
    public ResponseEntity<List<Map<String, String>>> getSessionMessages(
            @PathVariable Long id, Principal principal) {
        if (!checkOwner(id, principal)) {
            return ResponseEntity.ok(new ArrayList<Map<String, String>>());
        }
        return ResponseEntity.ok(sessionService.getMessagesAsMap(id));
    }

    private boolean checkOwner(Long sessionId, Principal principal) {
        return principal != null && sessionService.isOwner(sessionId, principal.getName());
    }

    // ── 메시지 전송 2단계 SSE ──────────────────────────────────────────────

    /** Step 1: 메시지 저장 (즉시 반환). sessionId가 없으면 새 세션 생성 */
    @PostMapping("/send")
    @ResponseBody
    public Map<String, Object> send(@RequestParam String message,
                                    @RequestParam(required = false) Long sessionId,
                                    @RequestParam(required = false) String context,
                                    HttpSession httpSession,
                                    Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (principal == null) {
            resp.put("success", false);
            resp.put("error", "로그인이 필요합니다.");
            return resp;
        }

        // 세션 ID 결정: 없으면 신규 생성, 있으면 소유권 확인
        Long effectiveSessionId = sessionId;
        if (effectiveSessionId == null
                || !sessionService.isOwner(effectiveSessionId, principal.getName())) {
            ChatSession s = sessionService.create(principal.getName(), "새 대화");
            effectiveSessionId = s.getId();
        }

        // pending 정보를 HTTP 세션에 저장 (stream 엔드포인트에서 사용)
        Map<String, String> pending = new LinkedHashMap<String, String>();
        pending.put("message", message);
        pending.put("sessionId", String.valueOf(effectiveSessionId));
        if (context != null && !context.trim().isEmpty()) {
            pending.put("context", context);
        }
        httpSession.setAttribute(PENDING_KEY, pending);

        resp.put("success",   true);
        resp.put("sessionId", effectiveSessionId);
        return resp;
    }

    /** Step 2: EventSource 스트리밍 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpSession httpSession, Principal principal) {
        final SseEmitter emitter = new SseEmitter(180_000L);

        @SuppressWarnings("unchecked")
        final Map<String, String> pending = (Map<String, String>) httpSession.getAttribute(PENDING_KEY);
        httpSession.removeAttribute(PENDING_KEY);

        if (principal == null || pending == null || pending.get("message") == null) {
            try {
                emitter.send(SseEmitter.event().name("error_msg").data("전송할 메시지가 없습니다."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        final String username = principal.getName();
        final String message  = pending.get("message");
        final String context  = pending.get("context");
        final Long   sessionId;
        try {
            sessionId = Long.parseLong(pending.get("sessionId"));
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error_msg").data("세션 정보가 유효하지 않습니다."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // 세션 소유권 재확인
        if (!sessionService.isOwner(sessionId, username)) {
            try {
                emitter.send(SseEmitter.event().name("error_msg").data("세션 접근 권한이 없습니다."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // DB에 사용자 메시지 추가
        sessionService.addUserMessage(sessionId, message);
        // 첫 메시지면 자동 제목 생성
        sessionService.autoGenerateTitle(sessionId, message);

        // 시스템 프롬프트 구성
        String customPrompt = promptService.findActivePrompt("AI_CHAT");
        StringBuilder sysPrompt = new StringBuilder();
        if (customPrompt != null) {
            sysPrompt.append(customPrompt);
        } else {
            sysPrompt.append(PromptController.AI_CHAT_DEFAULT_PROMPT);
        }
        String memo = settings.getProjectContext();
        if (memo != null && !memo.trim().isEmpty()) {
            sysPrompt.append("\n\n[프로젝트 컨텍스트]\n").append(memo);
        }
        if (context != null) {
            sysPrompt.append("\n\n[분석 결과 컨텍스트]\n")
                      .append(context.length() > 3000 ? context.substring(0, 3000) + "..." : context);
        }
        // v4.4.x — 사용자 메시지에서 테이블/컬럼명 자동 감지 → DB 스키마 + 코드 발췌 주입
        // 예: "T_SHOP_INVT_RANK 의 FINAL_RANK 가 언제 UPDATE 되는지" 질문 시
        //     자동으로 해당 테이블 메타 + UPDATE 구문 포함 코드 스니펫 첨부
        if (contextEnricher != null) {
            try {
                String autoCtx = contextEnricher.enrich(message);
                if (autoCtx != null && !autoCtx.isEmpty()) {
                    sysPrompt.append("\n\n[자동 감지 컨텍스트 — DB 스키마 + 프로젝트 코드]")
                             .append(autoCtx);
                }
            } catch (Exception ignored) { /* enricher 실패는 채팅 흐름에 영향 없음 */ }
        }

        // DB에서 메시지 목록 로드 (방금 추가한 사용자 메시지 포함)
        final List<Map<String, String>> history = sessionService.getMessagesAsMap(sessionId);
        final List<ClaudeMessage> messages = new ArrayList<ClaudeMessage>();
        for (Map<String, String> h : history) {
            messages.add(new ClaudeMessage(h.get("role"), h.get("content")));
        }

        final String finalSysPrompt = sysPrompt.toString();
        final Long finalSessionId = sessionId;

        // 즉시 connected 이벤트
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // 하트비트
        final AtomicBoolean streaming = new AtomicBoolean(false);
        final AtomicBoolean completed = new AtomicBoolean(false);
        final Thread heartbeat = new Thread(new Runnable() {
            public void run() {
                try {
                    while (!completed.get()) {
                        Thread.sleep(3000);
                        if (completed.get()) break;
                        if (!streaming.get()) {
                            try { emitter.send(SseEmitter.event().comment("heartbeat")); }
                            catch (IOException e) { break; }
                        }
                    }
                } catch (InterruptedException ignored) {}
            }
        });
        heartbeat.setDaemon(true);
        heartbeat.start();

        // 메인 스트리밍
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    final StringBuilder responseBuf = new StringBuilder();
                    claudeClient.chatStream(
                            finalSysPrompt,
                            messages,
                            claudeClient.getProperties().getMaxTokens(),
                            new Consumer<String>() {
                                public void accept(String chunk) {
                                    streaming.set(true);
                                    responseBuf.append(chunk);
                                    try { SseStreamController.sendSseData(emitter, chunk); }
                                    catch (IOException e) { emitter.completeWithError(e); }
                                }
                            });

                    // AI 응답을 DB에 저장
                    sessionService.addAssistantMessage(finalSessionId, responseBuf.toString());

                    emitter.send(SseEmitter.event().name("done").data("ok"));
                    Thread.sleep(50);
                    emitter.complete();
                } catch (Throwable e) {
                    String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    try { emitter.send(SseEmitter.event().name("error_msg").data(errMsg)); }
                    catch (IOException ignored) {}
                    try { emitter.completeWithError(
                            e instanceof Exception ? (Exception) e : new RuntimeException(e)); }
                    catch (Exception ignored) {}
                } finally {
                    completed.set(true);
                    heartbeat.interrupt();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return emitter;
    }
}
