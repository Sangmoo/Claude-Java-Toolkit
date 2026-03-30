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

    @Lob
    @Column(nullable = false)
    private String inputContent;

    @Lob
    @Column(nullable = false)
    private String outputContent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Required by JPA — do not use directly */
    protected ReviewHistory() {}

    public ReviewHistory(String type, String title, String inputContent, String outputContent) {
        this.type          = type;
        this.title         = title;
        this.inputContent  = inputContent;
        this.outputContent = outputContent;
        this.createdAt     = LocalDateTime.now();
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public long getId()              { return id; }
    public String getType()          { return type; }
    public String getTitle()         { return title; }
    public String getInputContent()  { return inputContent; }
    public String getOutputContent() { return outputContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** MM-dd HH:mm format */
    public String getFormattedDate() {
        return createdAt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    /** Korean label for the history type */
    public String getTypeLabel() {
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
        return type;
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
