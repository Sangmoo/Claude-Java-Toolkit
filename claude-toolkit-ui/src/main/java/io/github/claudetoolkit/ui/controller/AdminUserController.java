package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/users";
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
}
