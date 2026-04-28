package io.github.claudetoolkit.ui.compliance;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.6.x — 컴플라이언스 리포트용 데이터 어그리게이션 서비스.
 *
 * <p>review_history / audit_log 두 테이블에서 *기간 + 타입* 으로 필터링한
 * 통계를 모아 {@link ComplianceData} 로 반환.
 *
 * <p>모든 카운트는 SQL COUNT 또는 작은 SELECT 만 사용 (5MB 이상 row 패치 X).
 * 리포트 생성은 단일 트랜잭션 내에서 수십 ms 안에 끝난다.
 */
@Service
public class ComplianceDataAggregator {

    private static final Logger log = LoggerFactory.getLogger(ComplianceDataAggregator.class);

    @PersistenceContext
    private EntityManager em;

    public ComplianceData aggregate(LocalDate from, LocalDate to) {
        ComplianceData d = new ComplianceData();
        d.from = from;
        d.to   = to;
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atTime(23, 59, 59);

        d.security  = aggregateSecurity(fromDt, toDt);
        d.auth      = aggregateAuth(fromDt, toDt);
        d.masking   = aggregateMasking(fromDt, toDt);
        d.activityByType        = aggregateActivityByType(fromDt, toDt);
        d.totalAnalysisInPeriod = countTotalReviews(fromDt, toDt);
        d.totalAnalysisByUserCount = countDistinctUsers(fromDt, toDt);
        return d;
    }

    // ── 1) 보안 발견 — review_history.type='SQL_SECURITY' 또는 'CODE_REVIEW_SEC' ──

    @SuppressWarnings("unchecked")
    private ComplianceData.SecurityFindings aggregateSecurity(LocalDateTime fromDt, LocalDateTime toDt) {
        ComplianceData.SecurityFindings s = new ComplianceData.SecurityFindings();
        try {
            String securityTypes = "('SQL_SECURITY','CODE_REVIEW_SEC')";
            s.totalSecurityReviews = countReviews(fromDt, toDt, securityTypes, null);
            s.highSeverityCount    = countReviews(fromDt, toDt, securityTypes, "[SEVERITY: HIGH]");
            s.mediumSeverityCount  = countReviews(fromDt, toDt, securityTypes, "[SEVERITY: MEDIUM]");
            s.lowSeverityCount     = countReviews(fromDt, toDt, securityTypes, "[SEVERITY: LOW]");

            // 최근 HIGH 등급 발견 — 최대 10개
            List<ReviewHistory> highRows = (List<ReviewHistory>) em.createQuery(
                    "SELECT h FROM ReviewHistory h " +
                            "WHERE h.createdAt >= :from AND h.createdAt <= :to " +
                            "AND h.type IN ('SQL_SECURITY', 'CODE_REVIEW_SEC') " +
                            "AND LOWER(h.outputContent) LIKE :sev " +
                            "ORDER BY h.createdAt DESC")
                    .setParameter("from", fromDt)
                    .setParameter("to",   toDt)
                    .setParameter("sev",  "%[severity: high]%")
                    .setMaxResults(10)
                    .getResultList();
            List<Map<String, Object>> findings = new ArrayList<>(highRows.size());
            for (ReviewHistory h : highRows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",        h.getId());
                m.put("type",      h.getType());
                m.put("typeLabel", h.getTypeLabel());
                m.put("title",     h.getTitle());
                m.put("createdAt", h.getFormattedDate());
                m.put("username",  h.getUsername());
                findings.add(m);
            }
            s.recentHighFindings = findings;
        } catch (Exception e) {
            log.warn("[Compliance] 보안 발견 집계 실패", e);
        }
        return s;
    }

    private long countReviews(LocalDateTime from, LocalDateTime to, String typesInClause, String severityLike) {
        try {
            StringBuilder jpql = new StringBuilder(
                    "SELECT COUNT(h) FROM ReviewHistory h " +
                            "WHERE h.createdAt >= :from AND h.createdAt <= :to " +
                            "AND h.type IN " + typesInClause);
            if (severityLike != null) {
                jpql.append(" AND LOWER(h.outputContent) LIKE :sev");
            }
            javax.persistence.Query q = em.createQuery(jpql.toString())
                    .setParameter("from", from).setParameter("to", to);
            if (severityLike != null) {
                q.setParameter("sev", "%" + severityLike.toLowerCase() + "%");
            }
            return ((Number) q.getSingleResult()).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    // ── 2) 인증/권한 — audit_log 기반 ──

    private ComplianceData.AuthStats aggregateAuth(LocalDateTime fromDt, LocalDateTime toDt) {
        ComplianceData.AuthStats a = new ComplianceData.AuthStats();
        try {
            a.totalAuditEntries = scalarCount(
                    "SELECT COUNT(l) FROM AuditLog l WHERE l.createdAt >= :from AND l.createdAt <= :to",
                    fromDt, toDt, null);

            a.loginAttempts = scalarCount(
                    "SELECT COUNT(l) FROM AuditLog l WHERE l.createdAt >= :from AND l.createdAt <= :to " +
                            "AND l.endpoint LIKE :ep",
                    fromDt, toDt, "%/login%");

            a.loginFailures = scalarCount(
                    "SELECT COUNT(l) FROM AuditLog l WHERE l.createdAt >= :from AND l.createdAt <= :to " +
                            "AND l.endpoint LIKE :ep AND l.statusCode >= 400",
                    fromDt, toDt, "%/login%");

            a.permissionDenials = scalarCount(
                    "SELECT COUNT(l) FROM AuditLog l WHERE l.createdAt >= :from AND l.createdAt <= :to " +
                            "AND l.statusCode = 403",
                    fromDt, toDt, null);

            a.apiCalls5xx = scalarCount(
                    "SELECT COUNT(l) FROM AuditLog l WHERE l.createdAt >= :from AND l.createdAt <= :to " +
                            "AND l.statusCode >= 500",
                    fromDt, toDt, null);
        } catch (Exception e) {
            log.warn("[Compliance] 인증/권한 집계 실패", e);
        }
        return a;
    }

    private long scalarCount(String jpql, LocalDateTime from, LocalDateTime to, String epLike) {
        try {
            javax.persistence.Query q = em.createQuery(jpql)
                    .setParameter("from", from).setParameter("to", to);
            if (epLike != null) q.setParameter("ep", epLike);
            return ((Number) q.getSingleResult()).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    // ── 3) 데이터 보호 — type='DATA_MASKING' / 'INPUT_MASKING' ──

    private ComplianceData.DataProtection aggregateMasking(LocalDateTime fromDt, LocalDateTime toDt) {
        ComplianceData.DataProtection d = new ComplianceData.DataProtection();
        try {
            d.maskingAnalyses = scalarCount(
                    "SELECT COUNT(h) FROM ReviewHistory h WHERE h.createdAt >= :from AND h.createdAt <= :to " +
                            "AND h.type = 'DATA_MASKING'",
                    fromDt, toDt, null);
            d.inputMaskingUses = scalarCount(
                    "SELECT COUNT(h) FROM ReviewHistory h WHERE h.createdAt >= :from AND h.createdAt <= :to " +
                            "AND h.type = 'INPUT_MASKING'",
                    fromDt, toDt, null);
        } catch (Exception e) {
            log.warn("[Compliance] 데이터 보호 집계 실패", e);
        }
        return d;
    }

    // ── 4) 분석 활동 — type 별 카운트 ──

    @SuppressWarnings("unchecked")
    private Map<String, Long> aggregateActivityByType(LocalDateTime fromDt, LocalDateTime toDt) {
        Map<String, Long> out = new LinkedHashMap<>();
        try {
            List<Object[]> rows = em.createQuery(
                    "SELECT h.type, COUNT(h) FROM ReviewHistory h " +
                            "WHERE h.createdAt >= :from AND h.createdAt <= :to " +
                            "GROUP BY h.type ORDER BY COUNT(h) DESC")
                    .setParameter("from", fromDt).setParameter("to", toDt)
                    .getResultList();
            for (Object[] r : rows) {
                out.put((String) r[0], ((Number) r[1]).longValue());
            }
        } catch (Exception e) {
            log.warn("[Compliance] 활동별 집계 실패", e);
        }
        return out;
    }

    private long countTotalReviews(LocalDateTime fromDt, LocalDateTime toDt) {
        try {
            return ((Number) em.createQuery(
                    "SELECT COUNT(h) FROM ReviewHistory h WHERE h.createdAt >= :from AND h.createdAt <= :to")
                    .setParameter("from", fromDt).setParameter("to", toDt)
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    private long countDistinctUsers(LocalDateTime fromDt, LocalDateTime toDt) {
        try {
            return ((Number) em.createQuery(
                    "SELECT COUNT(DISTINCT h.username) FROM ReviewHistory h " +
                            "WHERE h.createdAt >= :from AND h.createdAt <= :to AND h.username IS NOT NULL")
                    .setParameter("from", fromDt).setParameter("to", toDt)
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }
}
