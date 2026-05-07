package io.github.claudetoolkit.ui.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v4.7.x — #M3 Platform: X-Api-Key 헤더 기반 멀티 키 인증 필터.
 *
 * <p>{@code /api/v1/**} 경로 진입 시:
 * <ul>
 *   <li>이미 *세션 인증* 된 사용자 → 그대로 통과 (브라우저 사용자)</li>
 *   <li>X-Api-Key 헤더 있음 → 검증 + SecurityContext set (외부 client)</li>
 *   <li>둘 다 없음 → 401 + JSON</li>
 * </ul>
 *
 * <p>분당 호출 한도 (rate limit) 도 키별로 적용. sliding window — JVM in-memory.
 */
@Component
public class PlatformAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuthFilter.class);

    /** 인증 객체에 부착할 marker — 다른 endpoint 에서 *어떻게* 인증됐는지 식별 가능 */
    public static final String ATTR_API_KEY = "platform.apiKey";

    private final ApiKeyService service;

    /** 키별 분당 호출 횟수 sliding window — 단순 list of timestamps */
    private final ConcurrentHashMap<Long, java.util.Deque<Long>> windows = new ConcurrentHashMap<>();

    public PlatformAuthFilter(ApiKeyService service) {
        this.service = service;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String path = req.getRequestURI();
        // /api/v1/** 만 적용. 그 외 경로는 SecurityConfig 기본 정책에 위임
        return !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        // 이미 세션 인증된 사용자는 통과 (브라우저 ↔ 도구 직접 사용)
        SecurityContext ctx = SecurityContextHolder.getContext();
        if (ctx.getAuthentication() != null
                && ctx.getAuthentication().isAuthenticated()
                && !"anonymousUser".equals(ctx.getAuthentication().getPrincipal())) {
            chain.doFilter(req, res);
            return;
        }

        String apiKey = req.getHeader("X-Api-Key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            // 키 없음 — Spring Security 가 401 또는 redirect 처리하도록 통과
            chain.doFilter(req, res);
            return;
        }

        Optional<ApiKey> verified = service.verify(apiKey.trim());
        if (!verified.isPresent()) {
            sendUnauthorized(res, "유효하지 않거나 만료/회수된 API key");
            return;
        }
        ApiKey k = verified.get();

        // Rate limit — 분당 호출 한도
        if (!checkRateLimit(k)) {
            sendTooManyRequests(res, k.getRateLimitPerMinute());
            return;
        }

        // SecurityContext 에 인증 객체 set (issuer 의 username 으로 인증)
        // role 은 키의 role + ROLE_USER (기본)
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_USER")
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(k.getCreatedBy(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 사용 기록 (비동기 옵션 가능 — 일단 동기)
        service.touchUsage(k.getId());

        // 다른 controller 에서 *어떤 키* 인지 알 수 있게 request attribute 에 부착
        req.setAttribute(ATTR_API_KEY, k);

        chain.doFilter(req, res);
    }

    /**
     * 키별 분당 호출 한도. simple sliding window — 마지막 60초 내 호출 횟수 카운트.
     * limit <= 0 이면 제한 없음.
     */
    private boolean checkRateLimit(ApiKey k) {
        Integer limit = k.getRateLimitPerMinute();
        if (limit == null || limit <= 0) return true;

        long now = System.currentTimeMillis();
        java.util.Deque<Long> window = windows.computeIfAbsent(k.getId(), x -> new java.util.ArrayDeque<>());
        synchronized (window) {
            // 60초 이상 된 timestamp 제거
            while (!window.isEmpty() && now - window.peekFirst() > 60_000L) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    private static void sendUnauthorized(HttpServletResponse res, String msg) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(
            "{\"success\":false,\"status\":401,\"error\":\"" + escape(msg) + "\"}"
        );
    }

    private static void sendTooManyRequests(HttpServletResponse res, int limit) throws IOException {
        res.setStatus(429);
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Retry-After", "60");
        res.getWriter().write(
            "{\"success\":false,\"status\":429,\"error\":\"분당 호출 한도 (" + limit + ") 초과 — 60초 후 재시도\"}"
        );
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
