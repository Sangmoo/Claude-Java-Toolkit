package io.github.claudetoolkit.sql.mockdata;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Generates INSERT-ready mock data from a table DDL or schema description.
 *
 * <p>Output is plain SQL INSERT statements ready to copy-paste or run.
 */
public class MockDataGeneratorService {

    private static final String SYSTEM_PROMPT =
            "You are an expert Oracle DBA who creates realistic test data for development and QA teams.\n" +
            "Given a table DDL or schema description, generate SQL INSERT statements with realistic, " +
            "varied sample data.\n\n" +
            "Rules:\n" +
            "- Use Oracle-compatible INSERT INTO ... VALUES (...) syntax\n" +
            "- Match data types exactly (VARCHAR2, NUMBER, DATE, TIMESTAMP, CLOB, etc.)\n" +
            "- Use Oracle date literals: TO_DATE('2024-01-15','YYYY-MM-DD') or SYSDATE\n" +
            "- Generate diverse, realistic values (no 'test1', 'test2' placeholders)\n" +
            "- Respect NOT NULL constraints, check constraints, and obvious FK patterns\n" +
            "- For CHAR/VARCHAR2: use context-appropriate Korean or English strings\n" +
            "- For sequences: use SEQUENCE_NAME.NEXTVAL when a sequence is obvious\n" +
            "- End each statement with a semicolon\n" +
            "- Add a COMMIT; at the end\n" +
            "- Output ONLY the SQL statements, no explanations\n\n" +
            "If the input contains Korean column names or comments, use Korean sample values where appropriate.";

    private final ClaudeClient claudeClient;

    public MockDataGeneratorService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Generate INSERT statements from a DDL string.
     *
     * @param ddl      CREATE TABLE ... DDL or schema description
     * @param rowCount number of rows to generate
     * @param format   "insert" (plain SQL), "merge" (MERGE INTO), "csv" (CSV header + rows)
     */
    public String generate(String ddl, int rowCount, String format) {
        String prompt = buildPrompt(ddl, rowCount, format);
        return claudeClient.chat(SYSTEM_PROMPT, prompt);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private String buildPrompt(String ddl, int rowCount, String format) {
        StringBuilder sb = new StringBuilder();

        if ("merge".equalsIgnoreCase(format)) {
            sb.append("Generate ").append(rowCount)
              .append(" rows as Oracle MERGE INTO statements (upsert pattern) for:\n\n");
        } else if ("csv".equalsIgnoreCase(format)) {
            sb.append("Generate ").append(rowCount)
              .append(" rows as CSV (first line = header with column names) for:\n\n");
        } else {
            sb.append("Generate ").append(rowCount)
              .append(" rows as Oracle INSERT INTO statements for:\n\n");
        }

        sb.append("```sql\n").append(ddl).append("\n```");
        return sb.toString();
    }
}
