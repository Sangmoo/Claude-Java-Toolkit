package io.github.claudetoolkit.docgen.javadoc;

import io.github.claudetoolkit.starter.client.ClaudeClient;

public class JavadocGeneratorService {

    private static final String SYSTEM_JAVADOC =
        "당신은 Java 시니어 개발자입니다. 입력된 Java 소스 코드에 Javadoc 주석을 추가하여 완성된 소스 코드를 반환하세요.\n" +
        "규칙:\n" +
        "1. 클래스, 인터페이스, 열거형: /** ... */ 클래스 레벨 Javadoc 추가 (목적, 주요 기능)\n" +
        "2. public/protected 메서드: @param, @return, @throws 태그 포함 Javadoc 추가\n" +
        "3. 필드: 의미 있는 경우 /** 한 줄 Javadoc */ 추가\n" +
        "4. 기존 주석이 있으면 Javadoc 형식으로 업그레이드\n" +
        "5. 소스 코드에 한국어 식별자/주석이 있으면 한국어로, 영문이면 영문으로 작성\n" +
        "6. Lombok 어노테이션(@Getter, @Setter 등)이 있으면 생성 메서드 설명 포함\n" +
        "7. Spring 어노테이션(@RestController, @Service 등) 있으면 레이어 설명 포함\n" +
        "원본 코드 구조/로직은 절대 변경하지 말고, 주석만 추가/수정하여 전체 소스를 반환하세요.";

    private final ClaudeClient claudeClient;

    public JavadocGeneratorService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public String generate(String javaSource) {
        return generate(javaSource, "");
    }

    public String generate(String javaSource, String projectContext) {
        String system = SYSTEM_JAVADOC;
        if (projectContext != null && !projectContext.trim().isEmpty()) {
            system = system + "\n\n[프로젝트 컨텍스트]\n" + projectContext;
        }
        return claudeClient.chat(system, javaSource);
    }
}
