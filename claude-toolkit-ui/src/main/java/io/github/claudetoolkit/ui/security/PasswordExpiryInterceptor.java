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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 비밀번호 만료 알림 인터셉터 (v2.6.0).
 *
 * <p>기준 시각(baseTime) = max({@code lastPasswordChangeAt}, {@code passwordSnoozeAt}, {@code createdAt})
 * 으로부터 90일이 경과하면 비밀번호 변경 권고 페이지로 리다이렉트합니다.
 *
 * <p>강제 변경이 아니므로 사용자는 "다음에 변경하기" 버튼으로 스누즈할 수 있습니다.
 * 스누즈 시 {@code passwordSnoozeAt = now()}로 설정되어 다시 90일 카운팅이 시작됩니다.
 */
@Configuration
public class PasswordExpiryInterceptor implements WebMvcConfigurer {

    /** 비밀번호 만료 기간 (일) */
    private static final long EXPIRY_DAYS = 90L;

    private final AppUserRepository userRepository;

    public PasswordExpiryInterceptor(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ExpiryCheckHandler())
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/login", "/login/**", "/logout",
                    "/setup", "/setup/**",
                    "/account/password", "/account/change-password",
                    "/account/snooze-password", "/account/me", "/account/save-profile",
                    "/assets/**", "/favicon.svg", "/manifest.json",
                    "/css/**", "/js/**",
                    "/actuator/**", "/error", "/api/**"
                );
    }

    private class ExpiryCheckHandler implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            Principal principal = request.getUserPrincipal();
            if (principal == null) return true;

            AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user == null) return true;

            // 초기 비밀번호 강제 변경이 우선 → 통과 (PasswordChangeInterceptor가 처리)
            if (user.isMustChangePassword()) return true;

            // 기준 시각 계산: snoozeAt > lastChangeAt > createdAt 순서
            LocalDateTime baseTime = user.getPasswordSnoozeAt();
            if (baseTime == null) baseTime = user.getLastPasswordChangeAt();
            if (baseTime == null) baseTime = user.getCreatedAt();
            if (baseTime == null) return true;  // 기준 없음 → 통과

            long daysSince = ChronoUnit.DAYS.between(baseTime, LocalDateTime.now());
            if (daysSince >= EXPIRY_DAYS) {
                response.sendRedirect("/account/password?expired=true");
                return false;
            }
            return true;
        }
    }
}
