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
    public String getRole()                { return role; }
    public boolean isEnabled()             { return enabled; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public LocalDateTime getLastLoginAt()  { return lastLoginAt; }

    public void setUsername(String username)           { this.username = username; }
    public void setPasswordHash(String passwordHash)  { this.passwordHash = passwordHash; }
    public void setRole(String role)                   { this.role = role; }
    public void setEnabled(boolean enabled)            { this.enabled = enabled; }
    public void setCreatedAt(LocalDateTime createdAt)  { this.createdAt = createdAt; }
    public void setLastLoginAt(LocalDateTime t)        { this.lastLoginAt = t; }
}
