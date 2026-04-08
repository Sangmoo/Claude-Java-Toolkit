package io.github.claudetoolkit.ui.roi;

import io.github.claudetoolkit.ui.history.ReviewHistory;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ROI 계산기 — 순수 계산 클래스 (Spring Bean 아님).
 *
 * <p>계산식:
 * <ul>
 *   <li>savedTimeMin = count × timeSaving(type)</li>
 *   <li>savedCostWon = (savedTimeMin / 60.0) × hourlyRateWon</li>
 *   <li>aiCostWon    = ((inputTokens/1M × inputCost) + (outputTokens/1M × outputCost)) × usdToKrw</li>
 *   <li>netProfitWon = savedCostWon - aiCostWon</li>
 *   <li>roiPercent   = aiCostWon > 0 ? (netProfitWon / aiCostWon) × 100 : 0</li>
 * </ul>
 */
public class RoiCalculator {

    private final RoiSettings settings;

    public RoiCalculator(RoiSettings settings) {
        this.settings = settings;
    }

    // ── 월별 집계 ──────────────────────────────────────────────────────────────

    /**
     * 최근 N개월의 월별 ROI 데이터를 계산합니다.
     */
    public List<MonthlyRoiData> calcMonthly(List<ReviewHistory> histories, int months) {
        YearMonth now = YearMonth.now();
        Map<String, MonthlyRoiData> map = new LinkedHashMap<String, MonthlyRoiData>();

        // 최근 N개월 슬롯 초기화
        for (int i = months - 1; i >= 0; i--) {
            String key = now.minusMonths(i).format(DateTimeFormatter.ofPattern("yyyy-MM"));
            map.put(key, new MonthlyRoiData(key));
        }

        for (ReviewHistory h : histories) {
            String ym = h.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            if (!map.containsKey(ym)) continue;
            MonthlyRoiData d = map.get(ym);
            d.usageCount++;
            d.savedTimeMin += settings.getTimeSaving(h.getType());
            long inTok  = h.getInputTokens()  != null ? h.getInputTokens()  : 0L;
            long outTok = h.getOutputTokens() != null ? h.getOutputTokens() : 0L;
            d.inputTokens  += inTok;
            d.outputTokens += outTok;
        }

        List<MonthlyRoiData> result = new ArrayList<MonthlyRoiData>(map.values());
        for (MonthlyRoiData d : result) {
            d.savedCostWon = (d.savedTimeMin / 60.0) * settings.getHourlyRateWon();
            d.aiCostWon    = calcAiCostWon(d.inputTokens, d.outputTokens);
            d.netProfitWon = d.savedCostWon - d.aiCostWon;
            d.roiPercent   = d.aiCostWon > 0 ? (d.netProfitWon / d.aiCostWon) * 100.0 : 0.0;
        }
        return result;
    }

    // ── 기능별 집계 ───────────────────────────────────────────────────────────

    public List<FeatureRoiData> calcByFeature(List<ReviewHistory> histories, int months) {
        YearMonth cutoff = YearMonth.now().minusMonths(months - 1);
        Map<String, FeatureRoiData> map = new LinkedHashMap<String, FeatureRoiData>();

        for (ReviewHistory h : histories) {
            YearMonth ym = YearMonth.from(h.getCreatedAt());
            if (ym.isBefore(cutoff)) continue;
            String type = h.getType();
            if (!map.containsKey(type)) map.put(type, new FeatureRoiData(type));
            FeatureRoiData d = map.get(type);
            d.usageCount++;
            d.savedTimeMin += settings.getTimeSaving(type);
            long inTok  = h.getInputTokens()  != null ? h.getInputTokens()  : 0L;
            long outTok = h.getOutputTokens() != null ? h.getOutputTokens() : 0L;
            d.inputTokens  += inTok;
            d.outputTokens += outTok;
        }

        List<FeatureRoiData> result = new ArrayList<FeatureRoiData>(map.values());
        for (FeatureRoiData d : result) {
            d.savedCostWon = (d.savedTimeMin / 60.0) * settings.getHourlyRateWon();
            d.aiCostWon    = calcAiCostWon(d.inputTokens, d.outputTokens);
            d.netProfitWon = d.savedCostWon - d.aiCostWon;
            d.roiPercent   = d.aiCostWon > 0 ? (d.netProfitWon / d.aiCostWon) * 100.0 : 0.0;
        }
        return result;
    }

    // ── 요약 합계 ─────────────────────────────────────────────────────────────

    public SummaryData calcSummary(List<ReviewHistory> histories, int months) {
        List<MonthlyRoiData> monthly = calcMonthly(histories, months);
        SummaryData s = new SummaryData();
        for (MonthlyRoiData d : monthly) {
            s.totalCount    += d.usageCount;
            s.totalSavedMin += d.savedTimeMin;
            s.totalSavedWon += d.savedCostWon;
            s.totalAiWon    += d.aiCostWon;
        }
        s.totalNetWon   = s.totalSavedWon - s.totalAiWon;
        s.overallRoi    = s.totalAiWon > 0 ? (s.totalNetWon / s.totalAiWon) * 100.0 : 0.0;
        return s;
    }

    // ── 내부 계산 ─────────────────────────────────────────────────────────────

    private double calcAiCostWon(long inputTok, long outputTok) {
        double costUsd = (inputTok  / 1_000_000.0 * settings.getInputCostPerMillion())
                       + (outputTok / 1_000_000.0 * settings.getOutputCostPerMillion());
        return costUsd * settings.getUsdToKrw();
    }

    // ── 데이터 클래스 ─────────────────────────────────────────────────────────

    public static class MonthlyRoiData {
        public String  yearMonth;
        public int     usageCount;
        public int     savedTimeMin;
        public long    inputTokens;
        public long    outputTokens;
        public double  savedCostWon;
        public double  aiCostWon;
        public double  netProfitWon;
        public double  roiPercent;

        public MonthlyRoiData(String yearMonth) {
            this.yearMonth = yearMonth;
        }
    }

    public static class FeatureRoiData {
        public String  type;
        public int     usageCount;
        public int     savedTimeMin;
        public long    inputTokens;
        public long    outputTokens;
        public double  savedCostWon;
        public double  aiCostWon;
        public double  netProfitWon;
        public double  roiPercent;

        public FeatureRoiData(String type) {
            this.type = type;
        }
    }

    public static class SummaryData {
        public int    totalCount;
        public long   totalSavedMin;
        public double totalSavedWon;
        public double totalAiWon;
        public double totalNetWon;
        public double overallRoi;
    }
}
