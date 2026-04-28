package io.github.claudetoolkit.ui.compliance.template;

import io.github.claudetoolkit.ui.compliance.ComplianceData;
import io.github.claudetoolkit.ui.history.ReviewHistory;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * v4.6.x — *정보통신망법* (정보통신망 이용촉진 및 정보보호 등에 관한 법률) 보안 점검 리포트.
 *
 * <p>핵심 의무: 접근통제 / 암호화 / 접속기록 보존 (1년 이상) / 침해사고 대응 / 외부망 분리.
 *
 * <p>FSS 와 PIPA 와 비교한 차별점: *접속기록 보존* 과 *침입 탐지* 에 비중. audit_log
 * 누적 건수와 5xx / 인증 실패 패턴이 핵심 지표.
 */
public final class NetworkActTemplate {

    private NetworkActTemplate() {}

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String build(ComplianceData d) {
        StringBuilder sb = new StringBuilder(8192);

        // 표지
        sb.append("# 정보통신망법 보안 점검 리포트\n\n");
        sb.append("> 자동 집계 — 접근통제 / 접속기록 / 침해사고 대응 관점. 정보보호 최고책임자(CISO) 검토 필수.\n\n");

        // 1. 개요
        sb.append("## 1. 리포트 개요\n\n");
        sb.append("| 항목 | 내용 |\n|---|---|\n");
        sb.append("| 감사 기간 | ").append(fmt(d.from)).append(" ~ ").append(fmt(d.to)).append(" |\n");
        sb.append("| 보고서 생성 | ").append(d.generatedAt).append(" |\n");
        sb.append("| 생성자 | ").append(safe(d.generatedBy)).append(" |\n");
        sb.append("| 적용 법령 | 정보통신망 이용촉진 및 정보보호 등에 관한 법률 |\n\n");

        // 2. 접근 통제 (인증/권한)
        sb.append("## 2. 접근 통제 — 인증/권한 (audit_log)\n\n");
        sb.append("| 항목 | 발생 횟수 | 의미 |\n|---|---|---|\n");
        sb.append("| 전체 API 호출 | ").append(d.auth.totalAuditEntries).append(" | 접속기록 보존 대상 |\n");
        sb.append("| 로그인 시도 | ").append(d.auth.loginAttempts).append(" | 정상/실패 모두 |\n");
        sb.append("| 로그인 실패 | ").append(d.auth.loginFailures);
        if (d.auth.loginAttempts > 0) {
            double failRate = 100.0 * d.auth.loginFailures / d.auth.loginAttempts;
            sb.append(" (").append(String.format(Locale.ROOT, "%.1f%%", failRate)).append(")");
        }
        sb.append(" | 비정상 접근 시도 가능성 |\n");
        sb.append("| 권한 거부 (HTTP 403) | ").append(d.auth.permissionDenials).append(" | 비인가 자원 접근 차단 |\n");
        sb.append("| 시스템 오류 (HTTP 5xx) | ").append(d.auth.apiCalls5xx).append(" | 잠재적 DoS / 침해 |\n\n");

        // 침해 의심 임계 알림
        if (d.auth.loginFailures > 100) {
            sb.append("⚠️ **로그인 실패 100회 초과** — Brute-force 공격 의심. KISA 침해사고 대응 매뉴얼 점검 권장.\n\n");
        }
        if (d.auth.permissionDenials > 100) {
            sb.append("⚠️ **권한 거부 100회 초과** — 권한 상승 시도 가능성. IP 추적 및 차단 검토.\n\n");
        }
        if (d.auth.apiCalls5xx * 100 > d.auth.totalAuditEntries) {
            sb.append("⚠️ **5xx 오류율 1% 초과** — 안정성 또는 자원 고갈 공격 가능성.\n\n");
        }

        // 3. 접속기록 보존
        sb.append("## 3. 접속기록 보존 현황\n\n");
        long retainedDays = java.time.temporal.ChronoUnit.DAYS.between(d.from, d.to) + 1;
        sb.append("- 본 감사 기간 (").append(retainedDays).append("일) 누적 audit_log: **")
                .append(d.auth.totalAuditEntries).append("건**\n");
        sb.append("- 정보통신망법 시행령 제15조: 개인정보 처리시스템 접속기록을 *최소 1년* 보관 의무\n");
        sb.append("- 정기 보존정책 점검: ").append(retainedDays >= 365 ? "✅ 1년 이상" : "⚠️ 1년 미만 — 별도 보관 정책 점검").append("\n\n");

        // 4. 보안 사고 / 취약점
        sb.append("## 4. 보안 취약점 분석 활동\n\n");
        long sec = d.security.totalSecurityReviews;
        sb.append("- 보안 분석 (SQL 보안 + 코드 보안): **").append(sec).append("건**\n");
        if (sec > 0) {
            sb.append("- 발견 등급: 🔴 HIGH ").append(d.security.highSeverityCount)
                    .append(" / 🟡 MEDIUM ").append(d.security.mediumSeverityCount)
                    .append(" / 🟢 LOW ").append(d.security.lowSeverityCount).append("\n\n");
        }

        if (d.security.recentHighFindings != null && !d.security.recentHighFindings.isEmpty()) {
            sb.append("### 4.1 HIGH 등급 발견 — 침해사고 대응 검토 필요\n\n");
            sb.append("| # | 분석 유형 | 제목 | 사용자 | 일시 |\n|---|---|---|---|---|\n");
            int i = 1;
            for (Map<String, Object> f : d.security.recentHighFindings) {
                sb.append("| ").append(i++).append(" | ")
                        .append(safe(f.get("typeLabel"))).append(" | ")
                        .append(escapePipe(safe(f.get("title")))).append(" | ")
                        .append(safe(f.get("username"))).append(" | ")
                        .append(safe(f.get("createdAt"))).append(" |\n");
            }
            sb.append("\n");
        }

        // 5. 분석 활동
        sb.append("## 5. 분석 활동 통계 (기간 내)\n\n");
        sb.append("- 전체 분석 건수: **").append(d.totalAnalysisInPeriod).append("건**\n");
        sb.append("- 분석 사용자 수: **").append(d.totalAnalysisByUserCount).append("명**\n\n");

        // 6. 자동 점검
        sb.append("## 6. 정보통신망법 자동 점검 항목\n\n");
        sb.append("| 점검 항목 | 결과 | 근거 |\n|---|---|---|\n");
        sb.append(checkRow("접속기록 보존 활성 (audit_log 작동)",
                d.auth.totalAuditEntries > 0 || d.totalAnalysisInPeriod == 0,
                "audit_log " + d.auth.totalAuditEntries + "건"));
        sb.append(checkRow("로그인 실패율 < 30%",
                d.auth.loginAttempts == 0 || d.auth.loginFailures * 100 < d.auth.loginAttempts * 30,
                "실패 " + d.auth.loginFailures + " / 시도 " + d.auth.loginAttempts));
        sb.append(checkRow("HIGH 등급 침해 의심 0건",
                d.security.highSeverityCount == 0,
                "HIGH " + d.security.highSeverityCount + "건"));
        sb.append(checkRow("Brute-force 의심 패턴 없음 (실패 < 100)",
                d.auth.loginFailures < 100,
                "로그인 실패 " + d.auth.loginFailures + "건"));
        sb.append(checkRow("5xx 오류율 < 1%",
                d.auth.totalAuditEntries == 0 || d.auth.apiCalls5xx * 100 < d.auth.totalAuditEntries,
                "5xx " + d.auth.apiCalls5xx + " / " + d.auth.totalAuditEntries));
        sb.append("\n");

        // 7. 수동 검토 (정보통신망법 특화)
        sb.append("## 7. 수동 검토 권장 항목 — 정보통신망법 특화\n\n");
        sb.append("- [ ] **CISO 지정** — 자산총액 5조원 이상 또는 매출 3천억원 이상시 의무\n");
        sb.append("- [ ] **외부망/내부망 분리** — 인터넷망 ↔ 처리시스템망 물리적/논리적 분리\n");
        sb.append("- [ ] **암호화** — 비밀번호, 주민번호, 계좌번호 등 저장 시 일방향 / 양방향 암호화\n");
        sb.append("- [ ] **침입탐지/차단** — IDS / IPS / WAF 운영 + 룰 정기 갱신\n");
        sb.append("- [ ] **취약점 점검** — 연 1회 이상 외부 모의해킹 / 인프라 점검\n");
        sb.append("- [ ] **사고 보고** — 침해사고 발생 시 24시간 내 KISA·과기정통부 신고\n");
        sb.append("- [ ] **보안 패치** — OS / 미들웨어 / 라이브러리 정기 업데이트 (월 1회)\n");
        sb.append("- [ ] **정보보호 인증** — ISMS / ISMS-P / ISO 27001 보유 + 갱신\n");
        sb.append("- [ ] **DDoS 방어** — 트래픽 모니터링 + L4 / L7 방어 장비\n\n");

        // 8. 종합 의견
        sb.append("## 8. 종합 의견\n\n");
        if (d.security.highSeverityCount > 0) {
            sb.append("- 🔴 **즉시 조치**: HIGH 등급 ").append(d.security.highSeverityCount)
                    .append("건. 위 4.1 사례 침해사고 대응 절차 적용 검토.\n");
        }
        if (d.auth.loginFailures > 100) {
            sb.append("- ⚠️ **Brute-force 방어 강화**: 로그인 실패 ").append(d.auth.loginFailures)
                    .append("회 — IP 차단·계정 잠금 정책 점검.\n");
        }
        if (d.auth.totalAuditEntries == 0 && d.totalAnalysisInPeriod > 0) {
            sb.append("- ⚠️ **접속기록 누락 의심**: audit_log 가 비어 있으나 분석 활동 ")
                    .append(d.totalAnalysisInPeriod).append("건 — AuditLogFilter 동작 확인.\n");
        }
        if (d.security.highSeverityCount == 0
                && d.auth.loginFailures < 100
                && d.auth.totalAuditEntries > 0) {
            sb.append("- ✅ 자동 점검 양호. 위 7항의 CISO/망분리/암호화/모의해킹 정기 점검 진행.\n");
        }
        sb.append("\n---\n\n");
        sb.append("_본 리포트는 자동 집계 결과입니다. 실제 정보통신망법 대응엔 *정보보호 최고책임자(CISO)* ")
                .append("및 ISMS 인증심사원 자문을 받으시기 바랍니다._\n");

        return sb.toString();
    }

    private static String fmt(java.time.LocalDate d) { return d != null ? d.format(DATE_FMT) : "-"; }
    private static String safe(Object o) { if (o == null) return "-"; String s = o.toString(); return s.isEmpty() ? "-" : s; }
    private static String escapePipe(String s) { if (s == null) return "-"; return s.replace("|", "\\|").replace("\n", " "); }
    private static String checkRow(String item, boolean pass, String evidence) {
        return "| " + escapePipe(item) + " | " + (pass ? "✅ 통과" : "⚠️ 주의") + " | " + escapePipe(evidence) + " |\n";
    }
}
