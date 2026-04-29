package io.github.claudetoolkit.ui.audit;

import io.github.claudetoolkit.ui.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.7.x — Settings 변경 감사 로그 REST API (ADMIN 전용).
 *
 * <p>경로 prefix {@code /api/v1/admin/config-changes} → SecurityConfig 의
 * {@code /api/v1/admin/**} 패턴이 ADMIN 권한을 자동 강제.
 */
@RestController
@RequestMapping("/api/v1/admin/config-changes")
public class ConfigChangeLogController {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeLogController.class);

    private final ConfigChangeLogRepository repo;

    public ConfigChangeLogController(ConfigChangeLogRepository repo) {
        this.repo = repo;
    }

    /**
     * 페이지네이션 + 필터 조회.
     * 파라미터: category, user, from(yyyy-MM-dd), to(yyyy-MM-dd), page, size
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(value = "category", defaultValue = "") String category,
            @RequestParam(value = "user",     defaultValue = "") String user,
            @RequestParam(value = "from",     defaultValue = "") String from,
            @RequestParam(value = "to",       defaultValue = "") String to,
            @RequestParam(value = "page",     defaultValue = "0") int page,
            @RequestParam(value = "size",     defaultValue = "50") int size) {
        try {
            String catFilter  = category.trim().isEmpty() ? null : category.trim();
            String userFilter = user.trim().isEmpty()     ? null : user.trim();
            LocalDateTime sinceDt = parseDateOrNull(from, false);
            LocalDateTime untilDt = parseDateOrNull(to,   true);

            int safePage = Math.max(0, page);
            int safeSize = Math.max(1, Math.min(200, size));

            Page<ConfigChangeLog> p = repo.findFiltered(
                    catFilter, userFilter, sinceDt, untilDt,
                    PageRequest.of(safePage, safeSize));

            List<Map<String, Object>> rows = new ArrayList<>(p.getNumberOfElements());
            for (ConfigChangeLog c : p.getContent()) {
                rows.add(toRow(c));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("page",        p.getNumber());
            data.put("size",        p.getSize());
            data.put("totalPages",  p.getTotalPages());
            data.put("totalElements", p.getTotalElements());
            data.put("entries",     rows);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[ConfigChangeLog] list 실패", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("목록 조회 실패: " + e.getMessage()));
        }
    }

    /** 단일 변경 항목 상세 (모달의 before/after diff 용) */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable("id") long id) {
        try {
            return repo.findById(id)
                    .map(c -> ResponseEntity.ok(ApiResponse.ok(toRow(c))))
                    .orElse(ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("로그를 찾을 수 없습니다: " + id)));
        } catch (Exception e) {
            log.warn("[ConfigChangeLog] detail 실패", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("상세 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 필터 dropdown 메타 — 변경자 / 카테고리 별 카운트.
     * 페이지 진입시 한 번 로드해 dropdown 채움.
     */
    @GetMapping("/meta")
    public ResponseEntity<ApiResponse<Map<String, Object>>> meta() {
        try {
            List<Object[]> userRows = repo.countByUser();
            List<Object[]> catRows  = repo.countByCategory();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("users",      toCountList(userRows, "username"));
            data.put("categories", toCountList(catRows,  "category"));
            data.put("total",      repo.count());
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[ConfigChangeLog] meta 실패", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("메타 조회 실패: " + e.getMessage()));
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Map<String, Object> toRow(ConfigChangeLog c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           c.getId());
        m.put("configKey",    c.getConfigKey());
        m.put("configLabel",  c.getConfigLabel());
        m.put("category",     c.getCategory());
        m.put("oldValue",     c.getOldValue());
        m.put("newValue",     c.getNewValue());
        m.put("sensitive",    c.isSensitive());
        m.put("operation",    c.getOperation());
        m.put("changedBy",    c.getChangedBy());
        m.put("changedAt",    c.getFormattedChangedAt());
        m.put("ipAddress",    c.getIpAddress());
        return m;
    }

    private static List<Map<String, Object>> toCountList(List<Object[]> rows, String labelKey) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(labelKey, r[0] != null ? r[0].toString() : "(empty)");
            m.put("count",  ((Number) r[1]).longValue());
            out.add(m);
        }
        return out;
    }

    private static LocalDateTime parseDateOrNull(String s, boolean endOfDay) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            LocalDate d = LocalDate.parse(s.trim());
            return endOfDay ? d.atTime(23, 59, 59) : d.atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }
}
