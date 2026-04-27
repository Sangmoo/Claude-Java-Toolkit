package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 하네스 품질 대시보드 — 누적 분석 통계를 시각화합니다.
 */
@Controller
@RequestMapping("/harness/dashboard")
public class HarnessDashboardController {

    private static final Logger log = LoggerFactory.getLogger(HarnessDashboardController.class);

    private final ReviewHistoryService historyService;

    public HarnessDashboardController(ReviewHistoryService historyService) {
        this.historyService = historyService;
    }

    /** Returns aggregated statistics for dashboard charts. */
    @GetMapping("/stats")
    @ResponseBody
    public Map<String, Object> stats() {
        List<ReviewHistory> all  = historyService.findAll();
        Map<String, Object> result = new LinkedHashMap<String, Object>();

        int totalHarness = 0, approved = 0, needsRevision = 0;
        int javaCount = 0, sqlCount = 0;
        List<Map<String,Object>> timeline = new ArrayList<Map<String,Object>>();

        for (ReviewHistory h : all) {
            if ("HARNESS_REVIEW".equals(h.getType())) {
                totalHarness++;
                String output = h.getOutputContent() != null ? h.getOutputContent() : "";
                if (output.contains("APPROVED"))       approved++;
                if (output.contains("NEEDS_REVISION")) needsRevision++;
                if ("java".equalsIgnoreCase(h.getAnalysisLanguage())) javaCount++;
                if ("sql".equalsIgnoreCase(h.getAnalysisLanguage()))  sqlCount++;
                if (timeline.size() < 20) {
                    Map<String,Object> entry = new LinkedHashMap<String,Object>();
                    entry.put("date",  h.getFormattedDate());
                    entry.put("title", h.getTitle());
                    entry.put("lang",  h.getAnalysisLanguage() != null ? h.getAnalysisLanguage() : "?");
                    int score = extractTotalScore(output);
                    entry.put("score", score);
                    timeline.add(entry);
                }
            }
        }

        result.put("totalHarness",   totalHarness);
        result.put("approved",       approved);
        result.put("needsRevision",  needsRevision);
        result.put("javaCount",      javaCount);
        result.put("sqlCount",       sqlCount);
        result.put("totalHistory",   all.size());
        result.put("timeline",       timeline);
        return result;
    }

    /** Try to extract 종합 score from "종합: X/10" pattern in output. */
    private int extractTotalScore(String output) {
        if (output == null) return 0;
        int idx = output.indexOf("종합:");
        if (idx < 0) idx = output.indexOf("종합 :");
        if (idx < 0) return 0;
        int start = idx + 3;
        while (start < output.length() && (output.charAt(start) == ' ' || output.charAt(start) == ':')) start++;
        int end = output.indexOf("/10", start);
        if (end < 0 || end - start > 4) return 0;
        try { return Integer.parseInt(output.substring(start, end).trim()); }
        catch (NumberFormatException e) {
            log.debug("[Harness] 종합 점수 파싱 실패 — raw: '{}'", output.substring(start, Math.min(end + 3, output.length())));
            return 0;
        }
    }
}
