package io.github.claudetoolkit.ui.pipeline;

/**
 * 파이프라인 단계 스펙 (v2.9.5).
 */
public class PipelineStepSpec {

    private String id;             // 단계 식별자 (예: "review", "refactor")
    private String analysis;       // 분석 유형 (AnalysisType enum name)
    private String input;          // 입력 (변수 치환 가능)
    private String context;        // 추가 컨텍스트 (optional)
    private String condition;      // 실행 조건 (SpEL, optional)
    /** v3.0: true면 이전 단계와 동시 실행 */
    private boolean parallel = false;
    /** v3.0: 이 단계 실행 전 완료되어야 하는 단계 ID 목록 (콤마 구분) */
    private String dependsOn;

    public PipelineStepSpec() {}

    public String getId()          { return id; }
    public String getAnalysis()    { return analysis; }
    public String getInput()       { return input; }
    public String getContext()     { return context; }
    public String getCondition()   { return condition; }
    public boolean isParallel()    { return parallel; }
    public String getDependsOn()   { return dependsOn; }

    public void setId(String v)        { this.id = v; }
    public void setAnalysis(String v)  { this.analysis = v; }
    public void setInput(String v)     { this.input = v; }
    public void setContext(String v)   { this.context = v; }
    public void setCondition(String v) { this.condition = v; }
    public void setParallel(boolean v) { this.parallel = v; }
    public void setDependsOn(String v) { this.dependsOn = v; }

    /** dependsOn을 List로 반환 */
    public java.util.List<String> getDependsOnList() {
        java.util.List<String> list = new java.util.ArrayList<String>();
        if (dependsOn == null || dependsOn.trim().isEmpty()) return list;
        String[] parts = dependsOn.split(",");
        for (String p : parts) {
            String s = p.trim();
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }
}
