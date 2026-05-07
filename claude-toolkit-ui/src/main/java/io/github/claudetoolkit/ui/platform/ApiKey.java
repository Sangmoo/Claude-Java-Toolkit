package io.github.claudetoolkit.ui.platform;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * v4.7.x — #M3 Platform: 멀티 API 키 (per-key 발급/회수/quota).
 *
 * <p>기존 {@link io.github.claudetoolkit.ui.security.ApiKeyFilter} 의 *글로벌 단일 키* 와 별개.
 * MCP / CLI / SDK / 외부 자동화가 각자 자기 키를 가짐 → 사용처 추적 + per-key quota +
 * 회수 가능. 운영팀이 외부 노출 의심 시 *해당 키만* 즉시 회수.
 *
 * <p><b>SECURITY</b>: 평문 키는 *발급 직후 1회만* 사용자에게 노출. DB 에는 SHA-256 해시만 저장.
 * 비교는 timing-safe (MessageDigest.isEqual).
 */
@Entity
@Table(name = "platform_api_key", indexes = {
        @Index(name = "idx_pak_hash",    columnList = "keyHash"),
        @Index(name = "idx_pak_owner",   columnList = "createdBy"),
        @Index(name = "idx_pak_revoked", columnList = "revoked")
})
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 헥사 해시 (64자) — 평문은 저장 안 함 */
    @Column(nullable = false, length = 64, unique = true)
    private String keyHash;

    /** 키 prefix (예: {@code ctk_live_a1b2c3...}) — 식별용 (UI 표시) */
    @Column(nullable = false, length = 24)
    private String keyPrefix;

    /** 사람이 읽기 쉬운 라벨 (예: "Claude Code MCP", "내 IDE") */
    @Column(nullable = false, length = 100)
    private String name;

    /** 발급자 (Spring Security username) */
    @Column(nullable = false, length = 50)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 만료 시각 — null 이면 무기한 (권장 X) */
    @Column
    private LocalDateTime expiresAt;

    /** 마지막 사용 시각 — Filter 가 갱신 (옵셔널: 빈도 줄이려면 throttle) */
    @Column
    private LocalDateTime lastUsedAt;

    /** 회수 여부 — true 면 모든 호출 401 */
    @Column(nullable = false)
    private boolean revoked = false;

    /**
     * 키별 권한 — "READ_ONLY" (analyze 만) / "WRITE" (analyze + history 변경) / "ADMIN" (모든 endpoint).
     * 1차 출시는 단순 — 추후 RBAC 매트릭스 확장.
     */
    @Column(nullable = false, length = 20)
    private String role = "READ_ONLY";

    /** 분당 호출 한도 — 기본 60. 0 또는 음수면 제한 없음. */
    @Column(nullable = false)
    private Integer rateLimitPerMinute = 60;

    /** 누적 호출 카운터 (대장 표시용 — 실시간 정확도 보장 X) */
    @Column(nullable = false)
    private Long totalCalls = 0L;

    protected ApiKey() {}

    public ApiKey(String keyHash, String keyPrefix, String name, String createdBy,
                  LocalDateTime expiresAt, String role, Integer rateLimitPerMinute) {
        this.keyHash             = keyHash;
        this.keyPrefix           = keyPrefix;
        this.name                = name;
        this.createdBy           = createdBy;
        this.createdAt           = LocalDateTime.now();
        this.expiresAt           = expiresAt;
        this.role                = role != null ? role : "READ_ONLY";
        this.rateLimitPerMinute  = rateLimitPerMinute != null ? rateLimitPerMinute : 60;
    }

    /** 만료 / 회수 / 미사용 — 모두 통과해야 valid */
    public boolean isUsable() {
        if (revoked) return false;
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) return false;
        return true;
    }

    // ── getters / setters ────────────────────────────────────────────

    public Long          getId()                            { return id; }
    public String        getKeyHash()                       { return keyHash; }
    public String        getKeyPrefix()                     { return keyPrefix; }
    public String        getName()                          { return name; }
    public void          setName(String n)                  { this.name = n; }
    public String        getCreatedBy()                     { return createdBy; }
    public LocalDateTime getCreatedAt()                     { return createdAt; }
    public LocalDateTime getExpiresAt()                     { return expiresAt; }
    public void          setExpiresAt(LocalDateTime e)      { this.expiresAt = e; }
    public LocalDateTime getLastUsedAt()                    { return lastUsedAt; }
    public void          setLastUsedAt(LocalDateTime t)     { this.lastUsedAt = t; }
    public boolean       isRevoked()                        { return revoked; }
    public void          setRevoked(boolean v)              { this.revoked = v; }
    public String        getRole()                          { return role; }
    public void          setRole(String r)                  { this.role = r; }
    public Integer       getRateLimitPerMinute()            { return rateLimitPerMinute; }
    public void          setRateLimitPerMinute(Integer n)   { this.rateLimitPerMinute = n; }
    public Long          getTotalCalls()                    { return totalCalls; }
    public void          setTotalCalls(Long n)              { this.totalCalls = n; }
}
