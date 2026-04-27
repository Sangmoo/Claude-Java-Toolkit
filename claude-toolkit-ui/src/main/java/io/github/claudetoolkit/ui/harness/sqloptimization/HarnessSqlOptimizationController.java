package io.github.claudetoolkit.ui.harness.sqloptimization;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Phase C — Oracle SQL 성능 최적화 하네스 REST 컨트롤러.
 *
 * <p>3개 엔드포인트:
 * <ul>
 *   <li>{@code POST /api/v1/sql-optimization/analyze}      — 동기 분석, JSON 응답</li>
 *   <li>{@code POST /api/v1/sql-optimization/stream-init}  — 입력 등록, 일회성 streamId 반환</li>
 *   <li>{@code GET  /api/v1/sql-optimization/stream/{id}}  — SSE 채널, chunk 스트리밍</li>
 * </ul>
 *
 * <p><b>입력 크기 한도</b>: 2MB (쿼리·실행계획·통계 등 다중 입력 — Phase B(SP 5MB)와 D(로그 1MB) 사이).
 * <p><b>보안</b>: 인증 필요, CSRF 제외 ({@code /api/v1/sql-optimization/**}).
 * 권한 게이팅은 프론트가 {@code sql-optimization-harness} feature key로 처리.
 */
@RestController
@RequestMapping("/api/v1/sql-optimization")
public class HarnessSqlOptimizationController {

    private static final Logger log = LoggerFactory.getLogger(HarnessSqlOptimizationController.class);

    private static final int  MAX_INPUT_SIZE = 2_000_000;
    private static final long PENDING_TTL_MS = 5L * 60 * 1000;

    private final HarnessSqlOptimizationService optimizationService;
    private final ReviewHistoryService          historyService;

    private final ConcurrentHashMap<String, PendingInput> pending = new ConcurrentHashMap<String, PendingInput>();

    public HarnessSqlOptimizationController(HarnessSqlOptimizationService optimizationService,
                                            ReviewHistoryService historyService) {
        this.optimizationService = optimizationService;
        this.historyService      = historyService;
    }

    // ── 동기 분석 ────────────────────────────────────────────────────────────

    @PostMapping("/analyze")
    public Map<String, Object> analyze(
            @RequestParam("query")                                     String query,
            @RequestParam(value = "execution_plan",   defaultValue = "") String executionPlan,
            @RequestParam(value = "table_stats",      defaultValue = "") String tableStats,
            @RequestParam(value = "existing_indexes", defaultValue = "") String existingIndexes,
            @RequestParam(value = "data_volume",      defaultValue = "") String dataVolume,
            @RequestParam(value = "constraints",      defaultValue = "") String constraints) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            validateInput(query, executionPlan, tableStats, existingIndexes, dataVolume, constraints);
            HarnessRunResult run = optimizationService.analyze(
                    query, executionPlan, tableStats, existingIndexes, dataVolume, constraints);

            result.put("success",      run.isSuccess());
            result.put("runId",        run.getRunId());
            result.put("totalElapsed", run.getTotalElapsedMs());
            result.put("stages",       toStageMaps(run));
            result.put("error",        run.getError());

            if (run.isSuccess()) {
                String combined = combineForHistory(run);
                historyService.save("SQL_OPTIMIZATION_HARNESS", query, combined);
            }
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error",   e.getMessage());
        } catch (Exception e) {
            log.warn("[SqlOptimizationHarness] 분석 실패", e);
            result.put("success", false);
            result.put("error",   "SQL 최적화 분석 중 오류: " + safeMessage(e));
        }
        return result;
    }

    // ── SSE 스트리밍: 1단계 init ────────────────────────────────────────────

    @PostMapping("/stream-init")
    public Map<String, Object> streamInit(
            @RequestParam("query")                                     String query,
            @RequestParam(value = "execution_plan",   defaultValue = "") String executionPlan,
            @RequestParam(value = "table_stats",      defaultValue = "") String tableStats,
            @RequestParam(value = "existing_indexes", defaultValue = "") String existingIndexes,
            @RequestParam(value = "data_volume",      defaultValue = "") String dataVolume,
            @RequestParam(value = "constraints",      defaultValue = "") String constraints) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            validateInput(query, executionPlan, tableStats, existingIndexes, dataVolume, constraints);
            String streamId = UUID.randomUUID().toString();
            pending.put(streamId, new PendingInput(query, executionPlan, tableStats,
                    existingIndexes, dataVolume, constraints));
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
    public SseEmitter stream(@PathVariable("streamId") String streamId) {
        SseEmitter emitter = new SseEmitter(0L);
        PendingInput input = pending.remove(streamId);
        if (input == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("streamId 만료 또는 없음"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        Thread t = new Thread(() -> {
            try {
                Consumer<String> sink = chunk -> {
                    try { SseStreamController.sendSseData(emitter, chunk); }
                    catch (IOException ioe) { throw new RuntimeException(ioe); }
                };
                optimizationService.analyzeStream(
                        input.query, input.executionPlan, input.tableStats,
                        input.existingIndexes, input.dataVolume, input.constraints, sink);
                emitter.send(SseEmitter.event().name("done").data("ok"));
                emitter.complete();
            } catch (Exception e) {
                log.warn("[SqlOptimizationHarness] 스트리밍 실패", e);
                try { emitter.send(SseEmitter.event().name("error").data(safeMessage(e))); }
                catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });
        t.setDaemon(true);
        t.setName("sql-optimization-" + streamId.substring(0, 8));
        t.start();
        return emitter;
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private static void validateInput(String query, String executionPlan, String tableStats,
                                       String existingIndexes, String dataVolume, String constraints) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query는 필수입니다");
        }
        long total = sizeOf(query) + sizeOf(executionPlan) + sizeOf(tableStats)
                   + sizeOf(existingIndexes) + sizeOf(dataVolume) + sizeOf(constraints);
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

    private static List<Map<String, Object>> toStageMaps(HarnessRunResult run) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
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
        final String query, executionPlan, tableStats, existingIndexes, dataVolume, constraints;
        final long createdAt = System.currentTimeMillis();
        PendingInput(String query, String executionPlan, String tableStats,
                     String existingIndexes, String dataVolume, String constraints) {
            this.query = query; this.executionPlan = executionPlan; this.tableStats = tableStats;
            this.existingIndexes = existingIndexes; this.dataVolume = dataVolume; this.constraints = constraints;
        }
    }
}
