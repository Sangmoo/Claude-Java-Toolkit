package io.github.claudetoolkit.ui.dbprofile;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Saved Oracle DB connection profile.
 * Multiple profiles can be stored and switched into the active settings at runtime.
 */
@Entity
@Table(name = "db_profile")
public class DbProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false, length = 200)
    private String username;

    @Column(nullable = false, length = 500)
    private String password;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * v4.7.x — #G3 Live DB Phase 0: 이 프로필이 분석 페이지에서 *직접 EXPLAIN/통계 조회* 에
     * 사용 가능한지. 기본 false — ADMIN 이 명시적으로 활성화해야 함.
     *
     * <p>활성화 조건: 이 프로필의 user 가 *읽기 전용* 권한만 갖고 있어야 함 (DDL/DML 권한 X).
     * 이 플래그는 사용자 책임으로 명시적 토글하므로, "확인했음" 의 의미.
     */
    @Column(nullable = false)
    private boolean readOnlyForLiveAnalysis = false;

    /**
     * v4.7.x — Live DB 분석 시 사용할 별도 user (옵션). null 이면 기본 {@link #username} 사용.
     * 운영 user 와 분석 user 를 분리할 수 있게 함 — 권한 모델이 깨끗.
     */
    @Column(length = 200)
    private String liveAnalysisUser;

    /**
     * v4.7.x — Live DB 쿼리 statement timeout 초. null 이면 글로벌 default 사용
     * (LiveDbConfig.defaultTimeoutSeconds, 기본 30초).
     */
    @Column
    private Integer liveQueryTimeoutSeconds;

    protected DbProfile() {}

    public DbProfile(String name, String url, String username, String password, String description) {
        this.name        = name;
        this.url         = url;
        this.username    = username;
        this.password    = password;
        this.description = description != null ? description : "";
        this.createdAt   = LocalDateTime.now();
    }

    public String getFormattedDate() {
        return createdAt != null
                ? createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : "-";
    }

    public String getMaskedUrl() {
        if (url == null) return "";
        // Show host:port/service but hide credentials embedded in URL
        return url.length() > 80 ? url.substring(0, 77) + "..." : url;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId()                         { return id; }
    public void setId(Long id)                  { this.id = id; }

    public String getName()                     { return name; }
    public void setName(String name)            { this.name = name; }

    public String getUrl()                      { return url; }
    public void setUrl(String url)              { this.url = url; }

    public String getUsername()                 { return username; }
    public void setUsername(String username)    { this.username = username; }

    public String getPassword()                 { return password; }
    public void setPassword(String password)    { this.password = password; }

    public String getDescription()              { return description; }
    public void setDescription(String d)        { this.description = d; }

    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void setCreatedAt(LocalDateTime v)   { this.createdAt = v; }

    // v4.7.x — #G3 Live DB
    public boolean isReadOnlyForLiveAnalysis()                  { return readOnlyForLiveAnalysis; }
    public void    setReadOnlyForLiveAnalysis(boolean v)        { this.readOnlyForLiveAnalysis = v; }
    public String  getLiveAnalysisUser()                        { return liveAnalysisUser; }
    public void    setLiveAnalysisUser(String u)                { this.liveAnalysisUser = u; }
    public Integer getLiveQueryTimeoutSeconds()                 { return liveQueryTimeoutSeconds; }
    public void    setLiveQueryTimeoutSeconds(Integer s)        { this.liveQueryTimeoutSeconds = s; }
}
