package io.github.claudetoolkit.ui.harness.core;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.metrics.ToolkitMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Phase A — 하네스 파이프라인 실행기.
 *
 * <p>{@link HarnessStage} 목록을 순차 실행하며, 각 stage의 출력을
 * 다음 stage가 {@link HarnessContext}로 참조할 수 있게 누적합니다.
 *
 * <h3>두 가지 실행 모드</h3>
 * <ul>
 *   <li>{@link #run} — 비스트리밍, 전체 결과를 {@link HarnessRunResult}로 반환</li>
 *   <li>{@link #runStream} — SSE 스트리밍, chunk를 {@code onChunk} 콜백으로 흘려보냄</li>
 * </ul>
 *
 * <h3>스트리밍 마커</h3>
 * 스트리밍 모드는 stage 경계마다 다음 sentinel을 자동 emit합니다 (1-based index):
 * <pre>
 *   [[HARNESS_STAGE:1]]
 *   {streamHeader()}
 *   {Claude 응답 chunk들…}
 *   {streamFooter()}
 *
 *   [[HARNESS_STAGE:2]]
 *   …
 * </pre>
 * 프론트엔드는 이 마커로 stage 패널을 분리합니다.
 *
 * <h3>토큰 예산 / 이어쓰기</h3>
 * stage의 {@link HarnessStage#continuations()}가 0보다 크면
 * {@link ClaudeClient#chatWithContinuation}/{@code chatStreamWithContinuation}로 호출되어
 * 응답이 {@code max_tokens}로 잘리면 자동 이어쓰기 됩니다.
 *
 * <h3>메트릭 / 감사</h3>
 * {@link ToolkitMetrics}와 {@link HarnessAuditWriter}는 옵셔널입니다 — 없어도 정상 동작.
 */
@Service
public class HarnessOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(HarnessOrchestrator.class);

    /** 단계 경계 sentinel — 프론트가 stage 패널 전환에 사용. */
    public static final String STAGE_SENTINEL_PREFIX = "[[HARNESS_STAGE:";
    public static final String STAGE_SENTINEL_SUFFIX = "]]";

    private final ClaudeClient claudeClient;

    @Autowired(required = false)
    private ToolkitMetrics metrics;

    @Autowired(required = false)
    private HarnessAuditWriter auditWriter;

    public HarnessOrchestrator(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    // ── 비스트리밍 실행 ─────────────────────────────────────────────────────────

    /**
     * stage 목록을 순차 실행하고 전체 결과를 반환합니다.
     *
     * @param harnessName  하네스 식별자 (메트릭/감사용)
     * @param stages       실행할 stage 목록 (순서대로)
     * @param inputs       사용자 입력 묶음 — stage가 키로 조회
     * @param memo         프로젝트 컨텍스트 메모 (없으면 빈 문자열)
     * @param templateHint 분석 템플릿 힌트 (없으면 빈 문자열)
     * @return 전체 실행 결과 — stage별 출력 + 경과 시간 포함
     */
    public HarnessRunResult run(String harnessName, List<HarnessStage> stages,
                                Map<String, Object> inputs, String memo, String templateHint) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }
        String runId = UUID.randomUUID().toString();
        HarnessContext ctx = new HarnessContext(harnessName, runId, inputs, memo, templateHint);
        List<HarnessStageResult> results = new ArrayList<HarnessStageResult>();
        long totalT0 = System.currentTimeMillis();

        for (HarnessStage stage : stages) {
            long t0 = System.currentTimeMillis();
            try {
                String system = stage.buildSystem(ctx);
                String user   = stage.buildUser(ctx);
                String raw;
                if (stage.continuations() > 0) {
                    raw = claudeClient.chatWithContinuation(
                            system, user, stage.maxTokens(), stage.continuations());
                } else {
                    raw = claudeClient.chat(system, user, stage.maxTokens());
                }
                String processed = stage.postProcess(raw);
                long elapsed = System.currentTimeMillis() - t0;

                ctx.putStageOutput(stage.name(), processed);
                results.add(new HarnessStageResult(stage.name(), processed, elapsed, null));
                recordMetric(stage.name(), harnessName, elapsed);
                writeAudit(harnessName, runId, stage.name(), system, user, processed, elapsed, null);
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - t0;
                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("[Harness:{}] stage={} 실패 ({}ms): {}", harnessName, stage.name(), elapsed, err);
                results.add(new HarnessStageResult(stage.name(), "", elapsed, err));
                writeAudit(harnessName, runId, stage.name(), null, null, null, elapsed, err);
                return new HarnessRunResult(runId, harnessName, results,
                        System.currentTimeMillis() - totalT0,
                        "stage '" + stage.name() + "' 실패: " + err);
            }
        }
        return new HarnessRunResult(runId, harnessName, results,
                System.currentTimeMillis() - totalT0, null);
    }

    // ── SSE 스트리밍 실행 ──────────────────────────────────────────────────────

    /**
     * stage 목록을 순차 스트리밍 실행하며, 모든 chunk를 {@code onChunk}로 흘려보냅니다.
     *
     * <p>stage 경계마다 {@code [[HARNESS_STAGE:N]]\n} sentinel이 자동 emit됩니다.
     * stage의 {@link HarnessStage#streamHeader()}/{@code streamFooter()}도 emit됩니다.
     *
     * @param onChunk 각 텍스트 chunk를 받을 콜백
     */
    public void runStream(String harnessName, List<HarnessStage> stages,
                          Map<String, Object> inputs, String memo, String templateHint,
                          Consumer<String> onChunk) throws IOException {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }
        if (onChunk == null) {
            throw new IllegalArgumentException("onChunk must not be null");
        }
        String runId = UUID.randomUUID().toString();
        HarnessContext ctx = new HarnessContext(harnessName, runId, inputs, memo, templateHint);

        for (int i = 0; i < stages.size(); i++) {
            HarnessStage stage = stages.get(i);
            long t0 = System.currentTimeMillis();

            // 단계 경계 sentinel + 헤더
            String sentinel = STAGE_SENTINEL_PREFIX + (i + 1) + STAGE_SENTINEL_SUFFIX + "\n";
            onChunk.accept(i == 0 ? sentinel : "\n\n" + sentinel);
            String header = stage.streamHeader();
            if (header != null && !header.isEmpty()) onChunk.accept(header);

            String system = stage.buildSystem(ctx);
            String user   = stage.buildUser(ctx);
            final StringBuilder buf = new StringBuilder();
            Consumer<String> bufferingChunk = chunk -> {
                buf.append(chunk);
                onChunk.accept(chunk);
            };

            if (stage.continuations() > 0) {
                claudeClient.chatStreamWithContinuation(
                        system, user, stage.maxTokens(), stage.continuations(), bufferingChunk);
            } else {
                claudeClient.chatStream(system, user, stage.maxTokens(), bufferingChunk);
            }

            String footer = stage.streamFooter();
            if (footer != null && !footer.isEmpty()) onChunk.accept(footer);

            String processed = stage.postProcess(buf.toString());
            ctx.putStageOutput(stage.name(), processed);

            long elapsed = System.currentTimeMillis() - t0;
            recordMetric(stage.name(), harnessName, elapsed);
            writeAudit(harnessName, runId, stage.name(), system, user, processed, elapsed, null);
        }
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private void recordMetric(String stageName, String harnessName, long elapsedMs) {
        if (metrics != null) {
            try {
                metrics.recordHarnessStage(stageName, harnessName, elapsedMs);
            } catch (Exception e) {
                log.debug("[Harness] metric record 실패: {}", e.getMessage());
            }
        }
    }

    private void writeAudit(String harnessName, String runId, String stageName,
                            String system, String user, String output, long elapsedMs, String error) {
        if (auditWriter != null) {
            try {
                auditWriter.writeStage(harnessName, runId, stageName, system, user, output, elapsedMs, error);
            } catch (Exception e) {
                log.debug("[Harness] audit write 실패: {}", e.getMessage());
            }
        }
    }
}
