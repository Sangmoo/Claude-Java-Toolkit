package io.github.claudetoolkit.docgen.codereview;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Reviews Java / Spring source code for quality, security, and best-practice violations.
 *
 * <p>Each issue is tagged [SEVERITY: HIGH/MEDIUM/LOW] so the UI can apply
 * the same severity-filter UX as the SQL Advisor.
 */
public class CodeReviewService {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are a senior Java/Spring architect with 15+ years of enterprise development experience.\n" +
            "Review the provided Java or Spring source code and return a structured report in the following format:\n\n" +
            "## Summary\n" +
            "Brief overall assessment (2-3 sentences).\n\n" +
            "## Issues Found\n" +
            "List each issue with: [SEVERITY: HIGH/MEDIUM/LOW] description\n" +
            "Categories to cover: Spring anti-patterns, N+1 query risk, missing @Transactional, " +
            "circular dependency, improper exception handling, resource leaks.\n\n" +
            "## Security Vulnerabilities\n" +
            "List any: [SEVERITY: HIGH/MEDIUM/LOW] description\n" +
            "Check for: hardcoded credentials, SQL injection via string concatenation, " +
            "unvalidated user input, insecure deserialization, exposed sensitive data in logs.\n\n" +
            "## Refactoring Suggestions\n" +
            "Concrete improvements with before/after code examples.\n\n" +
            "## Code Quality Metrics\n" +
            "Comment on: method length, class cohesion, naming conventions, code duplication.\n\n" +
            "Be specific. Include line references when possible. " +
            "Use Korean for explanations if the code contains Korean comments or identifiers.";

    public static final String DEFAULT_SYSTEM_PROMPT_SECURITY =
            "You are a Java application security expert (OWASP, SANS Top 25).\n" +
            "Perform a SECURITY-ONLY review of the provided Java/Spring code.\n\n" +
            "## Security Audit Report\n\n" +
            "### Critical Vulnerabilities\n" +
            "[SEVERITY: HIGH] items only\n\n" +
            "### Medium Risk Issues\n" +
            "[SEVERITY: MEDIUM] items\n\n" +
            "### Low Risk / Hardening\n" +
            "[SEVERITY: LOW] items\n\n" +
            "### Recommendations\n" +
            "Prioritized fix list.\n\n" +
            "Check for: SQL injection, XSS, CSRF, insecure direct object reference, " +
            "hardcoded secrets, weak cryptography, path traversal, XXE, insecure deserialization, " +
            "missing input validation, improper error handling revealing stack traces.\n" +
            "Use Korean if the code contains Korean comments.";

    private final ClaudeClient claudeClient;

    public CodeReviewService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Full code review (quality + security + refactoring).
     */
    public String review(String sourceCode, String sourceType) {
        return reviewWithContext(sourceCode, sourceType, "", null);
    }

    public String reviewSecurity(String sourceCode, String sourceType) {
        return reviewSecurity(sourceCode, sourceType, null);
    }

    public String reviewSecurity(String sourceCode, String sourceType, String customPrompt) {
        String effectivePrompt = (customPrompt != null && !customPrompt.trim().isEmpty()) ? customPrompt : DEFAULT_SYSTEM_PROMPT_SECURITY;
        String prompt = "Perform a security audit on the following " + sourceType + ":\n\n```java\n" + sourceCode + "\n```";
        return claudeClient.chat(effectivePrompt, prompt);
    }

    public String reviewWithContext(String sourceCode, String sourceType, String projectContext) {
        return reviewWithContext(sourceCode, sourceType, projectContext, null);
    }

    public String reviewWithContext(String sourceCode, String sourceType, String projectContext, String customPrompt) {
        String effectivePrompt = (customPrompt != null && !customPrompt.trim().isEmpty()) ? customPrompt : DEFAULT_SYSTEM_PROMPT;
        StringBuilder sb = new StringBuilder();
        if (projectContext != null && !projectContext.trim().isEmpty()) {
            sb.append(projectContext).append("\n\n---\n\n");
            sb.append("Using the project context above, review the following ").append(sourceType).append(":\n\n");
        } else {
            sb.append("Review the following ").append(sourceType).append(":\n\n");
        }
        sb.append("```java\n").append(sourceCode).append("\n```");
        return claudeClient.chat(effectivePrompt, sb.toString());
    }
}
