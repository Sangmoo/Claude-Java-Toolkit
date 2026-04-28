package io.github.claudetoolkit.ui.harness.logrca;

import io.github.claudetoolkit.ui.controller.SseStreamController;
import io.github.claudetoolkit.ui.harness.core.HarnessRunResult;
import io.github.claudetoolkit.ui.harness.core.HarnessStageResult;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Phase D — 오류 로그 RCA 하네스 REST 컨트롤러.
 *
 * <p>3개 엔드포인트:
 * <ul>
 *   <li>{@code POST /api/v1/log-rca/analyze}      — 동기 분석, JSON 응답</li>
 *   <li>{@code POST /api/v1/log-rca/stream-init}  — 입력 등록, 일회성 streamId 반환</li>
 *   <li>{@code GET  /api/v1/log-rca/stream/{id}}  — SSE 채널 오픈, chunk 스트리밍</li>
 * </ul>
 *
 * <p>SSE 두 단계 패턴(init→GET)은 브라우저 EventSource가 GET만 지원하기 때문.
 * pending 입력은 UUID 키로 in-memory에 5분 TTL로 보관됩니다.
 *
 * <p><b>보안</b>: 모든 엔드포인트는 인증 필요 (SecurityConfig {@code anyRequest().authenticated()}).
 * 추가로 {@code log_analysis} feature key로 권한 게이팅 (PermissionInterceptor).
 * CSRF는 SecurityConfig에서 {@code /api/v1/log-rca/**} 제외됨 (XHR/SSE).
 */
@RestController
@RequestMapping("/api/v1/log-rca")
public class HarnessLogRcaController {

    private static final Logger log = LoggerFactory.getLogger(HarnessLogRcaController.class);

    /** 입력 크기 한도 — DoS 방지. 운영 환경에서 1MB 이내로 충분. */
    private static final int MAX_INPUT_SIZE = 1_000_000; // 1MB
    /** pending 입력 TTL — SseStreamController와 동일 정책. */
    private static final long PENDING_TTL_MS = 5L * 60 * 1000;

    private final HarnessLogRcaService rcaService;
    private final ReviewHistoryService historyService;

    /** stream-init → stream/{id} 사이의 일회성 입력 보관소. */
    private final ConcurrentHashMap<String, PendingInput> pending = new ConcurrentHashMap<String, PendingInput>();

    public HarnessLogRcaController(HarnessLogRcaService rcaService,
                                   ReviewHistoryService historyService) {
        this.rcaService     = rcaService;
        this.historyService = historyService;
    }

    // ── 동기 분석 ────────────────────────────────────────────────────────────

    @PostMapping("/analyze")
    public Map<String, Object> analyze(
            @RequestParam("error_log")                                String errorLog,
            @RequestParam(value = "timeline",      defaultValue = "") String timeline,
            @RequestParam(value = "related_code",  defaultValue = "") String relatedCode,
            @RequestParam(value = "env",           defaultValue = "") String env,
            @RequestParam(value = "analysis_mode", defaultValue = "general") String analysisMode) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            validateInput(errorLog, timeline, relatedCode, env);
            HarnessRunResult run = rcaService.analyze(errorLog, timeline, relatedCode, env, analysisMode);

            result.put("success",      run.isSuccess());
            result.put("runId",        run.getRunId());
            result.put("totalElapsed", run.getTotalElapsedMs());
            result.put("stages",       toStageMaps(run));
            result.put("error",        run.getError());

            if (run.isSuccess()) {
                String combined = combineForHistory(run);
                historyService.save("LOG_RCA_HARNESS", errorLog, combined);
            }
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error",   e.getMessage());
        } catch (Exception e) {
            log.warn("[LogRcaHarness] 분석 실패", e);
            result.put("success", false);
            result.put("error",   "RCA 분석 중 오류: " + safeMessage(e));
        }
        return result;
    }

    // ── SSE 스트리밍: 1단계 init ────────────────────────────────────────────

    @PostMapping("/stream-init")
    public Map<String, Object> streamInit(
            @RequestParam("error_log")                                String errorLog,
            @RequestParam(value = "timeline",      defaultValue = "") String timeline,
            @RequestParam(value = "related_code",  defaultValue = "") String relatedCode,
            @RequestParam(value = "env",           defaultValue = "") String env,
            @RequestParam(value = "analysis_mode", defaultValue = "general") String analysisMode) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            validateInput(errorLog, timeline, relatedCode, env);
            String streamId = UUID.randomUUID().toString();
            pending.put(streamId, new PendingInput(errorLog, timeline, relatedCode, env, analysisMode));
            evictExpired();
            result.put("success",  true);
            result.put("streamId", streamId);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error",   e.getMessage());
        }
        return result;
    }

    // ── SSE 스트리밍: 2단계 채널 ────────────────────────────────────────────

    @GetMapping(value = "/stream/{streamId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("streamId") String streamId,
                             java.security.Principal principal) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout — 하네스가 길어도 OK
        PendingInput input = pending.remove(streamId);
        if (input == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("streamId 만료 또는 없음"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        final String capturedUser = principal != null ? principal.getName() : null;
        final StringBuilder accumulated = new StringBuilder();

        Thread t = new Thread(() -> {
            try {
                Consumer<String> sink = chunk -> {
                    accumulated.append(chunk);
                    try {
                        SseStreamController.sendSseData(emitter, chunk);
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                };
                rcaService.analyzeStream(
                        input.errorLog, input.timeline, input.relatedCode,
                        input.env, input.analysisMode, sink);
                try {
                    String savedOutput = accumulated.toString()
                            .replaceAll("\\[\\[HARNESS_STAGE:\\d+\\]\\]\\n?", "");
                    historyService.save("LOG_RCA_HARNESS", input.errorLog, savedOutput, capturedUser);
                } catch (Exception saveErr) {
                    log.warn("[LogRcaHarness] 이력 저장 실패 — 스트림은 정상 종료", saveErr);
                }
                emitter.send(SseEmitter.event().name("done").data("ok"));
                emitter.complete();
            } catch (Exception e) {
                log.warn("[LogRcaHarness] 스트리밍 실패", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(safeMessage(e)));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });
        t.setDaemon(true);
        t.setName("log-rca-harness-" + streamId.substring(0, 8));
        t.start();
        return emitter;
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private static void validateInput(String errorLog, String timeline,
                                       String relatedCode, String env) {
        if (errorLog == null || errorLog.trim().isEmpty()) {
            throw new IllegalArgumentException("error_log는 필수입니다");
        }
        long total = sizeOf(errorLog) + sizeOf(timeline) + sizeOf(relatedCode) + sizeOf(env);
        if (total > MAX_INPUT_SIZE) {
            throw new IllegalArgumentException(
                    "입력 총 크기가 한도를 초과했습니다 (" + total + " > " + MAX_INPUT_SIZE + " bytes)");
        }
    }

    private static long sizeOf(String s) { return s == null ? 0 : s.length(); }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue().createdAt > PENDING_TTL_MS);
    }

    private static java.util.List<Map<String, Object>> toStageMaps(HarnessRunResult run) {
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<Map<String, Object>>();
        for (HarnessStageResult s : run.getStages()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name",      s.getStageName());
            m.put("output",    s.getOutput());
            m.put("elapsedMs", s.getElapsedMs());
            m.put("error",     s.getError());
            list.add(m);
        }
        return list;
    }

    private static String combineForHistory(HarnessRunResult run) {
        StringBuilder sb = new StringBuilder(8192);
        for (HarnessStageResult s : run.getStages()) {
            sb.append("[[STAGE: ").append(s.getStageName()).append("]]\n");
            sb.append(s.getOutput()).append("\n\n");
        }
        return sb.toString();
    }

    private static String safeMessage(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    private static class PendingInput {
        final String errorLog, timeline, relatedCode, env, analysisMode;
        final long createdAt = System.currentTimeMillis();
        PendingInput(String errorLog, String timeline, String relatedCode, String env, String analysisMode) {
            this.errorLog = errorLog; this.timeline = timeline;
            this.relatedCode = relatedCode; this.env = env;
            this.analysisMode = analysisMode;
        }
    }
}
