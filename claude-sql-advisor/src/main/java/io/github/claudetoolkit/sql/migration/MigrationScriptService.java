package io.github.claudetoolkit.sql.migration;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Generates Oracle DB migration scripts by comparing Before / After DDL.
 *
 * <p>Supports plain Oracle DDL (ALTER TABLE), Flyway V-scripts, and Liquibase changesets.
 */
public class MigrationScriptService {

    private static final String SYSTEM_PROMPT =
            "You are an expert Oracle DBA specializing in database schema migrations.\n" +
            "Given two DDL definitions (BEFORE and AFTER), generate the migration script needed " +
            "to transform the BEFORE schema into the AFTER schema.\n\n" +
            "Rules:\n" +
            "- Use Oracle-compatible syntax (ALTER TABLE, CREATE INDEX, DROP COLUMN if safe, etc.)\n" +
            "- Always check for potential data loss (e.g., dropping NOT NULL, reducing column size)\n" +
            "- Add rollback section at the end showing how to revert the changes\n" +
            "- Add comments explaining each statement\n" +
            "- Add a warning if a change could cause data loss or downtime\n" +
            "- Prefix each risk item with [RISK: HIGH/MEDIUM/LOW]\n\n" +
            "If Korean is used in table/column names or comments, use Korean in your explanations.";

    private static final String SYSTEM_PROMPT_FLYWAY =
            "You are an expert Oracle DBA specializing in database schema migrations using Flyway.\n" +
            "Given two DDL definitions (BEFORE and AFTER), generate a Flyway migration script.\n\n" +
            "Format:\n" +
            "- Start with a comment block: -- V{version}__{description}.sql\n" +
            "- Include the forward migration SQL statements\n" +
            "- End with an undo section prefixed: -- Undo (for Flyway Pro or manual rollback)\n" +
            "- Use Oracle-compatible syntax only\n" +
            "- Flag risks with [RISK: HIGH/MEDIUM/LOW]\n" +
            "If Korean is used, use Korean in explanations.";

    private static final String SYSTEM_PROMPT_LIQUIBASE =
            "You are an expert Oracle DBA specializing in database schema migrations using Liquibase.\n" +
            "Given two DDL definitions (BEFORE and AFTER), generate a Liquibase XML changeset.\n\n" +
            "Format:\n" +
            "- Use proper Liquibase XML: <databaseChangeLog>, <changeSet id=... author=...>\n" +
            "- Include <rollback> sections\n" +
            "- Use Liquibase-native change types where possible (addColumn, dropColumn, modifyDataType, etc.)\n" +
            "- For complex Oracle-specific changes use <sql> tags\n" +
            "- Flag risks with XML comments: <!-- [RISK: HIGH] ... -->\n" +
            "If Korean is used, use Korean in comments.";

    private final ClaudeClient claudeClient;

    public MigrationScriptService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Generate a migration script.
     *
     * @param beforeDdl DDL of the current (before) schema
     * @param afterDdl  DDL of the target (after) schema
     * @param format    "oracle" | "flyway" | "liquibase"
     */
    public String generate(String beforeDdl, String afterDdl, String format) {
        String system = selectSystemPrompt(format);
        String prompt = buildPrompt(beforeDdl, afterDdl, format);
        return claudeClient.chat(system, prompt);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private String selectSystemPrompt(String format) {
        if ("flyway".equalsIgnoreCase(format))     return SYSTEM_PROMPT_FLYWAY;
        if ("liquibase".equalsIgnoreCase(format))  return SYSTEM_PROMPT_LIQUIBASE;
        return SYSTEM_PROMPT;
    }

    private String buildPrompt(String beforeDdl, String afterDdl, String format) {
        return "## BEFORE DDL (current schema)\n\n```sql\n" + beforeDdl + "\n```\n\n" +
               "## AFTER DDL (target schema)\n\n```sql\n" + afterDdl + "\n```\n\n" +
               "Generate the " + format.toUpperCase() + " migration script to migrate from BEFORE to AFTER.";
    }
}
