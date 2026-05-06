package io.github.claudetoolkit.ui.livedb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v4.7.x — #G3 Live DB Phase 5: 프로필별 자동 회로차단기.
 *
 * <p><b>정책</b>: <b>10분 내 timeout 5건</b> 발생 시 해당 프로필을 5분 자동 비활성.
 * 만료 후 자동 복구 (사용자가 재시도하면 다시 정상 호출).
 *
 * <p>이 클래스는 *단순한 in-memory 구현* — 분산 환경 (multi-instance) 에선 각
 * instance 가 독립 breaker 를 가짐. 운영팀이 모든 instance 의 breaker 를 한 번에
 * 끄려면 application restart 필요.
 *
 * <p>설계 의도: Live DB 가 일시 장애를 만나면 (DBA maintenance / 네트워크 단절 등)
 * 모든 사용자가 timeout 으로 30초씩 묶이는 것을 방지. 자동 5분 휴면 후 복구 시도.
 */
@Component
public class LiveDbCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(LiveDbCircuitBreaker.class);

    /** 윈도우: 최근 N 분 내 timeout 카운트 */
    static final long WINDOW_MS  = 10 * 60 * 1000L;  // 10 min
    /** 한계: 윈도우 내 이만큼 timeout 시 회로 차단 */
    static final int  TIMEOUT_THRESHOLD = 5;
    /** 차단 지속 시간 — 이후 자동 복구 시도 */
    static final long OPEN_DURATION_MS = 5 * 60 * 1000L;  // 5 min

    private final LiveDbCallStats stats;
    /** 프로필 ID → 차단 시작 시각 (0 이면 차단 안 됨) */
    private final Map<Long, Long> openedAt = new ConcurrentHashMap<Long, Long>();

    public LiveDbCircuitBreaker(LiveDbCallStats stats) {
        this.stats = stats;
    }

    /**
     * 프로필이 현재 차단 상태인지 확인. true 면 호출 거부.
     */
    public boolean isOpen(Long profileId) {
        Long opened = openedAt.get(profileId);
        if (opened == null) {
            // 차단된 적 없음 — 단, 현재 timeout 임계 초과 여부도 즉시 검사 (lazy open)
            if (recentTimeoutCount(profileId) >= TIMEOUT_THRESHOLD) {
                openedAt.put(profileId, System.currentTimeMillis());
                log.warn("[LiveDbCircuitBreaker] 프로필 {} 자동 차단 — 최근 {}분 timeout {}건",
                         profileId, WINDOW_MS / 60000, TIMEOUT_THRESHOLD);
                return true;
            }
            return false;
        }
        // 이미 차단된 상태 — 만료 시각 검사
        long elapsed = System.currentTimeMillis() - opened;
        if (elapsed >= OPEN_DURATION_MS) {
            // 자동 복구
            openedAt.remove(profileId);
            log.info("[LiveDbCircuitBreaker] 프로필 {} 자동 복구 ({}분 경과)",
                     profileId, OPEN_DURATION_MS / 60000);
            return false;
        }
        return true;
    }

    /** 차단까지 남은 시간 (초) — 헬스 대시보드 표시용. 차단 안 됐으면 0. */
    public long secondsUntilHalfOpen(Long profileId) {
        Long opened = openedAt.get(profileId);
        if (opened == null) return 0;
        long remaining = OPEN_DURATION_MS - (System.currentTimeMillis() - opened);
        return Math.max(0, remaining / 1000);
    }

    /** ADMIN 이 강제 복구 */
    public void forceClose(Long profileId) {
        openedAt.remove(profileId);
        log.info("[LiveDbCircuitBreaker] 프로필 {} ADMIN 강제 복구", profileId);
    }

    /** 모든 회로 초기화 (테스트용) */
    public void reset() {
        openedAt.clear();
    }

    private int recentTimeoutCount(Long profileId) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        int count = 0;
        for (Long ts : stats.getRecentTimeoutTimestamps(profileId)) {
            if (ts >= cutoff) count++;
        }
        return count;
    }
}
