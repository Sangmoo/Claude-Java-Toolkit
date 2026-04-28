package io.github.claudetoolkit.ui.harness.spmigration;

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
 * Phase B — SP→Java 마이그레이션 하네스 REST 컨트롤러.
 *
 * <p>3개 엔드포인트:
 * <ul>
 *   <li>{@code POST /api/v1/sp-migration/analyze}      — 동기 분석, JSON 응답</li>
 *   <li>{@code POST /api/v1/sp-migration/stream-init}  — 입력 등록, 일회성 streamId 반환</li>
 *   <li>{@code GET  /api/v1/sp-migration/stream/{id}}  — SSE 채널, chunk 스트리밍</li>
 * </ul>
 *
 * <p><b>입력 크기 한도</b>: 5MB (SP는 수천 줄까지 가능 — 일반 코드보다 크게 잡음).
 * <p><b>보안</b>: 인증 필요(SecurityConfig), CSRF는 {@code /api/v1/sp-migration/**} 제외.
 * 권한 게이팅은 프론트가 {@code sp-migration-harness} feature key로 처리.
 */
@RestController
@RequestMapping("/api/v1/sp-migration")
public class HarnessSpMigrationController {

    private static final Logger log = LoggerFactory.getLogger(HarnessSpMigrationController.class);

    /** 입력 크기 한도 — SP는 일반 코드보다 클 수 있어 5MB. */
    private static final int  MAX_INPUT_SIZE = 5_000_000;
    private static final long PENDING_TTL_MS = 5L * 60 * 1000;

    private final HarnessSpMigrationService migrationService;
    private final ReviewHistoryService      historyService;

    private final ConcurrentHashMap<String, PendingInput> pending = new ConcurrentHashMap<String, PendingInput>();

    public HarnessSpMigrationController(HarnessSpMigrationService migrationService,
                                        ReviewHistoryService historyService) {
        this.migrationService = migrationService;
        this.historyService   = historyService;
    }

    // ── 동기 분석 ────────────────────────────────────────────────────────────

    @PostMapping("/analyze")
    public Map<String, Object> analyze(
            @RequestParam(value = "sp_source",        defaultValue = "") String spSource,
            @RequestParam(value = "sp_name",          defaultValue = "") String spName,
            @RequestParam(value = "sp_type",          defaultValue = "") String spType,
            @RequestParam(value = "table_ddl",        defaultValue = "") String tableDdl,
            @RequestParam(value = "index_ddl",        defaultValue = "") String indexDdl,
            @RequestParam(value = "call_example",     defaultValue = "") String callExample,
            @RequestParam(value = "business_context", defaultValue = "") String businessContext) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            validateInput(spSource, spName, tableDdl, indexDdl, callExample, businessContext);
            HarnessRunResult run = migrationService.analyze(
                    spSource, spName, spType, tableDdl, indexDdl, callExample, businessContext);

            result.put("success",      run.isSuccess());
            result.put("runId",        run.getRunId());
            result.put("totalElapsed", run.getTotalElapsedMs());
            result.put("stages",       toStageMaps(run));
            result.put("error",        run.getError());

            if (run.isSuccess()) {
                String combined = combineForHistory(run);
                String historyInput = !spSource.isEmpty() ? spSource : ("[SP_NAME] " + spName + " " + spType);
                historyService.save("SP_MIGRATION_HARNESS", historyInput, combined);
            }
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error",   e.getMessage());
        } catch (Exception e) {
            log.warn("[SpMigrationHarness] 분석 실패", e);
            result.put("success", false);
            result.put("error",   "SP 마이그레이션 분석 중 오류: " + safeMessage(e));
        }
        return result;
    }

    // ── SSE 스트리밍: 1단계 init ────────────────────────────────────────────

    @PostMapping("/stream-init")
    public Map<String, Object> streamInit(
            @RequestParam(value = "sp_source",        defaultValue = "") String spSource,
            @RequestParam(value = "sp_name",          defaultValue = "") String spName,
            @RequestParam(value = "sp_type",          defaultValue = "") String spType,
            @RequestParam(value = "table_ddl",        defaultValue = "") String tableDdl,
            @RequestParam(value = "index_ddl",        defaultValue = "") String indexDdl,
            @RequestParam(value = "call_example",     defaultValue = "") String callExample,
            @RequestParam(value = "business_context", defaultValue = "") String businessContext) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            validateInput(spSource, spName, tableDdl, indexDdl, callExample, businessContext);
            String streamId = UUID.randomUUID().toString();
            pending.put(streamId, new PendingInput(spSource, spName, spType,
                    tableDdl, indexDdl, callExample, businessContext));
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
        SseEmitter emitter = new SseEmitter(0L);
        PendingInput input = pending.remove(streamId);
        if (input == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("streamId 만료 또는 없음"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        // 백그라운드 스레드에서 SecurityContext 가 비므로 요청 스레드에서 username capture
        final String capturedUser = principal != null ? principal.getName() : null;
        final StringBuilder accumulated = new StringBuilder();

        Thread t = new Thread(() -> {
            try {
                Consumer<String> sink = chunk -> {
                    accumulated.append(chunk);
                    try { SseStreamController.sendSseData(emitter, chunk); }
                    catch (IOException ioe) { throw new RuntimeException(ioe); }
                };
                migrationService.analyzeStream(
                        input.spSource, input.spName, input.spType,
                        input.tableDdl, input.indexDdl,
                        input.callExample, input.businessContext, sink);
                // 스트리밍 완료 → 리뷰 이력에 저장 (sync /analyze 와 동일 형식)
                try {
                    String savedOutput = accumulated.toString()
                            .replaceAll("\\[\\[HARNESS_STAGE:\\d+\\]\\]\\n?", "");
                    String historyInput = (input.spSource != null && !input.spSource.isEmpty())
                            ? input.spSource
                            : ("[SP_NAME] " + input.spName + " " + input.spType);
                    historyService.save("SP_MIGRATION_HARNESS", historyInput, savedOutput, capturedUser);
                } catch (Exception saveErr) {
                    log.warn("[SpMigrationHarness] 이력 저장 실패 — 스트림은 정상 종료", saveErr);
                }
                emitter.send(SseEmitter.event().name("done").data("ok"));
                emitter.complete();
            } catch (Exception e) {
                log.warn("[SpMigrationHarness] 스트리밍 실패", e);
                try { emitter.send(SseEmitter.event().name("error").data(safeMessage(e))); }
                catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });
        t.setDaemon(true);
        t.setName("sp-migration-" + streamId.substring(0, 8));
        t.start();
        return emitter;
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private static void validateInput(String spSource, String spName,
                                       String tableDdl, String indexDdl,
                                       String callExample, String businessContext) {
        // sp_source 또는 sp_name 둘 중 하나는 필요 (Service에서 한 번 더 검증)
        boolean hasSource = spSource != null && !spSource.trim().isEmpty();
        boolean hasName   = spName   != null && !spName.trim().isEmpty();
        if (!hasSource && !hasName) {
            throw new IllegalArgumentException("sp_source 또는 sp_name 중 하나는 필수입니다");
        }
        long total = sizeOf(spSource) + sizeOf(tableDdl) + sizeOf(indexDdl)
                   + sizeOf(callExample) + sizeOf(businessContext);
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
        final String spSource, spName, spType, tableDdl, indexDdl, callExample, businessContext;
        final long createdAt = System.currentTimeMillis();
        PendingInput(String spSource, String spName, String spType,
                     String tableDdl, String indexDdl,
                     String callExample, String businessContext) {
            this.spSource = spSource; this.spName = spName; this.spType = spType;
            this.tableDdl = tableDdl; this.indexDdl = indexDdl;
            this.callExample = callExample; this.businessContext = businessContext;
        }
    }
}
