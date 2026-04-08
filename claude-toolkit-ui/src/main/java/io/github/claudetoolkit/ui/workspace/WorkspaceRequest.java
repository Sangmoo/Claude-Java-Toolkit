package io.github.claudetoolkit.ui.workspace;

/**
 * 워크스페이스 분석 요청 DTO.
 * <p>{@link AnalysisService#buildSystemPrompt(WorkspaceRequest)} 및
 * {@link AnalysisService#buildUserMessage(WorkspaceRequest)}에 전달됩니다.
 */
public class WorkspaceRequest {

    /** 분석 대상 코드 또는 SQL */
    private String code;

    /** 언어 식별자 (java / sql / kotlin / python / javascript / typescript) */
    private String language;

    /** 수행할 분석 유형 */
    private AnalysisType analysisType;

    /** 프로젝트 컨텍스트 메모 (Settings에서 설정) */
    private String projectContext;

    /** SQL 번역 시 소스 DB (oracle / mysql / postgresql / mssql) */
    private String sourceDb;

    /** SQL 번역 시 타겟 DB */
    private String targetDb;

    // ── 생성자 ──────────────────────────────────────────────────────────────

    public WorkspaceRequest() {}

    public WorkspaceRequest(String code, String language, AnalysisType analysisType, String projectContext) {
        this.code           = code;
        this.language       = language;
        this.analysisType   = analysisType;
        this.projectContext = projectContext;
    }

    // ── getters / setters ────────────────────────────────────────────────────

    public String       getCode()           { return code; }
    public void         setCode(String v)   { this.code = v; }

    public String       getLanguage()           { return language; }
    public void         setLanguage(String v)   { this.language = v; }

    public AnalysisType getAnalysisType()           { return analysisType; }
    public void         setAnalysisType(AnalysisType v) { this.analysisType = v; }

    public String       getProjectContext()           { return projectContext; }
    public void         setProjectContext(String v)   { this.projectContext = v; }

    public String       getSourceDb()           { return sourceDb; }
    public void         setSourceDb(String v)   { this.sourceDb = v; }

    public String       getTargetDb()           { return targetDb; }
    public void         setTargetDb(String v)   { this.targetDb = v; }
}
