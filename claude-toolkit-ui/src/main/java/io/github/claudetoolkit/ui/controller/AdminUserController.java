package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.security.RateLimitService;
import io.github.claudetoolkit.ui.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사용자 관리 컨트롤러 (ADMIN 전용).
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final RateLimitService rateLimitService;

    public AdminUserController(UserService userService, RateLimitService rateLimitService) {
        this.userService      = userService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String role,
            @RequestParam(defaultValue = "") String displayName,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String phone) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            userService.create(username, password, role, displayName, email, phone);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/change-role")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changeRole(
            @PathVariable Long id,
            @RequestParam String role) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            userService.changeRole(id, role);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changePassword(
            @PathVariable Long id,
            @RequestParam String newPassword) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            userService.changePassword(id, newPassword);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleEnabled(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            userService.toggleEnabled(id);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            userService.delete(id);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 사용자 정보 조회 (JSON) — v2.6.0: 잠금 상태, 일일/월간 한도, 사용량 포함 */
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        io.github.claudetoolkit.ui.user.AppUser user = userService.findById(id);
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.ok(resp);
        }
        resp.put("success", true);
        resp.put("id", user.getId());
        resp.put("username", user.getUsername());
        resp.put("displayName", user.getDisplayName());
        resp.put("email", user.getEmail());
        resp.put("phone", user.getPhone());
        resp.put("role", user.getRole());
        resp.put("personalApiKey", user.getPersonalApiKey() != null ? user.getPersonalApiKey() : "");
        resp.put("rateLimitPerMinute", user.getRateLimitPerMinute());
        resp.put("rateLimitPerHour", user.getRateLimitPerHour());
        resp.put("dailyApiLimit", user.getDailyApiLimit());
        resp.put("monthlyApiLimit", user.getMonthlyApiLimit());
        resp.put("locked", user.isLocked());
        resp.put("failedLoginAttempts", user.getFailedLoginAttempts());
        resp.put("lockedUntil", user.getLockedUntil() != null ? user.getLockedUntil().toString() : null);
        resp.put("ipWhitelist", user.getIpWhitelist() != null ? user.getIpWhitelist() : "");
        resp.put("usageStats", rateLimitService.getUsageStats(user.getUsername()));
        return ResponseEntity.ok(resp);
    }

    /** 사용자 정보 수정 (ID 제외) — v2.6.0: 일일/월간 한도 추가 */
    @PostMapping("/{id}/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long id,
            @RequestParam(defaultValue = "") String displayName,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String phone,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String personalApiKey,
            @RequestParam(defaultValue = "0") int rateLimitPerMinute,
            @RequestParam(defaultValue = "0") int rateLimitPerHour,
            @RequestParam(defaultValue = "0") int dailyApiLimit,
            @RequestParam(defaultValue = "0") int monthlyApiLimit) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            userService.updateInfo(id, displayName, email, phone, role, personalApiKey,
                    rateLimitPerMinute, rateLimitPerHour, dailyApiLimit, monthlyApiLimit);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 잠긴 사용자 수동 해제 (v2.6.0) */
    @PostMapping("/{id}/unlock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unlockUser(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            userService.unlock(id);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 사용자별 IP 화이트리스트 조회 (v2.6.0) */
    @GetMapping("/{id}/ip-whitelist")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getIpWhitelist(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        io.github.claudetoolkit.ui.user.AppUser user = userService.findById(id);
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.ok(resp);
        }
        resp.put("success", true);
        resp.put("username", user.getUsername());
        resp.put("whitelist", user.getIpWhitelist() != null ? user.getIpWhitelist() : "");
        return ResponseEntity.ok(resp);
    }

    /** 사용자별 IP 화이트리스트 저장 (v2.6.0) */
    @PostMapping("/{id}/ip-whitelist")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveIpWhitelist(
            @PathVariable Long id,
            @RequestParam(defaultValue = "") String whitelist) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            userService.updateIpWhitelist(id, whitelist);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }
}
