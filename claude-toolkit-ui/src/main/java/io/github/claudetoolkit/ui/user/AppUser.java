package io.github.claudetoolkit.ui.user;

import io.github.claudetoolkit.ui.security.CryptoUtils;

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

    /** 개인 Claude API 키 (AES 암호화 저장) */
    @Column(length = 500)
    private String personalApiKey;

    /** TOTP 시크릿 (AES 암호화 저장). null이면 2FA 비활성. */
    @Column(length = 500)
    private String totpSecret;

    /** 분당 API 호출 제한 (0=무제한) */
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int rateLimitPerMinute = 0;

    /** 시간당 API 호출 제한 (0=무제한) */
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int rateLimitPerHour = 0;

    /** ADMIN, REVIEWER, VIEWER */
    @Column(nullable = false, length = 20)
    private String role;

    /** 초기 비밀번호 강제 변경 플래그 (true이면 비밀번호 변경 전까지 모든 페이지 접근 차단) */
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean mustChangePassword = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastLoginAt;

    /** 로그인 실패 연속 횟수 (5회 도달 시 잠금) */
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int failedLoginAttempts = 0;

    /** 계정 잠금 해제 시각 (미래 시각이면 로그인 불가) */
    @Column
    private LocalDateTime lockedUntil;

    /** 마지막 비밀번호 변경 시각 */
    @Column
    private LocalDateTime lastPasswordChangeAt;

    /** 비밀번호 만료 알림 "다음에 변경하기" 스누즈 시각 (클릭 후 다시 90일 카운팅 시작) */
    @Column
    private LocalDateTime passwordSnoozeAt;

    /** 사용자별 IP 화이트리스트 (콤마 구분, 비어있으면 모든 IP 허용) */
    @Column(length = 500)
    private String ipWhitelist;

    /** v4.3.0 — 사용자 선호 언어 (ko/en/ja/zh/de). null 이면 ko 기본 */
    @Column(length = 5)
    private String locale;

    /** 일일 API 호출 제한 (0=무제한) */
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int dailyApiLimit = 0;

    /** 월간 API 호출 제한 (0=무제한) */
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int monthlyApiLimit = 0;

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
    /** 복호화된 개인 API 키 반환 */
    public String getPersonalApiKey()       { return CryptoUtils.decrypt(personalApiKey); }
    /** 복호화된 TOTP 시크릿 반환 */
    public String getTotpSecret()            { return CryptoUtils.decrypt(totpSecret); }
    public boolean isTotpEnabled() {
        String secret = getTotpSecret();
        return secret != null && !secret.isEmpty();
    }
    public int    getRateLimitPerMinute()  { return rateLimitPerMinute; }
    public int    getRateLimitPerHour()    { return rateLimitPerHour; }
    public String getDisplayName()         { return displayName; }
    public String getEmail()               { return email; }
    public String getPhone()               { return phone; }
    public String getRole()                { return role; }
    public boolean isMustChangePassword()  { return mustChangePassword; }
    public boolean isEnabled()             { return enabled; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public LocalDateTime getLastLoginAt()  { return lastLoginAt; }
    public int           getFailedLoginAttempts()  { return failedLoginAttempts; }
    public LocalDateTime getLockedUntil()          { return lockedUntil; }
    public LocalDateTime getLastPasswordChangeAt() { return lastPasswordChangeAt; }
    public LocalDateTime getPasswordSnoozeAt()     { return passwordSnoozeAt; }
    public String        getIpWhitelist()           { return ipWhitelist; }
    public String        getLocale()                { return locale; }
    public int           getDailyApiLimit()         { return dailyApiLimit; }
    public int           getMonthlyApiLimit()       { return monthlyApiLimit; }

    /** 현재 계정이 잠겨 있는지 확인 (lockedUntil이 미래 시각일 때 잠김) */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public void setUsername(String username)           { this.username = username; }
    public void setPasswordHash(String passwordHash)  { this.passwordHash = passwordHash; }
    /** 암호화하여 개인 API 키 저장 */
    public void setPersonalApiKey(String key) {
        this.personalApiKey = (key != null && !key.isEmpty()) ? CryptoUtils.ensureEncrypted(key) : null;
    }
    /** 암호화하여 TOTP 시크릿 저장 */
    public void setTotpSecret(String secret) {
        this.totpSecret = (secret != null && !secret.isEmpty()) ? CryptoUtils.ensureEncrypted(secret) : null;
    }
    public void setRateLimitPerMinute(int v)             { this.rateLimitPerMinute = v; }
    public void setRateLimitPerHour(int v)               { this.rateLimitPerHour = v; }
    public void setDisplayName(String displayName)     { this.displayName = displayName; }
    public void setEmail(String email)                 { this.email = email; }
    public void setPhone(String phone)                 { this.phone = phone; }
    public void setRole(String role)                   { this.role = role; }
    public void setMustChangePassword(boolean v)       { this.mustChangePassword = v; }
    public void setEnabled(boolean enabled)            { this.enabled = enabled; }
    public void setCreatedAt(LocalDateTime createdAt)  { this.createdAt = createdAt; }
    public void setLastLoginAt(LocalDateTime t)        { this.lastLoginAt = t; }
    public void setFailedLoginAttempts(int n)          { this.failedLoginAttempts = n; }
    public void setLockedUntil(LocalDateTime t)        { this.lockedUntil = t; }
    public void setLastPasswordChangeAt(LocalDateTime t){ this.lastPasswordChangeAt = t; }
    public void setPasswordSnoozeAt(LocalDateTime t)   { this.passwordSnoozeAt = t; }
    public void setIpWhitelist(String v)               { this.ipWhitelist = v; }
    public void setLocale(String v)                    { this.locale = v; }
    public void setDailyApiLimit(int v)                { this.dailyApiLimit = v; }
    public void setMonthlyApiLimit(int v)              { this.monthlyApiLimit = v; }
}
