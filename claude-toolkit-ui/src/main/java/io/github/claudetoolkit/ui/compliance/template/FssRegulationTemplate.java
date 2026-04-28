package io.github.claudetoolkit.ui.compliance.template;

import io.github.claudetoolkit.ui.compliance.ComplianceData;
import io.github.claudetoolkit.ui.history.ReviewHistory;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * v4.6.x — *전자금융감독규정* 보안 점검 리포트 Markdown 빌더.
 *
 * <p>금감원 IT 감리 / 외부감사 대응 서식을 단순화한 형태. 본 시스템에서 자동
 * 수집 가능한 항목만 자동 채움 — 자동으로 채울 수 없는 항목(네트워크 분리,
 * 백업 정책 등)은 *체크리스트 마지막에 수동 검토 안내* 표시.
 *
 * <p>출력 형태: GitHub-flavored Markdown. 같은 파일을 PDF / Confluence /
 * 사내 위키 어디로 내보내도 그대로 가독성 유지.
 *
 * <p><b>중요</b>: 이 리포트는 *자동 집계 도구* 일 뿐, 법적 효력을 갖는
 * 컴플라이언스 증빙은 아니다. 실제 감리 대응에는 *법무팀/컴플라이언스팀
 * 검토* 가 반드시 필요. 리포트 본문에도 같은 disclaimer 가 명시됨.
 */
public final class FssRegulationTemplate {

    private FssRegulationTemplate() {}

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String build(ComplianceData d) {
        StringBuilder sb = new StringBuilder(8192);

        // 표지
        sb.append("# 전자금융감독규정 보안 점검 리포트\n\n");
        sb.append("> 자동 집계 리포트 — 법적 컴플라이언스 증빙 아님. 실제 감리 대응엔 법무/컴플라이언스 팀 검토가 필요합니다.\n\n");

        // 1. 개요
        sb.append("## 1. 리포트 개요\n\n");
        sb.append("| 항목 | 내용 |\n|---|---|\n");
        sb.append("| 감사 기간 | ").append(fmt(d.from)).append(" ~ ").append(fmt(d.to)).append(" |\n");
        sb.append("| 보고서 생성 | ").append(d.generatedAt).append(" |\n");
        sb.append("| 생성자 | ").append(safe(d.generatedBy)).append(" |\n");
        sb.append("| 점검 도구 | Claude Java Toolkit (자동 집계) |\n\n");

        // 2. 보안 점검 결과 요약
        sb.append("## 2. 보안 점검 결과 요약\n\n");
        sb.append("### 2.1 보안 분석 활동\n\n");
        long sec = d.security.totalSecurityReviews;
        sb.append("- 기간 내 보안 분석 (SQL 보안 + 코드 보안 리뷰): **").append(sec).append("건**\n");
        if (sec > 0) {
            sb.append("- 등급별 발견:\n");
            sb.append("  - 🔴 HIGH: **").append(d.security.highSeverityCount).append("건**\n");
            sb.append("  - 🟡 MEDIUM: ").append(d.security.mediumSeverityCount).append("건\n");
            sb.append("  - 🟢 LOW: ").append(d.security.lowSeverityCount).append("건\n");
        } else {
            sb.append("- ⚠️ 보안 분석 활동 없음 — 정기 분석 권장 (월 1회 이상)\n");
        }
        sb.append("\n");

        // 2.2 HIGH 등급 발견 사례 표
        if (d.security.recentHighFindings != null && !d.security.recentHighFindings.isEmpty()) {
            sb.append("### 2.2 HIGH 등급 발견 사례 (최근 ").append(d.security.recentHighFindings.size()).append("건)\n\n");
            sb.append("| # | 분석 유형 | 제목 | 사용자 | 일시 |\n");
            sb.append("|---|---|---|---|---|\n");
            int i = 1;
            for (Map<String, Object> f : d.security.recentHighFindings) {
                sb.append("| ").append(i++).append(" | ")
                        .append(safe(f.get("typeLabel"))).append(" | ")
                        .append(escapePipe(safe(f.get("title")))).append(" | ")
                        .append(safe(f.get("username"))).append(" | ")
                        .append(safe(f.get("createdAt"))).append(" |\n");
            }
            sb.append("\n💡 각 사례의 상세는 **리뷰 이력 → 검색** 에서 ID 로 조회 가능.\n\n");
        }

        // 3. 인증/권한 통제
        sb.append("## 3. 인증·권한 통제 현황\n\n");
        sb.append("(audit_log 기반 — 인증 성공/실패 + 권한 거부 + 5xx 오류)\n\n");
        sb.append("| 항목 | 발생 횟수 |\n|---|---|\n");
        sb.append("| 전체 API 호출 (감사 로그) | ").append(d.auth.totalAuditEntries).append(" |\n");
        sb.append("| 로그인 시도 | ").append(d.auth.loginAttempts).append(" |\n");
        sb.append("| 로그인 실패 (4xx) | ").append(d.auth.loginFailures);
        if (d.auth.loginAttempts > 0) {
            double failRate = 100.0 * d.auth.loginFailures / d.auth.loginAttempts;
            sb.append(" (").append(String.format(Locale.ROOT, "%.1f%%", failRate)).append(")");
        }
        sb.append(" |\n");
        sb.append("| 권한 거부 (HTTP 403) | ").append(d.auth.permissionDenials).append(" |\n");
        sb.append("| 서버 오류 (HTTP 5xx) | ").append(d.auth.apiCalls5xx).append(" |\n\n");

        if (d.auth.loginFailures > 100) {
            sb.append("⚠️ **로그인 실패 횟수가 100회를 초과** 합니다. 비정상 접근 시도 가능성 — 감사 로그 상세 검토 권장.\n\n");
        }
        if (d.auth.permissionDenials > 50) {
            sb.append("⚠️ **권한 거부 50회 초과** — 권한 설정 점검 또는 비인가 접근 시도 의심.\n\n");
        }

        // 4. 데이터 보호
        sb.append("## 4. 개인·민감정보 보호 활동\n\n");
        sb.append("- 데이터 마스킹 분석: **").append(d.masking.maskingAnalyses).append("건**\n");
        sb.append("- 입력 마스킹 사용: **").append(d.masking.inputMaskingUses).append("건**\n\n");

        // 5. 분석 활동 통계
        sb.append("## 5. 기간 내 분석 활동 통계\n\n");
        sb.append("- 전체 분석 건수: **").append(d.totalAnalysisInPeriod).append("건**\n");
        sb.append("- 분석 수행 사용자 수: **").append(d.totalAnalysisByUserCount).append("명**\n\n");

        if (!d.activityByType.isEmpty()) {
            sb.append("### 5.1 분석 유형별 건수\n\n");
            sb.append("| 분석 유형 | 건수 |\n|---|---|\n");
            for (Map.Entry<String, Long> e : d.activityByType.entrySet()) {
                sb.append("| ").append(escapePipe(typeLabel(e.getKey())))
                        .append(" | ").append(e.getValue()).append(" |\n");
            }
            sb.append("\n");
        }

        // 6. 자동 점검 결과 한눈에 — 통과/주의 표
        sb.append("## 6. 자동 점검 항목 요약\n\n");
        sb.append("| 점검 항목 | 결과 | 근거 |\n|---|---|---|\n");
        sb.append(checkRow("정기 보안 분석 수행",      sec >= 1,
                "기간 내 SQL/코드 보안 분석 " + sec + "건"));
        sb.append(checkRow("HIGH 등급 미해결 없음",   d.security.highSeverityCount == 0,
                "HIGH 발견 " + d.security.highSeverityCount + "건"));
        sb.append(checkRow("로그인 실패율 < 30%",
                d.auth.loginAttempts == 0 || d.auth.loginFailures * 100 < d.auth.loginAttempts * 30,
                "실패 " + d.auth.loginFailures + " / 시도 " + d.auth.loginAttempts));
        sb.append(checkRow("서버 5xx 오류율 < 1%",
                d.auth.totalAuditEntries == 0
                        || d.auth.apiCalls5xx * 100 < d.auth.totalAuditEntries,
                "5xx " + d.auth.apiCalls5xx + " / 전체 " + d.auth.totalAuditEntries));
        sb.append(checkRow("개인정보 마스킹 활동 1건 이상",
                d.masking.maskingAnalyses + d.masking.inputMaskingUses > 0,
                "마스킹 " + (d.masking.maskingAnalyses + d.masking.inputMaskingUses) + "건"));
        sb.append("\n");

        // 7. 자동 집계 대상 외 — 수동 검토 권장 항목
        sb.append("## 7. 수동 검토 권장 항목 (자동 집계 대상 아님)\n\n");
        sb.append("아래 항목은 본 도구로 자동 점검할 수 없으므로 *연 1회 이상* 별도 점검 필요:\n\n");
        sb.append("- [ ] **망 분리** — 운영망 ↔ 개발망 ↔ 외부망 통신 정책\n");
        sb.append("- [ ] **암호화** — 저장 데이터 (DB/파일) + 통신 (TLS 1.2+) 적용 여부\n");
        sb.append("- [ ] **백업/복구** — 분기별 복구 훈련, 백업 보관 위치 분리\n");
        sb.append("- [ ] **변경관리** — 운영 반영 전 결재/검토 프로세스\n");
        sb.append("- [ ] **물리적 보안** — 전산실 출입통제, CCTV, 화재 감지\n");
        sb.append("- [ ] **외주 인력** — 비밀유지 서약, 접근권한 별도 관리\n");
        sb.append("- [ ] **사고 대응 매뉴얼** — IRP / DDoS / 침입 등 시나리오별 대응\n\n");

        // 8. 종합 의견
        sb.append("## 8. 종합 의견\n\n");
        if (d.security.highSeverityCount > 0) {
            sb.append("- 🔴 **즉시 조치 필요**: HIGH 등급 보안 발견 ")
                    .append(d.security.highSeverityCount).append("건 존재. 위 2.2 항 사례 우선 검토.\n");
        }
        if (d.auth.permissionDenials > 50) {
            sb.append("- ⚠️ **모니터링 강화 권장**: 권한 거부 ")
                    .append(d.auth.permissionDenials).append("회 — 비인가 접근 시도 가능성.\n");
        }
        if (d.totalAnalysisInPeriod < 10) {
            sb.append("- ℹ️ 기간 내 분석 활동이 ")
                    .append(d.totalAnalysisInPeriod).append("건으로 낮음 — 정기 보안 분석 일정 수립 권장.\n");
        }
        if (d.security.highSeverityCount == 0
                && d.auth.permissionDenials <= 50
                && d.totalAnalysisInPeriod >= 10) {
            sb.append("- ✅ 자동 점검 항목 전반 양호. 위 7항의 수동 검토 항목을 완료하면 점검 일순환 종료.\n");
        }
        sb.append("\n---\n\n");
        sb.append("_본 리포트는 Claude Java Toolkit 의 자동 집계 결과입니다. 법적 컴플라이언스 증빙으로 사용하기 전에 법무·컴플라이언스 팀 검토를 받으시기 바랍니다._\n");

        return sb.toString();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String fmt(java.time.LocalDate d) {
        return d != null ? d.format(DATE_FMT) : "-";
    }

    private static String safe(Object o) {
        if (o == null) return "-";
        String s = o.toString();
        return s.isEmpty() ? "-" : s;
    }

    private static String escapePipe(String s) {
        if (s == null) return "-";
        return s.replace("|", "\\|").replace("\n", " ");
    }

    private static String checkRow(String item, boolean pass, String evidence) {
        String mark = pass ? "✅ 통과" : "⚠️ 주의";
        return "| " + escapePipe(item) + " | " + mark + " | " + escapePipe(evidence) + " |\n";
    }

    private static String typeLabel(String type) {
        if (type == null) return "-";
        return ReviewHistory.typeLabelOf(type);
    }
}
