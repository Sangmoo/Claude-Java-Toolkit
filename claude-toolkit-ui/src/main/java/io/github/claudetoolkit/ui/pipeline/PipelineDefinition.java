package io.github.claudetoolkit.ui.pipeline;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 분석 파이프라인 정의 (v2.9.5).
 *
 * <p>YAML 기반 파이프라인 스크립트를 DB에 저장합니다.
 * 내장 파이프라인({@code isBuiltin = true})은 삭제 불가.
 */
@Entity
@Table(name = "pipeline_definition", indexes = {
    @Index(name = "idx_pipedef_owner",   columnList = "createdBy"),
    @Index(name = "idx_pipedef_builtin", columnList = "isBuiltin")
})
public class PipelineDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** YAML 원본 컨텐츠 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String yamlContent;

    /** 입력 언어 (java / sql / kotlin 등) */
    @Column(length = 20)
    private String inputLanguage;

    /** 내장 파이프라인 여부 (true면 삭제 금지) */
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isBuiltin = false;

    /** 생성자 username (내장은 null) */
    @Column(length = 50)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected PipelineDefinition() {}

    public PipelineDefinition(String name, String description, String yamlContent,
                              String inputLanguage, boolean isBuiltin, String createdBy) {
        this.name          = name;
        this.description   = description;
        this.yamlContent   = yamlContent;
        this.inputLanguage = inputLanguage;
        this.isBuiltin     = isBuiltin;
        this.createdBy     = createdBy;
        this.createdAt     = LocalDateTime.now();
        this.updatedAt     = this.createdAt;
    }

    // ── getters / setters ──
    public Long          getId()            { return id; }
    public String        getName()          { return name; }
    public String        getDescription()   { return description; }
    public String        getYamlContent()   { return yamlContent; }
    public String        getInputLanguage() { return inputLanguage; }
    public boolean       isBuiltin()        { return isBuiltin; }
    public String        getCreatedBy()     { return createdBy; }
    public LocalDateTime getCreatedAt()     { return createdAt; }
    public LocalDateTime getUpdatedAt()     { return updatedAt; }

    public void setName(String v)        { this.name = v; }
    public void setDescription(String v) { this.description = v; }
    public void setYamlContent(String v) { this.yamlContent = v; }
    public void setInputLanguage(String v){ this.inputLanguage = v; }
    public void touch()                   { this.updatedAt = LocalDateTime.now(); }
}
