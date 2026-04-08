package io.github.claudetoolkit.ui.security;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * API 호출 감사 로그 엔티티.
 * review_history 와 동일한 H2 파일 DB에 저장됩니다.
 */
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_created", columnList = "createdAt")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /** 요청 URI (최대 300자) */
    @Column(nullable = false, length = 300)
    private String endpoint;

    /** HTTP 메서드 (GET/POST/…) */
    @Column(nullable = false, length = 10)
    private String method;

    /** 클라이언트 IP (프록시 고려 X-Forwarded-For 우선) */
    @Column(nullable = true, length = 60)
    private String ip;

    /** User-Agent (최대 300자) */
    @Column(nullable = true, length = 300)
    private String userAgent;

    /** HTTP 응답 상태 코드 */
    @Column(nullable = true)
    private Integer statusCode;

    /** X-Api-Key 헤더 사용 여부 */
    @Column(nullable = false)
    private boolean apiKeyUsed = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {}

    public AuditLog(String endpoint, String method, String ip,
                    String userAgent, Integer statusCode, boolean apiKeyUsed) {
        this.endpoint   = truncate(endpoint, 300);
        this.method     = truncate(method,   10);
        this.ip         = truncate(ip,       60);
        this.userAgent  = truncate(userAgent, 300);
        this.statusCode = statusCode;
        this.apiKeyUsed = apiKeyUsed;
        this.createdAt  = LocalDateTime.now();
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public long          getId()         { return id; }
    public String        getEndpoint()   { return endpoint; }
    public String        getMethod()     { return method; }
    public String        getIp()         { return ip; }
    public String        getUserAgent()  { return userAgent; }
    public Integer       getStatusCode() { return statusCode; }
    public boolean       isApiKeyUsed()  { return apiKeyUsed; }
    public LocalDateTime getCreatedAt()  { return createdAt; }

    public String getFormattedDate() {
        return createdAt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
    }

    public String getStatusBadgeColor() {
        if (statusCode == null) return "#64748b";
        if (statusCode < 300)  return "#10b981";
        if (statusCode < 400)  return "#f59e0b";
        if (statusCode < 500)  return "#ef4444";
        return "#7c3aed";
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
