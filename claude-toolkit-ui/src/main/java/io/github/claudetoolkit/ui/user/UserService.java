package io.github.claudetoolkit.ui.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 관리 서비스.
 */
@Service
public class UserService {

    private final AppUserRepository userRepository;
    private final BCryptPasswordEncoder encoder;

    public UserService(AppUserRepository userRepository, BCryptPasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.encoder        = encoder;
    }

    public List<AppUser> findAll() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    public AppUser findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Transactional
    public AppUser create(String username, String rawPassword, String role,
                          String displayName, String email, String phone) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 ID입니다: " + username);
        }
        AppUser user = new AppUser(username, encoder.encode(rawPassword), role.toUpperCase());
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPhone(phone);
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long id, String newRawPassword) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    public RuntimeException get() {
                        return new IllegalArgumentException("사용자를 찾을 수 없습니다.");
                    }
                });
        user.setPasswordHash(encoder.encode(newRawPassword));
        userRepository.save(user);
    }

    @Transactional
    public void changeRole(Long id, String newRole) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    public RuntimeException get() {
                        return new IllegalArgumentException("사용자를 찾을 수 없습니다.");
                    }
                });
        user.setRole(newRole.toUpperCase());
        userRepository.save(user);
    }

    @Transactional
    public void toggleEnabled(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    public RuntimeException get() {
                        return new IllegalArgumentException("사용자를 찾을 수 없습니다.");
                    }
                });
        // ADMIN 비활성화 시 마지막 ADMIN인지 체크
        if (user.isEnabled() && "ADMIN".equals(user.getRole())) {
            if (userRepository.countByRoleAndEnabledTrue("ADMIN") <= 1) {
                throw new IllegalStateException("마지막 관리자 계정은 비활성화할 수 없습니다.");
            }
        }
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    public RuntimeException get() {
                        return new IllegalArgumentException("사용자를 찾을 수 없습니다.");
                    }
                });
        if ("ADMIN".equals(user.getRole()) && userRepository.countByRoleAndEnabledTrue("ADMIN") <= 1) {
            throw new IllegalStateException("마지막 관리자 계정은 삭제할 수 없습니다.");
        }
        userRepository.delete(user);
    }
}
