package io.github.claudetoolkit.ui.history;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JPA entity representing a single review/generation history entry.
 * Persisted to H2 file database at ~/.claude-toolkit/history-db.
 */
@Entity
@Table(name = "review_history")
public class ReviewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String inputContent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String outputContent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Root-node cost from EXPLAIN PLAN (nullable — only set for EXPLAIN_PLAN type) */
    @Column(nullable = true)
    private Long costValue;

    /** Input token count from Claude API (nullable — only set for v1.3.0+ requests) */
    @Column(nullable = true)
    private Long inputTokens;

    /** Output token count from Claude API (nullable — only set for v1.3.0+ requests) */
    @Column(nullable = true)
    private Long outputTokens;

    /** Original source code submitted to harness pipeline (nullable — only for HARNESS_REVIEW) */
    @Column(nullable = true, columnDefinition = "TEXT")
    private String originalCode;

    /** Improved code produced by the Builder step (nullable — only for HARNESS_REVIEW) */
    @Column(nullable = true, columnDefinition = "TEXT")
    private String improvedCode;

    /** Language used in harness analysis: "java" or "sql" (nullable — only for HARNESS_REVIEW) */
    @Column(nullable = true, length = 20)
    private String analysisLanguage;

    /** 분석을 실행한 사용자 username (v2.4.0+) */
    @Column(length = 50)
    private String username;

    /** v4.2.x: 리뷰 승인 상태 — PENDING / ACCEPTED / REJECTED */
    @Column(length = 20)
    private String reviewStatus = "PENDING";

    /** v4.2.x: 승인/거절한 리뷰어 username */
    @Column(length = 50)
    private String reviewedBy;

    /** v4.2.x: 승인/거절 시각 */
    @Column
    private LocalDateTime reviewedAt;

    /** v4.2.x: 승인/거절 코멘트 */
    @Column(columnDefinition = "TEXT")
    private String reviewNote;

    /** Required by JPA — do not use directly */
    protected ReviewHistory() {}

    public ReviewHistory(String type, String title, String inputContent, String outputContent) {
        this.type          = type;
        this.title         = title;
        this.inputContent  = inputContent;
        this.outputContent = outputContent;
        this.createdAt     = LocalDateTime.now();
    }

    public ReviewHistory(String type, String title, String inputContent, String outputContent, Long costValue) {
        this(type, title, inputContent, outputContent);
        this.costValue = costValue;
    }

    public ReviewHistory(String type, String title, String inputContent, String outputContent, Long costValue, Long inputTokens, Long outputTokens) {
        this(type, title, inputContent, outputContent, costValue);
        this.inputTokens  = inputTokens;
        this.outputTokens = outputTokens;
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public long getId()              { return id; }
    public String getType()          { return type; }
    public String getTitle()         { return title; }
    public String getInputContent()  { return inputContent; }
    public String getOutputContent() { return outputContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getCostValue()       { return costValue; }
    public Long getInputTokens()     { return inputTokens; }
    public void setInputTokens(Long t) { this.inputTokens = t; }
    public Long getOutputTokens()    { return outputTokens; }
    public void setOutputTokens(Long t) { this.outputTokens = t; }

    public String getOriginalCode()            { return originalCode; }
    public void setOriginalCode(String c)      { this.originalCode = c; }
    public String getImprovedCode()            { return improvedCode; }
    public void setImprovedCode(String c)      { this.improvedCode = c; }
    public String getAnalysisLanguage()        { return analysisLanguage; }
    public void setAnalysisLanguage(String l)  { this.analysisLanguage = l; }
    public String getUsername()                { return username; }
    public void setUsername(String u)          { this.username = u; }

    // v4.2.x: 리뷰 승인 상태
    public String getReviewStatus()            { return reviewStatus == null ? "PENDING" : reviewStatus; }
    public void setReviewStatus(String s)      { this.reviewStatus = s; }
    public String getReviewedBy()              { return reviewedBy; }
    public void setReviewedBy(String r)        { this.reviewedBy = r; }
    public LocalDateTime getReviewedAt()       { return reviewedAt; }
    public void setReviewedAt(LocalDateTime t) { this.reviewedAt = t; }
    public String getReviewNote()              { return reviewNote; }
    public void setReviewNote(String n)        { this.reviewNote = n; }

    public long getTotalTokens() {
        long i = inputTokens  != null ? inputTokens  : 0;
        long o = outputTokens != null ? outputTokens : 0;
        return i + o;
    }

    /** MM-dd HH:mm format */
    public String getFormattedDate() {
        return createdAt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    /** Korean label for the history type */
    public String getTypeLabel() {
        return typeLabelOf(type);
    }

    /**
     * v4.6.x — type 문자열만으로 한국어 라벨 변환하는 정적 헬퍼.
     *
     * <p>이전엔 호출부가 {@code new ReviewHistory(type, "", "", "")} dummy 인스턴스를
     * 만들고 {@link #getTypeLabel()} 호출했었는데 fragile 하고 nullable=false 컬럼
     * 정책이 바뀌면 깨질 수 있어서 정적 메서드로 추출. 라벨 매핑의 단일 진실 출처.
     */
    public static String typeLabelOf(String type) {
        if ("SQL_REVIEW".equals(type))      return "SQL 리뷰";
        if ("SQL_SECURITY".equals(type))    return "SQL 보안";
        if ("DOC_GEN".equals(type))         return "문서 생성";
        if ("CODE_CONVERT".equals(type))    return "코드 변환";
        if ("ERD".equals(type))             return "ERD 분석";
        if ("TEST_GEN".equals(type))        return "테스트 생성";
        if ("API_SPEC".equals(type))        return "API 명세";
        if ("CODE_REVIEW".equals(type))     return "코드 리뷰";
        if ("CODE_REVIEW_SEC".equals(type)) return "코드 보안";
        if ("MOCK_DATA".equals(type))       return "Mock 데이터";
        if ("COMPLEXITY".equals(type))      return "복잡도 분석";
        if ("MIGRATION".equals(type))       return "마이그레이션";
        if ("LOG_ANALYSIS".equals(type))    return "로그 분석";
        if ("REGEX_GEN".equals(type))       return "정규식 생성";
        if ("COMMIT_MSG".equals(type))      return "커밋 메시지";
        if ("JAVADOC".equals(type))         return "Javadoc 생성";
        if ("REFACTORING".equals(type))     return "리팩터링";
        if ("DEP_CHECK".equals(type))       return "의존성 분석";
        if ("DATA_MASKING".equals(type))    return "데이터 마스킹";
        if ("SPRING_MIGRATE".equals(type))  return "Spring 마이그레이션";
        if ("ERD_DDL".equals(type))         return "DDL 생성";
        if ("EXPLAIN_PLAN".equals(type))    return "실행계획";
        if ("SQL_BATCH".equals(type))       return "SQL 배치";
        if ("SQL_TRANSLATE".equals(type))   return "SQL 번역";
        if ("HARNESS_REVIEW".equals(type))  return "하네스 리뷰";
        return type != null ? type : "";
    }

    /** Accent colour hex for the type badge */
    public String getTypeBadgeColor() {
        if ("SQL_REVIEW".equals(type))      return "#3b82f6";
        if ("SQL_SECURITY".equals(type))    return "#ef4444";
        if ("DOC_GEN".equals(type))         return "#8b5cf6";
        if ("CODE_CONVERT".equals(type))    return "#f59e0b";
        if ("ERD".equals(type))             return "#10b981";
        if ("TEST_GEN".equals(type))        return "#ef4444";
        if ("API_SPEC".equals(type))        return "#06b6d4";
        if ("CODE_REVIEW".equals(type))     return "#3b82f6";
        if ("CODE_REVIEW_SEC".equals(type)) return "#ef4444";
        if ("MOCK_DATA".equals(type))       return "#f59e0b";
        if ("COMPLEXITY".equals(type))      return "#06b6d4";
        if ("MIGRATION".equals(type))       return "#ec4899";
        if ("LOG_ANALYSIS".equals(type))    return "#f43f5e";
        if ("REGEX_GEN".equals(type))       return "#a855f7";
        if ("COMMIT_MSG".equals(type))      return "#14b8a6";
        if ("JAVADOC".equals(type))         return "#8b5cf6";
        if ("REFACTORING".equals(type))     return "#06b6d4";
        if ("DEP_CHECK".equals(type))       return "#d97706";
        if ("DATA_MASKING".equals(type))    return "#dc2626";
        if ("SPRING_MIGRATE".equals(type))  return "#16a34a";
        if ("ERD_DDL".equals(type))         return "#14b8a6";
        if ("EXPLAIN_PLAN".equals(type))    return "#3b82f6";
        if ("SQL_BATCH".equals(type))       return "#f97316";
        if ("SQL_TRANSLATE".equals(type))   return "#f97316";
        if ("HARNESS_REVIEW".equals(type))  return "#8b5cf6";
        return "#64748b";
    }

    /** First 200 chars of output, stripped of Markdown symbols */
    public String getOutputPreview() {
        if (outputContent == null || outputContent.isEmpty()) return "";
        String preview = outputContent.length() > 200
                ? outputContent.substring(0, 200)
                : outputContent;
        return preview.replaceAll("[#*`>]", "").replaceAll("\\s+", " ").trim();
    }
}
