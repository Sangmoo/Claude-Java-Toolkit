package io.github.claudetoolkit.ui.compliance;

import io.github.claudetoolkit.ui.compliance.template.FssRegulationTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v4.6.x — FSS (전자금융감독규정) 리포트 markdown 빌더 단위 테스트.
 *
 * <p>DB 의존 없이 순수 함수 단위로 검증. 백엔드 배포 전 {@code mvn test}
 * 한 번만 돌려도 Stage 1 정상 동작 확신 가능.
 */
class FssRegulationTemplateTest {

    @Test
    @DisplayName("빈 데이터 — 모든 카운트 0 일 때도 markdown 정상 생성")
    void emptyData_buildsValidMarkdown() {
        ComplianceData d = newSampleData(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        // 일부러 모든 통계 0 으로 둠

        String md = FssRegulationTemplate.build(d);

        assertTrue(md.contains("# 전자금융감독규정 보안 점검 리포트"), "최상위 제목 포함");
        assertTrue(md.contains("2026-01-01"), "감사 시작일 포함");
        assertTrue(md.contains("2026-03-31"), "감사 종료일 포함");
        assertTrue(md.contains("## 8. 종합 의견"),  "종합 의견 섹션 포함");
        assertTrue(md.contains("자동 점검 항목 요약"), "자동 점검 표 포함");
        assertTrue(md.contains("수동 검토 권장 항목"), "수동 검토 체크리스트 포함");
        assertTrue(md.contains("법무"), "법적 disclaimer 포함");
    }

    @Test
    @DisplayName("HIGH 등급 발견 1+ 건 — 즉시 조치 필요 알림 + 사례표")
    void highSeverityFinding_addsImmediateActionNote() {
        ComplianceData d = newSampleData(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));
        d.security.totalSecurityReviews = 50;
        d.security.highSeverityCount    = 3;
        d.security.mediumSeverityCount  = 5;

        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("id",        12345L);
        finding.put("type",      "SQL_SECURITY");
        finding.put("typeLabel", "SQL 보안");
        finding.put("title",     "T_USER 테이블 조회 — UNION 기반 인젝션 의심");
        finding.put("createdAt", "2026-04-15 14:23:00");
        finding.put("username",  "kim");
        d.security.recentHighFindings = new ArrayList<>();
        d.security.recentHighFindings.add(finding);

        String md = FssRegulationTemplate.build(d);

        assertTrue(md.contains("HIGH: **3건**"),                    "HIGH 카운트 노출");
        assertTrue(md.contains("HIGH 등급 발견 사례"),                  "사례 섹션 헤더");
        assertTrue(md.contains("T_USER 테이블 조회"),                   "사례 제목 노출");
        assertTrue(md.contains("⚠️ 통과") || md.contains("⚠️ 주의"), "자동 점검에서 HIGH 미해결 → 주의");
        assertTrue(md.contains("즉시 조치 필요"),                      "종합 의견에 즉시 조치 권고");
    }

    @Test
    @DisplayName("로그인 실패 100+ — 이상징후 알림 추가")
    void highLoginFailures_addsAlert() {
        ComplianceData d = newSampleData(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));
        d.auth.loginAttempts = 1000;
        d.auth.loginFailures = 250;  // 25% 실패율

        String md = FssRegulationTemplate.build(d);

        assertTrue(md.contains("로그인 실패 횟수가 100회를 초과"),
                "로그인 실패 100+ 알림");
        assertTrue(md.contains("(25.0%)"), "실패율 % 표시");
    }

    @Test
    @DisplayName("권한 거부 50+ — 모니터링 강화 알림")
    void highPermissionDenials_addsAlert() {
        ComplianceData d = newSampleData(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));
        d.auth.permissionDenials = 80;

        String md = FssRegulationTemplate.build(d);

        assertTrue(md.contains("권한 거부 50회 초과"), "권한 거부 50+ 알림");
        assertTrue(md.contains("모니터링 강화 권장"), "종합 의견 추가");
    }

    @Test
    @DisplayName("분석 활동 type 별 통계 — 한국어 라벨로 변환")
    void activityByType_usesKoreanLabel() {
        ComplianceData d = newSampleData(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));
        d.activityByType.put("SQL_REVIEW",     45L);
        d.activityByType.put("HARNESS_REVIEW", 32L);
        d.activityByType.put("UNKNOWN_TYPE",   5L);  // 매핑 안 된 타입

        String md = FssRegulationTemplate.build(d);

        assertTrue(md.contains("SQL 리뷰"),     "type SQL_REVIEW 의 한국어 라벨");
        assertTrue(md.contains("하네스 리뷰"),   "HARNESS_REVIEW 의 한국어 라벨");
        assertTrue(md.contains("UNKNOWN_TYPE"), "라벨 미정의시 type 그대로 fallback");
        assertTrue(md.contains("| 45 |"),       "건수 셀 노출");
    }

    @Test
    @DisplayName("Markdown 파이프 escape — 제목에 | 들어 있어도 표 구조 깨지지 않음")
    void pipeInTitle_isEscaped() {
        ComplianceData d = newSampleData(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));
        d.security.highSeverityCount = 1;

        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("id", 1L);
        finding.put("type", "SQL_SECURITY");
        finding.put("typeLabel", "SQL 보안");
        finding.put("title", "악의적 입력 'a|b|c|d' 처리"); // | 3개
        finding.put("createdAt", "2026-04-15 14:00:00");
        finding.put("username", "tester");
        d.security.recentHighFindings = new ArrayList<>();
        d.security.recentHighFindings.add(finding);

        String md = FssRegulationTemplate.build(d);

        assertTrue(md.contains("a\\|b\\|c\\|d"), "파이프가 escape 되어야 함");
        assertFalse(md.contains("a|b|c|d"),       "원본 파이프는 남아 있으면 안 됨");
    }

    @Test
    @DisplayName("정상 운영 상태 — 모든 점검 통과 시 ✅ 종합 의견")
    void allHealthy_addsApproveNote() {
        ComplianceData d = newSampleData(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));
        d.totalAnalysisInPeriod        = 50;
        d.totalAnalysisByUserCount     = 5;
        d.security.totalSecurityReviews = 12;
        d.security.highSeverityCount    = 0;
        d.auth.loginAttempts            = 100;
        d.auth.loginFailures            = 5;
        d.auth.permissionDenials        = 2;
        d.masking.maskingAnalyses       = 3;

        String md = FssRegulationTemplate.build(d);

        assertTrue(md.contains("✅") || md.contains("자동 점검 항목 전반 양호"),
                "정상 상태 — ✅ 또는 양호 코멘트");
        assertFalse(md.contains("즉시 조치 필요"),       "정상엔 즉시 조치 코멘트 없어야 함");
        assertFalse(md.contains("로그인 실패 횟수가 100"), "로그인 실패 알림도 없어야 함");
    }

    @Test
    @DisplayName("자동 점검 5개 항목 모두 노출")
    void allFiveCheckItems_appear() {
        ComplianceData d = newSampleData(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));

        String md = FssRegulationTemplate.build(d);

        assertTrue(md.contains("정기 보안 분석 수행"),          "체크 1");
        assertTrue(md.contains("HIGH 등급 미해결 없음"),        "체크 2");
        assertTrue(md.contains("로그인 실패율"),               "체크 3");
        assertTrue(md.contains("서버 5xx 오류율"),             "체크 4");
        assertTrue(md.contains("개인정보 마스킹 활동 1건 이상"), "체크 5");
    }

    @Test
    @DisplayName("수동 검토 7개 항목 모두 노출")
    void allManualReviewItems_appear() {
        ComplianceData d = newSampleData(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));

        String md = FssRegulationTemplate.build(d);

        // 7개 체크리스트
        assertTrue(md.contains("- [ ] **망 분리**"),       "수동 1");
        assertTrue(md.contains("- [ ] **암호화**"),       "수동 2");
        assertTrue(md.contains("- [ ] **백업/복구**"),    "수동 3");
        assertTrue(md.contains("- [ ] **변경관리**"),     "수동 4");
        assertTrue(md.contains("- [ ] **물리적 보안**"),  "수동 5");
        assertTrue(md.contains("- [ ] **외주 인력**"),    "수동 6");
        assertTrue(md.contains("- [ ] **사고 대응 매뉴얼**"), "수동 7");
    }

    // ── helper ──────────────────────────────────────────────────────────────

    private ComplianceData newSampleData(LocalDate from, LocalDate to) {
        ComplianceData d = new ComplianceData();
        d.type        = ComplianceReportType.FSS;
        d.from        = from;
        d.to          = to;
        d.generatedAt = "2026-04-30 10:00:00";
        d.generatedBy = "admin";
        d.security    = new ComplianceData.SecurityFindings();
        d.auth        = new ComplianceData.AuthStats();
        d.masking     = new ComplianceData.DataProtection();
        return d;
    }
}
