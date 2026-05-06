package io.github.claudetoolkit.ui.livedb;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * v4.7.x — #G3 Live DB Phase 5: 프로필별 메모리 내 호출 통계.
 *
 * <p>{@link LiveDbContextService} / {@link IndexSimulatorService} 가 호출 직후
 * {@link #recordSuccess} / {@link #recordFailure} / {@link #recordTimeout} 를 호출.
 * {@code GET /api/v1/admin/livedb/stats} 가 헬스 대시보드용 데이터로 직렬화.
 *
 * <p><b>비영속</b>: JVM 라이프타임 동안만 누적. 서버 재시작시 0 으로 리셋. 운영
 * 모니터링용으로는 충분 (장기 추세는 Prometheus 메트릭 별도 — Phase 5 범위 밖).
 *
 * <p>동시성: ConcurrentHashMap + AtomicLong — 락 없는 누적.
 */
@Component
public class LiveDbCallStats {

    /** 프로필 ID → 누적 카운터 */
    private final Map<Long, ProfileStats> byProfile = new ConcurrentHashMap<Long, ProfileStats>();

    public void recordSuccess(Long profileId, long latencyMs) {
        get(profileId).recordSuccess(latencyMs);
    }

    public void recordFailure(Long profileId, long latencyMs) {
        get(profileId).recordFailure(latencyMs);
    }

    public void recordTimeout(Long profileId, long latencyMs) {
        get(profileId).recordTimeout(latencyMs);
    }

    /**
     * 프로필별 + 합계 통계를 헬스 대시보드용 Map 으로.
     * 키: "byProfile" → List of {profileId, success, failure, timeout, avgLatencyMs, p95LatencyMs?}
     *     "total"     → {success, failure, timeout, totalCalls}
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();

        long totalSuccess = 0, totalFailure = 0, totalTimeout = 0;
        List<Map<String, Object>> profilesList = new ArrayList<Map<String, Object>>();

        for (Map.Entry<Long, ProfileStats> e : byProfile.entrySet()) {
            ProfileStats ps = e.getValue();
            long success = ps.success.get();
            long failure = ps.failure.get();
            long timeout = ps.timeout.get();
            long totalCalls = success + failure + timeout;
            long sumLatency = ps.sumLatencyMs.get();
            long avgLatency = totalCalls > 0 ? sumLatency / totalCalls : 0;

            Map<String, Object> p = new LinkedHashMap<String, Object>();
            p.put("profileId",     e.getKey());
            p.put("success",       success);
            p.put("failure",       failure);
            p.put("timeout",       timeout);
            p.put("totalCalls",    totalCalls);
            p.put("avgLatencyMs",  avgLatency);
            p.put("lastCallMillis", ps.lastCallMillis.get());
            profilesList.add(p);

            totalSuccess += success;
            totalFailure += failure;
            totalTimeout += timeout;
        }

        Map<String, Object> total = new LinkedHashMap<String, Object>();
        total.put("success",   totalSuccess);
        total.put("failure",   totalFailure);
        total.put("timeout",   totalTimeout);
        total.put("totalCalls", totalSuccess + totalFailure + totalTimeout);

        result.put("byProfile", profilesList);
        result.put("total",     total);
        return result;
    }

    /** 테스트 / 어드민 디버깅 용도 — 모든 카운터 초기화 */
    public void reset() {
        byProfile.clear();
    }

    /** 특정 프로필의 *모든 timeout 발생 시각* 반환 — CircuitBreaker 가 sliding window 검사용 */
    public List<Long> getRecentTimeoutTimestamps(Long profileId) {
        ProfileStats ps = byProfile.get(profileId);
        if (ps == null) return new ArrayList<Long>();
        synchronized (ps.timeoutHistory) {
            return new ArrayList<Long>(ps.timeoutHistory);
        }
    }

    private ProfileStats get(Long profileId) {
        // computeIfAbsent — atomic 으로 첫 entry 만 1번 생성
        return byProfile.computeIfAbsent(
                profileId, k -> new ProfileStats());
    }

    // ── inner class ────────────────────────────────────────────────────────

    private static class ProfileStats {
        final AtomicLong success      = new AtomicLong(0);
        final AtomicLong failure      = new AtomicLong(0);
        final AtomicLong timeout      = new AtomicLong(0);
        final AtomicLong sumLatencyMs = new AtomicLong(0);
        final AtomicLong lastCallMillis = new AtomicLong(0);
        /** 최근 timeout 발생 시각 (ms) — CircuitBreaker 가 윈도우 검사. 보존: 최근 100건. */
        final java.util.Deque<Long> timeoutHistory = new java.util.ArrayDeque<Long>(100);

        void recordSuccess(long latencyMs) {
            success.incrementAndGet();
            sumLatencyMs.addAndGet(latencyMs);
            lastCallMillis.set(System.currentTimeMillis());
        }
        void recordFailure(long latencyMs) {
            failure.incrementAndGet();
            sumLatencyMs.addAndGet(latencyMs);
            lastCallMillis.set(System.currentTimeMillis());
        }
        void recordTimeout(long latencyMs) {
            timeout.incrementAndGet();
            sumLatencyMs.addAndGet(latencyMs);
            lastCallMillis.set(System.currentTimeMillis());
            synchronized (timeoutHistory) {
                timeoutHistory.addLast(System.currentTimeMillis());
                while (timeoutHistory.size() > 100) timeoutHistory.removeFirst();
            }
        }
    }
}
