package io.github.claudetoolkit.docgen.loganalyzer;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Analyzes Spring Boot / Java error logs and stack traces using Claude.
 *
 * <p>Provides two analysis modes:
 * <ul>
 *   <li>{@link #analyze(String)} – general log analysis</li>
 *   <li>{@link #analyzeSecurity(String)} – security-focused log analysis
 *       (SQL injection, XSS, authentication errors)</li>
 * </ul>
 */
public class LogAnalyzerService {

    private static final String SYSTEM_PROMPT =
            "당신은 Spring Boot / Java 에러 로그 및 스택트레이스 분석 전문가입니다.\n" +
            "제공된 로그를 분석하여 다음 형식으로 출력하세요:\n\n" +
            "## 오류 분석\n" +
            "- 오류 종류: 발생한 예외/오류의 유형\n" +
            "- 발생 위치: 스택트레이스 상의 정확한 클래스 및 라인\n\n" +
            "## 원인 파악\n" +
            "구체적인 원인을 단계별로 설명하세요. 관련 코드 흐름과 함께 원인을 분석하세요.\n\n" +
            "## 해결 방법\n" +
            "해결 방법을 구체적으로 제시하고, 가능하면 코드 예시를 포함하세요.\n\n" +
            "## 예방 방법\n" +
            "유사한 오류가 재발하지 않도록 예방하는 방법을 설명하세요.";

    private static final String SYSTEM_PROMPT_SECURITY =
            "당신은 Spring Boot / Java 보안 로그 분석 전문가입니다.\n" +
            "SQL 인젝션, XSS(Cross-Site Scripting), 인증 오류 등 보안 위협을 탐지하고 분석합니다.\n" +
            "제공된 로그를 분석하여 다음 형식으로 출력하세요:\n\n" +
            "## 오류 분석\n" +
            "- 오류 종류: 발생한 보안 이슈/예외의 유형\n" +
            "- 발생 위치: 스택트레이스 상의 정확한 클래스 및 라인\n" +
            "- 보안 위협 분류: SQL 인젝션 / XSS / 인증 오류 / 인가 오류 / 기타\n\n" +
            "## 원인 파악\n" +
            "보안 취약점의 구체적인 원인을 분석하세요. 공격 패턴이 감지된 경우 해당 패턴을 설명하세요.\n\n" +
            "## 해결 방법\n" +
            "보안 이슈 해결 방법을 코드 예시와 함께 제시하세요.\n" +
            "OWASP 가이드라인을 참고하여 권장 보안 설정을 포함하세요.\n\n" +
            "## 예방 방법\n" +
            "유사한 보안 취약점이 재발하지 않도록 예방하는 방법을 설명하세요.";

    private final ClaudeClient claudeClient;

    public LogAnalyzerService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Analyzes a general Spring Boot / Java log or stack trace.
     *
     * @param logContent the raw log text to analyze
     * @return structured analysis report in Markdown
     */
    public String analyze(String logContent) {
        return analyze(logContent, "");
    }

    public String analyze(String logContent, String projectContext) {
        String system = projectContext != null && !projectContext.trim().isEmpty()
                ? SYSTEM_PROMPT + "\n\n[프로젝트 컨텍스트]\n" + projectContext
                : SYSTEM_PROMPT;
        String prompt = "다음 로그를 분석해주세요:\n\n```\n" + logContent + "\n```";
        return claudeClient.chat(system, prompt);
    }

    /**
     * Analyzes a log with a focus on security threats such as
     * SQL injection, XSS, and authentication failures.
     *
     * @param logContent the raw log text to analyze
     * @return structured security-focused analysis report in Markdown
     */
    public String analyzeSecurity(String logContent) {
        return analyzeSecurity(logContent, "");
    }

    public String analyzeSecurity(String logContent, String projectContext) {
        String system = projectContext != null && !projectContext.trim().isEmpty()
                ? SYSTEM_PROMPT_SECURITY + "\n\n[프로젝트 컨텍스트]\n" + projectContext
                : SYSTEM_PROMPT_SECURITY;
        String prompt = "다음 로그에서 보안 위협(SQL 인젝션, XSS, 인증 오류 등)을 탐지하고 분석해주세요:\n\n```\n" + logContent + "\n```";
        return claudeClient.chat(system, prompt);
    }
}
