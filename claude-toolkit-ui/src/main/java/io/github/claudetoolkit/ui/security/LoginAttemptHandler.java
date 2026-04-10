package io.github.claudetoolkit.ui.security;

import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 로그인 실패 처리 핸들러 (v2.6.0).
 *
 * <ul>
 *   <li>잘못된 비밀번호 → {@code failedLoginAttempts++}</li>
 *   <li>5회 연속 실패 시 10분 계정 잠금 ({@code lockedUntil = now + 10min})</li>
 *   <li>감사 로그에 LOGIN_FAIL / LOGIN_LOCKOUT 이벤트 기록</li>
 *   <li>잠금된 사용자는 {@link AppUserDetailsService}에서 {@code LockedException} 발생</li>
 * </ul>
 *
 * <p>성공 시 카운터 리셋은 {@link TwoFactorAuthHandler}에서 처리.
 */
@Component
public class LoginAttemptHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptHandler.class);

    private static final int  MAX_ATTEMPTS   = 5;
    private static final long LOCK_MINUTES   = 10L;
    private static final ZoneId KST          = ZoneId.of("Asia/Seoul");

    private final AppUserRepository userRepository;
    private final AuditLogService   auditLogService;

    public LoginAttemptHandler(AppUserRepository userRepository,
                               AuditLogService auditLogService) {
        this.userRepository  = userRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        String ip       = resolveClientIp(request);
        String ua       = request.getHeader("User-Agent");

        // 이미 잠긴 사용자가 다시 시도한 경우 → 전용 메시지
        // Spring Security의 DaoAuthenticationProvider는 loadUserByUsername에서 던진
        // LockedException을 InternalAuthenticationServiceException으로 래핑하므로 둘 다 체크
        if (isLockedException(exception)) {
            auditLogService.log("/login", "POST", ip, ua, 423, false, username);
            response.sendRedirect("/login?error=locked");
            return;
        }

        // 사용자 조회 (존재하지 않는 username도 감사 로그에 기록)
        AppUser user = username != null ? userRepository.findByUsername(username).orElse(null) : null;

        if (user == null) {
            // 존재하지 않는 계정
            auditLogService.log("/login", "POST", ip, ua, 401, false, username);
            response.sendRedirect("/login?error=true");
            return;
        }

        // 실패 카운터 증가
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now(KST).plusMinutes(LOCK_MINUTES));
            userRepository.save(user);
            auditLogService.log("/login", "POST", ip, ua, 423, false, username);
            log.warn("[LOGIN] User '{}' locked after {} failed attempts for {} minutes",
                    username, attempts, LOCK_MINUTES);
            response.sendRedirect("/login?error=locked");
        } else {
            userRepository.save(user);
            auditLogService.log("/login", "POST", ip, ua, 401, false, username);
            response.sendRedirect("/login?error=true&attempts=" + attempts);
        }
    }

    /** LockedException 또는 그것을 감싼 InternalAuthenticationServiceException 체크 */
    private boolean isLockedException(Exception ex) {
        if (ex instanceof LockedException) return true;
        if (ex instanceof InternalAuthenticationServiceException
                && ex.getCause() instanceof LockedException) return true;
        return false;
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return req.getRemoteAddr();
    }
}
