package io.github.claudetoolkit.docgen.regex;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Generates regular expressions from natural-language descriptions using Claude.
 *
 * <p>Supports Java, JavaScript, and Python language-specific code examples.
 */
public class RegexGeneratorService {

    private static final String SYSTEM_PROMPT =
            "당신은 정규식(Regular Expression) 전문가입니다.\n" +
            "자연어 설명을 받아 정확하고 효율적인 정규식을 생성하고 설명합니다.\n" +
            "다음 형식으로 출력하세요:\n\n" +
            "## 정규식\n" +
            "```\n" +
            "(생성된 정규식 패턴만 작성)\n" +
            "```\n\n" +
            "## 설명\n" +
            "정규식의 각 부분을 상세히 설명하세요. 각 토큰/그룹의 의미를 명확히 기술하세요.\n\n" +
            "## Java 사용 예제\n" +
            "Pattern과 Matcher를 이용한 Java 코드 예제를 작성하세요.\n\n" +
            "## 테스트 케이스\n" +
            "일치하는 예제 5개와 일치하지 않는 예제 5개를 표 형식으로 제시하세요.";

    private final ClaudeClient claudeClient;

    public RegexGeneratorService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Generates a regular expression from a natural-language description,
     * with a code usage example in the specified language.
     *
     * @param description natural-language description of what the regex should match
     * @param language    target language for the code example: "java", "javascript", or "python"
     * @return structured regex result report in Markdown
     */
    public String generate(String description, String language) {
        return generate(description, language, "");
    }

    /**
     * Generates a regular expression from a natural-language description with optional project context.
     *
     * @param description    natural-language description of what the regex should match
     * @param language       target language for the code example: "java", "javascript", or "python"
     * @param projectContext optional project context memo; empty string means no context
     * @return structured regex result report in Markdown
     */
    public String generate(String description, String language, String projectContext) {
        String langLabel;
        String codeExampleInstruction;

        if ("javascript".equalsIgnoreCase(language)) {
            langLabel = "JavaScript";
            codeExampleInstruction =
                    "## JavaScript 사용 예제\n" +
                    "RegExp 객체와 test()/match() 메서드를 이용한 JavaScript 코드 예제를 작성하세요.";
        } else if ("python".equalsIgnoreCase(language)) {
            langLabel = "Python";
            codeExampleInstruction =
                    "## Python 사용 예제\n" +
                    "re 모듈의 compile()/match()/search()/findall() 메서드를 이용한 Python 코드 예제를 작성하세요.";
        } else if ("oracle".equalsIgnoreCase(language)) {
            langLabel = "Oracle SQL";
            codeExampleInstruction =
                    "## Oracle SQL 사용 예제\n" +
                    "REGEXP_LIKE, REGEXP_SUBSTR, REGEXP_REPLACE, REGEXP_COUNT 함수를 이용한 Oracle SQL 코드 예제를 작성하세요.";
        } else if ("kotlin".equalsIgnoreCase(language)) {
            langLabel = "Kotlin";
            codeExampleInstruction =
                    "## Kotlin 사용 예제\n" +
                    "Kotlin의 Regex 클래스와 matches()/find()/findAll()/replace() 메서드를 이용한 코드 예제를 작성하세요.";
        } else {
            langLabel = "Java";
            codeExampleInstruction =
                    "## Java 사용 예제\n" +
                    "Pattern과 Matcher를 이용한 Java 코드 예제를 작성하세요.";
        }

        String systemWithLang =
                "당신은 정규식(Regular Expression) 전문가입니다.\n" +
                "자연어 설명을 받아 정확하고 효율적인 정규식을 생성하고 설명합니다.\n" +
                "다음 형식으로 출력하세요:\n\n" +
                "## 정규식\n" +
                "```\n" +
                "(생성된 정규식 패턴만 작성)\n" +
                "```\n\n" +
                "## 설명\n" +
                "정규식의 각 부분을 상세히 설명하세요. 각 토큰/그룹의 의미를 명확히 기술하세요.\n\n" +
                codeExampleInstruction + "\n\n" +
                "## 테스트 케이스\n" +
                "일치하는 예제 5개와 일치하지 않는 예제 5개를 표 형식으로 제시하세요.";

        if (projectContext != null && !projectContext.trim().isEmpty()) {
            systemWithLang = systemWithLang + "\n\n[프로젝트 컨텍스트]\n" + projectContext;
        }

        String prompt = "다음 설명에 맞는 정규식을 " + langLabel + " 언어 기준으로 생성해주세요:\n\n" + description;
        return claudeClient.chat(systemWithLang, prompt);
    }
}
