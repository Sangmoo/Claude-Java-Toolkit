package io.github.claudetoolkit.ui.share;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 팀 공유 설정 엔티티.
 *
 * <p>configType:
 * <ul>
 *   <li>CONTEXT — 프로젝트 컨텍스트</li>
 *   <li>TEMPLATE — 분석 템플릿 (번들)</li>
 *   <li>PROMPT — 커스텀 프롬프트</li>
 * </ul>
 */
@Entity
@Table(name = "shared_config")
public class SharedConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String configType;

    @Column(nullable = false, length = 200)
    private String name;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false, length = 50)
    private String createdBy;

    @Column(nullable = false)
    private boolean isPublic;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected SharedConfig() {}

    public SharedConfig(String configType, String name, String content, String createdBy, boolean isPublic) {
        this.configType = configType;
        this.name       = name;
        this.content    = content;
        this.createdBy  = createdBy;
        this.isPublic   = isPublic;
        this.createdAt  = LocalDateTime.now();
    }

    public Long getId()                    { return id; }
    public String getConfigType()          { return configType; }
    public String getName()                { return name; }
    public String getContent()             { return content; }
    public String getCreatedBy()           { return createdBy; }
    public boolean isPublic()              { return isPublic; }
    public LocalDateTime getCreatedAt()    { return createdAt; }

    public void setName(String name)              { this.name = name; }
    public void setContent(String content)        { this.content = content; }
    public void setPublic(boolean isPublic)       { this.isPublic = isPublic; }
}
