package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import io.github.claudetoolkit.ui.security.AuditLog;
import io.github.claudetoolkit.ui.security.AuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 팀 대시보드 (ADMIN 전용).
 * 팀 전체 분석 트렌드, 사용자별 활동, 기능별 사용 빈도.
 */
@Controller
@RequestMapping("/admin/team-dashboard")
public class TeamDashboardController {

    private final ReviewHistoryRepository historyRepository;
    private final AuditLogRepository      auditLogRepository;

    public TeamDashboardController(ReviewHistoryRepository historyRepository,
                                   AuditLogRepository auditLogRepository) {
        this.historyRepository = historyRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public String page() {
        return "admin/team-dashboard";
    }

    @GetMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> data(@RequestParam(defaultValue = "30") int days) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // 1. 전체 분석 이력
        List<ReviewHistory> allHistory = historyRepository.findAll();

        // 2. 일별 분석 건수 (최근 N일)
        Map<String, Integer> dailyCounts = new LinkedHashMap<String, Integer>();
        for (int i = days - 1; i >= 0; i--) {
            dailyCounts.put(LocalDate.now().minusDays(i).toString(), 0);
        }
        for (ReviewHistory h : allHistory) {
            if (h.getCreatedAt().isAfter(since)) {
                String date = h.getCreatedAt().toLocalDate().toString();
                if (dailyCounts.containsKey(date)) {
                    dailyCounts.put(date, dailyCounts.get(date) + 1);
                }
            }
        }
        result.put("dailyCounts", dailyCounts);

        // 3. 사용자별 분석 건수
        Map<String, Integer> userCounts = new LinkedHashMap<String, Integer>();
        Map<String, Long> userTokens = new LinkedHashMap<String, Long>();
        for (ReviewHistory h : allHistory) {
            if (h.getCreatedAt().isAfter(since)) {
                String user = h.getUsername() != null ? h.getUsername() : "unknown";
                userCounts.put(user, (userCounts.containsKey(user) ? userCounts.get(user) : 0) + 1);
                long tokens = h.getTotalTokens();
                userTokens.put(user, (userTokens.containsKey(user) ? userTokens.get(user) : 0L) + tokens);
            }
        }
        result.put("userCounts", userCounts);
        result.put("userTokens", userTokens);

        // 4. 기능별 사용 빈도
        Map<String, Integer> typeCounts = new LinkedHashMap<String, Integer>();
        for (ReviewHistory h : allHistory) {
            if (h.getCreatedAt().isAfter(since)) {
                String type = h.getType();
                typeCounts.put(type, (typeCounts.containsKey(type) ? typeCounts.get(type) : 0) + 1);
            }
        }
        result.put("typeCounts", typeCounts);

        // 5. 요약 통계
        int totalAnalyses = 0;
        long totalTokens = 0;
        for (ReviewHistory h : allHistory) {
            if (h.getCreatedAt().isAfter(since)) {
                totalAnalyses++;
                totalTokens += h.getTotalTokens();
            }
        }
        result.put("totalAnalyses", totalAnalyses);
        result.put("totalTokens", totalTokens);
        result.put("activeUsers", userCounts.size());
        result.put("days", days);

        return ResponseEntity.ok(result);
    }
}
