package io.github.claudetoolkit.ui.config;

import io.github.claudetoolkit.ui.security.SecuritySettings;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 설치 마법사 인터셉터.
 * 설치 미완료 상태에서 /setup 외 페이지 접근 시 /setup으로 리다이렉트.
 */
@Configuration
public class SetupInterceptor implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SetupCheckInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/setup", "/setup/**",
                    "/login", "/logout",
                    "/css/**", "/js/**", "/favicon.svg",
                    "/actuator/**", "/share/**",
                    "/error"
                );
    }

    private static class SetupCheckInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            try {
                if (!SecuritySettings.load().isSetupCompleted()) {
                    // API 키가 설정되어 있으면 자동으로 설치 완료 처리 (기존 사용자)
                    String apiKey = System.getenv("CLAUDE_API_KEY");
                    if (apiKey != null && !apiKey.isEmpty()) {
                        SecuritySettings ss = SecuritySettings.load();
                        ss.setSetupCompleted(true);
                        ss.save();
                        return true;
                    }
                    response.sendRedirect("/setup");
                    return false;
                }
            } catch (Exception ignored) {
                // 설정 파일 읽기 실패 시 통과
            }
            return true;
        }
    }
}
