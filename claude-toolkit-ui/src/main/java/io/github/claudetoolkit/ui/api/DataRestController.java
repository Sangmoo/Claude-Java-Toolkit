package io.github.claudetoolkit.ui.api;

import io.github.claudetoolkit.ui.chat.ChatSessionService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
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
    private final ToolkitSettings toolkitSettings;

    public DataRestController(ChatSessionService chatSessionService, ToolkitSettings toolkitSettings) {
        this.chatSessionService = chatSessionService;
        this.toolkitSettings = toolkitSettings;
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

    @GetMapping("/pipelines/executions")
    public ResponseEntity<ApiResponse<List<?>>> pipelineExecutions(Authentication auth) {
        try {
            List<?> list = em.createQuery(
                "SELECT e FROM PipelineExecution e WHERE e.username = :u ORDER BY e.startedAt DESC"
            ).setParameter("u", auth.getName()).setMaxResults(50).getResultList();
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

    // ── 추가 데이터 APIs ─────────────────────────────────────────────

    @GetMapping("/review-requests")
    public ResponseEntity<ApiResponse<List<?>>> reviewRequests(Authentication auth) {
        try {
            List<?> list = em.createQuery(
                "SELECT r FROM ReviewRequest r ORDER BY r.createdAt DESC"
            ).setMaxResults(100).getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/schedule")
    public ResponseEntity<ApiResponse<List<?>>> schedule() {
        try {
            List<?> list = em.createQuery(
                "SELECT p FROM PipelineDefinition p WHERE p.scheduleCron IS NOT NULL ORDER BY p.name"
            ).getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/roi-report")
    public ResponseEntity<ApiResponse<Map<String, Object>>> roiReport() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            long totalAnalysis = ((Number) em.createQuery("SELECT COUNT(h) FROM ReviewHistory h").getSingleResult()).longValue();
            long totalChat = ((Number) em.createQuery("SELECT COUNT(m) FROM ChatMessage m").getSingleResult()).longValue();
            data.put("totalAnalysis", totalAnalysis);
            data.put("totalChat", totalChat);
            data.put("estimatedHoursSaved", totalAnalysis * 0.5 + totalChat * 0.1);
        } catch (Exception e) {
            data.put("totalAnalysis", 0);
            data.put("totalChat", 0);
            data.put("estimatedHoursSaved", 0);
        }
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/prompts")
    public ResponseEntity<ApiResponse<List<?>>> prompts() {
        try {
            List<?> list = em.createQuery("SELECT p FROM CustomPrompt p ORDER BY p.category, p.name").getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/settings/prompts")
    public ResponseEntity<ApiResponse<List<?>>> settingsPrompts() {
        return prompts(); // 동일 데이터
    }

    @GetMapping("/settings/shared")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sharedConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("info", "팀 설정 공유 기능 — Settings에서 내보내기/가져오기 가능");
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<?>>> search(@RequestParam(defaultValue = "") String q, Authentication auth) {
        if (q.trim().isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
        try {
            List<?> list = em.createQuery(
                "SELECT h FROM ReviewHistory h WHERE h.username = :u AND " +
                "(LOWER(h.menuName) LIKE :q OR LOWER(h.inputText) LIKE :q) ORDER BY h.createdAt DESC"
            ).setParameter("u", auth.getName())
             .setParameter("q", "%" + q.toLowerCase() + "%")
             .setMaxResults(50).getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/admin/team-dashboard")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> teamDashboard() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<?> users = em.createQuery("SELECT u FROM AppUser u ORDER BY u.username").getResultList();
            // 간략한 통계 반환
            for (Object u : users) {
                Map<String, Object> stat = new LinkedHashMap<>();
                try {
                    java.lang.reflect.Method getName = u.getClass().getMethod("getUsername");
                    String username = (String) getName.invoke(u);
                    stat.put("username", username);
                    long count = ((Number) em.createQuery("SELECT COUNT(h) FROM ReviewHistory h WHERE h.username = :u")
                        .setParameter("u", username).getSingleResult()).longValue();
                    stat.put("analysisCount", count);
                    long chatCount = ((Number) em.createQuery("SELECT COUNT(s) FROM ChatSession s WHERE s.username = :u")
                        .setParameter("u", username).getSingleResult()).longValue();
                    stat.put("chatCount", chatCount);
                    result.add(stat);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * ToolkitSettings 전체 값 (마스킹된 비밀번호/API키)
     * React Settings 페이지 초기 로드용.
     */
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> settingsData() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("dbUrl", toolkitSettings.getDb().getUrl());
            data.put("dbUsername", toolkitSettings.getDb().getUsername());
            data.put("scanPath", toolkitSettings.getProject().getScanPath());
            data.put("projectContext", toolkitSettings.getProjectContext());
            data.put("claudeModel", toolkitSettings.getClaudeModel());
            data.put("accentColor", toolkitSettings.getAccentColor());
            data.put("slackWebhookUrl", toolkitSettings.getSlackWebhookUrl());
            data.put("teamsWebhookUrl", toolkitSettings.getTeamsWebhookUrl());
            data.put("jiraBaseUrl", toolkitSettings.getJiraBaseUrl());
            data.put("jiraProjectKey", toolkitSettings.getJiraProjectKey());
            data.put("jiraEmail", toolkitSettings.getJiraEmail());
            data.put("emailHost", toolkitSettings.getEmail().getHost());
            data.put("emailFrom", toolkitSettings.getEmail().getFrom());
        } catch (Exception ignored) {}
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /**
     * 현재 로그인 사용자의 비활성화된 기능 목록.
     */
    @GetMapping("/auth/my-permissions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myPermissions(Authentication auth) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<String> disabled = new ArrayList<>();
        try {
            List<?> perms = em.createQuery(
                "SELECT p.featureKey FROM UserPermission p WHERE p.userId = " +
                "(SELECT u.id FROM AppUser u WHERE u.username = :u) AND p.allowed = false"
            ).setParameter("u", auth.getName()).getResultList();
            for (Object k : perms) disabled.add(String.valueOf(k));
        } catch (Exception ignored) {}
        data.put("disabledFeatures", disabled);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/db-profiles")
    public ResponseEntity<ApiResponse<List<?>>> dbProfiles() {
        try {
            List<?> list = em.createQuery("SELECT p FROM DbProfile p ORDER BY p.id").getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/explain/dashboard")
    public ResponseEntity<ApiResponse<List<?>>> explainDashboard() {
        try {
            List<?> list = em.createQuery(
                "SELECT h FROM ReviewHistory h WHERE h.menuName LIKE '%실행계획%' OR h.menuName LIKE '%EXPLAIN%' ORDER BY h.createdAt DESC"
            ).setMaxResults(30).getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/harness/dashboard")
    public ResponseEntity<ApiResponse<List<?>>> harnessDashboard() {
        try {
            List<?> list = em.createQuery(
                "SELECT h FROM ReviewHistory h WHERE h.menuName LIKE '%하네스%' OR h.menuName LIKE '%HARNESS%' OR h.menuName LIKE '%코드리뷰%' ORDER BY h.createdAt DESC"
            ).setMaxResults(30).getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/admin/health/data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> systemHealth() {
        Map<String, Object> data = new LinkedHashMap<>();
        Runtime rt = Runtime.getRuntime();
        data.put("jvmHeapUsed", (rt.totalMemory() - rt.freeMemory()) / 1048576 + " MB");
        data.put("jvmHeapMax", rt.maxMemory() / 1048576 + " MB");
        data.put("heapUsagePercent", (int) ((rt.totalMemory() - rt.freeMemory()) * 100 / rt.maxMemory()));
        data.put("uptime", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 60000 + " min");
        data.put("threadCount", Thread.activeCount());
        data.put("javaVersion", System.getProperty("java.version"));
        data.put("osName", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        data.put("dbFileSize", "H2 File");
        data.put("diskFreeSpace", new java.io.File("/").getFreeSpace() / 1073741824 + " GB");
        data.put("apiStatus", "UP");
        try {
            long userCount = ((Number) em.createQuery("SELECT COUNT(u) FROM AppUser u").getSingleResult()).longValue();
            data.put("userCount", userCount);
        } catch (Exception e) { data.put("userCount", 0); }
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
