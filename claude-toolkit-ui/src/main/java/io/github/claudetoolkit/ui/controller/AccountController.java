package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import io.github.claudetoolkit.ui.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 본인 계정 관리 (비밀번호 변경).
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
            userService.changePassword(user.getId(), newPassword);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }
}
