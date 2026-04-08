package io.github.claudetoolkit.ui.prompt;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 커스텀 시스템 프롬프트 엔티티.
 * <p>분석 유형별로 사용자가 직접 편집한 시스템 프롬프트를 H2 DB에 저장합니다.
 * isActive=true 인 항목이 해당 분석 유형의 활성 프롬프트로 사용됩니다.
 */
@Entity
@Table(name = "custom_prompt")
public class CustomPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 분석 유형 식별자 (AnalysisType.name()) */
    @Column(nullable = false, length = 50)
    private String analysisType;

    /** 프롬프트 이름 (사용자 지정) */
    @Column(nullable = false, length = 100)
    private String promptName;

    /** 시스템 프롬프트 본문 */
    @Lob
    @Column(nullable = false)
    private String systemPrompt;

    /** 활성 여부 — true인 항목만 실제 분석에 사용됩니다 */
    @Column(nullable = false)
    private boolean isActive = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── getters / setters ─────────────────────────────────────────────────────

    public Long          getId()                      { return id; }
    public void          setId(Long id)               { this.id = id; }

    public String        getAnalysisType()            { return analysisType; }
    public void          setAnalysisType(String v)    { this.analysisType = v; }

    public String        getPromptName()              { return promptName; }
    public void          setPromptName(String v)      { this.promptName = v; }

    public String        getSystemPrompt()            { return systemPrompt; }
    public void          setSystemPrompt(String v)    { this.systemPrompt = v; }

    public boolean       isActive()                   { return isActive; }
    public void          setActive(boolean v)         { this.isActive = v; }

    public LocalDateTime getCreatedAt()               { return createdAt; }
    public void          setCreatedAt(LocalDateTime v){ this.createdAt = v; }

    public LocalDateTime getUpdatedAt()               { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime v){ this.updatedAt = v; }

    public String getFormattedUpdatedAt() {
        if (updatedAt == null) return "";
        return updatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
