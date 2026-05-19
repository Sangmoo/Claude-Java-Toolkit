package io.github.claudetoolkit.ui.security;

import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * TwoFactorAuthHandler 단위 테스트.
 *
 * <p>커버:
 * <ul>
 *   <li>ADMIN 로그인 → 세션에 2fa_pending + 2fa_username 세팅, /login/2fa 리다이렉트</li>
 *   <li>REVIEWER 로그인 → /로 바로 리다이렉트, 세션 세팅 없음</li>
 *   <li>VIEWER 로그인 → /로 바로 리다이렉트</li>
 *   <li>실패 카운터 > 0 인 ADMIN → 카운터 리셋 후 2FA 흐름</li>
 *   <li>DB에 없는 사용자 → 예외 없이 역할 기반 분기만 수행</li>
 * </ul>
 */
class TwoFactorAuthHandlerTest {

    private AppUserRepository       userRepository;
    private TwoFactorAuthHandler    handler;

    private HttpServletRequest      request;
    private HttpServletResponse     response;
    private HttpSession             session;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        handler        = new TwoFactorAuthHandler(userRepository);
        session        = mock(HttpSession.class);
        request        = mock(HttpServletRequest.class);
        response       = mock(HttpServletResponse.class);
        when(request.getSession()).thenReturn(session);
    }

    // ── ADMIN ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ADMIN 로그인 → 2fa_pending 세션 세팅 + /login/2fa 리다이렉트")
    void adminLogin_sets2faPending_redirectsTo2faPage() throws Exception {
        Authentication auth = mockAuth("admin1", "ROLE_ADMIN");
        when(userRepository.findByUsername("admin1"))
                .thenReturn(Optional.of(new AppUser("admin1", "hash", "ADMIN")));

        handler.onAuthenticationSuccess(request, response, auth);

        verify(session).setAttribute("2fa_pending", true);
        verify(session).setAttribute("2fa_username", "admin1");
        verify(response).sendRedirect("/login/2fa");
    }

    @Test
    @DisplayName("실패 카운터 > 0 인 ADMIN → 카운터 리셋 + 2FA 흐름")
    void adminWithFailedAttempts_resetsCounterThenRedirects() throws Exception {
        Authentication auth = mockAuth("admin2", "ROLE_ADMIN");
        AppUser admin = new AppUser("admin2", "hash", "ADMIN");
        admin.setFailedLoginAttempts(3);
        admin.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByUsername("admin2")).thenReturn(Optional.of(admin));

        handler.onAuthenticationSuccess(request, response, auth);

        verify(userRepository).save(admin);
        verify(response).sendRedirect("/login/2fa");
    }

    // ── Non-ADMIN ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("REVIEWER 로그인 → 세션 세팅 없이 / 로 리다이렉트")
    void reviewerLogin_redirectsToHome_noSessionSet() throws Exception {
        Authentication auth = mockAuth("reviewer1", "ROLE_REVIEWER");
        when(userRepository.findByUsername("reviewer1"))
                .thenReturn(Optional.of(new AppUser("reviewer1", "hash", "REVIEWER")));

        handler.onAuthenticationSuccess(request, response, auth);

        verify(session, never()).setAttribute(eq("2fa_pending"), any());
        verify(response).sendRedirect("/");
    }

    @Test
    @DisplayName("VIEWER 로그인 → / 로 리다이렉트")
    void viewerLogin_redirectsToHome() throws Exception {
        Authentication auth = mockAuth("viewer1", "ROLE_VIEWER");
        when(userRepository.findByUsername("viewer1"))
                .thenReturn(Optional.of(new AppUser("viewer1", "hash", "VIEWER")));

        handler.onAuthenticationSuccess(request, response, auth);

        verify(response).sendRedirect("/");
        verify(response, never()).sendRedirect("/login/2fa");
    }

    // ── 사용자 없는 경우 ────────────────────────────────────────────────

    @Test
    @DisplayName("DB에 없는 ADMIN → save 호출 없이 2FA 흐름 정상 동작")
    void userNotFoundInDb_doesNotThrow_adminStillRedirected() throws Exception {
        Authentication auth = mockAuth("ghost", "ROLE_ADMIN");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response, auth);

        verify(userRepository, never()).save(any());
        verify(response).sendRedirect("/login/2fa");
    }

    // ── Helper ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Authentication mockAuth(String username, String roleAuthority) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        Collection<GrantedAuthority> authorities =
                Arrays.<GrantedAuthority>asList(new SimpleGrantedAuthority(roleAuthority));
        doReturn(authorities).when(auth).getAuthorities();
        return auth;
    }
}
