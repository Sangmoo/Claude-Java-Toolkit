package io.github.claudetoolkit.ui.service;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 분석 결과 캐시 엔티티 (v2.7.0).
 *
 * <p>기존 인메모리 LRU 캐시를 H2 테이블로 영속화하여 서버 재시작 후에도
 * 캐시를 유지합니다.
 */
@Entity
@Table(name = "analysis_cache", indexes = {
    @Index(name = "idx_cache_key",     columnList = "cacheKey", unique = true),
    @Index(name = "idx_cache_expires", columnList = "expiresAt")
})
public class AnalysisCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 해시 기반 캐시 키 */
    @Column(nullable = false, length = 64, unique = true)
    private String cacheKey;

    /** 분석 유형 (feature name) */
    @Column(length = 50)
    private String feature;

    /** 캐시된 결과 (TEXT 타입) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String resultText;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int hitCount = 0;

    protected AnalysisCache() {}

    public AnalysisCache(String cacheKey, String feature, String resultText,
                         LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.cacheKey   = cacheKey;
        this.feature    = feature;
        this.resultText = resultText;
        this.createdAt  = createdAt;
        this.expiresAt  = expiresAt;
        this.hitCount   = 0;
    }

    // ── getters / setters ──
    public Long          getId()         { return id; }
    public String        getCacheKey()   { return cacheKey; }
    public String        getFeature()    { return feature; }
    public String        getResultText() { return resultText; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public LocalDateTime getExpiresAt()  { return expiresAt; }
    public int           getHitCount()   { return hitCount; }

    public void setResultText(String v)     { this.resultText = v; }
    public void setExpiresAt(LocalDateTime v){ this.expiresAt = v; }
    public void setHitCount(int v)          { this.hitCount = v; }
    public void incrementHitCount()         { this.hitCount++; }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
}
