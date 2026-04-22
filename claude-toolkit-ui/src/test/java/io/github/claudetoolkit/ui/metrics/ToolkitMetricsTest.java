package io.github.claudetoolkit.ui.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.4.0 — ToolkitMetrics 단위 테스트.
 *
 * <p>SimpleMeterRegistry 로 빠른 검증 — Counter / Timer 등록 + 카운트 증가 + warmup 동작.
 */
class ToolkitMetricsTest {

    private MeterRegistry registry;
    private ToolkitMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ToolkitMetrics(registry);
    }

    @Test
    @DisplayName("warmup — 시작 시점에 0 값 메트릭 등록 (Grafana no-data 회피)")
    void warmupPreRegistersCounters() {
        // warmup() 이 등록한 카운터들 — count() == 0 으로 존재해야 함
        Counter calls = registry.find("claude.api.calls")
                .tag("model", "unknown")
                .tag("feature", "unknown")
                .tag("status", "success")
                .counter();
        assertNotNull(calls, "claude.api.calls warmup 카운터");
        assertEquals(0.0, calls.count());

        assertNotNull(registry.find("pipeline.execution").tag("status", "success").counter());
        assertNotNull(registry.find("pipeline.execution").tag("status", "failure").counter());
    }

    @Test
    @DisplayName("recordClaudeApiCall — 카운터 증가")
    void recordApiCallIncrements() {
        metrics.recordClaudeApiCall("claude-sonnet-4", "sql_review", "success");
        metrics.recordClaudeApiCall("claude-sonnet-4", "sql_review", "success");

        Counter c = registry.find("claude.api.calls")
                .tag("model",   "claude-sonnet-4")
                .tag("feature", "sql_review")
                .tag("status",  "success")
                .counter();
        assertNotNull(c);
        assertEquals(2.0, c.count());
    }

    @Test
    @DisplayName("recordClaudeTokens — input/output 별도 누적")
    void recordTokensSeparate() {
        metrics.recordClaudeTokens("claude-haiku-4", 1500, 300);
        metrics.recordClaudeTokens("claude-haiku-4", 500,  100);

        Counter in = registry.find("claude.api.tokens")
                .tag("model", "claude-haiku-4").tag("direction", "input").counter();
        Counter out = registry.find("claude.api.tokens")
                .tag("model", "claude-haiku-4").tag("direction", "output").counter();
        assertEquals(2000.0, in.count());
        assertEquals(400.0,  out.count());
    }

    @Test
    @DisplayName("recordClaudeTokens — 0 / 음수 입력 시 무시 (안전)")
    void zeroOrNegativeTokensIgnored() {
        metrics.recordClaudeTokens("m", 0, 0);
        metrics.recordClaudeTokens("m", -1, -1);

        // 카운터가 등록되지 않았거나, 등록되었어도 count == 0
        Counter in = registry.find("claude.api.tokens")
                .tag("model", "m").tag("direction", "input").counter();
        if (in != null) assertEquals(0.0, in.count());
    }

    @Test
    @DisplayName("startAnalysis + stopAnalysis — Timer 측정")
    void analysisTimerMeasures() {
        Timer.Sample sample = metrics.startAnalysis();
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        metrics.stopAnalysis(sample, "SQL_REVIEW");

        Timer t = registry.find("analysis.duration").tag("type", "SQL_REVIEW").timer();
        assertNotNull(t);
        assertEquals(1L, t.count());
        assertTrue(t.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 10);
    }

    @Test
    @DisplayName("stopAnalysis(null) — NPE 없이 안전 무시")
    void stopAnalysisNullSafe() {
        assertDoesNotThrow(() -> metrics.stopAnalysis(null, "X"));
    }

    @Test
    @DisplayName("recordPipelineExecution — success/failure 분리 카운트")
    void pipelineExecutionStatuses() {
        metrics.recordPipelineExecution("success");
        metrics.recordPipelineExecution("success");
        metrics.recordPipelineExecution("failure");

        Counter ok = registry.find("pipeline.execution").tag("status", "success").counter();
        Counter fail = registry.find("pipeline.execution").tag("status", "failure").counter();
        assertEquals(2.0, ok.count());
        assertEquals(1.0, fail.count());
    }

    @Test
    @DisplayName("safe() — null/빈 문자열 → 'unknown' 으로 변환")
    void nullTagSafe() {
        metrics.recordClaudeApiCall(null, "", "success");
        Counter c = registry.find("claude.api.calls")
                .tag("model", "unknown")
                .tag("feature", "unknown")
                .tag("status", "success").counter();
        assertNotNull(c);
        assertTrue(c.count() >= 1.0);
    }

    @Test
    @DisplayName("동일 태그 조합 재호출 — 같은 카운터 재사용 (캐시 동작)")
    void counterCacheReuse() {
        metrics.recordClaudeApiCall("m", "f", "success");
        long countBefore = registry.getMeters().size();
        metrics.recordClaudeApiCall("m", "f", "success");
        metrics.recordClaudeApiCall("m", "f", "success");
        long countAfter = registry.getMeters().size();
        assertEquals(countBefore, countAfter, "동일 태그 → 미터 개수 증가 X");
    }

    // ── v4.4.0 신규 메트릭 ──────────────────────────────────────────────

    @Test
    @DisplayName("v4.4.0 — recordCacheHit/Miss 카운터")
    void cacheMetrics() {
        metrics.recordCacheHit("analysis");
        metrics.recordCacheHit("analysis");
        metrics.recordCacheMiss("analysis");

        Counter hits = registry.find("claude.cache.hits").tag("cache", "analysis").counter();
        Counter miss = registry.find("claude.cache.misses").tag("cache", "analysis").counter();
        assertEquals(2.0, hits.count());
        assertEquals(1.0, miss.count());
    }

    @Test
    @DisplayName("v4.4.0 — recordHarnessStage Timer (4단계)")
    void harnessStageTimer() {
        metrics.recordHarnessStage("analyst", "java", 100);
        metrics.recordHarnessStage("builder", "java", 500);

        io.micrometer.core.instrument.Timer t = registry.find("harness.stage.duration")
                .tag("stage", "analyst").tag("language", "java").timer();
        assertNotNull(t);
        assertEquals(1L, t.count());
    }

    @Test
    @DisplayName("v4.4.0 — SSE 연결 Gauge: increment/decrement")
    void sseConnectionsGauge() {
        metrics.incrementSseConnections();
        metrics.incrementSseConnections();
        metrics.incrementSseConnections();
        metrics.decrementSseConnections();

        io.micrometer.core.instrument.Gauge g = registry.find("notification.sse.connections").gauge();
        assertNotNull(g);
        assertEquals(2.0, g.value());

        // 음수 방지
        metrics.decrementSseConnections();
        metrics.decrementSseConnections();
        metrics.decrementSseConnections();   // 이미 0 → 더 내려가지 않아야
        assertEquals(0.0, g.value());
    }

    @Test
    @DisplayName("v4.4.0 — recordError 예외별/경로별 카운트")
    void errorMetrics() {
        metrics.recordError("NullPointerException", "/api/v1/sql/review");
        metrics.recordError("NullPointerException", "/api/v1/sql/review");
        metrics.recordError("IllegalArgumentException", "/api/v1/code/review");

        Counter npe = registry.find("claude.errors")
                .tag("exception", "NullPointerException")
                .tag("path", "/api/v1/sql/review").counter();
        assertEquals(2.0, npe.count());
    }

    @Test
    @DisplayName("v4.4.0 — recordPipelineStep 단계별 카운트")
    void pipelineStepMetrics() {
        metrics.recordPipelineStep("CODE_REVIEW", "success");
        metrics.recordPipelineStep("CODE_REVIEW", "success");
        metrics.recordPipelineStep("SQL_REVIEW",  "failure");

        Counter okCr = registry.find("pipeline.step.executions")
                .tag("stepType", "CODE_REVIEW").tag("status", "success").counter();
        assertEquals(2.0, okCr.count());
    }
}
