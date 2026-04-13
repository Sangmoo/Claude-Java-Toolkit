package io.github.claudetoolkit.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 핸들러 (React SPA 전환 v4.0).
 *
 * <ul>
 *   <li>브라우저 요청(Accept: text/html) → React SPA index.html 서빙</li>
 *   <li>API 요청(Accept: application/json) → JSON 에러 응답</li>
 * </ul>
 *
 * <p>기존 @Controller의 GET 매핑 제거 후 발생하는 405(Method Not Allowed),
 * 404(Not Found) 등을 React SPA로 포워딩하여 클라이언트 사이드 라우팅이 동작합니다.</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private volatile String indexHtml;

    private String getIndexHtml() {
        if (indexHtml == null) {
            try {
                ClassPathResource resource = new ClassPathResource("static/index.html");
                try (InputStream is = resource.getInputStream()) {
                    byte[] bytes = new byte[is.available()];
                    is.read(bytes);
                    indexHtml = new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                indexHtml = "<!DOCTYPE html><html><body><h1>Error</h1></body></html>";
            }
        }
        return indexHtml;
    }

    /**
     * 405 Method Not Allowed — 기존 @Controller에 GET 없이 POST만 있을 때 발생.
     * 브라우저 요청은 React SPA로 서빙 (React Router가 해당 경로 처리).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                    HttpServletRequest request) {
        if (isApiRequest(request)) {
            log.warn("API 405: {} {}", request.getMethod(), request.getRequestURI());
            return jsonError("Method Not Allowed", HttpStatus.METHOD_NOT_ALLOWED);
        }
        // 브라우저 GET → React SPA 서빙
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(getIndexHtml());
    }

    /**
     * 404 Not Found.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> handleNotFound(NoHandlerFoundException ex,
                                            HttpServletRequest request) {
        if (isApiRequest(request)) {
            return jsonError("Not Found", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(getIndexHtml());
    }

    /**
     * 403 Access Denied.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex,
                                                HttpServletRequest request) {
        log.warn("접근 거부: {} {}", request.getMethod(), request.getRequestURI());
        if (isApiRequest(request)) {
            return jsonError("접근 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(getIndexHtml());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException ex,
                                              HttpServletRequest request) {
        log.warn("잘못된 요청: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return jsonError(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("서버 오류: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            return jsonError("서버 오류: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(getIndexHtml());
    }

    // ── helpers ──

    private boolean isApiRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String xhr = request.getHeader("X-Requested-With");
        String uri = request.getRequestURI();
        // API 경로 또는 JSON 요청
        return uri.startsWith("/api/")
            || uri.startsWith("/stream/")
            || "XMLHttpRequest".equals(xhr)
            || (accept != null && accept.contains("application/json") && !accept.contains("text/html"));
    }

    private ResponseEntity<Map<String, Object>> jsonError(String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("success", false);
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
