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

    /** User-Agent (최대 500자) */
    @Column(nullable = true, length = 500)
    private String userAgent;

    /** HTTP 응답 상태 코드 */
    @Column(nullable = true)
    private Integer statusCode;

    /** X-Api-Key 헤더 사용 여부 */
    @Column(nullable = false)
    private boolean apiKeyUsed = false;

    /** 인증된 사용자명 (Spring Security) */
    @Column(length = 50)
    private String username;

    /** 요청 처리 소요시간 (밀리초) */
    @Column
    private Long durationMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {}

    public AuditLog(String endpoint, String method, String ip,
                    String userAgent, Integer statusCode, boolean apiKeyUsed) {
        this.endpoint   = truncate(endpoint, 300);
        this.method     = truncate(method,   10);
        this.ip         = truncate(ip,       60);
        this.userAgent  = truncate(userAgent, 500);
        this.statusCode = statusCode;
        this.apiKeyUsed = apiKeyUsed;
        this.createdAt  = LocalDateTime.now();
    }

    public AuditLog(String endpoint, String method, String ip,
                    String userAgent, Integer statusCode, boolean apiKeyUsed, String username) {
        this(endpoint, method, ip, userAgent, statusCode, apiKeyUsed);
        this.username = truncate(username, 50);
    }

    public AuditLog(String endpoint, String method, String ip,
                    String userAgent, Integer statusCode, boolean apiKeyUsed, String username, Long durationMs) {
        this(endpoint, method, ip, userAgent, statusCode, apiKeyUsed, username);
        this.durationMs = durationMs;
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public long          getId()         { return id; }
    public String        getEndpoint()   { return endpoint; }
    public String        getMethod()     { return method; }
    public String        getIp()         { return ip; }
    public String        getUserAgent()  { return userAgent; }
    public Integer       getStatusCode() { return statusCode; }
    public boolean       isApiKeyUsed()  { return apiKeyUsed; }
    public String        getUsername()   { return username; }
    public Long          getDurationMs(){ return durationMs; }
    public LocalDateTime getCreatedAt()  { return createdAt; }

    /** 액션 유형 추정 (endpoint 기반) */
    public String getActionType() {
        if (endpoint == null) return "기타";
        if (endpoint.contains("/download") || endpoint.contains("/export")) return "다운로드";
        if (endpoint.contains("/send-email")) return "이메일";
        if (endpoint.contains("/login"))  return "로그인";
        if (endpoint.contains("/logout")) return "로그아웃";
        if (endpoint.contains("/share"))  return "공유";
        if ("POST".equals(method)) {
            if (endpoint.contains("/run") || endpoint.contains("/generate") || endpoint.contains("/convert")
                || endpoint.contains("/init") || endpoint.contains("/analyze")) return "분석실행";
            if (endpoint.contains("/save") || endpoint.contains("/create")) return "저장";
            if (endpoint.contains("/delete")) return "삭제";
            return "변경";
        }
        return "조회";
    }

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
