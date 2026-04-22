package io.github.claudetoolkit.ui.flow.model;

/**
 * Flow 분석 요청. 프론트엔드 / REST 클라이언트가 보내는 입력.
 *
 * <p>{@code targetType=AUTO} 면 {@code query} 를 분석해 자동 판정:
 * {@code T_*} 또는 길이 8+ 대문자 → TABLE, {@code SP_*}/{@code FN_*} → SP,
 * 슬래시 포함 또는 .xml 끝 → MIPLATFORM_XML, namespace.id 형태 → SQL_ID.
 */
public class FlowAnalysisRequest {

    public enum TargetType { AUTO, TABLE, SP, SQL_ID, MIPLATFORM_XML }

    public enum DmlFilter  { ALL, INSERT, UPDATE, MERGE, DELETE }

    private String      query;
    private TargetType  targetType   = TargetType.AUTO;
    private DmlFilter   dmlFilter    = DmlFilter.ALL;
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

    public void setQuery(String q)              { this.query = q; }
    public void setTargetType(TargetType t)     { if (t != null) this.targetType = t; }
    public void setDmlFilter(DmlFilter d)       { if (d != null) this.dmlFilter = d; }
    public void setMaxBranches(int n)           { if (n > 0 && n < 50) this.maxBranches = n; }
    public void setIncludeDb(boolean b)         { this.includeDb = b; }
    public void setIncludeUi(boolean b)         { this.includeUi = b; }
}
