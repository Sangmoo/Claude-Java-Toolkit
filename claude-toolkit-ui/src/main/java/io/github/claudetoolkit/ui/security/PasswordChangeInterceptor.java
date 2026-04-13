package io.github.claudetoolkit.ui.security;

import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;

/**
 * 초기 비밀번호 강제 변경 인터셉터.
 *
 * <p>{@code AppUser.mustChangePassword == true}이면
 * 비밀번호 변경 페이지({@code /account/password})를 제외한 모든 페이지 접근을
 * 비밀번호 변경 페이지로 리다이렉트합니다.
 */
@Configuration
public class PasswordChangeInterceptor implements WebMvcConfigurer {

    private final AppUserRepository userRepository;

    public PasswordChangeInterceptor(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PasswordCheckHandler())
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/login", "/login/**", "/logout",
                    "/setup", "/setup/**",
                    "/account/password", "/account/change-password",
                    "/assets/**", "/favicon.svg", "/manifest.json",
                    "/css/**", "/js/**",
                    "/actuator/**", "/error", "/api/**"
                );
    }

    private class PasswordCheckHandler implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            Principal principal = request.getUserPrincipal();
            if (principal == null) return true;

            AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user != null && user.isMustChangePassword()) {
                response.sendRedirect("/account/password?mustChange=true");
                return false;
            }
            return true;
        }
    }
}
