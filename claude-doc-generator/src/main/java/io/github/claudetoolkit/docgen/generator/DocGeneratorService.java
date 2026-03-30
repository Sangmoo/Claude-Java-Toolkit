package io.github.claudetoolkit.docgen.generator;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Generates technical documentation from Oracle SP or Java source code.
 *
 * Supported output formats:
 * - Markdown (.md)
 * - Typst (.typ) — for professional PDF rendering
 *
 * Both formats support an optional project context string
 * (scanned Spring MVC files) to enrich the generated documentation.
 */
public class DocGeneratorService {

    private static final String SYSTEM_PROMPT_MARKDOWN =
            "You are a senior technical writer specializing in enterprise Java and Oracle database systems.\n" +
            "Generate comprehensive technical documentation in Markdown format.\n" +
            "Include: Overview, Parameters/Arguments, Return values, Business logic description,\n" +
            "Error handling, Dependencies, Usage examples, and Known limitations.\n" +
            "If a project context is provided, reference related classes/tables where relevant.\n" +
            "Write in Korean if the source code contains Korean identifiers or comments. Otherwise use English.";

    private static final String SYSTEM_PROMPT_ORACLE_PACKAGE =
            "당신은 Oracle PL/SQL 전문가입니다. Oracle Package (SPEC + BODY)를 분석하여 Markdown 기술 문서를 작성하세요. 각 함수/프로시저의 파라미터, 반환값, 비즈니스 로직을 설명하세요.";

    private static final String SYSTEM_PROMPT_TYPST =
            "You are a senior technical writer specializing in enterprise Java and Oracle database systems.\n" +
            "Generate comprehensive technical documentation in Typst format.\n" +
            "Use proper Typst syntax: #heading(), #table(), #code(), etc.\n" +
            "Include all sections: Overview, Parameters, Return values, Business logic, Error handling, Examples.\n" +
            "If a project context is provided, reference related classes/tables where relevant.\n" +
            "Write in Korean if the source code contains Korean identifiers or comments. Otherwise use English.";

    private final ClaudeClient claudeClient;

    public DocGeneratorService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Generate Markdown documentation from source code.
     */
    public String generateMarkdown(String sourceCode, String sourceType) {
        return generateMarkdownWithContext(sourceCode, sourceType, "");
    }

    /**
     * Generate Typst documentation from source code.
     */
    public String generateTypst(String sourceCode, String sourceType) {
        return generateTypstWithContext(sourceCode, sourceType, "");
    }

    /**
     * Generate Markdown documentation enriched with a scanned project context.
     *
     * @param projectContext formatted Markdown string from ProjectScannerService,
     *                       or empty string to skip context
     */
    public String generateMarkdownWithContext(String sourceCode, String sourceType, String projectContext) {
        String prompt = buildPrompt(sourceCode, sourceType, projectContext, "md");
        String systemPrompt = "Oracle Package".equals(sourceType)
                ? SYSTEM_PROMPT_ORACLE_PACKAGE
                : SYSTEM_PROMPT_MARKDOWN;
        return claudeClient.chat(systemPrompt, prompt);
    }

    /**
     * Generate Typst documentation enriched with a scanned project context.
     */
    public String generateTypstWithContext(String sourceCode, String sourceType, String projectContext) {
        String prompt = buildPrompt(sourceCode, sourceType, projectContext, "typst");
        return claudeClient.chat(SYSTEM_PROMPT_TYPST, prompt);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String buildPrompt(String sourceCode, String sourceType, String projectContext, String format) {
        StringBuilder sb = new StringBuilder();

        if (projectContext != null && !projectContext.trim().isEmpty()) {
            sb.append(projectContext).append("\n\n---\n\n");
            sb.append("Using the project context above, generate ");
        } else {
            sb.append("Generate ");
        }

        if ("typst".equals(format)) {
            sb.append("Typst-formatted technical documentation for the following ")
              .append(sourceType).append(":\n\n");
        } else {
            sb.append("technical documentation for the following ")
              .append(sourceType).append(":\n\n");
        }

        sb.append("```\n").append(sourceCode).append("\n```");
        return sb.toString();
    }
}
