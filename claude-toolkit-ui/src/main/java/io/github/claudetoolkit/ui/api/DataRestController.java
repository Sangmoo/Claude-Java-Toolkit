package io.github.claudetoolkit.ui.api;

import io.github.claudetoolkit.ui.chat.ChatSessionService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.security.RateLimitService;
import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
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
    private final RateLimitService rateLimitService;
    private final AppUserRepository userRepository;
    private final javax.sql.DataSource dataSource;

    public DataRestController(ChatSessionService chatSessionService,
                              ToolkitSettings toolkitSettings,
                              RateLimitService rateLimitService,
                              AppUserRepository userRepository,
                              javax.sql.DataSource dataSource) {
        this.chatSessionService = chatSessionService;
        this.toolkitSettings = toolkitSettings;
        this.rateLimitService = rateLimitService;
        this.userRepository = userRepository;
        this.dataSource = dataSource;
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
        try {
            Map<String, Integer> stats = rateLimitService.getUsageStats(auth.getName());
            data.put("todayCount", stats.get("today"));
            data.put("monthCount", stats.get("thisMonth"));

            AppUser user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user != null) {
                data.put("dailyLimit", user.getDailyApiLimit());
                data.put("monthlyLimit", user.getMonthlyApiLimit());
                data.put("rateLimitPerMinute", user.getRateLimitPerMinute());
                data.put("rateLimitPerHour", user.getRateLimitPerHour());
            } else {
                data.put("dailyLimit", 0);
                data.put("monthlyLimit", 0);
                data.put("rateLimitPerMinute", 0);
                data.put("rateLimitPerHour", 0);
            }
        } catch (Exception e) {
            data.put("todayCount", 0);
            data.put("monthCount", 0);
            data.put("dailyLimit", 0);
            data.put("monthlyLimit", 0);
        }
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

    /**
     * v4.2.x — 리뷰 이력 기반의 "내게 온 리뷰 / 내가 요청한 리뷰" API.
     *
     * <ul>
     *   <li>tab=received: REVIEWER/ADMIN 은 PENDING 상태의 모든 타인 이력 (본인 작성 제외),
     *       VIEWER 는 본인이 리뷰어로 할당된 항목만 (현재는 빈 리스트 — 할당 개념 없음)</li>
     *   <li>tab=sent: 본인이 작성한 이력 전체</li>
     * </ul>
     */
    @GetMapping("/review-queue")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> reviewQueue(
            @RequestParam(defaultValue = "received") String tab,
            Authentication auth) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            String me = auth != null ? auth.getName() : null;
            if (me == null) return ResponseEntity.ok(ApiResponse.ok(result));

            // 현재 사용자 role 조회
            String role = "VIEWER";
            try {
                Object roleObj = em.createQuery("SELECT u.role FROM AppUser u WHERE u.username = :u")
                        .setParameter("u", me).getSingleResult();
                if (roleObj != null) role = roleObj.toString();
            } catch (Exception ignored) {}

            @SuppressWarnings("unchecked")
            List<io.github.claudetoolkit.ui.history.ReviewHistory> list;
            if ("sent".equals(tab)) {
                // 내가 작성한 이력 — 전부 (상태 무관)
                list = em.createQuery(
                        "SELECT h FROM ReviewHistory h WHERE h.username = :u ORDER BY h.createdAt DESC",
                        io.github.claudetoolkit.ui.history.ReviewHistory.class)
                        .setParameter("u", me)
                        .setMaxResults(100)
                        .getResultList();
            } else {
                // 내게 온 리뷰 (received):
                //   REVIEWER/ADMIN → 타인이 작성한 PENDING 이력 (본인 검토 대상)
                //   VIEWER        → 본인 이력 중 REVIEWER/ADMIN 이 ACCEPTED/REJECTED 한 것 (피드백 확인용)
                if ("REVIEWER".equals(role) || "ADMIN".equals(role)) {
                    list = em.createQuery(
                            "SELECT h FROM ReviewHistory h "
                          + "WHERE h.username <> :u "
                          + "  AND (h.reviewStatus IS NULL OR h.reviewStatus = 'PENDING') "
                          + "ORDER BY h.createdAt DESC",
                            io.github.claudetoolkit.ui.history.ReviewHistory.class)
                            .setParameter("u", me)
                            .setMaxResults(100)
                            .getResultList();
                } else {
                    // VIEWER — 본인 이력 중 검토 완료된 것
                    list = em.createQuery(
                            "SELECT h FROM ReviewHistory h "
                          + "WHERE h.username = :u "
                          + "  AND h.reviewStatus IN ('ACCEPTED','REJECTED') "
                          + "ORDER BY h.reviewedAt DESC",
                            io.github.claudetoolkit.ui.history.ReviewHistory.class)
                            .setParameter("u", me)
                            .setMaxResults(100)
                            .getResultList();
                }
            }

            for (io.github.claudetoolkit.ui.history.ReviewHistory h : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",           h.getId());
                m.put("type",         h.getType());
                m.put("title",        h.getTitle());
                m.put("username",     h.getUsername());
                m.put("createdAt",    h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
                m.put("reviewStatus", h.getReviewStatus());
                m.put("reviewedBy",   h.getReviewedBy());
                m.put("reviewedAt",   h.getReviewedAt() != null ? h.getReviewedAt().toString() : null);
                m.put("reviewNote",   h.getReviewNote());
                result.add(m);
            }
        } catch (Exception e) {
            // Return whatever we have so far
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
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
            data.put("emailHost",     toolkitSettings.getEmail().getHost());
            data.put("emailPort",     toolkitSettings.getEmail().getPort());
            data.put("emailUsername", toolkitSettings.getEmail().getUsername());
            data.put("emailFrom",     toolkitSettings.getEmail().getFrom());
            data.put("emailTls",      toolkitSettings.getEmail().isTls());
            // 비밀번호는 노출 안 함 — 설정되어 있는지 여부만
            data.put("emailPasswordSet", toolkitSettings.getEmail().getPassword() != null
                    && !toolkitSettings.getEmail().getPassword().isEmpty());
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

        // ── 활성 DB 자동 감지 (자동 이관 + 런타임 전환 반영) ──
        // 이전엔 "H2 File" 문자열을 하드코딩했던 자리. 이제 실제 운영 DB 로
        // 부터 메타데이터를 가져온다.
        String dbType = "unknown";
        String dbDisplay = "(미연결)";
        try (java.sql.Connection conn = dataSource.getConnection()) {
            java.sql.DatabaseMetaData md = conn.getMetaData();
            String product = md.getDatabaseProductName();
            String version = md.getDatabaseProductVersion();
            dbType = detectDbType(product);
            data.put("dbType",        dbType);
            data.put("dbProduct",     product);
            data.put("dbVersion",     version);
            data.put("dbUrl",         maskJdbcUrl(md.getURL()));
            data.put("dbUsername",    md.getUserName());
            data.put("dbConnected",   true);

            if ("h2".equals(dbType)) {
                java.io.File h2 = new java.io.File(System.getProperty("user.home") + "/.claude-toolkit/history-db.mv.db");
                if (h2.exists()) {
                    long size = h2.length();
                    dbDisplay = String.format("H2 (%.2f MB)", size / (1024.0 * 1024.0));
                    data.put("dbFilePath", h2.getAbsolutePath());
                    data.put("dbFileBytes", size);
                } else {
                    dbDisplay = "H2 (파일 미생성)";
                }
            } else {
                // 외부 DB — 제품명 + 호스트 표시
                dbDisplay = product + " — " + extractHost(md.getURL());
            }
        } catch (Exception e) {
            data.put("dbConnected", false);
            data.put("dbError",     e.getMessage());
            dbDisplay = "(연결 실패)";
        }
        // 호환성 위해 기존 필드명 유지 + 새 필드 추가
        data.put("dbFileSize",  dbDisplay);
        data.put("diskFreeSpace", new java.io.File("/").getFreeSpace() / 1073741824 + " GB");

        // 자동 이관 후 런타임 오버라이드가 활성 상태인지
        data.put("dbOverrideActive", new java.io.File("data/db-override.properties").exists());

        data.put("apiStatus", "UP");
        try {
            long userCount = ((Number) em.createQuery("SELECT COUNT(u) FROM AppUser u").getSingleResult()).longValue();
            data.put("userCount", userCount);
        } catch (Exception e) { data.put("userCount", 0); }
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private String detectDbType(String product) {
        if (product == null) return "unknown";
        String l = product.toLowerCase();
        if (l.contains("h2"))         return "h2";
        if (l.contains("mysql"))      return "mysql";
        if (l.contains("postgresql")) return "postgresql";
        if (l.contains("postgres"))   return "postgresql";
        if (l.contains("oracle"))     return "oracle";
        return "unknown";
    }

    private String maskJdbcUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("password=([^&;]*)", "password=****")
                  .replaceAll(":[^:@/]+@", ":****@");
    }

    private String extractHost(String url) {
        if (url == null) return "";
        // jdbc:oracle:thin:@HOST:PORT:SID  → HOST:PORT
        // jdbc:oracle:thin:@//HOST:PORT/SVC → HOST:PORT
        // jdbc:mysql://HOST:PORT/DB → HOST:PORT
        // jdbc:postgresql://HOST:PORT/DB → HOST:PORT
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "@/{0,2}([^:/]+:\\d+)").matcher(url);
        return m.find() ? m.group(1) : url;
    }
}
