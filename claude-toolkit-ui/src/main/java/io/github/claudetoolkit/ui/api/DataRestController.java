package io.github.claudetoolkit.ui.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.claudetoolkit.ui.chat.ChatSessionService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.security.RateLimitService;
import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Tag(name = "Data", description = "파이프라인/이력/즐겨찾기/팀활동/검색 + 관리자 통계")
@RestController
@RequestMapping("/api/v1")
public class DataRestController {

    private static final Logger log = LoggerFactory.getLogger(DataRestController.class);

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
            log.warn("파이프라인 목록 조회 실패", e);
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
            log.warn("파이프라인 실행 이력 조회 실패: user={}", auth.getName(), e);
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    /**
     * v4.2.7 — 이력 목록 조회. 페이징 지원.
     *
     * <p>기존 프론트 호환: `?page`/`?size` 생략시 첫 200건 반환 (기존엔 100건).
     * 새 클라이언트는 `?page=0&size=20` 형태로 요청하고 `X-Total-Count` 헤더와
     * `hasMore` 파생 필드로 다음 페이지 존재 여부를 알 수 있다.
     *
     * <p>프론트가 이전 "전체 배열" 응답 포맷을 그대로 사용하므로 data 는 계속
     * List 이며, 메타 정보는 HTTP 헤더로 전달 (응답 스키마 변경 최소화).
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<?>>> history(
            Authentication auth,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size,
            @org.springframework.web.bind.annotation.RequestParam(value = "tag",  required = false) String  tag) {
        try {
            int effectiveSize = (size != null && size > 0) ? Math.min(size, 500) : 200;
            int effectivePage = (page != null && page >= 0) ? page : 0;
            int offset = effectivePage * effectiveSize;

            // v4.7.x: tag 필터가 있으면 콤마 구분 컬럼에서 정확 매칭 (CONCAT(',', tags, ',') LIKE '%,tag,%')
            String trimmedTag = (tag != null) ? tag.trim() : "";
            boolean filterByTag = !trimmedTag.isEmpty();

            String countJpql, listJpql;
            if (filterByTag) {
                countJpql = "SELECT COUNT(h) FROM ReviewHistory h WHERE h.username = :u " +
                        "AND LOWER(CONCAT(',', COALESCE(h.tags, ''), ',')) LIKE LOWER(CONCAT('%,', :tag, ',%'))";
                listJpql  = "SELECT h FROM ReviewHistory h WHERE h.username = :u " +
                        "AND LOWER(CONCAT(',', COALESCE(h.tags, ''), ',')) LIKE LOWER(CONCAT('%,', :tag, ',%')) " +
                        "ORDER BY h.createdAt DESC";
            } else {
                countJpql = "SELECT COUNT(h) FROM ReviewHistory h WHERE h.username = :u";
                listJpql  = "SELECT h FROM ReviewHistory h WHERE h.username = :u ORDER BY h.createdAt DESC";
            }

            javax.persistence.Query countQ = em.createQuery(countJpql).setParameter("u", auth.getName());
            javax.persistence.Query listQ  = em.createQuery(listJpql).setParameter("u", auth.getName());
            if (filterByTag) {
                countQ.setParameter("tag", trimmedTag);
                listQ.setParameter("tag", trimmedTag);
            }
            long total = ((Number) countQ.getSingleResult()).longValue();

            List<?> list = listQ
             .setFirstResult(offset)
             .setMaxResults(effectiveSize)
             .getResultList();
            boolean hasMore = (offset + list.size()) < total;
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(total))
                    .header("X-Has-More",   String.valueOf(hasMore))
                    .header("X-Page",       String.valueOf(effectivePage))
                    .header("X-Page-Size",  String.valueOf(effectiveSize))
                    .body(ApiResponse.ok(list));
        } catch (Exception e) {
            log.warn("이력 목록 조회 실패: user={}", auth.getName(), e);
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<List<?>>> favorites(
            Authentication auth,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", required = false) Integer size) {
        try {
            int effectiveSize = (size != null && size > 0) ? Math.min(size, 500) : 500;
            int effectivePage = (page != null && page >= 0) ? page : 0;
            int offset = effectivePage * effectiveSize;

            long total = ((Number) em.createQuery(
                "SELECT COUNT(f) FROM Favorite f WHERE f.username = :u"
            ).setParameter("u", auth.getName()).getSingleResult()).longValue();

            List<?> list = em.createQuery(
                "SELECT f FROM Favorite f WHERE f.username = :u ORDER BY f.createdAt DESC"
            ).setParameter("u", auth.getName())
             .setFirstResult(offset)
             .setMaxResults(effectiveSize)
             .getResultList();
            boolean hasMore = (offset + list.size()) < total;
            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(total))
                    .header("X-Has-More",   String.valueOf(hasMore))
                    .header("X-Page",       String.valueOf(effectivePage))
                    .header("X-Page-Size",  String.valueOf(effectiveSize))
                    .body(ApiResponse.ok(list));
        } catch (Exception e) {
            log.warn("즐겨찾기 목록 조회 실패: user={}", auth.getName(), e);
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
            log.warn("사용량 조회 실패: user={}", auth.getName(), e);
            data.put("todayCount", 0);
            data.put("monthCount", 0);
            data.put("dailyLimit", 0);
            data.put("monthlyLimit", 0);
        }
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── Endpoint usage stats (v4.2.8 — B2) ────────────────────────
    //
    // AuditLog 테이블을 집계해서 기간별 엔드포인트 사용 통계를 반환.
    // /api/v1/admin/** 은 ADMIN 만 접근 가능 (SecurityConfig).
    @GetMapping("/admin/endpoint-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> endpointStats(
            @org.springframework.web.bind.annotation.RequestParam(value = "days", defaultValue = "7") int days) {
        try {
            int effectiveDays = Math.max(1, Math.min(days, 365));
            java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(effectiveDays);

            // Top endpoints (by count)
            @SuppressWarnings("unchecked")
            List<Object[]> topEndpoints = em.createQuery(
                "SELECT a.endpoint, COUNT(a) FROM AuditLog a " +
                "WHERE a.createdAt >= :since " +
                "GROUP BY a.endpoint " +
                "ORDER BY COUNT(a) DESC"
            ).setParameter("since", since).setMaxResults(30).getResultList();

            List<Map<String, Object>> endpointRows = new ArrayList<Map<String, Object>>();
            for (Object[] row : topEndpoints) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("endpoint", row[0]);
                m.put("count",    ((Number) row[1]).longValue());
                endpointRows.add(m);
            }

            // Top users (by count)
            @SuppressWarnings("unchecked")
            List<Object[]> topUsers = em.createQuery(
                "SELECT a.username, COUNT(a) FROM AuditLog a " +
                "WHERE a.createdAt >= :since AND a.username IS NOT NULL " +
                "GROUP BY a.username " +
                "ORDER BY COUNT(a) DESC"
            ).setParameter("since", since).setMaxResults(15).getResultList();

            List<Map<String, Object>> userRows = new ArrayList<Map<String, Object>>();
            for (Object[] row : topUsers) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("username", row[0]);
                m.put("count",    ((Number) row[1]).longValue());
                userRows.add(m);
            }

            // Status code breakdown
            @SuppressWarnings("unchecked")
            List<Object[]> statusBreakdown = em.createQuery(
                "SELECT a.statusCode, COUNT(a) FROM AuditLog a " +
                "WHERE a.createdAt >= :since " +
                "GROUP BY a.statusCode " +
                "ORDER BY a.statusCode"
            ).setParameter("since", since).getResultList();

            List<Map<String, Object>> statusRows = new ArrayList<Map<String, Object>>();
            for (Object[] row : statusBreakdown) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("status", row[0] != null ? row[0] : 0);
                m.put("count",  ((Number) row[1]).longValue());
                statusRows.add(m);
            }

            // Daily trend — v4.4.x: DB 중립 방식 (Java 측에서 그룹화)
            // 이전 H2 의존 FORMATDATETIME() 호출 → MySQL/PostgreSQL/Oracle 에서
            // QueryException 발생. 이제 createdAt 만 가져와서 LocalDate 로 변환 후 집계.
            @SuppressWarnings("unchecked")
            List<java.time.LocalDateTime> dailyRaw = em.createQuery(
                "SELECT a.createdAt FROM AuditLog a " +
                "WHERE a.createdAt >= :since ORDER BY a.createdAt ASC"
            ).setParameter("since", since).getResultList();

            // LocalDate (yyyy-MM-dd) 별 카운트 — TreeMap 으로 자동 정렬
            java.util.TreeMap<String, Long> dailyMap = new java.util.TreeMap<String, Long>();
            // 기간 내 모든 날짜를 0으로 사전 채움 (빈 날짜도 차트에 표시)
            for (int d = 0; d < effectiveDays; d++) {
                String key = since.toLocalDate().plusDays(d).toString();
                dailyMap.put(key, 0L);
            }
            for (java.time.LocalDateTime ts : dailyRaw) {
                if (ts == null) continue;
                String key = ts.toLocalDate().toString();
                dailyMap.merge(key, 1L, Long::sum);
            }

            List<Map<String, Object>> dailyRows = new ArrayList<Map<String, Object>>();
            for (java.util.Map.Entry<String, Long> e : dailyMap.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("date",  e.getKey());
                m.put("count", e.getValue());
                dailyRows.add(m);
            }

            long total = ((Number) em.createQuery(
                "SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt >= :since"
            ).setParameter("since", since).getSingleResult()).longValue();

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("days",          effectiveDays);
            data.put("total",         total);
            data.put("topEndpoints",  endpointRows);
            data.put("topUsers",      userRows);
            data.put("statusCodes",   statusRows);
            data.put("dailyTrend",    dailyRows);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("엔드포인트 통계 조회 실패: days={}", days, e);
            Map<String, Object> empty = new LinkedHashMap<String, Object>();
            empty.put("error", e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(empty));
        }
    }

    // ── Team activity feed ─────────────────────────────────────────
    //
    // v4.2.8: 홈 대시보드에 표시할 팀 전체 최근 활동 피드.
    // - 본인 이외의 사용자들이 최근 만든 이력 목록
    // - 제한된 필드만 반환 (outputContent 제외 — 페이로드 최소화)
    // - 최대 20건
    @GetMapping("/team-activity")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> teamActivity(Authentication auth) {
        try {
            String currentUser = auth != null ? auth.getName() : null;
            @SuppressWarnings("unchecked")
            List<io.github.claudetoolkit.ui.history.ReviewHistory> list =
                (List<io.github.claudetoolkit.ui.history.ReviewHistory>) em.createQuery(
                    "SELECT h FROM ReviewHistory h " +
                    "WHERE h.username IS NOT NULL AND h.username <> '' AND h.username <> :me " +
                    "ORDER BY h.createdAt DESC"
                ).setParameter("me", currentUser != null ? currentUser : "")
                 .setMaxResults(20)
                 .getResultList();

            List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
            for (io.github.claudetoolkit.ui.history.ReviewHistory h : list) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id",           h.getId());
                m.put("type",         h.getType());
                m.put("title",        h.getTitle());
                m.put("username",     h.getUsername());
                m.put("reviewStatus", h.getReviewStatus());
                m.put("createdAt",    h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
                result.add(m);
            }
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.warn("팀 활동 피드 조회 실패", e);
            return ResponseEntity.ok(ApiResponse.ok(Collections.<Map<String, Object>>emptyList()));
        }
    }

    // ── Mention candidates ─────────────────────────────────────────
    //
    // v4.2.7: 댓글 @멘션 자동완성용. 로그인된 모든 사용자가 호출 가능하며
    // 민감 정보는 제외하고 username / displayName / role / enabled 만 반환한다.
    @GetMapping("/users/mentions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> mentionCandidates() {
        try {
            List<AppUser> users = em.createQuery(
                "SELECT u FROM AppUser u WHERE u.enabled = true ORDER BY u.username",
                AppUser.class
            ).getResultList();
            List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
            for (AppUser u : users) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("username",    u.getUsername());
                m.put("displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
                m.put("role",        u.getRole() != null ? u.getRole() : "VIEWER");
                list.add(m);
            }
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            log.warn("멘션 후보 조회 실패", e);
            return ResponseEntity.ok(ApiResponse.ok(Collections.<Map<String, Object>>emptyList()));
        }
    }

    // ── Admin APIs ─────────────────────────────────────────────────

    @GetMapping("/admin/users")
    public ResponseEntity<ApiResponse<List<?>>> adminUsers() {
        try {
            List<?> list = em.createQuery("SELECT u FROM AppUser u ORDER BY u.id").getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            log.warn("관리자 사용자 목록 조회 실패", e);
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
            log.warn("감사 로그 조회 실패", e);
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    @GetMapping("/admin/permissions")
    public ResponseEntity<ApiResponse<List<?>>> adminPermissions() {
        try {
            List<?> list = em.createQuery("SELECT u FROM AppUser u WHERE u.role <> 'ADMIN' ORDER BY u.username").getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            log.warn("권한 목록 조회 실패", e);
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
            log.warn("리뷰 요청 목록 조회 실패", e);
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    /**
     * v4.2.6 — 리뷰 이력 대시보드 데이터.
     *
     * <p>요청 파라미터:
     * <ul>
     *   <li>{@code days} (선택, 기본 7): 1/3/7/30 일 프리셋. {@code from/to} 가 있으면 무시됨.</li>
     *   <li>{@code from} (선택): 시작일 (yyyy-MM-dd)</li>
     *   <li>{@code to} (선택): 종료일 (yyyy-MM-dd)</li>
     * </ul>
     *
     * <p>응답:
     * <pre>{
     *   totalCount, pendingCount, acceptedCount, rejectedCount,
     *   pendingPercent, acceptedPercent, rejectedPercent,
     *   dailyTrend: [{date, pending, accepted, rejected}, ...],
     *   byType:     [{type, pending, accepted, rejected}, ...],
     *   byReviewer: [{username, accepted, rejected, total}, ...],
     *   from, to, days
     * }</pre>
     */
    @GetMapping("/admin/review-dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewDashboard(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            // ── 기간 결정 ─────────────────────────────────────────
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate fromDate;
            java.time.LocalDate toDate;
            if (from != null && !from.isEmpty()) {
                fromDate = java.time.LocalDate.parse(from);
                toDate   = (to != null && !to.isEmpty()) ? java.time.LocalDate.parse(to) : today;
            } else {
                int d = (days != null && days > 0) ? days : 7;
                toDate   = today;
                fromDate = today.minusDays(d - 1L);
            }
            java.time.LocalDateTime fromDt = fromDate.atStartOfDay();
            java.time.LocalDateTime toDt   = toDate.plusDays(1).atStartOfDay();

            // ── 기간 내 이력 조회 ─────────────────────────────────
            @SuppressWarnings("unchecked")
            List<io.github.claudetoolkit.ui.history.ReviewHistory> list = em.createQuery(
                    "SELECT h FROM ReviewHistory h "
                  + "WHERE h.createdAt >= :from AND h.createdAt < :to "
                  + "ORDER BY h.createdAt DESC",
                    io.github.claudetoolkit.ui.history.ReviewHistory.class)
                .setParameter("from", fromDt)
                .setParameter("to",   toDt)
                .getResultList();

            // ── 1. 전체 카운트 + 비율 ────────────────────────────
            long total = list.size();
            long pending = 0, accepted = 0, rejected = 0;
            // ── 2. 일별 트렌드 (날짜 키 사전 채움) ───────────────
            Map<String, long[]> daily = new LinkedHashMap<>();   // [pending, accepted, rejected]
            for (java.time.LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
                daily.put(d.toString(), new long[]{ 0, 0, 0 });
            }
            // ── 3. 타입별 ────────────────────────────────────────
            Map<String, long[]> byType = new LinkedHashMap<>();
            // ── 4. 리뷰어별 (작성자 != 검토자, 본인 검토 제외) ──
            Map<String, long[]> byReviewer = new LinkedHashMap<>(); // [accepted, rejected]

            for (io.github.claudetoolkit.ui.history.ReviewHistory h : list) {
                String status = h.getReviewStatus();
                if (status == null || status.isEmpty()) status = "PENDING";
                if ("ACCEPTED".equals(status))      accepted++;
                else if ("REJECTED".equals(status)) rejected++;
                else                                pending++;

                // 일별
                String dayKey = h.getCreatedAt().toLocalDate().toString();
                long[] dayBucket = daily.get(dayKey);
                if (dayBucket != null) {
                    if ("ACCEPTED".equals(status))      dayBucket[1]++;
                    else if ("REJECTED".equals(status)) dayBucket[2]++;
                    else                                dayBucket[0]++;
                }

                // 타입별
                String type = h.getType() != null ? h.getType() : "UNKNOWN";
                long[] typeBucket = byType.computeIfAbsent(type, k -> new long[]{ 0, 0, 0 });
                if ("ACCEPTED".equals(status))      typeBucket[1]++;
                else if ("REJECTED".equals(status)) typeBucket[2]++;
                else                                typeBucket[0]++;

                // 리뷰어별
                if (h.getReviewedBy() != null && !h.getReviewedBy().isEmpty()) {
                    long[] revBucket = byReviewer.computeIfAbsent(h.getReviewedBy(), k -> new long[]{ 0, 0 });
                    if ("ACCEPTED".equals(status))      revBucket[0]++;
                    else if ("REJECTED".equals(status)) revBucket[1]++;
                }
            }

            data.put("totalCount",      total);
            data.put("pendingCount",    pending);
            data.put("acceptedCount",   accepted);
            data.put("rejectedCount",   rejected);
            data.put("pendingPercent",  total > 0 ? Math.round(pending  * 1000.0 / total) / 10.0 : 0.0);
            data.put("acceptedPercent", total > 0 ? Math.round(accepted * 1000.0 / total) / 10.0 : 0.0);
            data.put("rejectedPercent", total > 0 ? Math.round(rejected * 1000.0 / total) / 10.0 : 0.0);

            // dailyTrend → List<Map>
            List<Map<String, Object>> trend = new ArrayList<>();
            for (Map.Entry<String, long[]> e : daily.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date",     e.getKey());
                m.put("pending",  e.getValue()[0]);
                m.put("accepted", e.getValue()[1]);
                m.put("rejected", e.getValue()[2]);
                m.put("total",    e.getValue()[0] + e.getValue()[1] + e.getValue()[2]);
                trend.add(m);
            }
            data.put("dailyTrend", trend);

            // byType → List<Map> (총합 내림차순)
            List<Map<String, Object>> typeList = new ArrayList<>();
            for (Map.Entry<String, long[]> e : byType.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type",     e.getKey());
                m.put("pending",  e.getValue()[0]);
                m.put("accepted", e.getValue()[1]);
                m.put("rejected", e.getValue()[2]);
                m.put("total",    e.getValue()[0] + e.getValue()[1] + e.getValue()[2]);
                typeList.add(m);
            }
            typeList.sort((a, b) -> Long.compare((Long) b.get("total"), (Long) a.get("total")));
            data.put("byType", typeList);

            // byReviewer → List<Map> (총합 내림차순)
            List<Map<String, Object>> revList = new ArrayList<>();
            for (Map.Entry<String, long[]> e : byReviewer.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("username", e.getKey());
                m.put("accepted", e.getValue()[0]);
                m.put("rejected", e.getValue()[1]);
                m.put("total",    e.getValue()[0] + e.getValue()[1]);
                revList.add(m);
            }
            revList.sort((a, b) -> Long.compare((Long) b.get("total"), (Long) a.get("total")));
            data.put("byReviewer", revList);

            data.put("from", fromDate.toString());
            data.put("to",   toDate.toString());
            data.put("days", java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1);
        } catch (Exception e) {
            log.warn("리뷰 대시보드 데이터 조회 실패", e);
            data.put("error", e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.ok(data));
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
            } catch (Exception e) {
                log.debug("사용자 역할 조회 실패: user={}", me, e);
            }

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
            log.warn("리뷰 큐 조회 실패: tab={}", tab, e);
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
            log.warn("스케줄 목록 조회 실패", e);
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
            log.warn("ROI 리포트 조회 실패", e);
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
            log.warn("프롬프트 목록 조회 실패", e);
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
    public ResponseEntity<ApiResponse<List<?>>> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(value = "type", defaultValue = "") String type,
            @RequestParam(value = "from", defaultValue = "") String from,
            @RequestParam(value = "to",   defaultValue = "") String to,
            @RequestParam(value = "sort", defaultValue = "recent") String sort,
            Authentication auth) {
        if (q.trim().isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
        try {
            // 동적 JPQL 조립 — 타입/날짜 필터는 비어 있을 때 절을 빼서 인덱스 스캔 폭을 줄인다.
            StringBuilder jpql = new StringBuilder(
                "SELECT h FROM ReviewHistory h WHERE h.username = :u AND " +
                "(LOWER(h.type) LIKE :q OR LOWER(h.title) LIKE :q OR " +
                " LOWER(h.inputContent) LIKE :q OR LOWER(h.outputContent) LIKE :q)");
            if (!type.trim().isEmpty()) jpql.append(" AND h.type = :type");
            java.time.LocalDateTime fromDt = parseDateOrNull(from, false);
            java.time.LocalDateTime toDt   = parseDateOrNull(to,   true);
            if (fromDt != null) jpql.append(" AND h.createdAt >= :fromDt");
            if (toDt   != null) jpql.append(" AND h.createdAt <= :toDt");
            jpql.append(" ORDER BY h.createdAt ").append("oldest".equals(sort) ? "ASC" : "DESC");

            javax.persistence.Query query = em.createQuery(jpql.toString())
                    .setParameter("u", auth.getName())
                    .setParameter("q", "%" + q.toLowerCase() + "%");
            if (!type.trim().isEmpty()) query.setParameter("type", type.trim());
            if (fromDt != null) query.setParameter("fromDt", fromDt);
            if (toDt   != null) query.setParameter("toDt",   toDt);

            @SuppressWarnings("unchecked")
            List<io.github.claudetoolkit.ui.history.ReviewHistory> rows =
                    (List<io.github.claudetoolkit.ui.history.ReviewHistory>)
                            query.setMaxResults(100).getResultList();

            // 프론트엔드(SearchPage) 가 기대하는 키로 평탄화 + 매치 위치 기반 snippet
            String qLower = q.toLowerCase();
            List<Map<String, Object>> result = new ArrayList<>(rows.size());
            for (io.github.claudetoolkit.ui.history.ReviewHistory h : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",        h.getId());
                m.put("type",      h.getType());
                m.put("menuName",  h.getTypeLabel());           // 한국어 라벨
                m.put("title",     h.getTitle());
                m.put("snippet",   buildSnippet(h, qLower));    // 매치 주변 ±60자
                m.put("matchField", findMatchField(h, qLower));  // input/output/title/type
                m.put("createdAt", h.getFormattedDate());        // MM-dd HH:mm
                result.add(m);
            }
            // 'relevance' 정렬 — 매치 필드 우선순위 (title > type > input > output) + 매치 횟수
            if ("relevance".equals(sort)) {
                result.sort((a, b) -> Integer.compare(
                        relevanceScore(b, qLower),
                        relevanceScore(a, qLower)));
            }
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.warn("[Search] 검색 실패 q={} type={} sort={}", q, type, sort, e);
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    /**
     * 검색 필터에 노출할 type 목록 — review_history 에 실제로 존재하는 타입 + 사용자 본인 행만.
     * (다른 사용자가 만든 타입까지 보이면 노이즈)
     */
    @GetMapping("/search/types")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchTypes(Authentication auth) {
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createQuery(
                "SELECT h.type, COUNT(h) FROM ReviewHistory h WHERE h.username = :u " +
                "GROUP BY h.type ORDER BY COUNT(h) DESC"
            ).setParameter("u", auth.getName()).getResultList();
            List<Map<String, Object>> result = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                String type = (String) row[0];
                Long count = ((Number) row[1]).longValue();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type",  type);
                m.put("label", typeLabel(type));
                m.put("count", count);
                result.add(m);
            }
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.warn("[Search] /search/types 실패", e);
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    private static java.time.LocalDateTime parseDateOrNull(String s, boolean endOfDay) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(s.trim());
            return endOfDay ? d.atTime(23, 59, 59) : d.atStartOfDay();
        } catch (Exception ignored) { return null; }
    }

    private static String buildSnippet(io.github.claudetoolkit.ui.history.ReviewHistory h, String qLower) {
        // 1) 매치된 필드를 찾고 그 위치 기준으로 ±60자 발췌
        String[][] candidates = {
                { h.getInputContent()  != null ? h.getInputContent()  : "", "input"  },
                { h.getOutputContent() != null ? h.getOutputContent() : "", "output" },
                { h.getTitle()         != null ? h.getTitle()         : "", "title"  },
        };
        for (String[] cand : candidates) {
            String text = cand[0];
            int idx = text.toLowerCase().indexOf(qLower);
            if (idx >= 0) {
                int from2 = Math.max(0, idx - 60);
                int to2   = Math.min(text.length(), idx + qLower.length() + 60);
                String prefix = from2 > 0 ? "..." : "";
                String suffix = to2 < text.length() ? "..." : "";
                String slice = text.substring(from2, to2)
                        .replaceAll("\\s+", " ")
                        .replaceAll("[#*`>]", "");
                return prefix + slice + suffix;
            }
        }
        // 2) fallback — outputContent 의 첫 200자 (마크다운 제거)
        return h.getOutputPreview();
    }

    private static String findMatchField(io.github.claudetoolkit.ui.history.ReviewHistory h, String qLower) {
        if (h.getTitle() != null && h.getTitle().toLowerCase().contains(qLower))               return "title";
        if (h.getType()  != null && h.getType().toLowerCase().contains(qLower))                return "type";
        if (h.getInputContent()  != null && h.getInputContent().toLowerCase().contains(qLower)) return "input";
        if (h.getOutputContent() != null && h.getOutputContent().toLowerCase().contains(qLower)) return "output";
        return "";
    }

    /** relevance 정렬용 — title > type 매치를 가장 높이 평가, output 매치는 가장 낮음 */
    private static int relevanceScore(Map<String, Object> r, String qLower) {
        String field = (String) r.getOrDefault("matchField", "");
        switch (field) {
            case "title":  return 100;
            case "type":   return 80;
            case "input":  return 50;
            case "output": return 20;
            default:       return 0;
        }
    }

    /** ReviewHistory.getTypeLabel() 의 static 버전 — type 문자열만으로 한국어 라벨 변환 */
    private static String typeLabel(String type) {
        return io.github.claudetoolkit.ui.history.ReviewHistory.typeLabelOf(type);
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
                } catch (Exception e) {
                    log.debug("팀 대시보드 개별 사용자 통계 조회 실패", e);
                }
            }
        } catch (Exception e) {
            log.warn("팀 대시보드 조회 실패", e);
        }
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
            data.put("miplatformRoot", toolkitSettings.getProject().getMiplatformRoot());
            data.put("miplatformPatterns", toolkitSettings.getProject().getMiplatformPatterns());
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
        } catch (Exception e) {
            log.warn("설정 데이터 조회 실패", e);
        }
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
        } catch (Exception e) {
            log.warn("사용자 권한 조회 실패: user={}", auth.getName(), e);
        }
        data.put("disabledFeatures", disabled);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/db-profiles")
    public ResponseEntity<ApiResponse<List<?>>> dbProfiles() {
        try {
            List<?> list = em.createQuery("SELECT p FROM DbProfile p ORDER BY p.id").getResultList();
            return ResponseEntity.ok(ApiResponse.ok(list));
        } catch (Exception e) {
            log.warn("DB 프로필 조회 실패", e);
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
            log.warn("실행계획 대시보드 조회 실패", e);
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
    }

    /**
     * v4.2.8 — 품질 대시보드 데이터.
     *
     * HARNESS_REVIEW / CODE_REVIEW / SQL_REVIEW 이력을 집계하여:
     *  - severity 분포 (HIGH/MEDIUM/LOW) — 출력 텍스트의 `[SEVERITY: X]` 마커 카운트
     *  - 카테고리 분포 — 설계/성능/보안/가독성/기타 키워드 매칭
     *  - histories — 드릴다운용 원본 이력 목록 (id, title, 해당 severities/categories 포함)
     *
     * 기간 필터: from/to (YYYY-MM-DD). 미지정시 최근 30일.
     */
    @GetMapping("/harness/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> harnessDashboard(
            @org.springframework.web.bind.annotation.RequestParam(value = "from", required = false) String fromStr,
            @org.springframework.web.bind.annotation.RequestParam(value = "to",   required = false) String toStr) {
        try {
            java.time.LocalDateTime from = (fromStr != null && !fromStr.isEmpty())
                    ? java.time.LocalDate.parse(fromStr).atStartOfDay()
                    : java.time.LocalDateTime.now().minusDays(30);
            java.time.LocalDateTime to = (toStr != null && !toStr.isEmpty())
                    ? java.time.LocalDate.parse(toStr).atTime(23, 59, 59)
                    : java.time.LocalDateTime.now();

            @SuppressWarnings("unchecked")
            List<io.github.claudetoolkit.ui.history.ReviewHistory> list =
                (List<io.github.claudetoolkit.ui.history.ReviewHistory>) em.createQuery(
                    "SELECT h FROM ReviewHistory h " +
                    "WHERE h.createdAt BETWEEN :from AND :to " +
                    "  AND (h.type = 'HARNESS_REVIEW' OR h.type = 'CODE_REVIEW' OR h.type = 'SQL_REVIEW') " +
                    "ORDER BY h.createdAt DESC"
                ).setParameter("from", from)
                 .setParameter("to",   to)
                 .setMaxResults(500)
                 .getResultList();

            // 집계
            Map<String, Long> severityMap = new LinkedHashMap<String, Long>();
            severityMap.put("HIGH",   0L);
            severityMap.put("MEDIUM", 0L);
            severityMap.put("LOW",    0L);

            Map<String, Long> categoryMap = new LinkedHashMap<String, Long>();
            categoryMap.put("성능",   0L);
            categoryMap.put("보안",   0L);
            categoryMap.put("가독성", 0L);
            categoryMap.put("설계",   0L);
            categoryMap.put("기타",   0L);

            List<Map<String, Object>> historyRows = new ArrayList<Map<String, Object>>();
            java.util.regex.Pattern severityRe =
                java.util.regex.Pattern.compile("\\[SEVERITY:\\s*(HIGH|MEDIUM|LOW)", java.util.regex.Pattern.CASE_INSENSITIVE);

            for (io.github.claudetoolkit.ui.history.ReviewHistory h : list) {
                String out = h.getOutputContent() != null ? h.getOutputContent() : "";

                // severity
                Set<String> itemSeverities = new LinkedHashSet<String>();
                java.util.regex.Matcher m = severityRe.matcher(out);
                while (m.find()) {
                    String sev = m.group(1).toUpperCase();
                    severityMap.put(sev, severityMap.getOrDefault(sev, 0L) + 1);
                    itemSeverities.add(sev);
                }

                // 카테고리 키워드 매칭
                Set<String> itemCategories = new LinkedHashSet<String>();
                String lower = out.toLowerCase();
                if (lower.contains("성능") || lower.contains("performance") || lower.contains("n+1"))   itemCategories.add("성능");
                if (lower.contains("보안") || lower.contains("security") || lower.contains("injection")) itemCategories.add("보안");
                if (lower.contains("가독") || lower.contains("readability") || lower.contains("네이밍")) itemCategories.add("가독성");
                if (lower.contains("설계") || lower.contains("design") || lower.contains("아키텍"))     itemCategories.add("설계");
                if (itemCategories.isEmpty()) itemCategories.add("기타");
                for (String c : itemCategories) {
                    categoryMap.put(c, categoryMap.getOrDefault(c, 0L) + 1);
                }

                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id",         h.getId());
                row.put("type",       h.getType());
                row.put("title",      h.getTitle());
                row.put("username",   h.getUsername());
                row.put("createdAt",  h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
                row.put("severities", new ArrayList<String>(itemSeverities));
                row.put("categories", new ArrayList<String>(itemCategories));
                historyRows.add(row);
            }

            List<Map<String, Object>> severityList = new ArrayList<Map<String, Object>>();
            for (Map.Entry<String, Long> e : severityMap.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("category", e.getKey());
                m.put("count",    e.getValue());
                severityList.add(m);
            }
            List<Map<String, Object>> categoryList = new ArrayList<Map<String, Object>>();
            for (Map.Entry<String, Long> e : categoryMap.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("category", e.getKey());
                m.put("count",    e.getValue());
                categoryList.add(m);
            }

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("from",       from.toLocalDate().toString());
            data.put("to",         to.toLocalDate().toString());
            data.put("totalCount", (long) list.size());
            data.put("severity",   severityList);
            data.put("categories", categoryList);
            data.put("histories",  historyRows);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("품질 대시보드 조회 실패", e);
            Map<String, Object> empty = new LinkedHashMap<String, Object>();
            empty.put("error", e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(empty));
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
        } catch (Exception e) {
            log.warn("사용자 수 조회 실패", e);
            data.put("userCount", 0);
        }
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
