package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.security.AuditLog;
import io.github.claudetoolkit.ui.security.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 감사 로그 시각화 대시보드 (ADMIN 전용).
 */
@Controller
@RequestMapping("/admin/audit-dashboard")
public class AuditDashboardController {

    private final AuditLogRepository auditLogRepository;

    public AuditDashboardController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public String page() {
        return "admin/audit-dashboard";
    }

    @GetMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> data(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // 최근 N일 로그
        Page<AuditLog> page = auditLogRepository.findFiltered(null, since, PageRequest.of(0, 5000));
        List<AuditLog> logs = page.getContent();

        // 1. 시간대별 요청 분포 (24시간)
        int[] hourly = new int[24];
        for (AuditLog log : logs) {
            hourly[log.getCreatedAt().getHour()]++;
        }
        result.put("hourlyDistribution", hourly);

        // 2. 일별 요청 건수
        Map<String, Integer> dailyCounts = new LinkedHashMap<String, Integer>();
        for (int i = days - 1; i >= 0; i--) {
            dailyCounts.put(LocalDate.now().minusDays(i).toString(), 0);
        }
        for (AuditLog log : logs) {
            String date = log.getCreatedAt().toLocalDate().toString();
            if (dailyCounts.containsKey(date)) {
                dailyCounts.put(date, dailyCounts.get(date) + 1);
            }
        }
        result.put("dailyCounts", dailyCounts);

        // 3. 사용자별 활동 순위 (Top 10)
        Map<String, Integer> userActivity = new LinkedHashMap<String, Integer>();
        for (AuditLog log : logs) {
            String user = log.getUsername() != null ? log.getUsername() : "anonymous";
            userActivity.put(user, (userActivity.containsKey(user) ? userActivity.get(user) : 0) + 1);
        }
        // 정렬하여 Top 10
        List<Map.Entry<String, Integer>> sorted = new ArrayList<Map.Entry<String, Integer>>(userActivity.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        Map<String, Integer> top10 = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            top10.put(sorted.get(i).getKey(), sorted.get(i).getValue());
        }
        result.put("userActivity", top10);

        // 4. 액션 유형별 파이 차트
        Map<String, Integer> actionCounts = new LinkedHashMap<String, Integer>();
        for (AuditLog log : logs) {
            String action = log.getActionType();
            actionCounts.put(action, (actionCounts.containsKey(action) ? actionCounts.get(action) : 0) + 1);
        }
        result.put("actionCounts", actionCounts);

        // 5. 에러율 (일별 4xx/5xx)
        Map<String, int[]> dailyErrors = new LinkedHashMap<String, int[]>();
        for (int i = days - 1; i >= 0; i--) {
            dailyErrors.put(LocalDate.now().minusDays(i).toString(), new int[]{0, 0}); // [total, errors]
        }
        for (AuditLog log : logs) {
            String date = log.getCreatedAt().toLocalDate().toString();
            int[] counts = dailyErrors.get(date);
            if (counts != null) {
                counts[0]++;
                if (log.getStatusCode() != null && log.getStatusCode() >= 400) counts[1]++;
            }
        }
        Map<String, Double> errorRates = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, int[]> e : dailyErrors.entrySet()) {
            int[] c = e.getValue();
            errorRates.put(e.getKey(), c[0] > 0 ? Math.round(c[1] * 1000.0 / c[0]) / 10.0 : 0.0);
        }
        result.put("errorRates", errorRates);

        // 6. 요약
        int totalRequests = logs.size();
        int errorCount = 0;
        for (AuditLog log : logs) {
            if (log.getStatusCode() != null && log.getStatusCode() >= 400) errorCount++;
        }
        result.put("totalRequests", totalRequests);
        result.put("errorCount", errorCount);
        result.put("errorRate", totalRequests > 0 ? Math.round(errorCount * 1000.0 / totalRequests) / 10.0 : 0.0);

        return ResponseEntity.ok(result);
    }
}
