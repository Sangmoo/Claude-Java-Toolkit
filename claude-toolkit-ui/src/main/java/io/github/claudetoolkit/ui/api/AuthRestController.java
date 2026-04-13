package io.github.claudetoolkit.ui.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * React 프론트엔드 인증 상태 확인 API.
 *
 * <ul>
 *   <li>GET /api/v1/auth/me — 현재 로그인 사용자 정보 반환</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthRestController {

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }

        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("VIEWER");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", auth.getName());
        data.put("role", role);

        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
