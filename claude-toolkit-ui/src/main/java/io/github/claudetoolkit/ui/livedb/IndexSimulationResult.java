package io.github.claudetoolkit.ui.livedb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v4.7.x — #G3 Live DB Phase 4: 인덱스 시뮬레이션 결과 DTO.
 *
 * <p>{@link IndexSimulator} 가 INVISIBLE INDEX 를 잠시 만들어 EXPLAIN 비용을 비교한
 * 결과를 담는다. 사용자에게 *현재 vs 추천 인덱스 적용 후* 의 정량 비교를 보여주기 위함.
 *
 * <p>모든 필드는 *옵셔널* — 시뮬레이션 일부 실패해도 가능한 부분만 반환.
 */
public class IndexSimulationResult {

    /** 사용자가 분석에 사용한 SQL */
    private String userSql;

    /** 시뮬레이션 대상 인덱스 정의 (CREATE INDEX 구문) */
    private List<String> simulatedIndexes = new ArrayList<String>();

    /** 인덱스 적용 *전* root operation cost (Oracle PLAN_TABLE 의 id=0 cost) — null 가능 */
    private Long beforeCost;

    /** 인덱스 적용 *후* root operation cost — null 가능 */
    private Long afterCost;

    /** 인덱스 적용 전 EXPLAIN PLAN markdown — Claude prompt 와 동일 형식 */
    private String beforePlanText;

    /** 인덱스 적용 후 EXPLAIN PLAN markdown */
    private String afterPlanText;

    /** 시뮬레이션 중 발생한 오류 메시지 (graceful degradation) */
    private List<String> warnings = new ArrayList<String>();

    /** 시뮬레이션 시도 시각 (사용자에게 신선도 표시용) */
    private long simulatedAtMillis = System.currentTimeMillis();

    public IndexSimulationResult() {}

    // ── getters / setters ──────────────────────────────────────────────────

    public String getUserSql()                                  { return userSql; }
    public void   setUserSql(String s)                          { this.userSql = s; }
    public List<String> getSimulatedIndexes()                   { return simulatedIndexes; }
    public void   setSimulatedIndexes(List<String> idx)         { this.simulatedIndexes = idx != null ? idx : new ArrayList<String>(); }
    public Long   getBeforeCost()                               { return beforeCost; }
    public void   setBeforeCost(Long c)                         { this.beforeCost = c; }
    public Long   getAfterCost()                                { return afterCost; }
    public void   setAfterCost(Long c)                          { this.afterCost = c; }
    public String getBeforePlanText()                           { return beforePlanText; }
    public void   setBeforePlanText(String s)                   { this.beforePlanText = s; }
    public String getAfterPlanText()                            { return afterPlanText; }
    public void   setAfterPlanText(String s)                    { this.afterPlanText = s; }
    public List<String> getWarnings()                           { return Collections.unmodifiableList(warnings); }
    public void   addWarning(String w)                          { if (w != null) warnings.add(w); }
    public long   getSimulatedAtMillis()                        { return simulatedAtMillis; }

    /**
     * 비용 감소율 (%) — beforeCost > 0 + afterCost 둘 다 있을 때만 계산.
     * null 이면 비교 불가 (UI 에서 "-" 표시).
     */
    public Double getImprovementPercent() {
        if (beforeCost == null || afterCost == null) return null;
        if (beforeCost <= 0) return null;
        if (afterCost.equals(beforeCost)) return 0.0;
        return Math.round((1.0 - (double) afterCost / beforeCost) * 1000) / 10.0;
    }

    /** 시뮬레이션이 실제로 일어났는지 (둘 다 cost 있어야 의미 있음) */
    public boolean hasComparison() {
        return beforeCost != null && afterCost != null;
    }
}
