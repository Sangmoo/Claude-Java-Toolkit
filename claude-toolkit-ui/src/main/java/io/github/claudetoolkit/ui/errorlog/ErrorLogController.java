package io.github.claudetoolkit.ui.errorlog;

import io.github.claudetoolkit.ui.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.4.0 — 에러 모니터링 REST API (ADMIN 전용 — /api/v1/admin/** 규칙 적용).
 *
 * <ul>
 *   <li>GET    /api/v1/admin/errors                   — 오류 그룹 목록 + 미해결 카운트</li>
 *   <li>GET    /api/v1/admin/errors/{id}              — 개별 그룹 상세 (스택트레이스 포함)</li>
 *   <li>POST   /api/v1/admin/errors/{id}/resolve      — 해결 처리</li>
 *   <li>POST   /api/v1/admin/errors/{id}/unresolve    — 미해결 복귀</li>
 *   <li>DELETE /api/v1/admin/errors/purge?days=30     — 30일 지난 해결 항목 일괄 삭제</li>
 * </ul>
 */
@Tag(name = "Admin", description = "에러 모니터링 (ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/errors")
public class ErrorLogController {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogController.class);

    private final ErrorLogRepository repo;
    private final ErrorLogService service;

    public ErrorLogController(ErrorLogRepository repo, ErrorLogService service) {
        this.repo = repo;
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestParam(value = "unresolvedOnly", defaultValue = "false") boolean unresolvedOnly) {
        try {
            int safeLimit = Math.max(1, Math.min(limit, 500));
            List<ErrorLog> rows = unresolvedOnly
                    ? repo.findUnresolved(PageRequest.of(0, safeLimit))
                    : repo.findRecent(PageRequest.of(0, safeLimit));

            List<Map<String, Object>> items = new ArrayList<>();
            for (ErrorLog e : rows) items.add(toListItem(e));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items",          items);
            data.put("totalCount",     repo.count());
            data.put("unresolvedCount", repo.countByResolvedFalse());
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("오류 로그 조회 실패", e);
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable("id") long id) {
        return repo.findById(id)
                .map(e -> ResponseEntity.ok(ApiResponse.ok(toDetail(e))))
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("Error log not found")));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<String>> resolve(
            @PathVariable("id") long id, Authentication auth) {
        boolean ok = service.markResolved(id, auth != null ? auth.getName() : "system");
        if (!ok) return ResponseEntity.status(404).body(ApiResponse.error("Error log not found"));
        return ResponseEntity.ok(ApiResponse.ok("resolved"));
    }

    @PostMapping("/{id}/unresolve")
    public ResponseEntity<ApiResponse<String>> unresolve(@PathVariable("id") long id) {
        boolean ok = service.markUnresolved(id);
        if (!ok) return ResponseEntity.status(404).body(ApiResponse.error("Error log not found"));
        return ResponseEntity.ok(ApiResponse.ok("unresolved"));
    }

    @DeleteMapping("/purge")
    public ResponseEntity<ApiResponse<Map<String, Object>>> purge(
            @RequestParam(value = "days", defaultValue = "30") int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        int deleted = service.purgeResolvedOlderThan(safeDays);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", deleted);
        data.put("days", safeDays);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── 변환 헬퍼 ───────────────────────────────────────────────────────

    private Map<String, Object> toListItem(ErrorLog e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",              e.getId());
        m.put("level",           e.getLevel());
        m.put("exceptionClass",  e.getExceptionClass());
        m.put("message",         e.getMessage());
        m.put("occurrenceCount", e.getOccurrenceCount());
        m.put("createdAt",       e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        m.put("lastOccurredAt",  e.getLastOccurredAt() != null ? e.getLastOccurredAt().toString() : null);
        m.put("requestPath",     e.getRequestPath());
        m.put("requestMethod",   e.getRequestMethod());
        m.put("username",        e.getUsername());
        m.put("resolved",        e.isResolved());
        m.put("resolvedBy",      e.getResolvedBy());
        m.put("resolvedAt",      e.getResolvedAt() != null ? e.getResolvedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toDetail(ErrorLog e) {
        Map<String, Object> m = toListItem(e);
        m.put("stackTrace", e.getStackTrace());
        m.put("userAgent",  e.getUserAgent());
        m.put("clientIp",   e.getClientIp());
        m.put("dedupeKey",  e.getDedupeKey());
        return m;
    }
}
