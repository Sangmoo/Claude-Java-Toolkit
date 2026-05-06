package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 5: LiveDbRateLimiter 단위 테스트.
 *
 * <p>실제 시간 기반이라 sliding window 의 만료 검증은 mock clock 없이는 어려움 —
 * 여기선 카운팅 동작 + 비활성 + 사용자 분리 만 검증.
 */
class LiveDbRateLimiterTest {

    @Test
    @DisplayName("limit=10 — 10번까지 통과, 11번째 거부")
    void limitEnforced() {
        LiveDbConfig cfg = newConfig(10);
        LiveDbRateLimiter limiter = new LiveDbRateLimiter(cfg);

        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("alice", 1L),
                       "i=" + i + " 통과해야 함");
        }
        assertFalse(limiter.tryAcquire("alice", 1L),
                    "11번째 요청 거부");
    }

    @Test
    @DisplayName("limit=0 — 비활성 (모든 호출 통과)")
    void disabledLimit() {
        LiveDbConfig cfg = newConfig(0);
        LiveDbRateLimiter limiter = new LiveDbRateLimiter(cfg);
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryAcquire("alice", 1L));
        }
    }

    @Test
    @DisplayName("limit=-1 — 음수도 비활성 처리")
    void negativeLimitDisabled() {
        LiveDbConfig cfg = newConfig(-5);
        LiveDbRateLimiter limiter = new LiveDbRateLimiter(cfg);
        assertTrue(limiter.tryAcquire("alice", 1L));
    }

    @Test
    @DisplayName("사용자별 격리 — alice 가 다 써도 bob 은 통과")
    void perUserIsolation() {
        LiveDbConfig cfg = newConfig(2);
        LiveDbRateLimiter limiter = new LiveDbRateLimiter(cfg);

        assertTrue(limiter.tryAcquire("alice", 1L));
        assertTrue(limiter.tryAcquire("alice", 1L));
        assertFalse(limiter.tryAcquire("alice", 1L));  // alice 한도 도달

        // bob 은 별개 quota
        assertTrue(limiter.tryAcquire("bob", 1L));
        assertTrue(limiter.tryAcquire("bob", 1L));
    }

    @Test
    @DisplayName("프로필별 격리 — 같은 alice 라도 다른 프로필은 별도 quota")
    void perProfileIsolation() {
        LiveDbConfig cfg = newConfig(2);
        LiveDbRateLimiter limiter = new LiveDbRateLimiter(cfg);

        assertTrue(limiter.tryAcquire("alice", 1L));
        assertTrue(limiter.tryAcquire("alice", 1L));
        assertFalse(limiter.tryAcquire("alice", 1L));  // profile 1 한도

        assertTrue(limiter.tryAcquire("alice", 2L));   // profile 2 별개
        assertTrue(limiter.tryAcquire("alice", 2L));
    }

    @Test
    @DisplayName("null username — '(anon)' 으로 격리")
    void nullUsername() {
        LiveDbConfig cfg = newConfig(2);
        LiveDbRateLimiter limiter = new LiveDbRateLimiter(cfg);

        assertTrue(limiter.tryAcquire(null, 1L));
        assertTrue(limiter.tryAcquire(null, 1L));
        assertFalse(limiter.tryAcquire(null, 1L));   // anon 한도

        assertTrue(limiter.tryAcquire("alice", 1L)); // alice 별개
    }

    @Test
    @DisplayName("remaining — 남은 quota 정확 반환 + 비활성 시 -1")
    void remainingQuery() {
        LiveDbConfig cfg = newConfig(5);
        LiveDbRateLimiter limiter = new LiveDbRateLimiter(cfg);

        assertEquals(5, limiter.remaining("alice", 1L));
        limiter.tryAcquire("alice", 1L);
        assertEquals(4, limiter.remaining("alice", 1L));
        for (int i = 0; i < 4; i++) limiter.tryAcquire("alice", 1L);
        assertEquals(0, limiter.remaining("alice", 1L));

        // 비활성
        LiveDbConfig disabledCfg = newConfig(0);
        LiveDbRateLimiter disabled = new LiveDbRateLimiter(disabledCfg);
        assertEquals(-1, disabled.remaining("alice", 1L));
    }

    @Test
    @DisplayName("reset — 모든 윈도우 초기화")
    void resetClearsAll() {
        LiveDbConfig cfg = newConfig(2);
        LiveDbRateLimiter limiter = new LiveDbRateLimiter(cfg);
        limiter.tryAcquire("alice", 1L);
        limiter.tryAcquire("alice", 1L);
        assertFalse(limiter.tryAcquire("alice", 1L));

        limiter.reset();
        assertTrue(limiter.tryAcquire("alice", 1L), "reset 후 통과");
    }

    private static LiveDbConfig newConfig(int limit) {
        LiveDbConfig c = new LiveDbConfig();
        c.setMaxCallsPerMinute(limit);
        return c;
    }
}
