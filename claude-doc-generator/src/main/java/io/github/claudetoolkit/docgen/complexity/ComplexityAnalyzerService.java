package io.github.claudetoolkit.docgen.complexity;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Analyzes Java source code complexity and produces a refactoring priority report.
 *
 * <p>Can analyze a single file (pasted text) or receive a pre-built project
 * summary from {@code ProjectScannerService}.
 */
public class ComplexityAnalyzerService {

    private static final String SYSTEM_PROMPT =
            "You are a senior software architect specializing in Java code quality and refactoring.\n" +
            "Analyze the provided Java source code and produce a structured Complexity Analysis Report.\n\n" +
            "## Complexity Analysis Report\n\n" +
            "### Overall Assessment\n" +
            "Brief summary (2-3 sentences): overall quality, main concerns.\n\n" +
            "### Method-Level Complexity\n" +
            "Table format:\n" +
            "| Method | Estimated Cyclomatic Complexity | Lines | Issue |\n" +
            "List the top 10 most complex methods. Estimate complexity from: branch count (if/else/switch/loop/try-catch/ternary).\n\n" +
            "### Class-Level Metrics\n" +
            "| Class | LOC | Methods | Concerns |\n" +
            "Flag: God classes (>500 LOC), low cohesion, too many responsibilities.\n\n" +
            "### Refactoring Priority\n" +
            "Numbered list (highest priority first):\n" +
            "1. [PRIORITY: HIGH] ClassName.methodName — reason — suggested fix\n" +
            "2. [PRIORITY: MEDIUM] ...\n" +
            "Use [PRIORITY: HIGH/MEDIUM/LOW] tags consistently.\n\n" +
            "### Code Smells Detected\n" +
            "List patterns found: Long Method, Large Class, Feature Envy, Data Clumps, " +
            "Primitive Obsession, Divergent Change, Shotgun Surgery, etc.\n\n" +
            "### Quick Wins\n" +
            "Small, low-risk improvements that can be done in < 1 hour each.\n\n" +
            "Use Korean for explanations if the code contains Korean comments or identifiers.";

    private final ClaudeClient claudeClient;

    public ComplexityAnalyzerService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Analyze a single Java source file.
     */
    public String analyze(String sourceCode) {
        String prompt = "Analyze the complexity of the following Java source code:\n\n```java\n" + sourceCode + "\n```";
        return claudeClient.chat(SYSTEM_PROMPT, prompt);
    }

    /**
     * Analyze using a pre-built project summary string from ProjectScannerService.
     *
     * @param projectContext formatted project context (file list + content)
     */
    public String analyzeProject(String projectContext) {
        String prompt = "Analyze the complexity of the following Java project.\n" +
                "Focus on the classes and methods with the highest complexity.\n\n" +
                projectContext;
        return claudeClient.chat(SYSTEM_PROMPT, prompt);
    }
}
