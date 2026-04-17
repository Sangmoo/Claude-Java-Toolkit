package io.github.claudetoolkit.ui.api;

import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * React 프론트엔드 인증 API.
 *
 * <ul>
 *   <li>GET  /api/v1/auth/me    — 현재 로그인 사용자 정보</li>
 *   <li>POST /api/v1/auth/login — JSON 기반 로그인</li>
 *   <li>POST /api/v1/auth/logout — 세션 무효화</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthRestController {

    private static final Logger log = LoggerFactory.getLogger(AuthRestController.class);

    private final AuthenticationManager authManager;
    private final AppUserRepository userRepository;

    public AuthRestController(AuthenticationManager authManager, AppUserRepository userRepository) {
        this.authManager = authManager;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }
        return ResponseEntity.ok(ApiResponse.ok(buildUserMap(auth)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        // 프론트엔드에서 Base64 인코딩된 비밀번호 디코딩
        if ("true".equals(body.get("encoded")) && !password.isEmpty()) {
            try {
                password = new String(java.util.Base64.getDecoder().decode(password), "UTF-8");
            } catch (Exception e) {
                log.debug("Base64 비밀번호 디코딩 실패 — 원본 사용: user={}", username, e);
            }
        }

        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            SecurityContextHolder.getContext().setAuthentication(auth);

            // 세션 생성
            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT",
                    SecurityContextHolder.getContext());

            // ADMIN 사용자 2FA 체크
            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (isAdmin) {
                session.setAttribute("2fa_pending", true);
                session.setAttribute("2fa_username", username);
                Map<String, Object> data = buildUserMap(auth);
                data.put("require2fa", true);
                return ResponseEntity.ok(ApiResponse.ok(data));
            }

            return ResponseEntity.ok(ApiResponse.ok(buildUserMap(auth)));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("아이디 또는 비밀번호가 올바르지 않습니다."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponse.ok("logged out"));
    }

    /**
     * v4.3.0 — 사용자 선호 언어 저장 (ko/en/ja/zh/de).
     * 프론트엔드 언어 전환 시 호출 — 다음 로그인부터 자동으로 같은 언어 적용 가능.
     */
    @org.springframework.web.bind.annotation.PutMapping("/locale")
    public ResponseEntity<ApiResponse<String>> updateLocale(
            @RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }
        String locale = body != null ? body.get("locale") : null;
        if (locale == null || !locale.matches("^(ko|en|ja|zh|de)$")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("locale 은 ko/en/ja/zh/de 중 하나여야 합니다."));
        }
        try {
            AppUser user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user == null) {
                return ResponseEntity.status(404).body(ApiResponse.error("사용자 정보를 찾을 수 없습니다."));
            }
            user.setLocale(locale);
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.ok(locale));
        } catch (Exception e) {
            log.warn("locale 저장 실패: user={}, locale={}", auth.getName(), locale, e);
            return ResponseEntity.status(500).body(ApiResponse.error("locale 저장 실패: " + e.getMessage()));
        }
    }

    private Map<String, Object> buildUserMap(Authentication auth) {
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("VIEWER");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", auth.getName());
        data.put("role", role);

        // 사용자별 비활성화된 기능 목록 (ADMIN은 제외 — 모든 기능 허용)
        try {
            AppUser user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user != null) {
                if (!"ADMIN".equals(role)) {
                    java.util.List<String> disabled = new java.util.ArrayList<>();
                    // UserPermission 조회는 EntityManager 필요 — 간략화: 클라이언트가 필요시 별도 조회
                    data.put("userId", user.getId());
                    data.put("disabledFeatures", disabled);
                }
                // v4.3.0: 사용자 선호 언어 — 프론트가 로그인 후 동기화
                if (user.getLocale() != null) {
                    data.put("locale", user.getLocale());
                }
            }
        } catch (Exception e) {
            log.warn("사용자 정보 조회 실패: user={}", auth.getName(), e);
        }
        return data;
    }
}
