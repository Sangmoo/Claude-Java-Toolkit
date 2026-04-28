package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.api.ApiResponse;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v4.6.x — 사용자/팀 인사이트 엔드포인트.
 *
 * <p>{@code /roi-report} 페이지를 *개인/팀 분석 활동 대시보드* 로 확장하기 위한
 * 데이터 소스. 기존 {@code /api/v1/roi-report} 가 *전체 시스템* 통계만 반환하는
 * 것과 달리, 여기서는 다음을 분리 노출:
 * <ul>
 *   <li>{@code /user} — 본인의 활동 (top 5 기능, 주간 추이, 시간 절감)</li>
 *   <li>{@code /team} — 팀 평균 + 본인 순위·백분위 (ADMIN 전용)</li>
 * </ul>
 *
 * <p>리텐션 향상 목적 — 사용자가 자기 사용량을 가시화해 사용을 늘리도록 유도.
 */
@RestController
@RequestMapping("/api/v1")
public class InsightsController {

    private static final Logger log = LoggerFactory.getLogger(InsightsController.class);

    /** 분석 1건당 절감 시간 (시간 단위, 기존 /roi-report 와 동일 가정) */
    private static final double HOURS_PER_ANALYSIS = 0.5;
    /** 채팅 1세션당 절감 시간 (시간 단위) */
    private static final double HOURS_PER_CHAT     = 0.1;

    @PersistenceContext
    private EntityManager em;

    /**
     * 본인의 활동 통계 — 모든 인증된 사용자 호출 가능.
     * 응답: totalAnalysis, totalChat, hoursSaved, topFeatures[], weeklyTrend[].
     */
    @GetMapping("/insights/user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> userInsights(
            @RequestParam(value = "weeks", defaultValue = "12") int weeks,
            Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("인증 필요"));
        }
        int safeWeeks = Math.max(1, Math.min(52, weeks));
        String me = auth.getName();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("username", me);

            long totalAnalysis = countAnalysisByUser(me);
            long totalChat     = countChatByUser(me);
            double hoursSaved  = totalAnalysis * HOURS_PER_ANALYSIS + totalChat * HOURS_PER_CHAT;
            data.put("totalAnalysis", totalAnalysis);
            data.put("totalChat",     totalChat);
            data.put("hoursSaved",    round1(hoursSaved));

            data.put("topFeatures", topFeaturesForUser(me, 5));
            data.put("weeklyTrend", weeklyTrendForUser(me, safeWeeks));
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[Insights] /user 실패 user={}", me, e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(
                    "사용자 인사이트 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 팀 전체 통계 + 본인 순위 — ADMIN 전용 (URL 패턴 {@code /api/v1/admin/**}
     * 으로 SecurityConfig 가 자동 게이팅).
     */
    @GetMapping("/admin/insights/team")
    public ResponseEntity<ApiResponse<Map<String, Object>>> teamInsights(Authentication auth) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            // 사용자별 분석 카운트 — 큰 쪽부터
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createQuery(
                    "SELECT h.username, COUNT(h) FROM ReviewHistory h WHERE h.username IS NOT NULL " +
                            "GROUP BY h.username ORDER BY COUNT(h) DESC"
            ).getResultList();

            long teamTotalAnalysis = 0;
            List<Map<String, Object>> users = new ArrayList<>(rows.size());
            for (Object[] r : rows) {
                String name = (String) r[0];
                long count = ((Number) r[1]).longValue();
                teamTotalAnalysis += count;
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("username", name);
                u.put("count",    count);
                users.add(u);
            }
            int userCount = users.size();
            data.put("userCount",          userCount);
            data.put("teamTotalAnalysis",  teamTotalAnalysis);
            data.put("teamTotalHoursSaved",
                    round1(teamTotalAnalysis * HOURS_PER_ANALYSIS));
            data.put("averagePerUser",
                    userCount > 0 ? round1((double) teamTotalAnalysis / userCount) : 0.0);

            // top 5 만 응답에 포함 (개인정보 보호 — 5명 미만 팀이면 전체)
            int topN = Math.min(5, userCount);
            data.put("topUsers", users.subList(0, topN));

            // 본인 순위 + 백분위
            String me = auth != null ? auth.getName() : null;
            if (me != null) {
                long myCount = 0;
                int myRank = 0;
                for (int i = 0; i < users.size(); i++) {
                    if (me.equals(users.get(i).get("username"))) {
                        myCount = ((Number) users.get(i).get("count")).longValue();
                        myRank  = i + 1;
                        break;
                    }
                }
                data.put("myCount",      myCount);
                data.put("myRank",       myRank);
                data.put("myPercentile", percentile(myRank, userCount));
            }

            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[Insights] /team 실패", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(
                    "팀 인사이트 조회 실패: " + e.getMessage()));
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private long countAnalysisByUser(String username) {
        try {
            return ((Number) em.createQuery(
                    "SELECT COUNT(h) FROM ReviewHistory h WHERE h.username = :u"
            ).setParameter("u", username).getSingleResult()).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    private long countChatByUser(String username) {
        // ChatMessage 자체엔 username 이 없고 ChatSession 에 있음 → 세션 수로 근사
        try {
            return ((Number) em.createQuery(
                    "SELECT COUNT(s) FROM ChatSession s WHERE s.username = :u"
            ).setParameter("u", username).getSingleResult()).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    /** review_history 의 사용자별 type 분포 top N */
    private List<Map<String, Object>> topFeaturesForUser(String username, int topN) {
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createQuery(
                    "SELECT h.type, COUNT(h) FROM ReviewHistory h WHERE h.username = :u " +
                            "GROUP BY h.type ORDER BY COUNT(h) DESC"
            ).setParameter("u", username).setMaxResults(topN).getResultList();
            List<Map<String, Object>> out = new ArrayList<>(rows.size());
            for (Object[] r : rows) {
                String type = (String) r[0];
                long count = ((Number) r[1]).longValue();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type",  type);
                m.put("label", typeLabel(type));
                m.put("count", count);
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 최근 N주 분석 카운트 (week ISO 형식: yyyy-Www).
     * Java 1.8 호환 — DB 함수 의존하지 않고 메모리에서 그룹핑.
     */
    private List<Map<String, Object>> weeklyTrendForUser(String username, int weeks) {
        LocalDateTime since = LocalDateTime.now().minusWeeks(weeks);
        @SuppressWarnings("unchecked")
        List<ReviewHistory> rows;
        try {
            Query q = em.createQuery(
                    "SELECT h FROM ReviewHistory h WHERE h.username = :u AND h.createdAt >= :since"
            ).setParameter("u", username).setParameter("since", since);
            rows = q.getResultList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
        // ISO week of year (월요일 기준) 으로 그룹핑
        WeekFields wf = WeekFields.of(DayOfWeek.MONDAY, 4);
        Map<String, Long> counts = new LinkedHashMap<>();
        // 현재부터 N주 거꾸로 — 빈 주에도 0 채워서 차트가 매끄럽게
        LocalDateTime cursor = LocalDateTime.now();
        for (int i = weeks - 1; i >= 0; i--) {
            LocalDateTime w = cursor.minusWeeks(i);
            String key = String.format(Locale.ROOT, "%d-W%02d",
                    w.get(wf.weekBasedYear()), w.get(wf.weekOfWeekBasedYear()));
            counts.put(key, 0L);
        }
        for (ReviewHistory h : rows) {
            if (h.getCreatedAt() == null) continue;
            String key = String.format(Locale.ROOT, "%d-W%02d",
                    h.getCreatedAt().get(wf.weekBasedYear()),
                    h.getCreatedAt().get(wf.weekOfWeekBasedYear()));
            counts.merge(key, 1L, Long::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>(counts.size());
        long cumulative = 0L;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            cumulative += e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("week",       e.getKey());
            m.put("count",      e.getValue());
            m.put("cumulative", cumulative);
            m.put("hoursSaved", round1(cumulative * HOURS_PER_ANALYSIS));
            out.add(m);
        }
        return out;
    }

    private static String typeLabel(String type) {
        if (type == null) return "";
        ReviewHistory dummy = new ReviewHistory(type, "", "", "");
        return dummy.getTypeLabel();
    }

    /** 1등이면 100백분위, 꼴찌면 0백분위 */
    private static int percentile(int rank, int total) {
        if (rank <= 0 || total <= 1) return rank == 1 ? 100 : 0;
        return (int) Math.round(100.0 * (total - rank) / (total - 1));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
