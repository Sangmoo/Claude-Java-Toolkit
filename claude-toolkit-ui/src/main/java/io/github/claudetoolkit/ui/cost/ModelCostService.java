package io.github.claudetoolkit.ui.cost;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.3.0 — Claude API 모델별 비용 추적 + 분석 유형별 추천 모델 산출.
 *
 * <p><b>비용 산정 방식:</b> Anthropic 공식 단가표(2025-04 기준, 1M tokens 단위)를
 * 모델별로 보관하고 ReviewHistory 의 누적 토큰을 곱해 USD 비용을 산출.
 *
 * <p><b>추천 로직:</b> 분석 유형별로 최근 30일간의 평균 입력 토큰, 출력 토큰,
 * 리뷰 승인률(ACCEPTED/total) 을 보고 다음 규칙으로 모델 추천:
 * <ul>
 *   <li>평균 입력 ≤ 2,000 + 승인률 ≥ 0.85 → <b>Haiku</b> (충분한 성능, 80% 비용 절감)</li>
 *   <li>평균 입력 ≤ 8,000 + 승인률 ≥ 0.70 → <b>Sonnet</b> (균형)</li>
 *   <li>그 외 → <b>Opus</b> (복잡도 높음, 정확도 최우선)</li>
 * </ul>
 *
 * <p>실제 ReviewHistory 에는 어떤 모델로 호출됐는지 저장돼있지 않으므로
 * "현재 사용 모델" 은 클라이언트의 effective model 을 외부에서 주입받음.
 */
@Service
public class ModelCostService {

    private static final Logger log = LoggerFactory.getLogger(ModelCostService.class);

    /**
     * 모델별 USD 단가 (per 1M tokens). Anthropic 공식 가격 기반.
     * https://www.anthropic.com/pricing
     *
     * 2025-04 기준 - 향후 가격 조정 시 이 표만 갱신하면 전체 계산이 자동 반영됨.
     */
    public static final Map<String, ModelPricing> PRICING = new LinkedHashMap<String, ModelPricing>();
    static {
        // Claude Opus 4 / 4.x
        PRICING.put("claude-opus-4",        new ModelPricing("Opus 4",   15.00, 75.00));
        PRICING.put("claude-opus-4-5",      new ModelPricing("Opus 4.5", 15.00, 75.00));
        PRICING.put("claude-opus-4-1",      new ModelPricing("Opus 4.1", 15.00, 75.00));
        // Claude Sonnet 4 / 4.x
        PRICING.put("claude-sonnet-4",      new ModelPricing("Sonnet 4",        3.00, 15.00));
        PRICING.put("claude-sonnet-4-5",    new ModelPricing("Sonnet 4.5",      3.00, 15.00));
        PRICING.put("claude-sonnet-4-20250514", new ModelPricing("Sonnet 4 (May)", 3.00, 15.00));
        // Claude Haiku
        PRICING.put("claude-haiku-4",       new ModelPricing("Haiku 4",  1.00,  5.00));
        PRICING.put("claude-haiku-3-5",     new ModelPricing("Haiku 3.5", 0.80,  4.00));
        // 호환 alias (기본 fallback)
        PRICING.put("default",              new ModelPricing("Default Sonnet", 3.00, 15.00));
    }

    private final ReviewHistoryRepository historyRepo;

    public ModelCostService(ReviewHistoryRepository historyRepo) {
        this.historyRepo = historyRepo;
    }

    /**
     * 최근 N일 비용 + 추천 분석.
     *
     * @param days        조회 기간 (1~365)
     * @param currentModel 현재 사용 중인 모델 (절감 효과 비교용, null 가능)
     */
    public Map<String, Object> analyze(int days, String currentModel) {
        int safeDays = Math.max(1, Math.min(days, 365));
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);
        List<ReviewHistory> all = historyRepo.findRecentEntries(PageRequest.of(0, 5000));

        // 기간 내 + 토큰 정보 있는 항목만
        List<ReviewHistory> filtered = new ArrayList<ReviewHistory>();
        for (ReviewHistory h : all) {
            if (h.getCreatedAt() != null && h.getCreatedAt().isAfter(since)
                    && (h.getInputTokens() != null || h.getOutputTokens() != null)) {
                filtered.add(h);
            }
        }

        // 유형별 집계
        Map<String, TypeStats> byType = new HashMap<String, TypeStats>();
        long totalIn = 0, totalOut = 0, totalCount = 0;
        for (ReviewHistory h : filtered) {
            String type = h.getType() != null ? h.getType() : "UNKNOWN";
            TypeStats s = byType.get(type);
            if (s == null) {
                s = new TypeStats(type);
                byType.put(type, s);
            }
            long in  = h.getInputTokens()  != null ? h.getInputTokens()  : 0;
            long out = h.getOutputTokens() != null ? h.getOutputTokens() : 0;
            s.totalIn  += in;
            s.totalOut += out;
            s.count++;
            if ("ACCEPTED".equals(h.getReviewStatus())) s.accepted++;
            else if ("REJECTED".equals(h.getReviewStatus())) s.rejected++;
            totalIn  += in;
            totalOut += out;
            totalCount++;
        }

        // 추천 모델 + 비용 계산
        ModelPricing currentPricing = lookupPricing(currentModel);
        List<Map<String, Object>> typeRows = new ArrayList<Map<String, Object>>();
        double totalCurrentCost      = 0.0;
        double totalRecommendedCost  = 0.0;

        for (TypeStats s : byType.values()) {
            double avgIn  = s.count > 0 ? (double) s.totalIn  / s.count : 0;
            double avgOut = s.count > 0 ? (double) s.totalOut / s.count : 0;
            double acceptRate = (s.accepted + s.rejected) > 0
                    ? (double) s.accepted / (s.accepted + s.rejected) : 0.0;

            String recommendedKey = recommend(avgIn, acceptRate);
            ModelPricing recPricing = PRICING.get(recommendedKey);

            double currentCost     = costFor(currentPricing, s.totalIn, s.totalOut);
            double recommendedCost = costFor(recPricing,     s.totalIn, s.totalOut);
            double saving          = currentCost - recommendedCost;
            double savingPct       = currentCost > 0 ? saving / currentCost * 100 : 0;

            totalCurrentCost     += currentCost;
            totalRecommendedCost += recommendedCost;

            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("type",             s.type);
            row.put("count",            s.count);
            row.put("totalInputTokens", s.totalIn);
            row.put("totalOutputTokens",s.totalOut);
            row.put("avgInputTokens",   round(avgIn, 0));
            row.put("avgOutputTokens",  round(avgOut, 0));
            row.put("acceptedCount",    s.accepted);
            row.put("rejectedCount",    s.rejected);
            row.put("acceptRate",       round(acceptRate * 100, 1));
            row.put("currentCostUsd",     round(currentCost, 4));
            row.put("recommendedModelKey",   recommendedKey);
            row.put("recommendedModelLabel", recPricing != null ? recPricing.label : recommendedKey);
            row.put("recommendedCostUsd",  round(recommendedCost, 4));
            row.put("monthlySavingUsd",    round(saving, 4));
            row.put("savingPercent",       round(savingPct, 1));
            row.put("rationale",           rationale(avgIn, acceptRate, recommendedKey));
            typeRows.add(row);
        }
        // 절감액 큰 순으로 정렬 (가장 임팩트 큰 추천을 위로)
        typeRows.sort((a, b) -> Double.compare(
                ((Number) b.get("monthlySavingUsd")).doubleValue(),
                ((Number) a.get("monthlySavingUsd")).doubleValue()));

        // 응답 조립
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("days",                 safeDays);
        result.put("currentModel",         currentModel != null ? currentModel : "default");
        result.put("currentModelLabel",    currentPricing != null ? currentPricing.label : "Default");
        result.put("totalAnalyses",        totalCount);
        result.put("totalInputTokens",     totalIn);
        result.put("totalOutputTokens",    totalOut);
        result.put("totalCurrentCostUsd",     round(totalCurrentCost, 2));
        result.put("totalRecommendedCostUsd", round(totalRecommendedCost, 2));
        result.put("totalMonthlySavingUsd",   round(totalCurrentCost - totalRecommendedCost, 2));
        result.put("totalSavingPercent",      totalCurrentCost > 0
                ? round((totalCurrentCost - totalRecommendedCost) / totalCurrentCost * 100, 1) : 0.0);
        result.put("byType",               typeRows);
        result.put("pricingTable",         buildPricingTable());
        log.debug("ModelCostService 분석: days={}, types={}, currentCost=${}",
                safeDays, byType.size(), round(totalCurrentCost, 2));
        return result;
    }

    /** 단순 fallback: 모델 이름 prefix 매칭 */
    private ModelPricing lookupPricing(String model) {
        if (model == null || model.isEmpty()) return PRICING.get("default");
        ModelPricing exact = PRICING.get(model);
        if (exact != null) return exact;
        String lower = model.toLowerCase();
        for (Map.Entry<String, ModelPricing> e : PRICING.entrySet()) {
            if (lower.contains(e.getKey().toLowerCase())) return e.getValue();
        }
        if (lower.contains("opus"))   return PRICING.get("claude-opus-4");
        if (lower.contains("haiku"))  return PRICING.get("claude-haiku-4");
        if (lower.contains("sonnet")) return PRICING.get("claude-sonnet-4");
        return PRICING.get("default");
    }

    private double costFor(ModelPricing p, long inTok, long outTok) {
        if (p == null) return 0.0;
        return (inTok / 1_000_000.0) * p.inputUsdPer1M
             + (outTok / 1_000_000.0) * p.outputUsdPer1M;
    }

    /** 추천 키 결정 — Haiku/Sonnet/Opus 중 하나 */
    private String recommend(double avgInputTokens, double acceptRate) {
        if (avgInputTokens <= 2000 && acceptRate >= 0.85) return "claude-haiku-4";
        if (avgInputTokens <= 8000 && acceptRate >= 0.70) return "claude-sonnet-4";
        return "claude-opus-4";
    }

    private String rationale(double avgIn, double rate, String reco) {
        StringBuilder sb = new StringBuilder();
        sb.append("평균 입력 ").append((long) avgIn).append(" 토큰");
        if (rate > 0) sb.append(", 승인률 ").append(String.format("%.0f%%", rate * 100));
        if ("claude-haiku-4".equals(reco)) sb.append(" → 단순 분석으로 Haiku 충분 (80% 절감)");
        else if ("claude-sonnet-4".equals(reco)) sb.append(" → 균형형 Sonnet 적합");
        else sb.append(" → 복잡 분석, 정확도 우선 Opus 권장");
        return sb.toString();
    }

    private List<Map<String, Object>> buildPricingTable() {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, ModelPricing> e : PRICING.entrySet()) {
            if ("default".equals(e.getKey())) continue;
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("model",           e.getKey());
            r.put("label",           e.getValue().label);
            r.put("inputPer1M",      e.getValue().inputUsdPer1M);
            r.put("outputPer1M",     e.getValue().outputUsdPer1M);
            rows.add(r);
        }
        return rows;
    }

    private double round(double v, int places) {
        double scale = Math.pow(10, places);
        return Math.round(v * scale) / scale;
    }

    // ── 내부 데이터 클래스 ─────────────────────────────────────────────────

    public static final class ModelPricing {
        public final String label;
        public final double inputUsdPer1M;
        public final double outputUsdPer1M;
        public ModelPricing(String label, double inputUsdPer1M, double outputUsdPer1M) {
            this.label = label; this.inputUsdPer1M = inputUsdPer1M; this.outputUsdPer1M = outputUsdPer1M;
        }
    }

    private static final class TypeStats {
        final String type;
        long totalIn, totalOut, count, accepted, rejected;
        TypeStats(String type) { this.type = type; }
    }
}
