package io.github.claudetoolkit.ui.config;

import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import io.github.claudetoolkit.ui.user.UserPermission;
import io.github.claudetoolkit.ui.user.UserPermissionRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.*;

/**
 * 사용자별 기능 권한을 request attribute에 주입하고,
 * 차단된 기능 URL 접근 시 403을 반환하는 인터셉터.
 *
 * <p>ADMIN은 모든 기능 허용. VIEWER/REVIEWER만 제한 적용.
 */
@Configuration
public class PermissionInterceptor implements WebMvcConfigurer {

    /** featureKey → URL 경로 매핑 */
    private static final Map<String, String> FEATURE_PATHS = new LinkedHashMap<String, String>();
    static {
        // 분석
        FEATURE_PATHS.put("workspace",     "/workspace");
        FEATURE_PATHS.put("advisor",       "/advisor");
        FEATURE_PATHS.put("sql-translate", "/sql-translate");
        FEATURE_PATHS.put("sql-batch",     "/sql-batch");
        FEATURE_PATHS.put("erd",           "/erd");
        FEATURE_PATHS.put("complexity",    "/complexity");
        FEATURE_PATHS.put("explain",       "/explain");
        FEATURE_PATHS.put("harness",       "/harness");
        FEATURE_PATHS.put("codereview",    "/codereview");
        // 생성
        FEATURE_PATHS.put("docgen",        "/docgen");
        FEATURE_PATHS.put("testgen",       "/testgen");
        FEATURE_PATHS.put("apispec",       "/apispec");
        FEATURE_PATHS.put("converter",     "/converter");
        FEATURE_PATHS.put("mockdata",      "/mockdata");
        FEATURE_PATHS.put("migration",     "/migration");
        FEATURE_PATHS.put("batch",         "/batch");
        FEATURE_PATHS.put("depcheck",      "/depcheck");
        FEATURE_PATHS.put("migrate",       "/migrate");
        // 기록
        FEATURE_PATHS.put("history",       "/history");
        FEATURE_PATHS.put("favorites",     "/favorites");
        FEATURE_PATHS.put("usage",         "/usage");
        FEATURE_PATHS.put("roi-report",    "/roi-report");
        FEATURE_PATHS.put("schedule",      "/schedule");
        FEATURE_PATHS.put("review-requests", "/review-requests");
        // 도구
        FEATURE_PATHS.put("loganalyzer",   "/loganalyzer");
        FEATURE_PATHS.put("regex",         "/regex");
        FEATURE_PATHS.put("commitmsg",     "/commitmsg");
        FEATURE_PATHS.put("maskgen",       "/maskgen");
        FEATURE_PATHS.put("input-masking", "/input-masking");
        FEATURE_PATHS.put("github-pr",     "/github-pr");
        FEATURE_PATHS.put("git-diff",      "/git-diff");
        // 채팅
        FEATURE_PATHS.put("chat",          "/chat");
        // 기타
        FEATURE_PATHS.put("prompts",       "/prompts");
        FEATURE_PATHS.put("search",        "/search");
    }

    /** Rate limit 대상 URL 패턴 (POST 분석 실행) */
    private static final java.util.Set<String> RATE_LIMIT_PATHS = new java.util.HashSet<String>(
            java.util.Arrays.asList("/workspace/run", "/workspace/compare",
                    "/sql-translate/init", "/harness/analyze", "/codereview/review",
                    "/github-pr/analyze", "/git-diff/analyze"));

    private final AppUserRepository userRepository;
    private final UserPermissionRepository permissionRepository;
    private final io.github.claudetoolkit.ui.security.RateLimitService rateLimitService;
    private final io.github.claudetoolkit.starter.client.ClaudeClient claudeClient;

    public PermissionInterceptor(AppUserRepository userRepository,
                                 UserPermissionRepository permissionRepository,
                                 io.github.claudetoolkit.ui.security.RateLimitService rateLimitService,
                                 io.github.claudetoolkit.starter.client.ClaudeClient claudeClient) {
        this.userRepository       = userRepository;
        this.permissionRepository = permissionRepository;
        this.rateLimitService     = rateLimitService;
        this.claudeClient         = claudeClient;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/admin", "/admin/users");
        registry.addRedirectViewController("/admin/", "/admin/users");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PermCheckInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/login", "/logout", "/setup", "/setup/**",
                    "/css/**", "/js/**", "/favicon.svg",
                    "/actuator/**", "/share/**", "/error",
                    "/admin/**", "/settings/**", "/security/**",
                    "/account/**", "/stream/**"
                );
    }

    private class PermCheckInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            Principal principal = request.getUserPrincipal();
            if (principal == null) return true;

            // ADMIN은 모든 기능 허용
            if (request.isUserInRole("ADMIN")) {
                request.setAttribute("allowedFeatures", FEATURE_PATHS.keySet());
                return true;
            }

            // 사용자 조회
            AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user == null) return true;

            // 권한 조회
            List<UserPermission> perms = permissionRepository.findByUserId(user.getId());
            Set<String> allowed = new HashSet<String>(FEATURE_PATHS.keySet()); // 기본 전체 허용

            for (UserPermission p : perms) {
                if (!p.isAllowed()) {
                    allowed.remove(p.getFeatureKey());
                }
            }

            // request attribute에 세팅 (사이드바에서 사용)
            request.setAttribute("allowedFeatures", allowed);

            // 현재 요청 URL이 차단된 기능인지 체크
            String path = request.getRequestURI();
            for (Map.Entry<String, String> entry : FEATURE_PATHS.entrySet()) {
                if (path.equals(entry.getValue()) || path.startsWith(entry.getValue() + "/")) {
                    if (!allowed.contains(entry.getKey())) {
                        response.sendRedirect("/login?denied=true");
                        return false;
                    }
                    break;
                }
            }

            // 사용자별 API 키 적용 (POST 분석 실행 시)
            // ThreadLocal 기반 오버라이드 → 공유 빈 수정 없이 요청별 격리
            if ("POST".equalsIgnoreCase(request.getMethod()) && RATE_LIMIT_PATHS.contains(path)) {
                String personalKey = user.getPersonalApiKey();
                if (personalKey != null && !personalKey.trim().isEmpty()) {
                    claudeClient.setApiKeyOverride(personalKey.trim());
                }
            }

            // Rate Limit 체크 (POST 분석 실행 요청에만 적용) — v2.6.0: 일일/월간 한도 포함
            if ("POST".equalsIgnoreCase(request.getMethod()) && RATE_LIMIT_PATHS.contains(path)) {
                String reason = rateLimitService.checkAndRecord(
                        user.getUsername(),
                        user.getRateLimitPerMinute(), user.getRateLimitPerHour(),
                        user.getDailyApiLimit(),     user.getMonthlyApiLimit());
                if (reason != null) {
                    response.setStatus(429);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false,\"error\":\"" + reason + "\"}");
                    return false;
                }
            }

            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception ex) {
            // 요청 스레드의 API 키 오버라이드 제거
            claudeClient.clearApiKeyOverride();
        }
    }
}
