package io.github.claudetoolkit.docgen.commitmsg;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Generates Git commit messages in Conventional Commits format using Claude.
 *
 * <p>Supports three commit styles:
 * <ul>
 *   <li>{@code conventional} – type(scope): subject (default)</li>
 *   <li>{@code angular} – Angular commit style</li>
 *   <li>{@code simple} – short Korean single-line message</li>
 * </ul>
 */
public class CommitMsgService {

    private static final String SYSTEM_PROMPT =
            "당신은 Git 커밋 메시지 작성 전문가입니다.\n" +
            "Conventional Commits 형식에 따라 명확하고 일관성 있는 커밋 메시지를 생성합니다.\n" +
            "사용 가능한 타입: feat / fix / docs / style / refactor / test / chore / perf\n\n" +
            "다음 형식으로 출력하세요:\n\n" +
            "## 추천 커밋 메시지\n" +
            "```\n" +
            "(커밋 메시지만 작성)\n" +
            "```\n\n" +
            "## 대안 메시지\n" +
            "1. (대안 1)\n" +
            "2. (대안 2)\n" +
            "3. (대안 3)\n\n" +
            "## 변경 사항 요약\n" +
            "핵심 변경 내용을 3줄 이내로 요약하세요.";

    private final ClaudeClient claudeClient;

    public CommitMsgService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Generates a commit message from a git diff or code snippet.
     *
     * @param diffOrCode  the git diff output or code change to analyze
     * @param commitStyle commit message style: "conventional" (default), "angular", or "simple"
     * @return structured commit message suggestions in Markdown
     */
    public String generate(String diffOrCode, String commitStyle) {
        return generate(diffOrCode, commitStyle, "");
    }

    /**
     * Generates a commit message from a git diff or code snippet with optional project context.
     *
     * @param diffOrCode     the git diff output or code change to analyze
     * @param commitStyle    commit message style: "conventional" (default), "angular", or "simple"
     * @param projectContext optional project context memo; empty string means no context
     * @return structured commit message suggestions in Markdown
     */
    public String generate(String diffOrCode, String commitStyle, String projectContext) {
        String styleInstruction;

        if ("angular".equalsIgnoreCase(commitStyle)) {
            styleInstruction =
                    "Angular 커밋 스타일을 사용하세요: <type>(<scope>): <short summary>\n" +
                    "Body에는 변경 동기와 이전 동작과의 차이를 설명하세요.\n" +
                    "Footer에는 Breaking Changes 또는 이슈 참조를 포함하세요.";
        } else if ("simple".equalsIgnoreCase(commitStyle)) {
            styleInstruction =
                    "간결한 한국어 단문 형식을 사용하세요. 동사로 시작하는 짧은 메시지를 작성하세요.\n" +
                    "예: '로그인 버그 수정', '사용자 인증 기능 추가', '코드 리팩토링'";
        } else {
            // conventional (default)
            styleInstruction =
                    "Conventional Commits 형식을 사용하세요: type(scope): subject\n" +
                    "타입: feat / fix / docs / style / refactor / test / chore / perf\n" +
                    "subject는 현재형 동사로 시작하고 50자 이내로 작성하세요.";
        }

        String systemWithStyle =
                "당신은 Git 커밋 메시지 작성 전문가입니다.\n" +
                styleInstruction + "\n\n" +
                "사용 가능한 타입: feat / fix / docs / style / refactor / test / chore / perf\n\n" +
                "다음 형식으로 출력하세요:\n\n" +
                "## 추천 커밋 메시지\n" +
                "```\n" +
                "(커밋 메시지만 작성)\n" +
                "```\n\n" +
                "## 대안 메시지\n" +
                "1. (대안 1)\n" +
                "2. (대안 2)\n" +
                "3. (대안 3)\n\n" +
                "## 변경 사항 요약\n" +
                "핵심 변경 내용을 3줄 이내로 요약하세요.";

        if (projectContext != null && !projectContext.trim().isEmpty()) {
            systemWithStyle = systemWithStyle + "\n\n[프로젝트 컨텍스트]\n" + projectContext;
        }

        String prompt = "다음 코드 변경사항(diff)을 분석하여 커밋 메시지를 생성해주세요:\n\n```diff\n" + diffOrCode + "\n```";
        return claudeClient.chat(systemWithStyle, prompt);
    }

    /**
     * Generates a commit message from a natural-language description of the changes.
     *
     * @param description natural-language description of what was changed and why
     * @return structured commit message suggestions in Markdown
     */
    public String generateFromDescription(String description) {
        return generateFromDescription(description, "");
    }

    /**
     * Generates a commit message from a natural-language description with optional project context.
     *
     * @param description    natural-language description of what was changed and why
     * @param projectContext optional project context memo; empty string means no context
     * @return structured commit message suggestions in Markdown
     */
    public String generateFromDescription(String description, String projectContext) {
        String systemPrompt = SYSTEM_PROMPT;
        if (projectContext != null && !projectContext.trim().isEmpty()) {
            systemPrompt = systemPrompt + "\n\n[프로젝트 컨텍스트]\n" + projectContext;
        }
        String prompt = "다음 변경 사항 설명을 바탕으로 커밋 메시지를 생성해주세요:\n\n" + description;
        return claudeClient.chat(systemPrompt, prompt);
    }
}
