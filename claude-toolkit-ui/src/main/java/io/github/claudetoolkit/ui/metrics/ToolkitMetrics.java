package io.github.claudetoolkit.ui.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * v4.3.0 — Claude Java Toolkit 의 도메인 메트릭을 Micrometer 로 발행.
 *
 * <p>발행 메트릭 (Prometheus 포맷):
 * <ul>
 *   <li>{@code claude_api_calls_total{model,feature,status}} — Claude API 호출 횟수</li>
 *   <li>{@code claude_api_tokens_total{model,direction}} — Claude API 토큰 누적량 (input/output)</li>
 *   <li>{@code analysis_duration_seconds{type}} — 분석 유형별 처리 시간 분포 (히스토그램 + p50/p95/p99)</li>
 *   <li>{@code pipeline_execution_total{status}} — 파이프라인 실행 횟수 (success/failure)</li>
 * </ul>
 *
 * <p>호출 측 패턴:
 * <pre>
 *   metrics.recordClaudeApiCall("claude-sonnet-4", "sql_review", "success");
 *   metrics.recordClaudeTokens("claude-sonnet-4", 1234, 567);
 *   Timer.Sample sample = metrics.startAnalysis();
 *   // ... 분석 수행 ...
 *   metrics.stopAnalysis(sample, "SQL_REVIEW");
 *   metrics.recordPipelineExecution("success");
 * </pre>
 *
 * <p>호출처가 아직 미연결인 메트릭이라도 등록만 해두면 Grafana 에서 0 으로 표시되어
 * 대시보드 깨짐을 방지한다.
 */
@Component
public class ToolkitMetrics {

    private final MeterRegistry registry;

    /** Counter 캐싱 — 태그 조합마다 별도 객체 생성을 피해 hot path 부담 최소화 */
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer>   timerCache   = new ConcurrentHashMap<>();

    public ToolkitMetrics(MeterRegistry registry) {
        this.registry = registry;
        // 시작 시점에 0 값으로 등록 — Grafana 패널이 "no data" 상태가 아닌 0 으로 표시됨
        warmup();
    }

    // ── 1. Claude API 호출 카운터 ──────────────────────────────────────────

    /**
     * Claude API 호출 1회 기록.
     *
     * @param model   모델 식별자 (예: "claude-sonnet-4-20250514", "claude-haiku")
     * @param feature 기능 키 (예: "sql_review", "code_review", "doc_gen")
     * @param status  결과 (예: "success", "failure", "rate_limited")
     */
    public void recordClaudeApiCall(String model, String feature, String status) {
        getCounter("claude.api.calls",
                Tags.of("model",   safe(model),
                        "feature", safe(feature),
                        "status",  safe(status))).increment();
    }

    // ── 2. Claude 토큰 사용량 ──────────────────────────────────────────────

    /** 입력/출력 토큰 합산 누적 — 비용 계산의 기반 */
    public void recordClaudeTokens(String model, long inputTokens, long outputTokens) {
        if (inputTokens > 0) {
            getCounter("claude.api.tokens",
                    Tags.of("model", safe(model), "direction", "input")).increment(inputTokens);
        }
        if (outputTokens > 0) {
            getCounter("claude.api.tokens",
                    Tags.of("model", safe(model), "direction", "output")).increment(outputTokens);
        }
    }

    // ── 3. 분석 처리 시간 (히스토그램) ─────────────────────────────────────

    /** 분석 시작 시점에 호출 — 반환된 Sample 을 stopAnalysis 에 전달 */
    public Timer.Sample startAnalysis() {
        return Timer.start(registry);
    }

    /** 분석 종료 시점에 호출 — 분석 유형별로 별도 Timer 에 기록됨 */
    public void stopAnalysis(Timer.Sample sample, String analysisType) {
        if (sample == null) return;
        sample.stop(getTimer("analysis.duration", Tags.of("type", safe(analysisType))));
    }

    // ── 4. 파이프라인 실행 카운터 ──────────────────────────────────────────

    /**
     * @param status "success" | "failure" | "cancelled"
     */
    public void recordPipelineExecution(String status) {
        getCounter("pipeline.execution", Tags.of("status", safe(status))).increment();
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────

    private Counter getCounter(String name, Tags tags) {
        return getOrCreate(counterCache, name + "|" + tags,
                k -> Counter.builder(name).tags(tags).register(registry));
    }

    private Timer getTimer(String name, Tags tags) {
        return getOrCreate(timerCache, name + "|" + tags,
                k -> Timer.builder(name)
                        .tags(tags)
                        .publishPercentileHistogram()
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry));
    }

    private <T> T getOrCreate(ConcurrentHashMap<String, T> cache, String key, Function<String, T> factory) {
        T existing = cache.get(key);
        if (existing != null) return existing;
        return cache.computeIfAbsent(key, factory);
    }

    private String safe(String v) {
        return v != null && !v.isEmpty() ? v : "unknown";
    }

    /** 시작 시점에 자주 쓰는 메트릭을 0 값으로 미리 등록 — Grafana 패널 "no data" 회피 */
    private void warmup() {
        getCounter("claude.api.calls",
                Tags.of("model", "unknown", "feature", "unknown", "status", "success"));
        getCounter("claude.api.tokens",
                Tags.of("model", "unknown", "direction", "input"));
        getCounter("claude.api.tokens",
                Tags.of("model", "unknown", "direction", "output"));
        getCounter("pipeline.execution", Tags.of("status", "success"));
        getCounter("pipeline.execution", Tags.of("status", "failure"));
        // 분석 시간 Timer 는 첫 호출 시 자동 등록 — warmup 불필요
    }

    // ── 외부에서 직접 시간 측정이 필요한 경우 사용 ───────────────────────────

    /**
     * 이미 측정된 duration(ms) 을 직접 기록할 때.
     */
    public void recordAnalysisDuration(String analysisType, long millis) {
        getTimer("analysis.duration", Tags.of("type", safe(analysisType)))
                .record(millis, TimeUnit.MILLISECONDS);
    }
}
