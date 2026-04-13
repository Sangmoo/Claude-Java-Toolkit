package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/usage")
public class UsageController {

    // 기본 단가 (per 1M tokens)
    private static final double DEFAULT_INPUT_COST  = 3.0;
    private static final double DEFAULT_OUTPUT_COST = 15.0;

    private final ReviewHistoryService historyService;
    private final ClaudeClient         claudeClient;
    private final ToolkitSettings      settings;

    public UsageController(ReviewHistoryService historyService, ClaudeClient claudeClient,
                           ToolkitSettings settings) {
        this.historyService = historyService;
        this.claudeClient   = claudeClient;
        this.settings       = settings;
    }

    /** 일별 집계 JSON */
    @GetMapping("/daily")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> daily(
            @RequestParam(defaultValue = "30") int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<ReviewHistory> all = historyService.findAll();
        Map<String, long[]> map = new LinkedHashMap<String, long[]>();
        for (ReviewHistory h : all) {
            if (h.getCreatedAt().isBefore(since)) continue;
            if (h.getInputTokens() == null && h.getOutputTokens() == null) continue;
            String date = h.getCreatedAt().toLocalDate().toString();
            long[] d = map.get(date);
            if (d == null) d = new long[]{0, 0, 0};
            d[0] += h.getInputTokens()  != null ? h.getInputTokens()  : 0;
            d[1] += h.getOutputTokens() != null ? h.getOutputTokens() : 0;
            d[2]++;
            map.put(date, d);
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, long[]> e : map.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("date", e.getKey());
            m.put("inputTokens", e.getValue()[0]);
            m.put("outputTokens", e.getValue()[1]);
            m.put("count", e.getValue()[2]);
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    /** 기능별 집계 JSON */
    @GetMapping("/by-type")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> byType(
            @RequestParam(defaultValue = "30") int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<ReviewHistory> all = historyService.findAll();
        Map<String, long[]> map = new LinkedHashMap<String, long[]>();
        for (ReviewHistory h : all) {
            if (h.getCreatedAt().isBefore(since)) continue;
            if (h.getTotalTokens() <= 0) continue;
            String type = h.getType();
            long[] d = map.get(type);
            if (d == null) d = new long[]{0, 0, 0};
            d[0] += h.getInputTokens()  != null ? h.getInputTokens()  : 0;
            d[1] += h.getOutputTokens() != null ? h.getOutputTokens() : 0;
            d[2]++;
            map.put(type, d);
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, long[]> e : map.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("type", e.getKey());
            m.put("inputTokens", e.getValue()[0]);
            m.put("outputTokens", e.getValue()[1]);
            m.put("count", e.getValue()[2]);
            result.add(m);
        }
        Collections.sort(result, new Comparator<Map<String, Object>>() {
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                long ta = ((Number)a.get("inputTokens")).longValue() + ((Number)a.get("outputTokens")).longValue();
                long tb = ((Number)b.get("inputTokens")).longValue() + ((Number)b.get("outputTokens")).longValue();
                return Long.compare(tb, ta);
            }
        });
        return ResponseEntity.ok(result);
    }

    /** 월별 비용 요약 JSON */
    @GetMapping("/cost-summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> costSummary() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<ReviewHistory> all = historyService.findAll();
        long monthInput = 0, monthOutput = 0, monthCount = 0;
        for (ReviewHistory h : all) {
            if (h.getCreatedAt().isBefore(monthStart)) continue;
            if (h.getInputTokens() != null) monthInput += h.getInputTokens();
            if (h.getOutputTokens() != null) monthOutput += h.getOutputTokens();
            monthCount++;
        }
        double inputCost  = monthInput  / 1_000_000.0 * DEFAULT_INPUT_COST;
        double outputCost = monthOutput / 1_000_000.0 * DEFAULT_OUTPUT_COST;
        double totalCost  = inputCost + outputCost;

        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("monthInputTokens", monthInput);
        resp.put("monthOutputTokens", monthOutput);
        resp.put("monthRequests", monthCount);
        resp.put("monthCostUsd", Math.round(totalCost * 10000) / 10000.0);
        resp.put("monthCostKrw", Math.round(totalCost * 1350));
        resp.put("model", claudeClient.getEffectiveModel());
        return ResponseEntity.ok(resp);
    }

    /** 일별 비용 추이 JSON (F12) */
    @GetMapping("/daily-cost")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> dailyCost(
            @RequestParam(defaultValue = "30") int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<ReviewHistory> all = historyService.findAll();
        Map<String, long[]> map = new LinkedHashMap<String, long[]>();
        for (int i = days - 1; i >= 0; i--) {
            map.put(LocalDate.now().minusDays(i).toString(), new long[]{0, 0});
        }
        for (ReviewHistory h : all) {
            if (h.getCreatedAt().isBefore(since)) continue;
            String date = h.getCreatedAt().toLocalDate().toString();
            long[] d = map.get(date);
            if (d == null) continue;
            d[0] += h.getInputTokens()  != null ? h.getInputTokens()  : 0;
            d[1] += h.getOutputTokens() != null ? h.getOutputTokens() : 0;
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, long[]> e : map.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("date", e.getKey());
            double cost = e.getValue()[0] / 1_000_000.0 * DEFAULT_INPUT_COST
                        + e.getValue()[1] / 1_000_000.0 * DEFAULT_OUTPUT_COST;
            m.put("cost", Math.round(cost * 10000) / 10000.0);
            m.put("costKrw", Math.round(cost * 1350));
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    /** 사용자별 비용 분배 JSON (ADMIN 전용, F12) */
    @GetMapping("/cost-by-user")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> costByUser(
            @RequestParam(defaultValue = "30") int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<ReviewHistory> all = historyService.findAll();
        Map<String, long[]> userMap = new LinkedHashMap<String, long[]>();
        for (ReviewHistory h : all) {
            if (h.getCreatedAt().isBefore(since)) continue;
            String user = h.getUsername() != null ? h.getUsername() : "unknown";
            long[] d = userMap.get(user);
            if (d == null) d = new long[]{0, 0, 0};
            d[0] += h.getInputTokens()  != null ? h.getInputTokens()  : 0;
            d[1] += h.getOutputTokens() != null ? h.getOutputTokens() : 0;
            d[2]++;
            userMap.put(user, d);
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, long[]> e : userMap.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("username", e.getKey());
            m.put("requests", e.getValue()[2]);
            m.put("inputTokens", e.getValue()[0]);
            m.put("outputTokens", e.getValue()[1]);
            double cost = e.getValue()[0] / 1_000_000.0 * DEFAULT_INPUT_COST
                        + e.getValue()[1] / 1_000_000.0 * DEFAULT_OUTPUT_COST;
            m.put("costUsd", Math.round(cost * 10000) / 10000.0);
            m.put("costKrw", Math.round(cost * 1350));
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }
}
