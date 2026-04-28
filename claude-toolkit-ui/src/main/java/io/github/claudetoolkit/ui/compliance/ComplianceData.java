package io.github.claudetoolkit.ui.compliance;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.6.x — 컴플라이언스 리포트 생성에 필요한 *집계 데이터* 컨테이너.
 *
 * <p>{@link ComplianceDataAggregator} 가 review_history / audit_log 등
 * 여러 소스에서 모은 결과를 모아두는 plain DTO. 템플릿 빌더는 이 객체만
 * 받아 markdown 을 생성하므로, 데이터 소스 변경이 템플릿에 영향 주지 않는다.
 */
public class ComplianceData {

    public ComplianceReportType type;
    public LocalDate from;
    public LocalDate to;
    public String   generatedAt;
    public String   generatedBy;

    /** 보안 발견 — type='SQL_SECURITY' 또는 'CODE_REVIEW_SEC' 분석 통계 */
    public SecurityFindings security = new SecurityFindings();

    /** 인증/권한 — audit_log 기반 */
    public AuthStats auth = new AuthStats();

    /** 데이터 보호 — type='DATA_MASKING' 분석 */
    public DataProtection masking = new DataProtection();

    /** 분석 활동 누적 — 기간 내 review_history 의 type 별 카운트 */
    public Map<String, Long> activityByType = new LinkedHashMap<>();

    public long totalAnalysisInPeriod;
    public long totalAnalysisByUserCount;  // 분석한 고유 사용자 수

    public static class SecurityFindings {
        public long totalSecurityReviews;        // SQL_SECURITY + CODE_REVIEW_SEC 총 건수
        public long highSeverityCount;            // outputContent 에 [SEVERITY: HIGH] 포함 건
        public long mediumSeverityCount;          // [SEVERITY: MEDIUM]
        public long lowSeverityCount;             // [SEVERITY: LOW]
        /** 최근 high 등급 발견 사례 (최대 10개) — 각 항목: {id, type, title, createdAt} */
        public List<Map<String, Object>> recentHighFindings;
    }

    public static class AuthStats {
        public long totalAuditEntries;
        public long loginAttempts;
        public long loginFailures;
        public long permissionDenials;     // statusCode=403
        public long apiCalls5xx;            // statusCode >= 500
    }

    public static class DataProtection {
        public long maskingAnalyses;        // type='DATA_MASKING' 분석 건수
        public long inputMaskingUses;       // type='INPUT_MASKING' (있다면)
    }
}
