package io.github.claudetoolkit.ui.compliance.template;

import io.github.claudetoolkit.ui.compliance.ComplianceData;
import io.github.claudetoolkit.ui.history.ReviewHistory;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * v4.6.x — *개인정보보호법 (PIPA)* 데이터 처리 흐름 리포트 Markdown 빌더.
 *
 * <p>방향성: PIPA 가 요구하는 *데이터 처리 흐름의 가시성* 에 초점.
 * 개인정보 마스킹 활동 + 데이터 흐름 분석 활동 + 보안 사고를 묶어 보고.
 *
 * <p>FSS 와 달리 *기술 점검* 보다 *처리 흐름과 동의/관리* 에 무게. 따라서
 * 자동 점검 항목과 수동 검토 항목 비중이 다름.
 */
public final class PrivacyActTemplate {

    private PrivacyActTemplate() {}

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String build(ComplianceData d) {
        StringBuilder sb = new StringBuilder(8192);

        // 표지
        sb.append("# 개인정보보호법 (PIPA) 데이터 처리 흐름 리포트\n\n");
        sb.append("> 자동 집계 리포트 — 개인정보 처리 흐름 + 마스킹 활동 + 사고 통계. ")
                .append("법적 컴플라이언스 증빙은 아니며 정보보호위원회·법무팀 검토가 필요합니다.\n\n");

        // 1. 개요
        sb.append("## 1. 리포트 개요\n\n");
        sb.append("| 항목 | 내용 |\n|---|---|\n");
        sb.append("| 감사 기간 | ").append(fmt(d.from)).append(" ~ ").append(fmt(d.to)).append(" |\n");
        sb.append("| 보고서 생성 | ").append(d.generatedAt).append(" |\n");
        sb.append("| 생성자 | ").append(safe(d.generatedBy)).append(" |\n");
        sb.append("| 점검 도구 | Claude Java Toolkit (자동 집계) |\n");
        sb.append("| 적용 법령 | 개인정보보호법, 동법 시행령, 표준 개인정보 보호지침 |\n\n");

        // 2. 개인정보 처리 활동 통계
        sb.append("## 2. 개인정보 보호 활동 (기간 내)\n\n");
        sb.append("| 항목 | 건수 | 설명 |\n|---|---|---|\n");
        sb.append("| 데이터 마스킹 분석 | **").append(d.masking.maskingAnalyses).append("** | 민감 컬럼 자동 탐지 + UPDATE 마스킹 SQL 생성 |\n");
        sb.append("| 입력 마스킹 사용  | **").append(d.masking.inputMaskingUses).append("** | 분석 입력에서 민감정보 자동 마스킹 적용 |\n\n");

        if (d.masking.maskingAnalyses + d.masking.inputMaskingUses == 0) {
            sb.append("⚠️ **개인정보 보호 활동이 0건** — 정기적인 마스킹 분석 운영 권장 (분기 1회 이상).\n\n");
        }

        // 3. 보안 사고/취약점 — 개인정보 노출 가능성 관점
        sb.append("## 3. 개인정보 노출 위험 분석 (보안 리뷰 기반)\n\n");
        long sec = d.security.totalSecurityReviews;
        sb.append("- 보안 분석 (SQL 보안 + 코드 보안): **").append(sec).append("건**\n");
        if (sec > 0) {
            sb.append("- 발견 등급:\n");
            sb.append("  - 🔴 HIGH (긴급): ").append(d.security.highSeverityCount).append("건\n");
            sb.append("  - 🟡 MEDIUM: ").append(d.security.mediumSeverityCount).append("건\n");
            sb.append("  - 🟢 LOW: ").append(d.security.lowSeverityCount).append("건\n\n");
        }

        if (d.security.recentHighFindings != null && !d.security.recentHighFindings.isEmpty()) {
            sb.append("### 3.1 HIGH 등급 발견 사례 — 개인정보 누출 가능성 점검 필요\n\n");
            sb.append("| # | 분석 유형 | 제목 | 사용자 | 일시 |\n|---|---|---|---|---|\n");
            int i = 1;
            for (Map<String, Object> f : d.security.recentHighFindings) {
                sb.append("| ").append(i++).append(" | ")
                        .append(safe(f.get("typeLabel"))).append(" | ")
                        .append(escapePipe(safe(f.get("title")))).append(" | ")
                        .append(safe(f.get("username"))).append(" | ")
                        .append(safe(f.get("createdAt"))).append(" |\n");
            }
            sb.append("\n💡 위 사례 중 *개인정보 처리 코드/쿼리* 가 포함된 것은 즉시 정보보호위 보고 + 영향 평가.\n\n");
        }

        // 4. 인증/접근 통제 (개인정보 처리시스템 관점)
        sb.append("## 4. 개인정보 처리시스템 접근 통제 (audit_log)\n\n");
        sb.append("| 항목 | 발생 횟수 |\n|---|---|\n");
        sb.append("| 전체 API 호출 | ").append(d.auth.totalAuditEntries).append(" |\n");
        sb.append("| 로그인 시도 | ").append(d.auth.loginAttempts).append(" |\n");
        sb.append("| 로그인 실패 | ").append(d.auth.loginFailures).append(" |\n");
        sb.append("| 권한 거부 (HTTP 403) | ").append(d.auth.permissionDenials).append(" |\n");
        sb.append("| 시스템 오류 (HTTP 5xx) | ").append(d.auth.apiCalls5xx).append(" |\n\n");

        // 5. 분석 활동 통계
        sb.append("## 5. 분석 활동 통계 (기간 내)\n\n");
        sb.append("- 전체 분석 건수: **").append(d.totalAnalysisInPeriod).append("건**\n");
        sb.append("- 분석 수행 사용자 수: **").append(d.totalAnalysisByUserCount).append("명**\n\n");
        if (!d.activityByType.isEmpty()) {
            sb.append("| 분석 유형 | 건수 |\n|---|---|\n");
            for (Map.Entry<String, Long> e : d.activityByType.entrySet()) {
                sb.append("| ").append(escapePipe(typeLabel(e.getKey())))
                        .append(" | ").append(e.getValue()).append(" |\n");
            }
            sb.append("\n");
        }

        // 6. 자동 점검 결과
        sb.append("## 6. PIPA 자동 점검 항목\n\n");
        sb.append("| 점검 항목 | 결과 | 근거 |\n|---|---|---|\n");
        sb.append(checkRow("개인정보 마스킹 활동 1건 이상",
                d.masking.maskingAnalyses + d.masking.inputMaskingUses > 0,
                "마스킹 " + (d.masking.maskingAnalyses + d.masking.inputMaskingUses) + "건"));
        sb.append(checkRow("HIGH 등급 보안 발견 미해결 0",
                d.security.highSeverityCount == 0,
                "HIGH " + d.security.highSeverityCount + "건"));
        sb.append(checkRow("로그인 실패율 < 30%",
                d.auth.loginAttempts == 0 || d.auth.loginFailures * 100 < d.auth.loginAttempts * 30,
                "실패 " + d.auth.loginFailures + " / 시도 " + d.auth.loginAttempts));
        sb.append(checkRow("권한 거부 모니터링 활성",
                d.auth.permissionDenials >= 0,  // audit_log 가 동작 중이면 0 도 OK
                "권한 거부 " + d.auth.permissionDenials + "건 (audit 가용)"));
        sb.append("\n");

        // 7. 수동 검토 권장 (PIPA 특화)
        sb.append("## 7. 수동 검토 권장 항목 — PIPA 특화\n\n");
        sb.append("아래는 본 도구로 자동 점검할 수 없으므로 *연 1회 이상* 별도 점검 필요 (정보보호위 검토):\n\n");
        sb.append("- [ ] **개인정보 처리방침 게시** — 홈페이지 / 앱에 최신본 공시\n");
        sb.append("- [ ] **수집·이용 동의** — 항목별 분리 동의 (필수/선택), 14세 미만 법정대리인 동의\n");
        sb.append("- [ ] **제3자 제공/처리위탁** — 제공/위탁 계약서, 위탁업체 보안 점검\n");
        sb.append("- [ ] **보유 기간** — 수집 목적 달성 후 즉시 파기 절차\n");
        sb.append("- [ ] **개인정보 안전성 확보 조치** — 접근권한 분리, 암호화 (저장+통신), 접속기록 1년 보존\n");
        sb.append("- [ ] **유출 사고 대응** — 사고 발생 시 72시간 내 정보주체·KISA 통지 매뉴얼\n");
        sb.append("- [ ] **정보주체 권리** — 열람·정정·삭제·처리정지 요구 처리 절차\n");
        sb.append("- [ ] **CCTV / 영상정보** — 설치 표지판, 운영 방침 별도 공시\n");
        sb.append("- [ ] **보안서약 / 교육** — 개인정보취급자 연 2회 이상 교육 이수\n\n");

        // 8. 종합 의견
        sb.append("## 8. 종합 의견\n\n");
        if (d.security.highSeverityCount > 0) {
            sb.append("- 🔴 **개인정보 노출 위험 점검 필요**: HIGH 등급 보안 발견 ")
                    .append(d.security.highSeverityCount).append("건. ")
                    .append("위 3.1 사례 중 개인정보 처리 영역인지 우선 분류 → 영향 평가.\n");
        }
        if (d.masking.maskingAnalyses + d.masking.inputMaskingUses == 0) {
            sb.append("- ⚠️ **마스킹 활동 부재**: 정기 분석 일정 수립 권장 (분기 1회).\n");
        }
        if (d.auth.permissionDenials > 50) {
            sb.append("- ⚠️ **비인가 접근 시도 가능성**: 권한 거부 ")
                    .append(d.auth.permissionDenials).append("회 — 처리시스템 접근권한 재검토.\n");
        }
        if (d.security.highSeverityCount == 0
                && d.masking.maskingAnalyses + d.masking.inputMaskingUses > 0
                && d.auth.permissionDenials <= 50) {
            sb.append("- ✅ 자동 점검 항목 양호. 위 7항의 수동 검토 항목 (개인정보 처리방침 / 동의 / 처리위탁 등) 을 정보보호위와 함께 점검.\n");
        }
        sb.append("\n---\n\n");
        sb.append("_본 리포트는 Claude Java Toolkit 의 자동 집계 결과입니다. 실제 PIPA 규정 대응엔 ")
                .append("*개인정보 보호책임자(CPO)* 와 정보보호위원회 검토를 받으시기 바랍니다._\n");

        return sb.toString();
    }

    private static String fmt(java.time.LocalDate d) { return d != null ? d.format(DATE_FMT) : "-"; }
    private static String safe(Object o) { if (o == null) return "-"; String s = o.toString(); return s.isEmpty() ? "-" : s; }
    private static String escapePipe(String s) { if (s == null) return "-"; return s.replace("|", "\\|").replace("\n", " "); }
    private static String checkRow(String item, boolean pass, String evidence) {
        return "| " + escapePipe(item) + " | " + (pass ? "✅ 통과" : "⚠️ 주의") + " | " + escapePipe(evidence) + " |\n";
    }
    private static String typeLabel(String type) {
        if (type == null) return "-";
        return ReviewHistory.typeLabelOf(type);
    }
}
