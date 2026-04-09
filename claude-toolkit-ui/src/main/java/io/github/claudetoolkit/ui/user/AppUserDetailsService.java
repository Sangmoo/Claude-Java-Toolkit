package io.github.claudetoolkit.ui.user;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * Spring Security UserDetailsService 구현.
 * AppUser 엔티티를 Spring Security UserDetails로 변환합니다.
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

        // 마지막 로그인 시간 갱신
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return new User(
                user.getUsername(),
                user.getPasswordHash(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }
}
