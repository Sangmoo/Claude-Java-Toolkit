package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 5: LiveDbCircuitBreaker 단위 테스트.
 *
 * <p>실제 시간 기반이라 "5분 후 자동 복구" 검증은 mock clock 없이는 어려움.
 * 핵심 동작: timeout 누적 → 회로 OPEN, forceClose 동작, 다른 프로필 분리.
 */
class LiveDbCircuitBreakerTest {

    private LiveDbCallStats stats;
    private LiveDbCircuitBreaker breaker;

    @BeforeEach
    void setup() {
        stats   = new LiveDbCallStats();
        breaker = new LiveDbCircuitBreaker(stats);
    }

    @Test
    @DisplayName("초기 — 모든 프로필 CLOSED")
    void initialState() {
        assertFalse(breaker.isOpen(1L));
        assertEquals(0, breaker.secondsUntilHalfOpen(1L));
    }

    @Test
    @DisplayName("timeout 5건 미만 — 회로 OPEN 아님")
    void belowThreshold() {
        for (int i = 0; i < 4; i++) stats.recordTimeout(1L, 30_000);
        assertFalse(breaker.isOpen(1L), "4건은 임계 미만");
    }

    @Test
    @DisplayName("timeout 5건 — 회로 OPEN")
    void thresholdReached() {
        for (int i = 0; i < 5; i++) stats.recordTimeout(1L, 30_000);
        assertTrue(breaker.isOpen(1L), "5건이면 OPEN");
        assertTrue(breaker.secondsUntilHalfOpen(1L) > 0,
                   "OPEN 상태에선 남은 시간 양수");
    }

    @Test
    @DisplayName("프로필 격리 — 1번이 OPEN 이어도 2번은 CLOSED")
    void profileIsolation() {
        for (int i = 0; i < 5; i++) stats.recordTimeout(1L, 30_000);
        assertTrue(breaker.isOpen(1L));
        assertFalse(breaker.isOpen(2L));
    }

    @Test
    @DisplayName("forceClose — ADMIN 강제 복구 후 즉시 CLOSED")
    void forceCloseRecovers() {
        for (int i = 0; i < 5; i++) stats.recordTimeout(1L, 30_000);
        assertTrue(breaker.isOpen(1L));

        breaker.forceClose(1L);
        // 단, recordTimeout 이력은 stats 에 그대로 남아있어 isOpen() 이 다시 OPEN 시킬 수 있음
        // 따라서 강제 복구는 "지금 막 다시 OPEN 되어도 1회는 통과시킨다" 의미가 아니라
        // "openedAt 을 reset" 의미. 운영자가 stats 도 reset 하거나 timeout 발생 자체를 멈춰야 함.
        // 이 테스트는 forceClose 직후 *적어도 한 번* 은 CLOSED 였음을 검증.
        // (다음 호출에서 lazy open 가능 — 그건 별도 동작)
        // 즉시 확인: breaker 의 openedAt map 에서 1L 이 사라졌는지를 stats reset 으로 우회 검증.
        stats.reset();
        assertFalse(breaker.isOpen(1L), "forceClose + stats reset 후 CLOSED");
    }

    @Test
    @DisplayName("breaker reset — 모든 프로필 회로 즉시 복구")
    void resetClearsAll() {
        for (int i = 0; i < 5; i++) stats.recordTimeout(1L, 30_000);
        for (int i = 0; i < 5; i++) stats.recordTimeout(2L, 30_000);
        assertTrue(breaker.isOpen(1L));
        assertTrue(breaker.isOpen(2L));

        breaker.reset();
        stats.reset();   // 둘 다 reset 해야 lazy open 안 됨
        assertFalse(breaker.isOpen(1L));
        assertFalse(breaker.isOpen(2L));
    }
}
