package io.github.claudetoolkit.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 핸들러.
 *
 * <ul>
 *   <li>HTML 요청 → error.html 에러 페이지 렌더링</li>
 *   <li>AJAX/JSON 요청 → {@code {"success":false, "error":"..."}} JSON 응답</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("접근 거부: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        if (isAjax(request)) {
            return jsonError("접근 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        return errorPage(403, "접근 거부", "이 페이지에 접근할 권한이 없습니다.");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("페이지 없음: {} {}", request.getMethod(), request.getRequestURI());
        if (isAjax(request)) {
            return jsonError("요청한 페이지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        return errorPage(404, "페이지 없음", "요청하신 페이지를 찾을 수 없습니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("잘못된 요청: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        if (isAjax(request)) {
            return jsonError(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return errorPage(400, "잘못된 요청", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("서버 오류: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        if (isAjax(request)) {
            return jsonError("서버 오류가 발생했습니다: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return errorPage(500, "서버 오류", "처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean isAjax(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String xhr    = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equals(xhr)
            || (accept != null && accept.contains("application/json"));
    }

    private ResponseEntity<Map<String, Object>> jsonError(String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("success", false);
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }

    private ModelAndView errorPage(int code, String title, String message) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("statusCode", code);
        mav.addObject("errorTitle", title);
        mav.addObject("errorMessage", message);
        mav.setStatus(HttpStatus.valueOf(code));
        return mav;
    }
}
