package io.github.claudetoolkit.ui.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 사용자별 API 호출 빈도 제한 서비스 (v4.2.1 — DB 영속화).
 *
 * <ul>
 *   <li>분당/시간당 제한: 메모리 기반 최근 1시간 타임스탬프 (재시작 시 리셋 허용)</li>
 *   <li>일일/월간 제한: {@link UserApiUsage} DB 테이블 기반 영속화</li>
 * </ul>
 */
@Service
public class RateLimitService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserApiUsageRepository usageRepo;

    /** username → 최근 1시간 호출 타임스탬프 (분/시간 체크용, 메모리) */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> callHistory =
            new ConcurrentHashMap<>();

    public RateLimitService(UserApiUsageRepository usageRepo) {
        this.usageRepo = usageRepo;
    }

    /**
     * @deprecated v2.6.0: 일일/월간 한도 포함 버전 사용
     */
    @Deprecated
    public String checkAndRecord(String username, int limitPerMinute, int limitPerHour) {
        return checkAndRecord(username, limitPerMinute, limitPerHour, 0, 0);
    }

    /**
     * API 호출을 기록하고 모든 제한(분/시/일/월) 초과 여부를 반환합니다.
     * 일일/월간 카운트는 DB에 영속화되어 재시작 후에도 유지됩니다.
     *
     * @return null = 허용, 문자열 = 제한 사유
     */
    @Transactional
    public String checkAndRecord(String username, int limitPerMinute, int limitPerHour,
                                 int dailyLimit, int monthlyLimit) {
        if (limitPerMinute <= 0 && limitPerHour <= 0 && dailyLimit <= 0 && monthlyLimit <= 0) {
            return null;
        }

        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now(KST);

        // ── 분/시간 체크 (메모리) ──────────────────────────────────
        if (limitPerMinute > 0 || limitPerHour > 0) {
            CopyOnWriteArrayList<Long> history = callHistory.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>());

            // 1시간 이전 기록 정리
            long oneHourAgo = now - 3600_000;
            List<Long> toRemove = new java.util.ArrayList<>();
            for (Long t : history) if (t < oneHourAgo) toRemove.add(t);
            history.removeAll(toRemove);

            if (limitPerMinute > 0) {
                long oneMinAgo = now - 60_000;
                int countMin = 0;
                for (Long t : history) if (t >= oneMinAgo) countMin++;
                if (countMin >= limitPerMinute) {
                    return "분당 호출 제한 초과 (" + limitPerMinute + "회/분)";
                }
            }
            if (limitPerHour > 0 && history.size() >= limitPerHour) {
                return "시간당 호출 제한 초과 (" + limitPerHour + "회/시)";
            }

            history.add(now);
        }

        // ── 일/월 체크 (DB 영속화) ─────────────────────────────────
        // v4.4.x — 이전: dailyLimit/monthlyLimit 가 모두 0(무제한)인 사용자는
        //                기록 자체가 안 되어 "사용량 모니터링" 페이지가 영영 0 표시.
        //         이후: 제한값과 무관하게 사용량은 항상 기록.
        //                제한 초과 검사만 limit > 0 일 때 수행.
        try {
            if (dailyLimit > 0) {
                int todayCount = usageRepo.findByUsernameAndUsageDate(username, today)
                        .map(UserApiUsage::getRequestCount).orElse(0);
                if (todayCount >= dailyLimit) {
                    return "일일 호출 제한 초과 (" + dailyLimit + "회/일)";
                }
            }

            if (monthlyLimit > 0) {
                LocalDate monthStart = today.withDayOfMonth(1);
                LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
                Long monthTotal = usageRepo.sumRequestsBetween(username, monthStart, monthEnd);
                int monthCount = monthTotal != null ? monthTotal.intValue() : 0;
                if (monthCount >= monthlyLimit) {
                    return "월간 호출 제한 초과 (" + monthlyLimit + "회/월)";
                }
            }

            // 카운트 증가 (upsert) — 항상 기록
            UserApiUsage usage = usageRepo.findByUsernameAndUsageDate(username, today)
                    .orElseGet(() -> new UserApiUsage(username, today));
            usage.increment();
            usageRepo.save(usage);
        } catch (Exception e) {
            // DB 오류 시 허용 (안전 fallback) — 서비스 중단 방지
            return null;
        }

        return null;
    }

    /**
     * 사용자의 현재 사용량 통계 — 실시간 DB 조회.
     * @return {today: int, thisMonth: int}
     */
    public Map<String, Integer> getUsageStats(String username) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("today", 0);
        stats.put("thisMonth", 0);

        try {
            LocalDate today = LocalDate.now(KST);
            int todayCount = usageRepo.findByUsernameAndUsageDate(username, today)
                    .map(UserApiUsage::getRequestCount).orElse(0);
            stats.put("today", todayCount);

            LocalDate monthStart = today.withDayOfMonth(1);
            LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
            Long monthTotal = usageRepo.sumRequestsBetween(username, monthStart, monthEnd);
            stats.put("thisMonth", monthTotal != null ? monthTotal.intValue() : 0);
        } catch (Exception ignored) {}

        return stats;
    }

    /** 매일 새벽 3시 KST: 100일 이전 사용량 기록 정리 */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldUsage() {
        try {
            LocalDate cutoff = LocalDate.now(KST).minusDays(100);
            usageRepo.deleteByUsageDateBefore(cutoff);
        } catch (Exception ignored) {}
    }
}
