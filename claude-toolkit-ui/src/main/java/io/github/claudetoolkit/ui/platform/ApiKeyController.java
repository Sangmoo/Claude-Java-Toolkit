package io.github.claudetoolkit.ui.platform;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.7.x — #M3 Platform: ADMIN 전용 API Key 관리.
 *
 * <p>SecurityConfig 가 {@code /api/v1/admin/**} 을 ADMIN 권한으로 제한하므로 추가 검증 X.
 *
 * <ul>
 *   <li>POST /api/v1/admin/api-keys — 신규 발급 (응답에 평문 1회 노출)</li>
 *   <li>GET  /api/v1/admin/api-keys — 전체 목록 (대장)</li>
 *   <li>POST /api/v1/admin/api-keys/{id}/revoke — 회수</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/api-keys")
public class ApiKeyController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApiKeyService    service;
    private final ApiKeyRepository repo;

    public ApiKeyController(ApiKeyService service, ApiKeyRepository repo) {
        this.service = service;
        this.repo    = repo;
    }

    /**
     * 신규 키 발급. **응답의 plaintext 는 1회 노출** — 사용자가 즉시 복사해야 함.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> issue(
            @RequestBody IssueRequest req,
            Authentication auth) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (req == null || req.name == null || req.name.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "name 필수");
            return ResponseEntity.badRequest().body(resp);
        }
        String role = (req.role != null && !req.role.isEmpty()) ? req.role : "READ_ONLY";
        Integer rate = req.rateLimitPerMinute != null ? req.rateLimitPerMinute : 60;

        ApiKeyService.IssueResult result = service.issue(
                req.name.trim(),
                auth.getName(),
                role,
                rate,
                req.ttlDays);

        resp.put("success",     true);
        resp.put("plaintext",   result.plaintext);   // ⚠️ 1회 노출
        resp.put("warning",     "이 키는 다시 표시되지 않습니다. 즉시 복사하여 안전한 곳에 보관하세요.");
        resp.put("entity",      toMap(result.entity));
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public Map<String, Object> list() {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<ApiKey> keys = repo.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiKey k : keys) rows.add(toMap(k));
        resp.put("success", true);
        resp.put("keys",    rows);
        return resp;
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revoke(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<>();
        boolean ok = service.revoke(id);
        if (!ok) {
            resp.put("success", false);
            resp.put("error",   "키를 찾을 수 없음: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }
        resp.put("success", true);
        resp.put("message", "회수됨 — 이후 호출은 401");
        return ResponseEntity.ok(resp);
    }

    // ── DTO ─────────────────────────────────────────────────────────

    public static class IssueRequest {
        public String  name;
        public String  role;                  // "READ_ONLY" / "WRITE" / "ADMIN"
        public Integer rateLimitPerMinute;    // 기본 60
        public Integer ttlDays;               // 기본 무기한 (null)
    }

    private static Map<String, Object> toMap(ApiKey k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                  k.getId());
        m.put("name",                k.getName());
        m.put("keyPrefix",           k.getKeyPrefix());
        m.put("createdBy",           k.getCreatedBy());
        m.put("createdAt",           k.getCreatedAt() != null ? k.getCreatedAt().format(TS) : null);
        m.put("expiresAt",           k.getExpiresAt() != null ? k.getExpiresAt().format(TS) : null);
        m.put("lastUsedAt",          k.getLastUsedAt() != null ? k.getLastUsedAt().format(TS) : null);
        m.put("revoked",             k.isRevoked());
        m.put("role",                k.getRole());
        m.put("rateLimitPerMinute",  k.getRateLimitPerMinute());
        m.put("totalCalls",          k.getTotalCalls());
        m.put("usable",              k.isUsable());
        return m;
    }
}
