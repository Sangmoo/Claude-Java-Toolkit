package io.github.claudetoolkit.ui.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * v4.4.0 — Springdoc OpenAPI 메타데이터.
 *
 * <p>Swagger UI 화면 상단 정보 + 보안 스킴(세션 쿠키) + 카테고리 태그 정의.
 * <p>접속:
 * <ul>
 *   <li>Swagger UI:  http://localhost:8027/swagger-ui.html  (ADMIN 전용)</li>
 *   <li>JSON 스펙:   http://localhost:8027/v3/api-docs       (Postman 임포트)</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI claudeToolkitOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Claude Java Toolkit REST API")
                .version("4.4.0")
                .description("AI-powered tools for Oracle DB & Java/Spring enterprise development.\n\n"
                        + "공식 REST API 카탈로그입니다. 모든 엔드포인트는 세션 인증을 사용합니다 — "
                        + "먼저 `POST /api/v1/auth/login` 으로 로그인 후 다른 API 를 호출하세요.\n\n"
                        + "v4.3.0 신규: SARIF/Excel 내보내기, 비용 옵티마이저, SQL 인덱스 시뮬레이션, 대시보드 위젯.\n"
                        + "ADMIN 권한 필요: `/api/v1/admin/**` 경로.")
                .contact(new Contact()
                    .name("Sangmoo")
                    .url("https://github.com/Sangmoo/Claude-Java-Toolkit"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .addServersItem(new Server()
                .url("/")
                .description("현재 서버"))
            // ── 보안 스킴: Spring Security 세션 쿠키 (JSESSIONID) ──
            .components(new Components()
                .addSecuritySchemes("sessionAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("JSESSIONID")
                    .description("Spring Security 세션 쿠키 — `POST /api/v1/auth/login` 으로 발급")))
            .addSecurityItem(new SecurityRequirement().addList("sessionAuth"))
            // ── 태그 — Swagger UI 좌측 카테고리 정렬 + 설명 ──
            .tags(Arrays.asList(
                new Tag().name("Auth")        .description("인증 / 로그아웃 / 사용자 정보 / Locale"),
                new Tag().name("SQL")         .description("SQL 리뷰 / 보안 / 실행계획 / 인덱스 시뮬레이션"),
                new Tag().name("Code")        .description("Java/Spring 코드 리뷰 / 보안 감사"),
                new Tag().name("Doc")         .description("기술 문서 / Javadoc 자동 생성"),
                new Tag().name("ERD")         .description("ERD 분석 / DDL 생성"),
                new Tag().name("Pipeline")    .description("분석 파이프라인 정의 / 실행 / 이력"),
                new Tag().name("History")     .description("리뷰 이력 / 즐겨찾기 / 검색"),
                new Tag().name("Dashboard")   .description("홈 대시보드 위젯 레이아웃"),
                new Tag().name("Export")      .description("SARIF / Excel 내보내기"),
                new Tag().name("Health")      .description("헬스체크 / 시스템 상태"),
                new Tag().name("Admin")       .description("관리자 전용: 사용자 / 권한 / 비용 / 통계 / 감사 로그")
            ));
    }
}
