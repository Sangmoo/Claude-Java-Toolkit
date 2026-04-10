package io.github.claudetoolkit.ui.user;

import io.github.claudetoolkit.ui.security.IpWhitelistChecker;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * Spring Security UserDetailsService 구현.
 * AppUser 엔티티를 Spring Security UserDetails로 변환합니다.
 *
 * <p>v2.6.0: 계정 잠금({@code lockedUntil}) 및 사용자별 IP 화이트리스트 검증 추가.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public AppUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsernameAndEnabledTrue(username)
                .orElseThrow(new java.util.function.Supplier<UsernameNotFoundException>() {
                    public UsernameNotFoundException get() {
                        return new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
                    }
                });

        // ── v2.6.0: 계정 잠금 체크 ──────────────────────────────────────
        if (user.isLocked()) {
            throw new LockedException("계정이 일시 잠금되었습니다. 잠시 후 다시 시도해주세요.");
        }

        // ── v2.6.0: 사용자별 IP 화이트리스트 체크 ───────────────────────
        String whitelist = user.getIpWhitelist();
        if (whitelist != null && !whitelist.trim().isEmpty()) {
            String clientIp = resolveClientIp();
            if (!IpWhitelistChecker.isAllowed(clientIp, whitelist)) {
                throw new LockedException("허용되지 않은 IP에서 접근했습니다: " + clientIp);
            }
        }

        // 마지막 로그인 시간 갱신
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return new User(
                user.getUsername(),
                user.getPasswordHash(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }

    /** 현재 HTTP 요청의 클라이언트 IP 추출 */
    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.trim().isEmpty()) {
                int comma = xff.indexOf(',');
                return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
