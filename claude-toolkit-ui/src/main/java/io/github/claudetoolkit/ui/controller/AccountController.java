package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import io.github.claudetoolkit.ui.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 본인 계정 관리 (비밀번호 변경, 개인 설정).
 */
@Controller
@RequestMapping("/account")
public class AccountController {

    private final AppUserRepository userRepository;
    private final UserService userService;
    private final BCryptPasswordEncoder encoder;

    public AccountController(AppUserRepository userRepository, UserService userService,
                             BCryptPasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.userService    = userService;
        this.encoder        = encoder;
    }

    /** 개인 API 키 저장 */
    @PostMapping("/save-api-key")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveApiKey(
            @RequestParam String apiKey, Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user == null) { resp.put("success", false); resp.put("error", "사용자 없음"); return ResponseEntity.ok(resp); }
            user.setPersonalApiKey(apiKey.trim().isEmpty() ? null : apiKey.trim());
            userRepository.save(user);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 내 정보 수정 */
    @PostMapping("/save-profile")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveProfile(
            @RequestParam(defaultValue = "") String displayName,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String phone,
            Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user == null) { resp.put("success", false); resp.put("error", "사용자 없음"); return ResponseEntity.ok(resp); }
            user.setDisplayName(displayName);
            user.setEmail(email);
            user.setPhone(phone);
            userRepository.save(user);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 2FA 시크릿 생성 (QR코드용) */
    @PostMapping("/setup-2fa")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setup2fa(Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) { resp.put("success", false); return ResponseEntity.ok(resp); }
        String secret = io.github.claudetoolkit.ui.security.TotpService.generateSecret();
        String otpUri = io.github.claudetoolkit.ui.security.TotpService.buildOtpAuthUri(
                secret, user.getUsername(), "Claude Toolkit");
        // 아직 저장하지 않음 — verify 후 저장
        resp.put("success", true);
        resp.put("secret", secret);
        resp.put("otpAuthUri", otpUri);
        return ResponseEntity.ok(resp);
    }

    /** 2FA 코드 검증 + 시크릿 저장 */
    @PostMapping("/verify-2fa")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verify2fa(
            @RequestParam String secret, @RequestParam String code, Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (!io.github.claudetoolkit.ui.security.TotpService.verifyCode(secret, code)) {
            resp.put("success", false);
            resp.put("error", "인증 코드가 올바르지 않습니다.");
            return ResponseEntity.ok(resp);
        }
        AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user != null) {
            user.setTotpSecret(secret);
            userRepository.save(user);
        }
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    /** 2FA 비활성화 */
    @PostMapping("/disable-2fa")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> disable2fa(Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user != null) {
            user.setTotpSecret(null);
            userRepository.save(user);
        }
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user == null) {
                resp.put("success", false);
                resp.put("error", "사용자를 찾을 수 없습니다.");
                return ResponseEntity.ok(resp);
            }
            if (!encoder.matches(currentPassword, user.getPasswordHash())) {
                resp.put("success", false);
                resp.put("error", "현재 비밀번호가 올바르지 않습니다.");
                return ResponseEntity.ok(resp);
            }
            // 비밀번호 정책 검증
            String policyError = userService.validatePassword(newPassword);
            if (policyError != null) {
                resp.put("success", false);
                resp.put("error", policyError);
                return ResponseEntity.ok(resp);
            }
            userService.changePassword(user.getId(), newPassword);
            // 강제 변경 플래그 해제 + v2.6.0: 비밀번호 변경 시각/스누즈 리셋
            user.setMustChangePassword(false);
            user.setLastPasswordChangeAt(LocalDateTime.now());
            user.setPasswordSnoozeAt(null);
            userRepository.save(user);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 비밀번호 변경 "다음에 변경하기" — passwordSnoozeAt을 현재 시각으로 설정 (v2.6.0) */
    @PostMapping("/snooze-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> snoozePassword(Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            AppUser user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user == null) {
                resp.put("success", false);
                resp.put("error", "사용자를 찾을 수 없습니다.");
                return ResponseEntity.ok(resp);
            }
            user.setPasswordSnoozeAt(LocalDateTime.now());
            userRepository.save(user);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }
}
