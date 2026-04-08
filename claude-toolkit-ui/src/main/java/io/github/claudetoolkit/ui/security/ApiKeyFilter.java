package io.github.claudetoolkit.ui.security;

import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * REST API 키 인증 필터.
 *
 * <ul>
 *   <li>SecuritySettings.apiKeyEnabled = true 일 때만 동작합니다.</li>
 *   <li>요청 헤더 {@code X-Api-Key} 값을 BCrypt 해시와 대조합니다.</li>
 *   <li>정적 리소스(/css, /js, /favicon.svg), 설정·보안 페이지는 면제됩니다.</li>
 *   <li>브라우저에서 직접 접근하는 HTML 페이지 (Accept: text/html)는 면제됩니다.</li>
 * </ul>
 */
@Component
@Order(1)
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        SecuritySettings settings = SecuritySettings.load();

        // API 키 인증이 비활성화된 경우 통과
        if (!settings.isApiKeyEnabled() || settings.getApiKeyHash() == null) {
            chain.doFilter(req, res);
            return;
        }

        // 면제 경로 확인
        String path = req.getRequestURI();
        if (isExemptPath(path)) {
            chain.doFilter(req, res);
            return;
        }

        // 브라우저 HTML 요청 면제 (주소창 직접 접근)
        String accept = req.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            chain.doFilter(req, res);
            return;
        }

        // X-Api-Key 헤더 검증
        String apiKey = req.getHeader("X-Api-Key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            sendUnauthorized(res, "X-Api-Key 헤더가 없습니다.");
            return;
        }

        try {
            if (!ENCODER.matches(apiKey, settings.getApiKeyHash())) {
                sendUnauthorized(res, "API 키가 올바르지 않습니다.");
                return;
            }
        } catch (Exception e) {
            sendUnauthorized(res, "API 키 검증 중 오류가 발생했습니다.");
            return;
        }

        chain.doFilter(req, res);
    }

    private boolean isExemptPath(String path) {
        return path.startsWith("/css/")
            || path.startsWith("/js/")
            || path.startsWith("/favicon")
            || path.startsWith("/settings")
            || path.startsWith("/security")
            || path.startsWith("/actuator");
    }

    private void sendUnauthorized(HttpServletResponse res, String message) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(
            "{\"success\":false,\"status\":401,\"error\":\"" + message + "\"}"
        );
    }
}
