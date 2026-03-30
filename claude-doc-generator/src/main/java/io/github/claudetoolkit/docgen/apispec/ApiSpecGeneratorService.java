package io.github.claudetoolkit.docgen.apispec;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Generates OpenAPI 3.0 YAML or Swagger 2.0 annotations
 * from Spring Boot Controller source code using Claude API.
 */
public class ApiSpecGeneratorService {

    private static final String SYSTEM_OPENAPI =
            "당신은 OpenAPI 3.0 명세서 작성 전문가입니다.\n" +
            "Spring Boot @RestController 또는 @Controller 코드를 분석하여 OpenAPI 3.0 YAML 명세서를 생성합니다.\n\n" +
            "규칙:\n" +
            "1. 모든 @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping을 paths로 변환\n" +
            "2. @RequestBody → requestBody (required, content, schema 포함)\n" +
            "3. @PathVariable → parameters (in: path)\n" +
            "4. @RequestParam  → parameters (in: query, required 여부)\n" +
            "5. @RequestHeader → parameters (in: header)\n" +
            "6. 응답: 200(정상), 400(잘못된 요청), 401(인증 실패), 404(미발견), 500(서버 오류) 포함\n" +
            "7. description, summary는 한국어로 작성\n" +
            "8. components/schemas에 DTO 구조 정의\n\n" +
            "반드시 openapi: '3.0.0', info, paths, components 섹션을 포함한 완전한 YAML만 출력하세요.";

    private static final String SYSTEM_SWAGGER =
            "당신은 Swagger 2.0 / SpringFox 어노테이션 전문가입니다.\n" +
            "Controller 코드에 Swagger 어노테이션을 추가하여 완전한 Java 코드를 반환합니다.\n\n" +
            "규칙:\n" +
            "1. @Api(tags = {\"태그명\"}) — Controller 클래스 수준\n" +
            "2. @ApiOperation(value = \"요약\", notes = \"상세 설명\") — 각 메서드\n" +
            "3. @ApiParam(value = \"설명\", required = true/false, example = \"예시값\") — 파라미터\n" +
            "4. @ApiResponse(code = 200, message = \"성공\") + @ApiResponses — 응답\n" +
            "5. @ApiIgnore — 내부용 엔드포인트 제외\n" +
            "6. description, value는 한국어로 작성\n" +
            "7. 기존 코드 구조 및 로직을 완전히 유지하며 어노테이션만 추가";

    private final ClaudeClient claudeClient;

    public ApiSpecGeneratorService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Generate OpenAPI 3.0 YAML from a Spring Controller.
     */
    public String generateOpenApiYaml(String controllerCode) {
        return generateOpenApiYaml(controllerCode, "");
    }

    /**
     * Add Swagger 2.0 annotations to an existing Controller.
     */
    public String generateSwaggerAnnotations(String controllerCode) {
        return generateSwaggerAnnotations(controllerCode, "");
    }

    /**
     * Generate based on output type.
     *
     * @param sourceCode  Controller source code
     * @param outputType  "openapi" for YAML, "swagger" for annotated Java
     */
    public String generate(String sourceCode, String outputType) {
        return generate(sourceCode, outputType, "");
    }

    public String generate(String sourceCode, String outputType, String projectContext) {
        if ("swagger".equalsIgnoreCase(outputType)) {
            return generateSwaggerAnnotations(sourceCode, projectContext);
        }
        return generateOpenApiYaml(sourceCode, projectContext);
    }

    public String generateOpenApiYaml(String controllerCode, String projectContext) {
        String system = projectContext != null && !projectContext.trim().isEmpty()
                ? SYSTEM_OPENAPI + "\n\n[프로젝트 컨텍스트]\n" + projectContext
                : SYSTEM_OPENAPI;
        String userMessage = "다음 Controller 코드에서 OpenAPI 3.0 YAML 명세서를 생성해주세요:\n\n```java\n"
                + controllerCode + "\n```";
        return claudeClient.chat(system, userMessage);
    }

    public String generateSwaggerAnnotations(String controllerCode, String projectContext) {
        String system = projectContext != null && !projectContext.trim().isEmpty()
                ? SYSTEM_SWAGGER + "\n\n[프로젝트 컨텍스트]\n" + projectContext
                : SYSTEM_SWAGGER;
        String userMessage = "다음 Controller 코드에 Swagger 2.0 어노테이션을 추가해주세요:\n\n```java\n"
                + controllerCode + "\n```";
        return claudeClient.chat(system, userMessage);
    }
}
