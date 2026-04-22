package io.github.claudetoolkit.ui.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** v4.4.0 — SSE 동시 연결 수 (Gauge 의 source 값). */
    private final AtomicInteger sseConnections = new AtomicInteger(0);

    public ToolkitMetrics(MeterRegistry registry) {
        this.registry = registry;
        // 시작 시점에 0 값으로 등록 — Grafana 패널이 "no data" 상태가 아닌 0 으로 표시됨
        warmup();
        // v4.4.0: SSE 연결 수 Gauge 등록
        Gauge.builder("notification.sse.connections", sseConnections, AtomicInteger::doubleValue)
                .description("현재 활성 SSE (Server-Sent Events) 연결 수")
                .register(registry);
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

    // ── 5. v4.4.0 신규: 파이프라인 단계별 카운트 ──────────────────────────

    /**
     * @param stepType  분석 유형 (CODE_REVIEW / SQL_REVIEW / ...)
     * @param status    "success" | "failure" | "skipped"
     */
    public void recordPipelineStep(String stepType, String status) {
        getCounter("pipeline.step.executions",
                Tags.of("stepType", safe(stepType), "status", safe(status))).increment();
    }

    // ── 6. v4.4.0 신규: 캐시 히트/미스 ─────────────────────────────────────

    /** @param cacheName 캐시 식별자 (예: "harness", "analysis") */
    public void recordCacheHit(String cacheName) {
        getCounter("claude.cache.hits", Tags.of("cache", safe(cacheName))).increment();
    }

    public void recordCacheMiss(String cacheName) {
        getCounter("claude.cache.misses", Tags.of("cache", safe(cacheName))).increment();
    }

    // ── 7. v4.4.0 신규: 하네스 4단계 처리 시간 ─────────────────────────────

    /**
     * Harness 파이프라인의 각 단계 (analyst/builder/reviewer/verifier) 별 측정.
     * @param stage    단계 이름 (소문자 권장)
     * @param language "java" / "sql" 등
     */
    public void recordHarnessStage(String stage, String language, long millis) {
        getTimer("harness.stage.duration",
                Tags.of("stage", safe(stage), "language", safe(language)))
                .record(millis, TimeUnit.MILLISECONDS);
    }

    // ── 8. v4.4.0 신규: SSE 연결 카운트 (Gauge) ───────────────────────────

    public void incrementSseConnections() { sseConnections.incrementAndGet(); }
    public void decrementSseConnections() {
        // 음수 방지
        sseConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    // ── 9. v4.4.0 신규: 에러 발생률 (#4 와 연동) ──────────────────────────

    /**
     * GlobalExceptionHandler / ErrorLogService 에서 호출 — Grafana 알림 트리거.
     * @param exceptionClass 예외 클래스 단축명
     * @param requestPath    요청 경로 (path 별 알람을 위해 태그)
     */
    public void recordError(String exceptionClass, String requestPath) {
        getCounter("claude.errors",
                Tags.of("exception", safe(exceptionClass),
                        "path",      safe(requestPath))).increment();
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
        // v4.4.0 신규
        getCounter("claude.cache.hits",   Tags.of("cache", "unknown"));
        getCounter("claude.cache.misses", Tags.of("cache", "unknown"));
        getCounter("claude.errors",       Tags.of("exception", "unknown", "path", "unknown"));
        getCounter("pipeline.step.executions",
                Tags.of("stepType", "unknown", "status", "success"));
        // 분석 시간 / 하네스 단계 Timer 는 첫 호출 시 자동 등록 — warmup 불필요
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
