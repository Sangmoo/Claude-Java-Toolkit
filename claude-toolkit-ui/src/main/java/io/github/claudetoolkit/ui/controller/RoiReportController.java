package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import io.github.claudetoolkit.ui.roi.RoiCalculator;
import io.github.claudetoolkit.ui.roi.RoiSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ROI 리포트 컨트롤러 — AI 도입 비용 대비 절감 효과를 시각화합니다.
 */
@Controller
@RequestMapping("/roi-report")
public class RoiReportController {

    private final ReviewHistoryService historyService;

    public RoiReportController(ReviewHistoryService historyService) {
        this.historyService = historyService;
    }

    /** ROI 리포트 메인 페이지 */
    @GetMapping
    public String index(Model model) {
        RoiSettings settings = RoiSettings.load();
        model.addAttribute("roiSettings", settings);
        return "roi-report/index";
    }

    /** 월별 ROI 데이터 JSON */
    @GetMapping("/data")
    @ResponseBody
    public ResponseEntity<List<RoiCalculator.MonthlyRoiData>> monthlyData(
            @RequestParam(value = "months", defaultValue = "6") int months) {
        months = clamp(months, 1, 24);
        List<ReviewHistory> all = historyService.findAll();
        RoiSettings settings = RoiSettings.load();
        RoiCalculator calc = new RoiCalculator(settings);
        return ResponseEntity.ok(calc.calcMonthly(all, months));
    }

    /** 기능별 ROI 데이터 JSON */
    @GetMapping("/by-feature")
    @ResponseBody
    public ResponseEntity<List<RoiCalculator.FeatureRoiData>> byFeature(
            @RequestParam(value = "months", defaultValue = "6") int months) {
        months = clamp(months, 1, 24);
        List<ReviewHistory> all = historyService.findAll();
        RoiSettings settings = RoiSettings.load();
        RoiCalculator calc = new RoiCalculator(settings);
        return ResponseEntity.ok(calc.calcByFeature(all, months));
    }

    /** 요약 카드 4종 JSON */
    @GetMapping("/summary")
    @ResponseBody
    public ResponseEntity<RoiCalculator.SummaryData> summary(
            @RequestParam(value = "months", defaultValue = "6") int months) {
        months = clamp(months, 1, 24);
        List<ReviewHistory> all = historyService.findAll();
        RoiSettings settings = RoiSettings.load();
        RoiCalculator calc = new RoiCalculator(settings);
        return ResponseEntity.ok(calc.calcSummary(all, months));
    }

    /** 현재 ROI 설정 조회 */
    @GetMapping("/settings")
    @ResponseBody
    public ResponseEntity<RoiSettings> getSettings() {
        return ResponseEntity.ok(RoiSettings.load());
    }

    /** ROI 설정 저장 */
    @PostMapping("/settings")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveSettings(
            @RequestParam(value = "hourlyRateWon",       defaultValue = "40000") int hourlyRateWon,
            @RequestParam(value = "inputCostPerMillion", defaultValue = "3.0")   double inputCost,
            @RequestParam(value = "outputCostPerMillion",defaultValue = "15.0")  double outputCost,
            @RequestParam(value = "usdToKrw",            defaultValue = "1380")  int usdToKrw,
            @RequestParam(value = "monthlyBudgetUsd",    defaultValue = "0.0")   double budget,
            @RequestParam(value = "budgetAlertEmail",    defaultValue = "")      String alertEmail,
            @RequestParam(value = "timeSavingsJson",     defaultValue = "")      String timeSavingsJson) {

        Map<String, Object> resp = new HashMap<String, Object>();
        try {
            RoiSettings s = RoiSettings.load();
            s.setHourlyRateWon(Math.max(1000, hourlyRateWon));
            s.setInputCostPerMillion(Math.max(0, inputCost));
            s.setOutputCostPerMillion(Math.max(0, outputCost));
            s.setUsdToKrw(Math.max(100, usdToKrw));
            s.setMonthlyBudgetUsd(Math.max(0, budget));
            s.setBudgetAlertEmail(alertEmail);

            // 기능별 절감 시간 파싱 (key=value 쌍, 쉼표 구분)
            if (!timeSavingsJson.isEmpty()) {
                for (String pair : timeSavingsJson.split(",")) {
                    String[] kv = pair.split("=");
                    if (kv.length == 2) {
                        try {
                            s.getTimeSavingByType().put(kv[0].trim(),
                                    Integer.parseInt(kv[1].trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            s.save();
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
