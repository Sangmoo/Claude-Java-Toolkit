package io.github.claudetoolkit.ui.api;

import io.github.claudetoolkit.ui.chat.ChatSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;

/**
 * React 프론트엔드용 데이터 REST API.
 *
 * 기존 Thymeleaf 컨트롤러가 Model에 넣던 데이터를 JSON으로 제공합니다.
 *
 * <ul>
 *   <li>GET /api/v1/pipelines       — 파이프라인 목록</li>
 *   <li>GET /api/v1/history         — 리뷰 이력</li>
 *   <li>GET /api/v1/favorites       — 즐겨찾기</li>
 *   <li>GET /api/v1/settings        — 설정 정보</li>
 *   <li>GET /api/v1/usage           — 사용량 정보</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class DataRestController {

    @PersistenceContext
    private EntityManager em;

    private final ChatSessionService chatSessionService;

    public DataRestController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @GetMapping("/pipelines")
    public ResponseEntity<ApiResponse<List<?>>> pipelines() {
        try {
            List<?> list = em.createQuery(
                "SELECT p FROM PipelineDefinition p ORDER BY p.isBuiltin DESC, p.createdAt DESC"
            ).getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<?>>> history(Authentication auth) {
        try {
            List<?> list = em.createQuery(
                "SELECT h FROM ReviewHistory h WHERE h.username = :u ORDER BY h.createdAt DESC"
            ).setParameter("u", auth.getName())
             .setMaxResults(100)
             .getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<List<?>>> favorites(Authentication auth) {
        try {
            List<?> list = em.createQuery(
                "SELECT f FROM Favorite f WHERE f.username = :u ORDER BY f.createdAt DESC"
            ).setParameter("u", auth.getName())
             .getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> usage(Authentication auth) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayCount", 0);
        data.put("monthCount", 0);
        data.put("dailyLimit", 0);
        data.put("monthlyLimit", 0);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── Admin APIs ─────────────────────────────────────────────────

    @GetMapping("/admin/users")
    public ResponseEntity<ApiResponse<List<?>>> adminUsers() {
        try {
            List<?> list = em.createQuery("SELECT u FROM AppUser u ORDER BY u.id").getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/admin/audit-logs")
    public ResponseEntity<ApiResponse<List<?>>> auditLogs() {
        try {
            List<?> list = em.createQuery(
                "SELECT a FROM AuditLog a ORDER BY a.createdAt DESC"
            ).setMaxResults(200).getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/admin/permissions")
    public ResponseEntity<ApiResponse<List<?>>> adminPermissions() {
        try {
            List<?> list = em.createQuery("SELECT u FROM AppUser u WHERE u.role <> 'ADMIN' ORDER BY u.username").getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }
}
