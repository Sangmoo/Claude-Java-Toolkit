package io.github.claudetoolkit.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.claudetoolkit.ui.controller.SpaForwardController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
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
    private ResponseEntity<String> serveSpa() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(SpaForwardController.getIndexHtml());
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
        return serveSpa();
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
        return serveSpa();
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
        return serveSpa();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException ex,
                                              HttpServletRequest request) {
        log.warn("잘못된 요청: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return jsonError(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex, HttpServletRequest request) {
        // ── 클라이언트 disconnect (Broken pipe) 는 정상 동작의 일부 ──
        // SSE 스트리밍 중 사용자가 페이지 이탈/탭 종료/네트워크 끊김으로
        // 소켓이 닫히면 다음 chunk write 가 실패하면서 ClientAbortException /
        // IOException("Broken pipe") 이 발생. 이는 오류가 아니라 정상적인 종료
        // 시그널이므로 ERROR 가 아니라 DEBUG 로 낮추고, 응답 본문 쓰기도 건너뛴다
        // (소켓이 이미 닫혀 있어 본문 쓰기도 또 다시 실패하기 때문 — 'Failure in
        // @ExceptionHandler' 2차 WARN 발생 원인).
        if (isClientDisconnect(ex)) {
            log.debug("[SSE] 클라이언트 연결 종료: {} {}", request.getMethod(), request.getRequestURI());
            // null 반환 시 Spring 은 추가 응답을 쓰지 않음 (이미 응답 헤더가 commit 되어
            // 있을 수도 있고, 어차피 소켓이 닫혀 있어 쓰기가 실패하므로 안전).
            return null;
        }

        log.error("서버 오류: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            return jsonError("서버 오류: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return serveSpa();
    }

    /**
     * 클라이언트 disconnect 로 인한 예외인지 판정.
     * - org.apache.catalina.connector.ClientAbortException
     * - java.io.IOException("Broken pipe") / IOException("Connection reset")
     * - cause chain 의 어느 곳에라도 위 두 가지가 있으면 true
     */
    private boolean isClientDisconnect(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            String name = cur.getClass().getName();
            if (name.equals("org.apache.catalina.connector.ClientAbortException")) return true;
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("broken pipe")
                        || lower.contains("connection reset")
                        || lower.contains("connection was aborted")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
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
