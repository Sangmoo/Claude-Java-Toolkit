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

    /**
     * 비밀번호 정책을 검증합니다.
     * @return 위반 시 한국어 에러 메시지, 통과 시 null
     */
    public String validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            return "비밀번호는 최소 8자 이상이어야 합니다.";
        }
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (int i = 0; i < rawPassword.length(); i++) {
            char c = rawPassword.charAt(i);
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        if (!hasUpper) return "비밀번호에 대문자가 최소 1개 포함되어야 합니다.";
        if (!hasLower) return "비밀번호에 소문자가 최소 1개 포함되어야 합니다.";
        if (!hasDigit) return "비밀번호에 숫자가 최소 1개 포함되어야 합니다.";
        if (!hasSpecial) return "비밀번호에 특수문자가 최소 1개 포함되어야 합니다.";
        return null;
    }

    @Transactional
    public AppUser create(String username, String rawPassword, String role,
                          String displayName, String email, String phone) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 ID입니다: " + username);
        }
        String policyError = validatePassword(rawPassword);
        if (policyError != null) {
            throw new IllegalArgumentException(policyError);
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
        String policyError = validatePassword(newRawPassword);
        if (policyError != null) {
            throw new IllegalArgumentException(policyError);
        }
        // 이전 비밀번호와 동일 여부 검사
        if (encoder.matches(newRawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("이전 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.");
        }
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

    /**
     * @deprecated v2.6.0: 이 오버로드는 rate limit 및 API 한도를 0으로 리셋합니다.
     *     10-param 버전을 사용하세요.
     */
    @Deprecated
    @Transactional
    public void updateInfo(Long id, String displayName, String email, String phone, String role) {
        updateInfo(id, displayName, email, phone, role, null, 0, 0, 0, 0);
    }

    @Transactional
    public void updateInfo(Long id, String displayName, String email, String phone, String role,
                           String personalApiKey, int rateLimitPerMinute, int rateLimitPerHour,
                           int dailyApiLimit, int monthlyApiLimit) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    public RuntimeException get() {
                        return new IllegalArgumentException("사용자를 찾을 수 없습니다.");
                    }
                });
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPhone(phone);
        if (role != null && !role.isEmpty()) {
            user.setRole(role.toUpperCase());
        }
        if (personalApiKey != null && !personalApiKey.isEmpty()) {
            user.setPersonalApiKey(personalApiKey);
        }
        user.setRateLimitPerMinute(rateLimitPerMinute);
        user.setRateLimitPerHour(rateLimitPerHour);
        user.setDailyApiLimit(dailyApiLimit);
        user.setMonthlyApiLimit(monthlyApiLimit);
        userRepository.save(user);
    }

    /** 잠긴 사용자 수동 해제 (ADMIN 전용). v2.6.0 */
    @Transactional
    public void unlock(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    public RuntimeException get() {
                        return new IllegalArgumentException("사용자를 찾을 수 없습니다.");
                    }
                });
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    /** 사용자별 IP 화이트리스트 저장. v2.6.0 */
    @Transactional
    public void updateIpWhitelist(Long id, String whitelist) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    public RuntimeException get() {
                        return new IllegalArgumentException("사용자를 찾을 수 없습니다.");
                    }
                });
        user.setIpWhitelist(whitelist == null || whitelist.trim().isEmpty() ? null : whitelist.trim());
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
