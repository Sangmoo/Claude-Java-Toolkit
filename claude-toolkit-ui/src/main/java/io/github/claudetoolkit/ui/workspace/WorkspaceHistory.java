package io.github.claudetoolkit.ui.workspace;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 통합 워크스페이스 분석 이력 엔티티
 */
@Entity
@Table(name = "workspace_history")
public class WorkspaceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 분석 언어 (java, sql, ...) */
    @Column(length = 30)
    private String language;

    /** 실행된 분석 유형 목록 (comma-separated, e.g. "CODE_REVIEW,SECURITY_AUDIT") */
    @Column(length = 500)
    private String analysisTypes;

    /** 코드 앞 500자 (미리보기용) */
    @Column(length = 500)
    private String codeSnippet;

    /** 선택된 소스 이름 (파일명 또는 DB 오브젝트명, optional) */
    @Column(length = 200)
    private String sourceName;

    /** 분석 완료 여부 */
    private boolean completed;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public WorkspaceHistory() {}

    public WorkspaceHistory(String language, String analysisTypes,
                             String codeSnippet, String sourceName) {
        this.language      = language;
        this.analysisTypes = analysisTypes;
        this.codeSnippet   = codeSnippet;
        this.sourceName    = sourceName;
        this.completed     = false;
        this.createdAt     = LocalDateTime.now();
    }

    public Long getId()                  { return id; }
    public String getLanguage()          { return language; }
    public String getAnalysisTypes()     { return analysisTypes; }
    public String getCodeSnippet()       { return codeSnippet; }
    public String getSourceName()        { return sourceName; }
    public boolean isCompleted()         { return completed; }
    public LocalDateTime getCreatedAt()  { return createdAt; }

    public void setCompleted(boolean completed) { this.completed = completed; }
}
