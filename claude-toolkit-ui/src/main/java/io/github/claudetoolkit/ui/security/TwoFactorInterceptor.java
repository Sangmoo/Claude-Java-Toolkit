package io.github.claudetoolkit.ui.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 2FA 미완료 상태에서 모든 페이지 접근을 /login/2fa로 강제 리다이렉트.
 */
@Configuration
public class TwoFactorInterceptor implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TwoFactorCheckInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/login", "/login/**",
                    "/logout", "/setup", "/setup/**",
                    "/assets/**", "/favicon.svg", "/manifest.json",
                    "/css/**", "/js/**",
                    "/actuator/**", "/error", "/api/**",
                    "/chat/**", "/stream/**"
                );
    }

    private static class TwoFactorCheckInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            HttpSession session = request.getSession(false);
            if (session != null && Boolean.TRUE.equals(session.getAttribute("2fa_pending"))) {
                response.sendRedirect("/login/2fa");
                return false;
            }
            return true;
        }
    }
}
