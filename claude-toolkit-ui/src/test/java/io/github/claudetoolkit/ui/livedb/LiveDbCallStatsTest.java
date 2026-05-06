package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 5: LiveDbCallStats 단위 테스트.
 */
class LiveDbCallStatsTest {

    @Test
    @DisplayName("초기 — snapshot 의 byProfile 빈 + total 0")
    void initialEmpty() {
        LiveDbCallStats stats = new LiveDbCallStats();
        Map<String, Object> snap = stats.snapshot();
        assertNotNull(snap);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byProfile = (List<Map<String, Object>>) snap.get("byProfile");
        assertTrue(byProfile.isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> total = (Map<String, Object>) snap.get("total");
        assertEquals(0L, total.get("totalCalls"));
    }

    @Test
    @DisplayName("성공/실패/timeout 카운팅")
    void countingAllTypes() {
        LiveDbCallStats stats = new LiveDbCallStats();
        stats.recordSuccess(1L, 100);
        stats.recordSuccess(1L, 200);
        stats.recordFailure(1L, 50);
        stats.recordTimeout(1L, 30_000);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byProfile = (List<Map<String, Object>>) stats.snapshot().get("byProfile");
        assertEquals(1, byProfile.size());
        Map<String, Object> p = byProfile.get(0);
        assertEquals(1L, p.get("profileId"));
        assertEquals(2L, p.get("success"));
        assertEquals(1L, p.get("failure"));
        assertEquals(1L, p.get("timeout"));
        assertEquals(4L, p.get("totalCalls"));
    }

    @Test
    @DisplayName("avg latency 계산")
    void avgLatency() {
        LiveDbCallStats stats = new LiveDbCallStats();
        stats.recordSuccess(1L, 100);
        stats.recordSuccess(1L, 200);
        stats.recordSuccess(1L, 300);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byProfile = (List<Map<String, Object>>) stats.snapshot().get("byProfile");
        assertEquals(200L, byProfile.get(0).get("avgLatencyMs"), "(100+200+300)/3");
    }

    @Test
    @DisplayName("프로필 격리 — 1번 + 2번 별도 누적")
    void profileIsolation() {
        LiveDbCallStats stats = new LiveDbCallStats();
        stats.recordSuccess(1L, 100);
        stats.recordFailure(2L, 200);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byProfile = (List<Map<String, Object>>) stats.snapshot().get("byProfile");
        assertEquals(2, byProfile.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> total = (Map<String, Object>) stats.snapshot().get("total");
        assertEquals(1L, total.get("success"));
        assertEquals(1L, total.get("failure"));
        assertEquals(2L, total.get("totalCalls"));
    }

    @Test
    @DisplayName("getRecentTimeoutTimestamps — 최근 timeout 시각 보존")
    void recentTimeouts() {
        LiveDbCallStats stats = new LiveDbCallStats();
        stats.recordTimeout(1L, 30_000);
        stats.recordTimeout(1L, 30_000);
        stats.recordTimeout(1L, 30_000);

        List<Long> ts = stats.getRecentTimeoutTimestamps(1L);
        assertEquals(3, ts.size());
        // 모두 최근 1초 안에 발생
        for (Long t : ts) {
            assertTrue(System.currentTimeMillis() - t < 5000);
        }
    }

    @Test
    @DisplayName("reset — 모두 초기화")
    void resetClearsAll() {
        LiveDbCallStats stats = new LiveDbCallStats();
        stats.recordSuccess(1L, 100);
        stats.reset();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byProfile = (List<Map<String, Object>>) stats.snapshot().get("byProfile");
        assertTrue(byProfile.isEmpty());
    }
}
