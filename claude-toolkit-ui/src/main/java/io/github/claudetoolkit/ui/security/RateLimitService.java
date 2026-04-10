package io.github.claudetoolkit.ui.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 사용자별 API 호출 빈도 제한 서비스.
 *
 * <p>메모리 기반 추적:
 * <ul>
 *   <li>분당/시간당 제한: 최근 1시간 타임스탬프 리스트</li>
 *   <li>일일/월간 제한: 일자별 카운트 맵 (메모리 효율)</li>
 * </ul>
 *
 * <p>v2.6.0: 일일/월간 API 한도 추가.
 */
@Service
public class RateLimitService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** username → 최근 1시간 호출 타임스탬프 (분/시간 체크용) */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> callHistory =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<Long>>();

    /** username → 일자별 호출 카운트 (일일/월간 체크용) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<LocalDate, AtomicInteger>> dailyCounts =
            new ConcurrentHashMap<String, ConcurrentHashMap<LocalDate, AtomicInteger>>();

    /**
     * API 호출을 기록하고 제한 초과 여부를 반환합니다 (분/시간만 체크).
     * @deprecated v2.6.0: 일일/월간 한도도 함께 체크하려면
     *     {@link #checkAndRecord(String, int, int, int, int)} 사용
     */
    @Deprecated
    public String checkAndRecord(String username, int limitPerMinute, int limitPerHour) {
        return checkAndRecord(username, limitPerMinute, limitPerHour, 0, 0);
    }

    /**
     * API 호출을 기록하고 모든 제한(분/시/일/월) 초과 여부를 반환합니다.
     *
     * @param username       사용자명
     * @param limitPerMinute 분당 제한 (0=무제한)
     * @param limitPerHour   시간당 제한 (0=무제한)
     * @param dailyLimit     일일 제한 (0=무제한)
     * @param monthlyLimit   월간 제한 (0=무제한)
     * @return null이면 허용, 문자열이면 제한 사유
     */
    public String checkAndRecord(String username, int limitPerMinute, int limitPerHour,
                                 int dailyLimit, int monthlyLimit) {
        if (limitPerMinute <= 0 && limitPerHour <= 0 && dailyLimit <= 0 && monthlyLimit <= 0) {
            return null; // 전부 무제한
        }

        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now(KST);
        YearMonth thisMonth = YearMonth.from(today);

        // ── 분/시간 체크 ──────────────────────────────────────────
        if (limitPerMinute > 0 || limitPerHour > 0) {
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
            if (limitPerHour > 0 && history.size() >= limitPerHour) {
                return "시간당 호출 제한 초과 (" + limitPerHour + "회/시)";
            }

            history.add(now);
        }

        // ── 일/월 체크 ────────────────────────────────────────────
        if (dailyLimit > 0 || monthlyLimit > 0) {
            ConcurrentHashMap<LocalDate, AtomicInteger> userDaily = dailyCounts.get(username);
            if (userDaily == null) {
                userDaily = new ConcurrentHashMap<LocalDate, AtomicInteger>();
                ConcurrentHashMap<LocalDate, AtomicInteger> existing = dailyCounts.putIfAbsent(username, userDaily);
                if (existing != null) userDaily = existing;
            }

            if (dailyLimit > 0) {
                AtomicInteger dayCount = userDaily.get(today);
                int currentDay = dayCount == null ? 0 : dayCount.get();
                if (currentDay >= dailyLimit) {
                    return "일일 호출 제한 초과 (" + dailyLimit + "회/일)";
                }
            }

            if (monthlyLimit > 0) {
                int monthTotal = 0;
                for (Map.Entry<LocalDate, AtomicInteger> e : userDaily.entrySet()) {
                    if (YearMonth.from(e.getKey()).equals(thisMonth)) {
                        monthTotal += e.getValue().get();
                    }
                }
                if (monthTotal >= monthlyLimit) {
                    return "월간 호출 제한 초과 (" + monthlyLimit + "회/월)";
                }
            }

            // 일자별 카운트 증가
            AtomicInteger counter = userDaily.get(today);
            if (counter == null) {
                counter = new AtomicInteger(0);
                AtomicInteger existing = userDaily.putIfAbsent(today, counter);
                if (existing != null) counter = existing;
            }
            counter.incrementAndGet();
        }

        return null;
    }

    /**
     * 사용자의 현재 사용량 통계를 반환합니다 (v2.6.0).
     * @return {"today": N, "thisMonth": N}
     */
    public Map<String, Integer> getUsageStats(String username) {
        Map<String, Integer> stats = new LinkedHashMap<String, Integer>();
        stats.put("today", 0);
        stats.put("thisMonth", 0);

        ConcurrentHashMap<LocalDate, AtomicInteger> userDaily = dailyCounts.get(username);
        if (userDaily == null) return stats;

        LocalDate today = LocalDate.now(KST);
        YearMonth thisMonth = YearMonth.from(today);
        int todayCount = 0;
        int monthCount = 0;
        for (Map.Entry<LocalDate, AtomicInteger> e : userDaily.entrySet()) {
            if (e.getKey().equals(today)) {
                todayCount = e.getValue().get();
            }
            if (YearMonth.from(e.getKey()).equals(thisMonth)) {
                monthCount += e.getValue().get();
            }
        }
        stats.put("today", todayCount);
        stats.put("thisMonth", monthCount);
        return stats;
    }

    /** 매일 새벽 3시 KST: 32일 이전 일자별 카운트 정리 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldDailyCounts() {
        LocalDate cutoff = LocalDate.now(KST).minusDays(32);
        for (ConcurrentHashMap<LocalDate, AtomicInteger> userDaily : dailyCounts.values()) {
            Iterator<Map.Entry<LocalDate, AtomicInteger>> it = userDaily.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getKey().isBefore(cutoff)) {
                    it.remove();
                }
            }
        }
    }
}
