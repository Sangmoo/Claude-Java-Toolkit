package io.github.claudetoolkit.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.claudetoolkit.ui.controller.SpaForwardController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        return jsonError(ex.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    /**
     * v4.2.7 — 필수 쿼리/폼 파라미터 누락 처리.
     * 예전엔 Exception.class 로 잡혀서 500 으로 내려갔지만 실제론 400 이어야 한다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParam(MissingServletRequestParameterException ex,
                                                 HttpServletRequest request) {
        String message = "필수 파라미터 누락: '" + ex.getParameterName() + "' (" + ex.getParameterType() + ")";
        log.warn("파라미터 누락: {} {} — {}", request.getMethod(), request.getRequestURI(), message);
        return jsonError(message, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * v4.2.7 — 파라미터 타입 불일치 (예: long id 에 문자열 전달). 400 으로 반환.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                 HttpServletRequest request) {
        String typeName = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String message = "파라미터 형식 오류: '" + ex.getName() + "' 은(는) " + typeName + " 타입이어야 합니다.";
        log.warn("타입 불일치: {} {} — {}", request.getMethod(), request.getRequestURI(), message);
        return jsonError(message, HttpStatus.BAD_REQUEST, request);
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

    /**
     * v4.2.7 — 표준화된 에러 응답 shape. 기존 필드(`success`, `error`) 는 그대로 유지하여
     * 프론트 파싱 로직을 깨지 않으면서, 관측성을 위해 추가 필드를 포함한다:
     * <ul>
     *   <li>{@code status} — HTTP 상태 코드 (int)</li>
     *   <li>{@code timestamp} — 서버 시각 (yyyy-MM-dd HH:mm:ss)</li>
     *   <li>{@code path} — 요청 URI</li>
     * </ul>
     *
     * <p>이 shape 는 모든 {@code @ExceptionHandler} 메서드에서 일관되게 사용되므로,
     * 프론트에서 {@code res.error} 외에 타임스탬프/상태 코드를 활용할 수 있다.
     */
    private ResponseEntity<Map<String, Object>> jsonError(String message, HttpStatus status,
                                                           HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("success",   false);
        body.put("status",    status.value());
        body.put("error",     message);
        body.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (request != null) {
            body.put("path",  request.getRequestURI());
        }
        return ResponseEntity.status(status).body(body);
    }

    /** v4.2.7 — 호환용 오버로드 (request 없을 때) */
    private ResponseEntity<Map<String, Object>> jsonError(String message, HttpStatus status) {
        return jsonError(message, status, null);
    }
}
