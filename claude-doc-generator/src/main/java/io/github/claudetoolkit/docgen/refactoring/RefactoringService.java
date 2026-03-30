package io.github.claudetoolkit.docgen.refactoring;

import io.github.claudetoolkit.starter.client.ClaudeClient;

public class RefactoringService {

    private static final String SYSTEM_REFACTOR =
        "당신은 Java 코드 리팩터링 전문가입니다. 입력된 Java 코드를 분석하여 다음 형식으로 응답하세요:\n\n" +
        "## 현재 코드 문제점\n" +
        "- [SEVERITY: HIGH/MEDIUM/LOW] 문제점 설명\n\n" +
        "## 리팩터링된 코드\n" +
        "```java\n" +
        "// 리팩터링된 전체 코드\n" +
        "```\n\n" +
        "## 변경 사항 요약\n" +
        "| 항목 | 변경 전 | 변경 후 | 이유 |\n\n" +
        "리팩터링 우선 적용 기준:\n" +
        "1. 가독성 개선 (의미 있는 변수명, 메서드 분리)\n" +
        "2. Java 8 Stream API, Optional, Lambda 활용\n" +
        "3. 중복 코드 제거 (DRY 원칙)\n" +
        "4. SOLID 원칙 위반 개선\n" +
        "5. 불필요한 null 체크, 복잡한 분기 단순화\n" +
        "6. try-with-resources, 제네릭 타입 안전성\n" +
        "7. 매직 넘버/스트링 → 상수화\n" +
        "비즈니스 로직은 변경하지 말고 구조적 개선만 수행하세요.";

    private final ClaudeClient claudeClient;

    public RefactoringService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public String refactor(String javaCode) {
        return refactor(javaCode, "");
    }

    public String refactor(String javaCode, String projectContext) {
        String system = SYSTEM_REFACTOR;
        if (projectContext != null && !projectContext.trim().isEmpty()) {
            system = system + "\n\n[프로젝트 컨텍스트]\n" + projectContext;
        }
        return claudeClient.chat(system, javaCode);
    }
}
