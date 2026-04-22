package io.github.claudetoolkit.ui.dashboard;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.claudetoolkit.ui.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.3.0 — 사용자별 홈 대시보드 위젯 레이아웃 REST API.
 *
 * <ul>
 *   <li>{@code GET /api/v1/dashboard/layout} — 현재 사용자의 위젯 레이아웃 조회</li>
 *   <li>{@code PUT /api/v1/dashboard/layout} — 전체 레이아웃 일괄 저장 (delete-then-insert)</li>
 * </ul>
 */
@Tag(name = "Dashboard", description = "홈 대시보드 위젯 레이아웃")
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final UserDashboardLayoutRepository repo;

    public DashboardController(UserDashboardLayoutRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/layout")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLayout(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }
        try {
            List<UserDashboardLayout> rows = repo.findByUsername(auth.getName());
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            for (UserDashboardLayout u : rows) out.add(toMap(u));
            return ResponseEntity.ok(ApiResponse.ok(out));
        } catch (Exception e) {
            log.warn("대시보드 레이아웃 조회 실패: user={}", auth.getName(), e);
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/layout")
    @Transactional
    public ResponseEntity<ApiResponse<String>> saveLayout(
            @RequestBody List<Map<String, Object>> body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("body 가 비어있습니다."));
        }
        try {
            String username = auth.getName();
            // delete-then-insert — 단순하지만 안전 (트랜잭션으로 보호)
            repo.deleteByUsername(username);
            for (Map<String, Object> item : body) {
                String widgetKey = strOf(item.get("widgetKey"));
                if (widgetKey == null || widgetKey.isEmpty()) continue;
                UserDashboardLayout u = new UserDashboardLayout();
                u.setUsername(username);
                u.setWidgetKey(widgetKey);
                u.setX(intOf(item.get("x"), 0));
                u.setY(intOf(item.get("y"), 0));
                u.setW(intOf(item.get("w"), 6));
                u.setH(intOf(item.get("h"), 4));
                u.setVisible(boolOf(item.get("visible"), true));
                Object cfg = item.get("configJson");
                if (cfg != null) u.setConfigJson(cfg.toString());
                repo.save(u);
            }
            return ResponseEntity.ok(ApiResponse.ok("saved"));
        } catch (Exception e) {
            log.warn("대시보드 레이아웃 저장 실패: user={}", auth.getName(), e);
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage()));
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private Map<String, Object> toMap(UserDashboardLayout u) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("widgetKey",  u.getWidgetKey());
        m.put("x",          u.getX());
        m.put("y",          u.getY());
        m.put("w",          u.getW());
        m.put("h",          u.getH());
        m.put("visible",    u.isVisible());
        m.put("configJson", u.getConfigJson());
        return m;
    }

    private String strOf(Object o) { return o == null ? null : o.toString(); }
    private int    intOf(Object o, int d)  {
        if (o == null) return d;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return d; }
    }
    private boolean boolOf(Object o, boolean d) {
        if (o == null) return d;
        if (o instanceof Boolean) return (Boolean) o;
        return Boolean.parseBoolean(o.toString());
    }
}
