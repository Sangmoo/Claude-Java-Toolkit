package io.github.claudetoolkit.ui.flow.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Flow 분석 요청. 프론트엔드 / REST 클라이언트가 보내는 입력.
 *
 * <p>{@code targetType=AUTO} 면 {@code query} 를 분석해 자동 판정:
 * {@code T_*} 또는 길이 8+ 대문자 → TABLE, {@code SP_*}/{@code FN_*} → SP,
 * 슬래시 포함 또는 .xml 끝 → MIPLATFORM_XML, namespace.id 형태 → SQL_ID.
 *
 * <p><b>v4.4.x DML 다중 선택</b> — {@link #dmlFilters} 가 새 권장 필드.
 * 비어있으면 기본 = INSERT/UPDATE/MERGE/DELETE 모두 (조회 제외 = 데이터 변경 흐름만).
 * 단일값 호환 위해 {@link #dmlFilter} (legacy) 도 받음.
 */
public class FlowAnalysisRequest {

    public enum TargetType { AUTO, TABLE, SP, SQL_ID, MIPLATFORM_XML }

    /** SELECT = 조회 (v4.4.x 추가) — 데이터 변경이 아닌 읽기 흐름 추적용 */
    public enum DmlFilter  { ALL, INSERT, UPDATE, MERGE, DELETE, SELECT }

    private String      query;
    private TargetType  targetType   = TargetType.AUTO;

    /** v4.4.x — 다중 선택. 비어있으면 INSERT/UPDATE/MERGE/DELETE 가 기본. */
    @JsonAlias({"dmls", "dmlList"})
    private Set<DmlFilter> dmlFilters = new LinkedHashSet<DmlFilter>();

    /** Legacy single-value (v4.4.x 이전 API 호환). dmlFilters 가 비어있을 때만 적용. */
    private DmlFilter   dmlFilter;

    /** 한 단계당 최대 분기 수 — 폭발 방지 (기본 3) */
    private int         maxBranches  = 3;
    /** DB 오브젝트 (SP/FUNC/TRIGGER) 도 같이 추적할지 (기본 true) */
    private boolean     includeDb    = true;
    /** MiPlatform 화면 매칭 단계까지 갈지 (기본 true) */
    private boolean     includeUi    = true;

    public String     getQuery()       { return query; }
    public TargetType getTargetType()  { return targetType; }
    public DmlFilter  getDmlFilter()   { return dmlFilter; }
    public int        getMaxBranches() { return maxBranches; }
    public boolean    isIncludeDb()    { return includeDb; }
    public boolean    isIncludeUi()    { return includeUi; }

    /** 정규화된 활성 DML 집합. 비어있으면 기본 (INS/UPD/MRG/DEL). ALL 이 포함되면 전체. */
    public Set<DmlFilter> getEffectiveDmls() {
        Set<DmlFilter> out = new LinkedHashSet<DmlFilter>();
        // 신규 다중 필드 우선
        if (dmlFilters != null && !dmlFilters.isEmpty()) out.addAll(dmlFilters);
        // legacy 단일 fallback
        else if (dmlFilter != null && dmlFilter != DmlFilter.ALL) out.add(dmlFilter);

        // ALL 이 들어있으면 전체로 확장 (SELECT 포함)
        if (out.contains(DmlFilter.ALL)) {
            return new LinkedHashSet<DmlFilter>(Arrays.asList(
                    DmlFilter.INSERT, DmlFilter.UPDATE, DmlFilter.MERGE,
                    DmlFilter.DELETE, DmlFilter.SELECT));
        }
        // 아무것도 없으면 기본 = 데이터 변경 4종 (조회 제외)
        if (out.isEmpty()) {
            return new LinkedHashSet<DmlFilter>(Arrays.asList(
                    DmlFilter.INSERT, DmlFilter.UPDATE, DmlFilter.MERGE, DmlFilter.DELETE));
        }
        return out;
    }

    public Set<DmlFilter> getDmlFilters() {
        return dmlFilters == null ? Collections.<DmlFilter>emptySet() : dmlFilters;
    }

    public void setQuery(String q)              { this.query = q; }
    public void setTargetType(TargetType t)     { if (t != null) this.targetType = t; }
    public void setDmlFilter(DmlFilter d)       { this.dmlFilter = d; }
    public void setDmlFilters(Set<DmlFilter> s) {
        this.dmlFilters = s == null ? new LinkedHashSet<DmlFilter>() : new LinkedHashSet<DmlFilter>(s);
    }
    public void setMaxBranches(int n)           { if (n > 0 && n < 50) this.maxBranches = n; }
    public void setIncludeDb(boolean b)         { this.includeDb = b; }
    public void setIncludeUi(boolean b)         { this.includeUi = b; }
}
