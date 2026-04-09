package io.github.claudetoolkit.ui.security;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * 사용자별 API 호출 빈도 제한 서비스.
 * 메모리 기반으로 분당/시간당 호출 횟수를 추적합니다.
 */
@Service
public class RateLimitService {

    /** username → 호출 타임스탬프 목록 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> callHistory =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<Long>>();

    /**
     * API 호출을 기록하고 제한 초과 여부를 반환합니다.
     *
     * @param username 사용자명
     * @param limitPerMinute 분당 제한 (0=무제한)
     * @param limitPerHour   시간당 제한 (0=무제한)
     * @return null이면 허용, 문자열이면 제한 사유
     */
    public String checkAndRecord(String username, int limitPerMinute, int limitPerHour) {
        if (limitPerMinute <= 0 && limitPerHour <= 0) return null; // 무제한

        long now = System.currentTimeMillis();
        CopyOnWriteArrayList<Long> history = callHistory.get(username);
        if (history == null) {
            history = new CopyOnWriteArrayList<Long>();
            CopyOnWriteArrayList<Long> existing = callHistory.putIfAbsent(username, history);
            if (existing != null) history = existing;
        }

        // 1시간 이전 기록 정리
        long oneHourAgo = now - 3600_000;
        List<Long> toRemove = new java.util.ArrayList<Long>();
        for (Long t : history) {
            if (t < oneHourAgo) toRemove.add(t);
        }
        history.removeAll(toRemove);

        // 분당 체크
        if (limitPerMinute > 0) {
            long oneMinAgo = now - 60_000;
            int countMin = 0;
            for (Long t : history) {
                if (t >= oneMinAgo) countMin++;
            }
            if (countMin >= limitPerMinute) {
                return "분당 호출 제한 초과 (" + limitPerMinute + "회/분)";
            }
        }

        // 시간당 체크
        if (limitPerHour > 0) {
            if (history.size() >= limitPerHour) {
                return "시간당 호출 제한 초과 (" + limitPerHour + "회/시)";
            }
        }

        // 기록 추가
        history.add(now);
        return null;
    }
}
