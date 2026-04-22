package io.github.claudetoolkit.ui.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security 설정.
 *
 * <ul>
 *   <li>Form 로그인: /login (GET/POST)</li>
 *   <li>로그아웃: /logout</li>
 *   <li>역할별 접근 제어: ADMIN / REVIEWER / VIEWER</li>
 *   <li>정적 리소스, 공유 링크는 인증 없이 접근 가능</li>
 *   <li>기존 ApiKeyFilter, AuditLogFilter와 공존</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TwoFactorAuthHandler twoFactorAuthHandler;
    private final LoginAttemptHandler  loginAttemptHandler;

    public SecurityConfig(TwoFactorAuthHandler twoFactorAuthHandler,
                          LoginAttemptHandler loginAttemptHandler) {
        this.twoFactorAuthHandler = twoFactorAuthHandler;
        this.loginAttemptHandler  = loginAttemptHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF: REST/SSE 엔드포인트 비활성, 폼은 활성
            .csrf()
                .ignoringAntMatchers(
                    "/login",
                    "/api/**", "/stream/**", "/workspace/**",
                    "/security/**", "/settings/**", "/admin/**", "/setup/**",
                    "/history/*/share", "/sql-translate/**",
                    "/harness/**", "/codereview/**", "/advisor/**",
                    "/docgen/**", "/converter/**", "/explain/**",
                    "/sql-batch/**", "/batch/**", "/mockdata/**",
                    "/migration/**", "/apispec/**", "/testgen/**",
                    "/complexity/**", "/erd/**", "/search/**",
                    "/loganalyzer/**", "/regex/**", "/commitmsg/**",
                    "/maskgen/**", "/input-masking/**", "/depcheck/**",
                    "/migrate/**", "/schedule/**", "/db-profiles/**",
                    "/roi-report/**", "/prompts/**", "/favorites/**",
                    "/usage/**", "/account/**", "/login/2fa/**",
                    "/github-pr/**", "/git-diff/**",
                    "/notifications/**",
                    "/chat/**",
                    // v4.2.x: 리뷰 이력 관련 POST 전체
                    //   - /history/{id}/share         : 공유 링크 생성
                    //   - /history/{id}/comments/**   : 댓글/대댓글
                    //   - /history/{id}/review-status : 승인/거절 (v4.2.3)
                    //   - /history/{id}/delete        : 삭제
                    "/history/**",
                    // v4.2.x: 분석 파이프라인 — POST /pipelines/{id}/run 등
                    "/pipelines/**",
                    // v4.2.x: 이메일 발송 (다수 수신자)
                    "/email/**"
                )
            .and()

            .authorizeRequests()
                // 공개: 정적 리소스, 로그인, 공유 링크
                .antMatchers("/assets/**", "/favicon.svg", "/manifest.json",
                             "/react/**",
                             "/api/v1/auth/login",          // React JSON 로그인
                             "/login", "/login/2fa", "/login/2fa/**",
                             "/setup", "/setup/**",
                             "/share/**", "/api/v1/share/**", "/actuator/**").permitAll()
                // ADMIN 전용 — Thymeleaf 관리 페이지
                .antMatchers("/admin/**").hasRole("ADMIN")
                // v4.2.7: ADMIN 전용 REST API (/api/v1/admin/users, /audit-logs, /permissions)
                // — 기존에는 "/admin/**" 규칙이 "/api/v1/admin/**" 을 매칭하지 않아
                //   사실상 VIEWER/REVIEWER 도 호출 가능했던 구멍을 막는다.
                .antMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // v4.4.0: Swagger UI / OpenAPI 스펙은 ADMIN 만 접근 가능
                // (API 카탈로그 = 잠재적 정보 누출 → 운영 환경에서 권한 게이팅 필수)
                .antMatchers("/swagger-ui.html", "/swagger-ui/**",
                             "/v3/api-docs", "/v3/api-docs/**",
                             "/swagger-resources/**").hasRole("ADMIN")
                // REVIEWER+: 프롬프트 편집
                .antMatchers("/settings/prompts", "/settings/prompts/**").hasAnyRole("ADMIN", "REVIEWER")
                // ADMIN 전용: 설정, 보안
                .antMatchers("/settings", "/settings/**").hasRole("ADMIN")
                .antMatchers("/security", "/security/**").hasRole("ADMIN")
                // 나머지: 인증된 사용자 (VIEWER 이상)
                .anyRequest().authenticated()
            .and()

            .exceptionHandling()
                .accessDeniedPage("/login?denied=true")
            .and()

            .sessionManagement()
                .sessionFixation().newSession()
                .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
                    .expiredUrl("/login?expired=true")
                .and()
            .and()

            .formLogin()
                .loginPage("/login")
                .successHandler(twoFactorAuthHandler)
                .failureHandler(loginAttemptHandler)
                .permitAll()
            .and()

            .logout()
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll();

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
