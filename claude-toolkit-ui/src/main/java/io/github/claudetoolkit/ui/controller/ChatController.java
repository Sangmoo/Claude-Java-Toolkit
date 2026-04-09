package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.starter.model.ClaudeMessage;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * AI 채팅 인터페이스 컨트롤러.
 *
 * <p>세션 기반 대화 히스토리 관리 (최대 20턴).
 * SSE 스트리밍으로 실시간 응답.
 */
@Controller
@RequestMapping("/chat")
public class ChatController {

    private static final int MAX_TURNS = 20;
    private static final String SESSION_KEY = "chat_history";

    private final ClaudeClient    claudeClient;
    private final ToolkitSettings settings;

    public ChatController(ClaudeClient claudeClient, ToolkitSettings settings) {
        this.claudeClient = claudeClient;
        this.settings     = settings;
    }

    @GetMapping
    public String chatPage() {
        return "chat/index";
    }

    /** 메시지 전송 — SSE 스트리밍 응답 */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestParam String message,
                           @RequestParam(required = false) String context,
                           HttpSession session) {
        final SseEmitter emitter = new SseEmitter(180_000L);

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

        // 시스템 프롬프트 구성
        StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("당신은 Claude Java Toolkit의 AI 어시스턴트입니다. ");
        sysPrompt.append("Java/Spring Boot/Oracle DB 기반 엔터프라이즈 개발에 대해 전문적으로 답변합니다. ");
        sysPrompt.append("코드 분석 결과에 대한 후속 질문에도 상세히 답변합니다. ");
        sysPrompt.append("항상 한국어로 답변하세요.");
        String memo = settings.getProjectContext();
        if (memo != null && !memo.trim().isEmpty()) {
            sysPrompt.append("\n\n[프로젝트 컨텍스트]\n").append(memo);
        }
        if (context != null && !context.trim().isEmpty()) {
            sysPrompt.append("\n\n[분석 결과 컨텍스트]\n").append(context.length() > 3000 ? context.substring(0, 3000) + "..." : context);
        }

        // Claude API 호출용 메시지 구성
        final List<ClaudeMessage> messages = new ArrayList<ClaudeMessage>();
        for (Map<String, String> h : history) {
            messages.add(new ClaudeMessage(h.get("role"), h.get("content")));
        }

        final List<Map<String, String>> finalHistory = history;

        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    final StringBuilder responseBuf = new StringBuilder();
                    // 멀티턴: 마지막 사용자 메시지만 추출, 이전 히스토리는 컨텍스트로 포함
                    StringBuilder userPrompt = new StringBuilder();
                    for (int i = 0; i < messages.size(); i++) {
                        ClaudeMessage m = messages.get(i);
                        if (i < messages.size() - 1) {
                            userPrompt.append("[").append(m.getRole()).append("]: ").append(m.getContent()).append("\n\n");
                        }
                    }
                    String lastUserMsg = messages.get(messages.size() - 1).getContent();
                    String fullUserMsg = userPrompt.length() > 0
                            ? "[이전 대화]\n" + userPrompt.toString() + "\n[현재 질문]\n" + lastUserMsg
                            : lastUserMsg;

                    claudeClient.chatStream(
                            sysPrompt.toString(),
                            fullUserMsg,
                            claudeClient.getProperties().getMaxTokens(),
                            new Consumer<String>() {
                                public void accept(String chunk) {
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
                } catch (Exception e) {
                    try { emitter.send(SseEmitter.event().name("error_msg").data(
                            e.getMessage() != null ? e.getMessage() : "채팅 오류")); }
                    catch (IOException ignored) {}
                    emitter.completeWithError(e);
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
