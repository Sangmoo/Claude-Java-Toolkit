package io.github.claudetoolkit.ui.security;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 모든 HTTP 요청을 감사 로그에 기록하는 필터.
 *
 * <ul>
 *   <li>정적 리소스(/css, /js, /favicon, /stream)는 기록하지 않습니다.</li>
 *   <li>ApiKeyFilter(Order=1) 다음인 Order=2로 동작합니다.</li>
 *   <li>ContentCachingResponseWrapper로 응답 상태 코드를 캡처합니다.</li>
 * </ul>
 */
@Component
@Order(2)
public class AuditLogFilter extends OncePerRequestFilter {

    private final AuditLogService auditLogService;

    public AuditLogFilter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();

        // 로깅 제외 경로
        if (shouldSkip(path)) {
            chain.doFilter(req, res);
            return;
        }

        ContentCachingResponseWrapper wrappedRes = new ContentCachingResponseWrapper(res);
        try {
            chain.doFilter(req, wrappedRes);
        } finally {
            String ip         = resolveClientIp(req);
            String userAgent  = req.getHeader("User-Agent");
            boolean apiKeyUsed= req.getHeader("X-Api-Key") != null;
            int status        = wrappedRes.getStatus();

            auditLogService.log(path, req.getMethod(), ip, userAgent, status, apiKeyUsed);
            wrappedRes.copyBodyToResponse();
        }
    }

    private boolean shouldSkip(String path) {
        return path.startsWith("/css/")
            || path.startsWith("/js/")
            || path.startsWith("/favicon")
            || path.contains("/stream/")        // SSE 스트리밍 (장기 연결) — /stream/, /workspace/stream/, /compare-stream/ 등
            || path.startsWith("/actuator");
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            // X-Forwarded-For: client, proxy1, proxy2
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return req.getRemoteAddr();
    }
}
