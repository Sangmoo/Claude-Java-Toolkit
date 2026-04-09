package io.github.claudetoolkit.ui.user;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 사용자 엔티티.
 *
 * <p>역할(role):
 * <ul>
 *   <li>ADMIN — 전체 기능 + 사용자 관리 + Settings</li>
 *   <li>REVIEWER — 분석 기능 + 프롬프트 편집</li>
 *   <li>VIEWER — 분석 기능 읽기/실행만</li>
 * </ul>
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    /** 사용자 이름 (표시명) */
    @Column(length = 100)
    private String displayName;

    /** 이메일 주소 */
    @Column(length = 200)
    private String email;

    /** 핸드폰 번호 */
    @Column(length = 20)
    private String phone;

    /** 개인 Claude API 키 (사용자별 분리 추적) */
    @Column(length = 200)
    private String personalApiKey;

    /** 분당 API 호출 제한 (0=무제한) */
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int rateLimitPerMinute = 0;

    /** 시간당 API 호출 제한 (0=무제한) */
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int rateLimitPerHour = 0;

    /** ADMIN, REVIEWER, VIEWER */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastLoginAt;

    protected AppUser() {}

    public AppUser(String username, String passwordHash, String role) {
        this.username     = username;
        this.passwordHash = passwordHash;
        this.role         = role;
        this.enabled      = true;
        this.createdAt    = LocalDateTime.now();
    }

    // ── getters / setters ──

    public Long getId()                    { return id; }
    public String getUsername()            { return username; }
    public String getPasswordHash()        { return passwordHash; }
    public String getPersonalApiKey()       { return personalApiKey; }
    public int    getRateLimitPerMinute()  { return rateLimitPerMinute; }
    public int    getRateLimitPerHour()    { return rateLimitPerHour; }
    public String getDisplayName()         { return displayName; }
    public String getEmail()               { return email; }
    public String getPhone()               { return phone; }
    public String getRole()                { return role; }
    public boolean isEnabled()             { return enabled; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public LocalDateTime getLastLoginAt()  { return lastLoginAt; }

    public void setUsername(String username)           { this.username = username; }
    public void setPasswordHash(String passwordHash)  { this.passwordHash = passwordHash; }
    public void setPersonalApiKey(String key)            { this.personalApiKey = key; }
    public void setRateLimitPerMinute(int v)             { this.rateLimitPerMinute = v; }
    public void setRateLimitPerHour(int v)               { this.rateLimitPerHour = v; }
    public void setDisplayName(String displayName)     { this.displayName = displayName; }
    public void setEmail(String email)                 { this.email = email; }
    public void setPhone(String phone)                 { this.phone = phone; }
    public void setRole(String role)                   { this.role = role; }
    public void setEnabled(boolean enabled)            { this.enabled = enabled; }
    public void setCreatedAt(LocalDateTime createdAt)  { this.createdAt = createdAt; }
    public void setLastLoginAt(LocalDateTime t)        { this.lastLoginAt = t; }
}
