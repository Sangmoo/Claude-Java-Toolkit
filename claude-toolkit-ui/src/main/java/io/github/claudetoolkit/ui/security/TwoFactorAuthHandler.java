package io.github.claudetoolkit.ui.security;

import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 로그인 성공 후 ADMIN 사용자에게 2FA 검증을 강제합니다.
 */
@Component
public class TwoFactorAuthHandler implements AuthenticationSuccessHandler {

    private final AppUserRepository userRepository;

    public TwoFactorAuthHandler(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        boolean isAdmin = authentication.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

        if (isAdmin) {
            // ADMIN은 2FA 검증 필요
            request.getSession().setAttribute("2fa_pending", true);
            request.getSession().setAttribute("2fa_username", authentication.getName());
            response.sendRedirect("/login/2fa");
        } else {
            // ADMIN이 아닌 사용자는 바로 메인 페이지
            response.sendRedirect("/");
        }
    }
}
