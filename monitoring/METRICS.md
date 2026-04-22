# Claude Java Toolkit — Prometheus 메트릭 카탈로그

> v4.4.0 기준 — `/actuator/prometheus` 엔드포인트로 노출되는 모든 메트릭의 정의 + Grafana 쿼리 예시.

## 📊 노출 방식

```bash
# 직접 확인
curl http://<host>:8027/actuator/prometheus | grep claude_

# Prometheus 스크랩 (15초 간격)
docker-compose --profile monitoring up -d
# → http://localhost:9090/graph
# → http://localhost:3000 (Grafana, admin/admin)
```

---

## 🟢 도메인 메트릭 (ToolkitMetrics)

### Claude API 호출

| 메트릭 | 타입 | 태그 | 의미 |
|--------|------|------|------|
| `claude_api_calls_total` | Counter | `model`, `feature`, `status` | Claude API 호출 횟수 |
| `claude_api_tokens_total` | Counter | `model`, `direction` (input/output) | 누적 토큰 사용량 |

**예시 쿼리**:
```promql
# 시간당 호출 수
sum(increase(claude_api_calls_total[1h]))

# 모델별 호출 비율
sum by (model) (rate(claude_api_calls_total[5m]))

# 24시간 토큰 합계 (input + output)
sum(increase(claude_api_tokens_total[24h]))

# 비용 추정 (Sonnet 기준 $3/$15 per 1M)
(sum by (direction) (increase(claude_api_tokens_total{direction="input"}[24h])) / 1e6) * 3
+ (sum by (direction) (increase(claude_api_tokens_total{direction="output"}[24h])) / 1e6) * 15
```

### 분석 처리 시간

| 메트릭 | 타입 | 태그 | 의미 |
|--------|------|------|------|
| `analysis_duration_seconds` | Timer | `type` (SQL_REVIEW / DOC_GEN / ...) | 분석 유형별 처리 시간 (히스토그램 + p50/p95/p99) |

**예시 쿼리**:
```promql
# p95 처리 시간 (분석 유형별)
histogram_quantile(0.95,
  sum by (type, le) (rate(analysis_duration_seconds_bucket[5m])))

# 분당 처리량
sum by (type) (rate(analysis_duration_seconds_count[1m]) * 60)
```

### Harness 4단계 (v4.4.0 신규)

| 메트릭 | 타입 | 태그 | 의미 |
|--------|------|------|------|
| `harness_stage_duration_seconds` | Timer | `stage` (analyst/builder/reviewer/verifier), `language` (java/sql) | 4단계 각각의 처리 시간 |

**활용**: Builder 단계가 평균보다 오래 걸리면 BUILDER_CONTINUATIONS 튜닝 신호

```promql
# 단계별 평균 시간 비교
avg by (stage) (rate(harness_stage_duration_seconds_sum[10m])
              / rate(harness_stage_duration_seconds_count[10m]))
```

### 파이프라인

| 메트릭 | 타입 | 태그 | 의미 |
|--------|------|------|------|
| `pipeline_execution_total` | Counter | `status` (success/failure) | 파이프라인 전체 실행 횟수 |
| `pipeline_step_executions_total` | Counter | `stepType`, `status` | 단계별 실행 횟수 (v4.4.0) |

```promql
# 24시간 성공률
sum(increase(pipeline_execution_total{status="success"}[24h]))
/ clamp_min(sum(increase(pipeline_execution_total[24h])), 1) * 100
```

### 캐시 (v4.4.0 신규)

| 메트릭 | 타입 | 태그 | 의미 |
|--------|------|------|------|
| `claude_cache_hits_total` | Counter | `cache` | 캐시 히트 횟수 |
| `claude_cache_misses_total` | Counter | `cache` | 캐시 미스 횟수 |

```promql
# 캐시 히트율 (%)
sum(rate(claude_cache_hits_total[5m]))
/ (sum(rate(claude_cache_hits_total[5m])) + sum(rate(claude_cache_misses_total[5m])))
* 100
```

### SSE 연결 수 (v4.4.0 신규)

| 메트릭 | 타입 | 의미 |
|--------|------|------|
| `notification_sse_connections` | Gauge | 현재 활성 SSE 연결 수 |

### 에러 발생률 (v4.4.0 신규 — #4 ErrorLog 와 연동)

| 메트릭 | 타입 | 태그 | 의미 |
|--------|------|------|------|
| `claude_errors_total` | Counter | `exception`, `path` | 예외별/경로별 에러 발생 |

**Grafana 알람 추천**:
```promql
# 5분 내 에러율이 분당 1건 초과
sum(rate(claude_errors_total[5m])) > 1/60
```

---

## 🔵 Spring Boot Actuator 자동 메트릭 (참고)

| 메트릭 | 의미 |
|--------|------|
| `http_server_requests_seconds_*` | HTTP 요청 시간 (uri/method/status 별) |
| `jvm_memory_used_bytes` | JVM 메모리 사용량 (heap/non-heap) |
| `jvm_gc_pause_seconds_*` | GC 일시정지 시간 |
| `tomcat_threads_busy` | Tomcat 작업 스레드 |
| `hikaricp_connections_active` | DB 커넥션 풀 활성 수 |

```promql
# HTTP p95 응답 시간 (URI 별)
histogram_quantile(0.95,
  sum by (uri, le) (rate(http_server_requests_seconds_bucket[5m])))

# JVM Heap 사용률
sum(jvm_memory_used_bytes{area="heap"})
/ sum(jvm_memory_max_bytes{area="heap"}) * 100
```

---

## 🎛 기본 Grafana 대시보드 — "Claude Toolkit Overview"

자동 프로비저닝되는 대시보드 (총 18 패널):

### 섹션 1: Claude API
- Claude API 호출 1h (Stat)
- 토큰 사용 24h (Stat)
- API 호출 추이 모델별 (Time series)
- 토큰 사용 추이 input vs output (Time series)

### 섹션 2: 분석 처리 시간
- 분석 시간 p95 분석 유형별 (Time series)
- 분석 처리량 분당 (Bar chart)

### 섹션 3: 파이프라인 + JVM
- 파이프라인 성공률 24h (Stat)
- JVM Heap 사용량 (Time series)
- HTTP p95 응답 시간 (Time series)

### 섹션 4: v4.4.0 운영 가시성 (신규 4패널)
- **캐시 히트율 분당** — 히트/미스 시계열 비교
- **SSE 동시 연결** — 게이지 (50/100 임계값)
- **에러 발생률 예외별** — 시계열 스택
- **하네스 단계별 처리 시간 p95** — analyst/builder/reviewer/verifier 비교
- **파이프라인 단계 처리량** — stepType 별 분당

---

## 🔔 권장 알림 (Prometheus AlertManager 또는 Grafana Alerts)

```yaml
# 1. 분당 에러 1건 초과 (5분 평균)
- alert: HighErrorRate
  expr: sum(rate(claude_errors_total[5m])) * 60 > 1
  for: 5m

# 2. JVM Heap 90% 초과
- alert: HighHeapUsage
  expr: sum(jvm_memory_used_bytes{area="heap"})
       / sum(jvm_memory_max_bytes{area="heap"}) > 0.9
  for: 10m

# 3. SSE 연결 100건 초과 (메모리 leak 의심)
- alert: TooManySseConnections
  expr: notification_sse_connections > 100
  for: 5m

# 4. 캐시 히트율 30% 미만 (캐시 효과 없음)
- alert: LowCacheHitRate
  expr: sum(rate(claude_cache_hits_total[10m]))
      / (sum(rate(claude_cache_hits_total[10m])) + sum(rate(claude_cache_misses_total[10m])))
      < 0.3
  for: 30m

# 5. 파이프라인 성공률 80% 미만
- alert: LowPipelineSuccess
  expr: sum(increase(pipeline_execution_total{status="success"}[1h]))
      / clamp_min(sum(increase(pipeline_execution_total[1h])), 1)
      < 0.8
  for: 15m
```

---

## 🛠 신규 메트릭 추가 가이드

`ToolkitMetrics` 에 메서드 추가 → 호출처에서 `metrics.recordXxx()` → 자동으로 Prometheus 노출.

```java
// 예: 새 카운터
public void recordSomething(String tag) {
    getCounter("claude.something", Tags.of("kind", safe(tag))).increment();
}

// warmup() 에 추가하면 시작 시점부터 0 값으로 표시
```

신규 메트릭은 이 문서에도 추가해주세요.
