package io.github.claudetoolkit.sql.advisor;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.sql.model.AdvisoryResult;
import io.github.claudetoolkit.sql.model.SqlType;

/**
 * Analyzes SQL statements and Oracle Stored Procedures using Claude API.
 *
 * <p>Produces advisory reports covering:
 * <ul>
 *   <li>Performance issues (missing indexes, full table scans, N+1 patterns)</li>
 *   <li>Oracle-specific anti-patterns (implicit type conversions, scalar subqueries)</li>
 *   <li>Refactoring suggestions (JOIN vs subquery, WITH clause usage)</li>
 *   <li>Stored procedure logic bugs (loop variable reset, cursor handling)</li>
 * </ul>
 */
public class SqlAdvisorService {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are an expert Oracle DBA and SQL performance engineer with 15+ years of experience.\n" +
            "Analyze the provided SQL or PL/SQL code and return a structured review in the following format:\n\n" +
            "## Summary\n" +
            "Brief overall assessment.\n\n" +
            "## Issues Found\n" +
            "List each issue with: [SEVERITY: HIGH/MEDIUM/LOW] description\n\n" +
            "## Refactoring Suggestions\n" +
            "Concrete code improvements with before/after examples.\n\n" +
            "## Oracle-Specific Notes\n" +
            "Oracle 11g/12c compatibility concerns if any.\n\n" +
            "Be specific. Include line references when possible. Use Korean for explanations if the code has Korean comments.";

    private final ClaudeClient claudeClient;

    public SqlAdvisorService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Review a SQL statement or PL/SQL stored procedure.
     */
    public AdvisoryResult review(String sqlContent, SqlType sqlType) {
        return reviewWithContext(sqlContent, sqlType, "");
    }

    /**
     * Convenience method — auto-detects SQL type from content.
     */
    public AdvisoryResult review(String sqlContent) {
        SqlType detected = SqlType.detect(sqlContent);
        return reviewWithContext(sqlContent, detected, "");
    }

    /**
     * Review with additional DB table metadata context (columns, PKs, indexes).
     * The {@code dbContext} string is prepended to the user prompt so Claude
     * can reason about the actual schema.
     *
     * @param sqlContent  the SQL or PL/SQL source code
     * @param sqlType     SQL type (auto-detected if null)
     * @param dbContext   formatted Markdown table metadata, or empty string
     */
    public AdvisoryResult reviewWithContext(String sqlContent, SqlType sqlType, String dbContext) {
        return reviewWithContext(sqlContent, sqlType, dbContext, null);
    }

    public AdvisoryResult reviewWithContext(String sqlContent, SqlType sqlType, String dbContext, String customPrompt) {
        if (sqlType == null) sqlType = SqlType.detect(sqlContent);
        String effectivePrompt = (customPrompt != null && !customPrompt.trim().isEmpty()) ? customPrompt : DEFAULT_SYSTEM_PROMPT;
        String prompt = buildPrompt(sqlContent, sqlType, dbContext);
        String response = claudeClient.chat(effectivePrompt, prompt);
        return AdvisoryResult.from(sqlContent, sqlType, response);
    }

    public static final String DEFAULT_SYSTEM_PROMPT_SECURITY =
            "You are an Oracle database security expert specializing in SQL injection prevention and secure coding.\n" +
            "Perform a SECURITY-ONLY audit of the provided SQL or PL/SQL code.\n\n" +
            "## SQL Security Audit Report\n\n" +
            "### Critical Vulnerabilities\n" +
            "List each: [SEVERITY: HIGH] description — impact — fix\n\n" +
            "### Medium Risk Issues\n" +
            "List each: [SEVERITY: MEDIUM] description — impact — fix\n\n" +
            "### Low Risk / Hardening\n" +
            "List each: [SEVERITY: LOW] description — recommendation\n\n" +
            "### Secure Coding Recommendations\n" +
            "Prioritized list of security improvements.\n\n" +
            "Check for:\n" +
            "- Dynamic SQL with string concatenation (SQL injection risk)\n" +
            "- Missing DBMS_ASSERT usage for object names in dynamic SQL\n" +
            "- Excessive EXECUTE IMMEDIATE without bind variables\n" +
            "- Over-privileged AUTHID CURRENT_USER vs DEFINER\n" +
            "- Sensitive data (passwords, PII) stored in plain text columns\n" +
            "- Hardcoded credentials in PL/SQL source\n" +
            "- Unhandled exceptions revealing internal schema info\n" +
            "- PUBLIC grants or wide privilege grants\n" +
            "- UTL_FILE / UTL_HTTP / JAVA usage without proper controls\n\n" +
            "Use Korean explanations if the code contains Korean comments.";

    /**
     * Security-focused audit of SQL/PL/SQL code.
     */
    public AdvisoryResult reviewSecurity(String sqlContent) {
        return reviewSecurity(sqlContent, null);
    }

    public AdvisoryResult reviewSecurity(String sqlContent, String customPrompt) {
        SqlType sqlType = SqlType.detect(sqlContent);
        String effectivePrompt = (customPrompt != null && !customPrompt.trim().isEmpty()) ? customPrompt : DEFAULT_SYSTEM_PROMPT_SECURITY;
        String prompt   = "Perform a security audit on the following " + sqlType.getDisplayName() + ":\n\n```sql\n" + sqlContent + "\n```";
        String response = claudeClient.chat(effectivePrompt, prompt);
        return AdvisoryResult.from(sqlContent, sqlType, response);
    }

    private static final String SYSTEM_PROMPT_INDEX =
            "당신은 Oracle DBA 전문가입니다. 주어진 SQL 쿼리를 분석하여 성능 최적화를 위한 인덱스 제안을 합니다. " +
            "## 현재 쿼리 분석, ## 추천 인덱스 (CREATE INDEX 구문 포함), ## 예상 성능 향상, ## 주의사항 " +
            "형식으로 출력하세요. 각 인덱스 제안은 [PRIORITY: HIGH/MEDIUM/LOW]로 표시하세요.";

    /**
     * Suggest indexes for optimizing the given SQL query.
     *
     * @param sqlContent the SQL query to analyze
     * @return Markdown-formatted index optimization suggestions
     */
    public String suggestIndexes(String sqlContent) {
        String userMessage = "다음 SQL 쿼리에 대한 인덱스 최적화 방안을 제안해주세요:\n\n```sql\n" + sqlContent + "\n```";
        return claudeClient.chat(SYSTEM_PROMPT_INDEX, userMessage);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String buildPrompt(String sqlContent, SqlType sqlType, String dbContext) {
        StringBuilder sb = new StringBuilder();
        if (dbContext != null && !dbContext.trim().isEmpty()) {
            sb.append(dbContext).append("\n\n");
            sb.append("---\n\n");
            sb.append("Use the table metadata above while reviewing the following ")
              .append(sqlType.getDisplayName()).append(":\n\n");
        } else {
            sb.append("Please review the following ").append(sqlType.getDisplayName()).append(":\n\n");
        }
        sb.append("```sql\n").append(sqlContent).append("\n```");
        return sb.toString();
    }
}
