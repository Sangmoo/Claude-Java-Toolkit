package io.github.claudetoolkit.ui.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.starter.model.ClaudeMessage;
import io.github.claudetoolkit.ui.controller.SseStreamController;
import io.github.claudetoolkit.ui.flow.history.FlowHistoryService;
import io.github.claudetoolkit.ui.flow.model.FlowAnalysisRequest;
import io.github.claudetoolkit.ui.metrics.ToolkitMetrics;
import io.github.claudetoolkit.ui.flow.model.FlowAnalysisResult;
import io.github.claudetoolkit.ui.flow.model.FlowEdge;
import io.github.claudetoolkit.ui.flow.model.FlowNode;
import io.github.claudetoolkit.ui.flow.model.FlowStep;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpSession;
import java.security.Principal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Phase 2 — LLM 으로 Phase 1 trace 결과를 사람 친화적 narrative + Mermaid 로 변환하면서
 * SSE 로 단계별 진행을 스트리밍.
 *
 * <h3>흐름</h3>
 * <pre>
 *   POST /flow/stream/start          ← 세션에 분석 요청 저장 (성공 시 ok 응답)
 *        ↓
 *   GET  /flow/stream  (EventSource) ← SSE 채널 오픈
 *        │ event: connected
 *        │ event: status   "🔍 Phase 1 추적 중 — MyBatis/SP/Java grep..."
 *        │ event: trace    {"nodes":[...], "edges":[...], "stats":{...}}   ← Phase 1 result JSON
 *        │ event: status   "✨ Claude 가 흐름도 작성 중..."
 *        │ message events  ← Claude markdown chunk 들 (narrative + mermaid 코드블록)
 *        │ event: done
 * </pre>
 *
 * <p>프론트는 {@code trace} 이벤트로 ReactFlow 다이어그램을 즉시 그리고,
 * 이어지는 markdown chunk 들을 좌측 패널에 점진적으로 표시한다.
 */
@Tag(name = "Flow Analysis (Stream)", description = "Phase 2 — LLM narrative + SSE 스트리밍")
@RestController
@RequestMapping("/flow")
public class FlowStreamController {

    private static final Logger log = LoggerFactory.getLogger(FlowStreamController.class);
    private static final String PENDING_KEY = "FLOW_PENDING_REQ";

    private final FlowAnalysisService  analysisService;
    private final ClaudeClient         claudeClient;
    private final ObjectMapper         objectMapper;
    private final FlowHistoryService   historyService;
    private final ToolkitMetrics       metrics;

    public FlowStreamController(FlowAnalysisService analysisService,
                                ClaudeClient claudeClient,
                                ObjectMapper objectMapper,
                                FlowHistoryService historyService,
                                ToolkitMetrics metrics) {
        this.analysisService = analysisService;
        this.claudeClient    = claudeClient;
        this.objectMapper    = objectMapper;
        this.historyService  = historyService;
        this.metrics         = metrics;
    }

    @Operation(summary = "분석 요청을 세션에 적재 (이후 GET /flow/stream 으로 SSE 시작)")
    @PostMapping("/stream/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody FlowAnalysisRequest req,
                                                     HttpSession session) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (req == null || req.getQuery() == null || req.getQuery().trim().isEmpty()) {
            resp.put("success", false); resp.put("error", "query 가 비어있습니다.");
            return ResponseEntity.ok(resp);
        }
        session.setAttribute(PENDING_KEY, req);
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "SSE 스트림 — 단계별 status + Phase 1 trace + LLM narrative")
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(HttpSession session, Principal principal) {
        final String userId = principal != null ? principal.getName() : null;
        // 30분 타임아웃 (대규모 ERP 추적이 길어질 수 있음)
        final SseEmitter emitter = new SseEmitter(30L * 60 * 1000);

        final FlowAnalysisRequest req = (FlowAnalysisRequest) session.getAttribute(PENDING_KEY);
        session.removeAttribute(PENDING_KEY);

        if (req == null || req.getQuery() == null || req.getQuery().trim().isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("error_msg").data("분석 요청을 먼저 POST /flow/stream/start 로 보내주세요."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // 즉시 connected + 시작 status
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
            emitter.send(SseEmitter.event().name("status").data("🔍 Phase 1 추적 중 — MyBatis / SP / Java / MiPlatform 인덱스 검색..."));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // 하트비트 (chat 패턴 동일 — 30초 idle 동안 SSE 닫힘 방지)
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

        // 메인 작업 — Phase 1 추적 + LLM 호출
        Thread worker = new Thread(new Runnable() {
            public void run() {
                try {
                    // ── Stage A. Phase 1 추적 ────────────────────────────────
                    long t0 = System.currentTimeMillis();
                    final FlowAnalysisResult result = analysisService.analyze(req);
                    long phase1Ms = System.currentTimeMillis() - t0;
                    metrics.recordFlowStage("phase1", phase1Ms);
                    log.info("[FlowStream] Phase1 완료 query='{}' nodes={} edges={} {}ms",
                            req.getQuery(), result.nodes.size(), result.edges.size(), phase1Ms);

                    // 추적 결과를 trace 이벤트로 즉시 보냄 → 프론트는 ReactFlow 그리기 시작
                    try {
                        String json = objectMapper.writeValueAsString(result);
                        emitter.send(SseEmitter.event().name("trace").data(json));
                    } catch (Exception e) {
                        log.warn("[FlowStream] trace 직렬화 실패: {}", e.getMessage());
                    }

                    if (result.nodes.isEmpty()) {
                        metrics.recordFlowAnalysis(result.targetType, "empty");
                        emitter.send(SseEmitter.event().name("status")
                                .data("⚠ 추적 결과가 비어있습니다. 키워드/scanPath/DB 설정을 확인하세요."));
                        emitter.send(SseEmitter.event().name("done").data("ok"));
                        emitter.complete();
                        return;
                    }

                    // ── Stage B. LLM 호출 ────────────────────────────────────
                    emitter.send(SseEmitter.event().name("status")
                            .data("✨ Claude 가 흐름도 작성 중... (" + result.nodes.size() + " 노드 분석)"));

                    String sysPrompt    = buildSystemPrompt();
                    String userMessage  = buildUserMessage(req, result);
                    final List<ClaudeMessage> messages = new ArrayList<ClaudeMessage>();
                    messages.add(new ClaudeMessage("user", userMessage));

                    final StringBuilder responseBuf = new StringBuilder();
                    long llmStart = System.currentTimeMillis();
                    claudeClient.chatStream(sysPrompt, messages,
                            claudeClient.getProperties().getMaxTokens(),
                            new Consumer<String>() {
                                public void accept(String chunk) {
                                    streaming.set(true);
                                    responseBuf.append(chunk);
                                    try { SseStreamController.sendSseData(emitter, chunk); }
                                    catch (IOException e) { emitter.completeWithError(e); }
                                }
                            });

                    metrics.recordFlowStage("llm", System.currentTimeMillis() - llmStart);
                    metrics.recordFlowAnalysis(result.targetType, "ok");

                    // Phase 4 — narrative 가 다 모였으면 이력 저장 (실패해도 SSE 흐름엔 영향 X)
                    if (userId != null) {
                        try { historyService.save(userId, req, result, responseBuf.toString()); }
                        catch (Exception ex) { log.warn("[FlowStream] history.save 실패: {}", ex.getMessage()); }
                    }

                    emitter.send(SseEmitter.event().name("done").data("ok"));
                    Thread.sleep(50);
                    emitter.complete();
                } catch (Throwable e) {
                    String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    log.warn("[FlowStream] 실패: {}", errMsg);
                    metrics.recordFlowAnalysis(req.getTargetType().name(), "error");
                    try { emitter.send(SseEmitter.event().name("error_msg").data(errMsg)); }
                    catch (IOException ignored) {}
                    try {
                        emitter.completeWithError(
                                e instanceof Exception ? (Exception) e : new RuntimeException(e));
                    } catch (Exception ignored) {}
                } finally {
                    completed.set(true);
                    heartbeat.interrupt();
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    // ── 프롬프트 빌더 ─────────────────────────────────────────────────────────

    /**
     * LLM system prompt — narrative 와 mermaid 모두 마크다운으로 출력하게 강제.
     * JSON 별도 출력은 시키지 않는다 (이미 trace 이벤트로 프론트에 전달됨).
     */
    private static String buildSystemPrompt() {
        return  "당신은 사내 ERP 시스템의 데이터 흐름을 사용자에게 설명하는 시니어 개발자입니다.\n"
              + "\n"
              + "[입력] 사용자가 \"테이블 X 에 데이터가 어떻게 들어가는지\" 같은 질문을 했고, "
              + "백엔드 추적 엔진이 코드/DB 인덱스를 이미 검색해 nodes/edges/steps 를 찾아 전달했습니다. "
              + "당신은 그 결과를 **자연어 흐름** + **Mermaid 다이어그램** 으로 다시 작성합니다.\n"
              + "\n"
              + "[출력 규칙 — 반드시 지킬 것]\n"
              + "1. 마크다운 한 덩어리로 응답합니다.\n"
              + "2. 다음 섹션 순서를 따릅니다:\n"
              + "   ## 📌 한 줄 요약  (질문에 대한 결론을 1~2 문장으로)\n"
              + "   ## 🔁 데이터 흐름 다이어그램  ( ```mermaid 코드블록 1개 — flowchart TD )\n"
              + "   ## 📋 단계별 설명  (1·2·3 번호 매겨서, 각 단계에 파일/SP/URL 정확히 인용)\n"
              + "   ## ⚠ 주의/추정  (인덱서가 못 찾은 영역, AI 추정 부분, 추가 확인 필요 항목)\n"
              + "3. 절대 입력 데이터에 없는 파일·테이블·SP 명을 만들어내지 않습니다 (환각 금지). 추정이면 \"추정\" 이라고 명시합니다.\n"
              + "4. Mermaid 다이어그램은 입력의 nodes/edges 를 기반으로 하되, 같은 type 끼리 subgraph 로 묶어 가독성 ↑.\n"
              + "5. 노드 라벨은 `파일명.메서드명` 또는 `OWNER.OBJECT_NAME` 처럼 구체적으로.\n"
              + "6. steps 가 비어있으면 nodes/edges 를 보고 직접 1·2·3 흐름을 재구성합니다.\n"
              + "7. 입력에 warnings 가 있으면 ⚠ 섹션에 그대로 포함하고, 가능한 보완책 (예: \"reindex 후 재시도\") 을 제시합니다.\n";
    }

    /**
     * Phase 1 result 를 LLM 이 처리하기 좋은 압축된 자연어 + 핵심 JSON 으로 변환.
     * raw JSON 을 통째로 던지면 토큰 낭비 + 모델 혼란 → 핵심만 정리해서 전달.
     */
    private String buildUserMessage(FlowAnalysisRequest req, FlowAnalysisResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 사용자 질문\n\"").append(req.getQuery()).append("\"\n\n");
        sb.append("# 추적 메타데이터\n");
        sb.append("- 결정된 targetType: ").append(r.targetType).append("\n");
        sb.append("- 통계: ").append(r.stats).append("\n");
        if (r.warnings != null && !r.warnings.isEmpty()) {
            sb.append("\n# ⚠ 경고\n");
            for (String w : r.warnings) sb.append("- ").append(w).append("\n");
        }

        sb.append("\n# 발견된 노드 (총 ").append(r.nodes.size()).append("개)\n");
        // 타입별로 그룹화해서 출력 (LLM 이 흐름을 파악하기 쉽게)
        appendNodesByType(sb, r.nodes, "ui",         "MiPlatform 화면");
        appendNodesByType(sb, r.nodes, "controller", "Spring Controller");
        appendNodesByType(sb, r.nodes, "service",    "Service / Manager");
        appendNodesByType(sb, r.nodes, "dao",        "DAO / Mapper Java");
        appendNodesByType(sb, r.nodes, "mybatis",    "MyBatis Statement");
        appendNodesByType(sb, r.nodes, "sp",         "Oracle SP / Trigger");
        appendNodesByType(sb, r.nodes, "table",      "DB 테이블");

        sb.append("\n# 노드 간 연결 (edges, 총 ").append(r.edges.size()).append("개)\n");
        // edge 가 너무 많으면 자르기 (~80개)
        List<FlowEdge> edges = r.edges.size() > 80 ? r.edges.subList(0, 80) : r.edges;
        for (FlowEdge e : edges) {
            sb.append("- ").append(e.from).append(" --[")
              .append(e.label == null ? "" : e.label).append("]--> ").append(e.to).append("\n");
        }
        if (r.edges.size() > 80) sb.append("- ... (총 ").append(r.edges.size()).append("개 중 80개만 표시)\n");

        if (r.steps != null && !r.steps.isEmpty()) {
            sb.append("\n# 인덱서가 자동 생성한 초안 단계\n");
            for (FlowStep s : r.steps) {
                sb.append(s.no).append(". [").append(s.actor).append("] ").append(s.what);
                if (s.file != null) sb.append("  — `").append(s.file).append("`");
                if (s.line != null) sb.append(":").append(s.line);
                sb.append("\n");
            }
        }
        sb.append("\n위 데이터를 기반으로 [출력 규칙] 에 따른 마크다운 답변을 작성하세요.");
        return sb.toString();
    }

    private static void appendNodesByType(StringBuilder sb, List<FlowNode> nodes,
                                          String type, String displayName) {
        List<FlowNode> filtered = new ArrayList<FlowNode>();
        for (FlowNode n : nodes) if (type.equals(n.type)) filtered.add(n);
        if (filtered.isEmpty()) return;
        sb.append("\n## ").append(displayName).append(" (").append(filtered.size()).append(")\n");
        for (FlowNode n : filtered) {
            sb.append("- `").append(n.id).append("` ").append(n.label);
            if (n.file != null) sb.append("  — `").append(n.file).append("`");
            if (n.line != null) sb.append(":").append(n.line);
            if (n.meta != null && !n.meta.isEmpty()) {
                Map<String, String> shown = new LinkedHashMap<String, String>();
                // snippet 은 너무 길어서 100자 자르기
                for (Map.Entry<String, String> e : n.meta.entrySet()) {
                    String v = e.getValue();
                    if (v != null && v.length() > 100) v = v.substring(0, 100) + "…";
                    shown.put(e.getKey(), v);
                }
                sb.append("  ").append(shown);
            }
            sb.append("\n");
        }
    }
}
