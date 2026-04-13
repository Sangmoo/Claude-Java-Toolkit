package io.github.claudetoolkit.ui.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SPA(React) 라우팅 지원 컨트롤러.
 *
 * <p>React SPA의 {@code /app/index.html}을 서빙하여
 * 모든 페이지 경로에서 클라이언트 사이드 라우팅이 동작하도록 합니다.</p>
 *
 * <h3>전략</h3>
 * <ol>
 *   <li>{@code /}, {@code /login} — 명시적 GET 매핑으로 index.html 직접 서빙</li>
 *   <li>기존 컨트롤러가 처리하는 POST/API 요청 — 기존 그대로 동작</li>
 *   <li>매핑 없는 경로(404) → {@link ErrorController}가 index.html 서빙</li>
 *   <li>API 요청(Accept: application/json)의 에러 → JSON 에러 응답</li>
 * </ol>
 */
@Controller
public class SpaForwardController implements ErrorController {

    private volatile String indexHtml;

    /**
     * React SPA index.html 내용을 캐시하여 반환.
     */
    private String getIndexHtml() {
        if (indexHtml == null) {
            try {
                ClassPathResource resource = new ClassPathResource("static/app/index.html");
                try (InputStream is = resource.getInputStream()) {
                    byte[] bytes = new byte[is.available()];
                    is.read(bytes);
                    indexHtml = new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                indexHtml = "<!DOCTYPE html><html><body><h1>React app not built</h1><p>Run: cd frontend && npm run build</p></body></html>";
            }
        }
        return indexHtml;
    }

    @ResponseBody
    private ResponseEntity<String> serveSpa() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(getIndexHtml());
    }

    // ── 명시적 페이지 경로 ──────────────────────────────────────────────

    @GetMapping("/")
    @ResponseBody
    public ResponseEntity<String> home() { return serveSpa(); }

    @GetMapping("/login")
    @ResponseBody
    public ResponseEntity<String> login() { return serveSpa(); }

    @GetMapping("/login/2fa")
    @ResponseBody
    public ResponseEntity<String> login2fa() { return serveSpa(); }

    @GetMapping("/setup")
    @ResponseBody
    public ResponseEntity<String> setup() { return serveSpa(); }

    @GetMapping({"/react", "/react/{path:[^\\.]*}", "/react/**"})
    @ResponseBody
    public ResponseEntity<String> reactLegacy() { return serveSpa(); }

    // ── 에러 핸들러 (404 catch-all) ──────────────────────────────────────

    @RequestMapping("/error")
    @ResponseBody
    public ResponseEntity<?> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = (status instanceof Integer) ? (Integer) status : 500;

        String accept = request.getHeader("Accept");
        boolean wantsHtml = accept == null || accept.contains("text/html");

        if (wantsHtml) {
            // 브라우저 → React SPA 서빙 (React Router가 404 페이지 표시)
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(getIndexHtml());
        }

        // API → JSON 에러 응답
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", statusCode);
        body.put("error", HttpStatus.valueOf(statusCode).getReasonPhrase());
        body.put("path", request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        return ResponseEntity.status(statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
