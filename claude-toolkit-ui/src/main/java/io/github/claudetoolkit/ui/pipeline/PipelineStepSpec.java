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

    public PipelineStepSpec() {}

    public String getId()        { return id; }
    public String getAnalysis()  { return analysis; }
    public String getInput()     { return input; }
    public String getContext()   { return context; }
    public String getCondition() { return condition; }

    public void setId(String v)        { this.id = v; }
    public void setAnalysis(String v)  { this.analysis = v; }
    public void setInput(String v)     { this.input = v; }
    public void setContext(String v)   { this.context = v; }
    public void setCondition(String v) { this.condition = v; }
}
