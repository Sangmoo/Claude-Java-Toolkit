package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.starter.model.ClaudeMessage;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.prompt.PromptService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AI 채팅 인터페이스 컨트롤러.
 *
 * <p>2단계 SSE 스트리밍:
 * <ol>
 *   <li>POST /chat/send — 메시지를 세션에 저장 (즉시 반환)</li>
 *   <li>GET  /chat/stream — EventSource로 실시간 스트리밍 (하트비트 포함)</li>
 * </ol>
 */
@Controller
@RequestMapping("/chat")
public class ChatController {

    private static final int MAX_TURNS = 20;
    private static final String SESSION_KEY = "chat_history";
    private static final String PENDING_KEY = "chat_pending";

    private final ClaudeClient    claudeClient;
    private final ToolkitSettings settings;
    private final PromptService   promptService;

    public ChatController(ClaudeClient claudeClient, ToolkitSettings settings,
                          PromptService promptService) {
        this.claudeClient  = claudeClient;
        this.settings      = settings;
        this.promptService = promptService;
    }

    @GetMapping
    public String chatPage() {
        return "chat/index";
    }

    /** Step 1: 메시지 저장 (즉시 반환) */
    @PostMapping("/send")
    @ResponseBody
    public Map<String, Object> send(@RequestParam String message,
                                    @RequestParam(required = false) String context,
                                    HttpSession session) {
        Map<String, String> pending = new LinkedHashMap<String, String>();
        pending.put("message", message);
        if (context != null && !context.trim().isEmpty()) {
            pending.put("context", context);
        }
        session.setAttribute(PENDING_KEY, pending);

        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }

    /** Step 2: EventSource 스트리밍 — 하트비트로 연결 유지 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpSession session) {
        final SseEmitter emitter = new SseEmitter(180_000L);

        // pending 메시지 꺼내기
        @SuppressWarnings("unchecked")
        final Map<String, String> pending = (Map<String, String>) session.getAttribute(PENDING_KEY);
        session.removeAttribute(PENDING_KEY);

        if (pending == null || pending.get("message") == null) {
            try {
                emitter.send(SseEmitter.event().name("error_msg").data("전송할 메시지가 없습니다."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        final String message = pending.get("message");
        final String context = pending.get("context");

        // 히스토리 로드
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) session.getAttribute(SESSION_KEY);
        if (history == null) {
            history = new ArrayList<Map<String, String>>();
        }

        // 사용자 메시지 추가
        Map<String, String> userMsg = new LinkedHashMap<String, String>();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        history.add(userMsg);

        // 시스템 프롬프트 구성 (커스텀 프롬프트 우선, 없으면 기본)
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

        // Claude API 호출용 메시지 구성
        final List<ClaudeMessage> messages = new ArrayList<ClaudeMessage>();
        for (Map<String, String> h : history) {
            messages.add(new ClaudeMessage(h.get("role"), h.get("content")));
        }

        final List<Map<String, String>> finalHistory = history;
        final String finalSysPrompt = sysPrompt.toString();

        // ── 즉시 connected 이벤트 전송 (연결 확립 확인) ──
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // ── 하트비트: 3초마다 SSE 코멘트 전송 (유휴 연결 끊김 방지) ──
        final AtomicBoolean streaming = new AtomicBoolean(false);
        final AtomicBoolean completed = new AtomicBoolean(false);
        final Thread heartbeat = new Thread(new Runnable() {
            public void run() {
                try {
                    while (!completed.get()) {
                        Thread.sleep(3000);
                        if (completed.get()) break;
                        // 아직 실제 데이터가 안 왔으면 하트비트 전송
                        if (!streaming.get()) {
                            try {
                                emitter.send(SseEmitter.event().comment("heartbeat"));
                            } catch (IOException e) {
                                break; // 연결 끊김 — 정상 종료
                            }
                        }
                    }
                } catch (InterruptedException ignored) {}
            }
        });
        heartbeat.setDaemon(true);
        heartbeat.start();

        // ── 메인 스트리밍 스레드 ──
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    final StringBuilder responseBuf = new StringBuilder();
                    // 멀티턴: 이전 히스토리 + 현재 질문
                    StringBuilder userPrompt = new StringBuilder();
                    for (int i = 0; i < messages.size(); i++) {
                        ClaudeMessage m = messages.get(i);
                        if (i < messages.size() - 1) {
                            userPrompt.append("[").append(m.getRole()).append("]: ")
                                      .append(m.getContent()).append("\n\n");
                        }
                    }
                    String lastUserMsg = messages.get(messages.size() - 1).getContent();
                    String fullUserMsg = userPrompt.length() > 0
                            ? "[이전 대화]\n" + userPrompt.toString() + "\n[현재 질문]\n" + lastUserMsg
                            : lastUserMsg;

                    claudeClient.chatStream(
                            finalSysPrompt,
                            fullUserMsg,
                            claudeClient.getProperties().getMaxTokens(),
                            new Consumer<String>() {
                                public void accept(String chunk) {
                                    streaming.set(true);  // 하트비트 중단 신호
                                    responseBuf.append(chunk);
                                    try { SseStreamController.sendSseData(emitter, chunk); }
                                    catch (IOException e) { emitter.completeWithError(e); }
                                }
                            });

                    // AI 응답을 히스토리에 추가
                    Map<String, String> aiMsg = new LinkedHashMap<String, String>();
                    aiMsg.put("role", "assistant");
                    aiMsg.put("content", responseBuf.toString());
                    finalHistory.add(aiMsg);

                    // 턴 수 제한
                    while (finalHistory.size() > MAX_TURNS * 2) {
                        finalHistory.remove(0);
                        if (!finalHistory.isEmpty()) finalHistory.remove(0);
                    }
                    session.setAttribute(SESSION_KEY, finalHistory);

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

    /** 대화 초기화 */
    @PostMapping("/clear")
    @ResponseBody
    public Map<String, Object> clear(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        session.removeAttribute(PENDING_KEY);
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }

    /** 대화 히스토리 조회 */
    @GetMapping("/history")
    @ResponseBody
    public List<Map<String, String>> getHistory(HttpSession session) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) session.getAttribute(SESSION_KEY);
        return history != null ? history : new ArrayList<Map<String, String>>();
    }
}
