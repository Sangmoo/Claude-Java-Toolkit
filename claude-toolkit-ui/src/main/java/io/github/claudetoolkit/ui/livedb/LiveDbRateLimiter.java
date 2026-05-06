package io.github.claudetoolkit.ui.livedb;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v4.7.x — #G3 Live DB Phase 5: 사용자 + 프로필 조합당 분당 호출 상한.
 *
 * <p>{@link LiveDbConfig#getMaxCallsPerMinute()} 가 0 이하면 비활성. 기본 10/min.
 * sliding window (최근 60초) 방식 — 자정 기준 reset 등 부정확한 fixed window 회피.
 *
 * <p><b>키 구성</b>: {@code <username>:<profileId>} — 같은 프로필을 여러 사용자가
 * 공유해도 사용자별로 격리된 quota. username 이 null 이면 "(anon)" 으로 fallback.
 */
@Component
public class LiveDbRateLimiter {

    private final LiveDbConfig config;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<String, Deque<Long>>();

    public LiveDbRateLimiter(LiveDbConfig config) {
        this.config = config;
    }

    /**
     * 키 (user × profile) 의 호출 가능 여부 검사 + 카운트.
     *
     * @return true 면 통과, false 면 rate limit 초과
     */
    public boolean tryAcquire(String username, Long profileId) {
        int limit = config.getMaxCallsPerMinute();
        if (limit <= 0) return true;  // 비활성

        String key = (username != null ? username : "(anon)") + ":" + profileId;
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000L;

        Deque<Long> window = windows.computeIfAbsent(key, k -> new ArrayDeque<Long>());
        synchronized (window) {
            // 60초 이전 호출 제거
            while (!window.isEmpty() && window.peekFirst() < cutoff) {
                window.removeFirst();
            }
            if (window.size() >= limit) return false;
            window.addLast(now);
            return true;
        }
    }

    /**
     * 현재 키의 *남은 호출 수* — 헬스 대시보드 표시용. 비활성이면 -1 반환.
     */
    public int remaining(String username, Long profileId) {
        int limit = config.getMaxCallsPerMinute();
        if (limit <= 0) return -1;

        String key = (username != null ? username : "(anon)") + ":" + profileId;
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000L;

        Deque<Long> window = windows.get(key);
        if (window == null) return limit;
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() < cutoff) {
                window.removeFirst();
            }
            return Math.max(0, limit - window.size());
        }
    }

    /** 테스트용 — 모든 윈도우 초기화 */
    public void reset() {
        windows.clear();
    }
}
