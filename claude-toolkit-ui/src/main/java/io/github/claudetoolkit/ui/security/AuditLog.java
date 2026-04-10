package io.github.claudetoolkit.ui.security;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

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
        this.createdAt  = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
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
        return createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** endpoint URL → 기능 메뉴 한국어명 매핑 */
    private static final Map<String, String> MENU_NAME_MAP = new LinkedHashMap<String, String>();
    static {
        MENU_NAME_MAP.put("/workspace",     "통합 워크스페이스");
        MENU_NAME_MAP.put("/advisor",       "SQL 리뷰");
        MENU_NAME_MAP.put("/sql-translate", "SQL DB 번역");
        MENU_NAME_MAP.put("/sql-batch",     "배치 SQL 분석");
        MENU_NAME_MAP.put("/erd",           "ERD 분석");
        MENU_NAME_MAP.put("/complexity",    "복잡도 분석");
        MENU_NAME_MAP.put("/explain",       "실행계획 분석");
        MENU_NAME_MAP.put("/harness",       "코드 리뷰 하네스");
        MENU_NAME_MAP.put("/codereview",    "코드 리뷰");
        MENU_NAME_MAP.put("/pipelines",     "분석 파이프라인");
        MENU_NAME_MAP.put("/docgen",        "기술 문서");
        MENU_NAME_MAP.put("/testgen",       "테스트 생성");
        MENU_NAME_MAP.put("/apispec",       "API 명세");
        MENU_NAME_MAP.put("/converter",     "코드 변환");
        MENU_NAME_MAP.put("/mockdata",      "Mock 데이터");
        MENU_NAME_MAP.put("/migration",     "DB 마이그레이션");
        MENU_NAME_MAP.put("/batch",         "Batch 처리");
        MENU_NAME_MAP.put("/depcheck",      "의존성 분석");
        MENU_NAME_MAP.put("/migrate",       "Spring 마이그레이션");
        MENU_NAME_MAP.put("/history",         "리뷰 이력");
        MENU_NAME_MAP.put("/favorites",       "즐겨찾기");
        MENU_NAME_MAP.put("/usage",           "사용량 모니터링");
        MENU_NAME_MAP.put("/roi-report",      "ROI 리포트");
        MENU_NAME_MAP.put("/schedule",        "분석 스케줄링");
        MENU_NAME_MAP.put("/review-requests", "팀 리뷰 요청");
        MENU_NAME_MAP.put("/loganalyzer",   "로그 분석기");
        MENU_NAME_MAP.put("/regex",         "정규식 생성기");
        MENU_NAME_MAP.put("/commitmsg",     "커밋 메시지");
        MENU_NAME_MAP.put("/maskgen",       "마스킹 스크립트");
        MENU_NAME_MAP.put("/input-masking", "민감정보 마스킹");
        MENU_NAME_MAP.put("/github-pr",     "GitHub PR 리뷰");
        MENU_NAME_MAP.put("/git-diff",      "Git Diff 분석");
        MENU_NAME_MAP.put("/chat",          "AI 채팅");
        MENU_NAME_MAP.put("/prompts",       "프롬프트 템플릿");
        MENU_NAME_MAP.put("/search",        "글로벌 검색");
        MENU_NAME_MAP.put("/settings",      "Settings");
        MENU_NAME_MAP.put("/security",      "보안 설정");
        MENU_NAME_MAP.put("/admin",         "관리");
        MENU_NAME_MAP.put("/account",       "내 설정");
        MENU_NAME_MAP.put("/db-profiles",   "DB 프로필");
        MENU_NAME_MAP.put("/login",         "로그인");
        MENU_NAME_MAP.put("/logout",        "로그아웃");
        MENU_NAME_MAP.put("/api/",          "REST API");
        MENU_NAME_MAP.put("/share",         "공유 링크");
        MENU_NAME_MAP.put("/notifications", "알림");
    }

    /** endpoint에서 기능 메뉴 이름을 추출합니다. */
    public String getMenuName() {
        if (endpoint == null) return "-";
        // "/" (홈) 정확히 매칭
        if ("/".equals(endpoint)) return "홈";
        for (Map.Entry<String, String> entry : MENU_NAME_MAP.entrySet()) {
            if (endpoint.equals(entry.getKey()) || endpoint.startsWith(entry.getKey() + "/")) {
                return entry.getValue();
            }
        }
        return "-";
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
