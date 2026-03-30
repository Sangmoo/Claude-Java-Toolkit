package io.github.claudetoolkit.docgen.testgen;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Generates JUnit 5 test code from Java source files using Claude API.
 *
 * Supported source types:
 * - Controller  → @WebMvcTest + MockMvc
 * - Service     → @ExtendWith(MockitoExtension) + Mockito
 * - Mapper/DAO  → @MybatisTest or H2-based
 * - General     → JUnit 5 unit test
 */
public class TestGeneratorService {

    private static final String SYSTEM_CONTROLLER =
            "당신은 Spring Boot 테스트 전문가입니다.\n" +
            "Spring @Controller/@RestController 코드를 분석하여 JUnit 5 + MockMvc 테스트를 생성합니다.\n\n" +
            "규칙:\n" +
            "1. @WebMvcTest 또는 @SpringBootTest + @AutoConfigureMockMvc 사용\n" +
            "2. @MockBean으로 Service 의존성 Mock\n" +
            "3. MockMvc로 HTTP 요청 시뮬레이션 (perform, andExpect)\n" +
            "4. 정상 케이스, 입력값 검증 실패, 서비스 예외 케이스 포함\n" +
            "5. @DisplayName으로 한국어 테스트 이름 지정\n" +
            "6. andExpect(status().isOk()), andExpect(jsonPath()) 활용\n" +
            "7. given(mockService.method()).willReturn(...) 패턴 사용";

    private static final String SYSTEM_SERVICE =
            "당신은 Spring Boot 단위 테스트 전문가입니다.\n" +
            "Spring @Service 코드를 분석하여 JUnit 5 + Mockito 단위 테스트를 생성합니다.\n\n" +
            "규칙:\n" +
            "1. @ExtendWith(MockitoExtension.class) 사용\n" +
            "2. @Mock으로 Repository/Mapper 등 의존성 Mock\n" +
            "3. @InjectMocks로 테스트 대상 Service 주입\n" +
            "4. when().thenReturn(), when().thenThrow() 활용\n" +
            "5. 정상 처리, 예외 발생(BusinessException 등), 빈 결과 케이스 포함\n" +
            "6. @DisplayName으로 한국어 테스트 이름 지정\n" +
            "7. assertThat() (AssertJ) 활용, verify() 검증";

    private static final String SYSTEM_MAPPER =
            "당신은 MyBatis 테스트 전문가입니다.\n" +
            "MyBatis Mapper 인터페이스/XML 코드를 분석하여 JUnit 5 통합 테스트를 생성합니다.\n\n" +
            "규칙:\n" +
            "1. @MybatisTest 어노테이션 사용 (MyBatis 자동 설정)\n" +
            "2. @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) 또는 H2 인메모리 DB\n" +
            "3. @Autowired로 Mapper 주입\n" +
            "4. SQL CRUD 각각에 대한 테스트 메서드 생성\n" +
            "5. @DisplayName으로 한국어 테스트 이름 지정\n" +
            "6. assertThat() (AssertJ) 활용\n" +
            "7. @Sql 어노테이션으로 테스트 데이터 삽입";

    private static final String SYSTEM_GENERAL =
            "당신은 Java 단위 테스트 전문가입니다.\n" +
            "주어진 Java 코드를 분석하여 JUnit 5 테스트를 생성합니다.\n\n" +
            "규칙:\n" +
            "1. 경계값 분석(BVA), 동등 분할(EP) 적용\n" +
            "2. Mockito로 외부 의존성 Mock\n" +
            "3. @DisplayName으로 한국어 테스트 이름 지정\n" +
            "4. 정상 케이스, 예외 케이스, null/빈값 경계 케이스 포함\n" +
            "5. assertThat() (AssertJ) 활용\n" +
            "6. @BeforeEach, @AfterEach로 공통 setUp/tearDown 처리";

    private final ClaudeClient claudeClient;

    public TestGeneratorService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Generate JUnit 5 test code for the given source.
     *
     * @param sourceCode Java source code
     * @param sourceType hint: "Controller", "Service", "Mapper", "Repository", etc.
     */
    public String generateTest(String sourceCode, String sourceType) {
        return generateTest(sourceCode, sourceType, "");
    }

    /**
     * Generate JUnit 5 test code with project context.
     *
     * @param sourceCode     Java source code
     * @param sourceType     hint: "Controller", "Service", "Mapper", etc.
     * @param projectContext optional project context memo (prepended to system prompt)
     */
    public String generateTest(String sourceCode, String sourceType, String projectContext) {
        String systemPrompt = selectSystemPrompt(sourceType);
        if (projectContext != null && !projectContext.trim().isEmpty()) {
            systemPrompt = systemPrompt + "\n\n[프로젝트 컨텍스트]\n" + projectContext;
        }
        String userMessage = "다음 " + sourceType + " 코드에 대한 완전한 JUnit 5 테스트 클래스를 생성해주세요:\n\n```java\n"
                + sourceCode + "\n```";
        return claudeClient.chat(systemPrompt, userMessage);
    }

    private String selectSystemPrompt(String sourceType) {
        if (sourceType == null) return SYSTEM_GENERAL;
        String lower = sourceType.toLowerCase();
        if (lower.contains("controller")) return SYSTEM_CONTROLLER;
        if (lower.contains("service"))    return SYSTEM_SERVICE;
        if (lower.contains("mapper") || lower.contains("repository") || lower.contains("dao")) return SYSTEM_MAPPER;
        return SYSTEM_GENERAL;
    }
}
