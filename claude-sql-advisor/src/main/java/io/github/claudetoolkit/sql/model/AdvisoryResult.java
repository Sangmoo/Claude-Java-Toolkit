package io.github.claudetoolkit.sql.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Result of a SQL advisory review.
 */
public class AdvisoryResult {

    private final String originalSql;
    private final SqlType sqlType;
    private final String reviewContent;
    private final String reviewedAt;

    private AdvisoryResult(String originalSql, SqlType sqlType, String reviewContent) {
        this.originalSql = originalSql;
        this.sqlType = sqlType;
        this.reviewContent = reviewContent;
        this.reviewedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static AdvisoryResult from(String sql, SqlType type, String review) {
        return new AdvisoryResult(sql, type, review);
    }

    /**
     * Format for console output.
     */
    public String toConsoleOutput() {
        return "═══════════════════════════════════════════════════════════\n" +
               "  Claude SQL Advisor — " + sqlType.getDisplayName() + "\n" +
               "  Reviewed at: " + reviewedAt + "\n" +
               "═══════════════════════════════════════════════════════════\n\n" +
               reviewContent + "\n";
    }

    /**
     * Format as Markdown for file output.
     */
    public String toMarkdown() {
        return "# SQL Advisory Report\n\n" +
               "- **Type**: " + sqlType.getDisplayName() + "\n" +
               "- **Reviewed at**: " + reviewedAt + "\n\n" +
               "## Original SQL\n\n```sql\n" + originalSql + "\n```\n\n" +
               "## Review\n\n" + reviewContent;
    }

    public String getOriginalSql() { return originalSql; }
    public SqlType getSqlType() { return sqlType; }
    public String getReviewContent() { return reviewContent; }
    public String getReviewedAt() { return reviewedAt; }
}
