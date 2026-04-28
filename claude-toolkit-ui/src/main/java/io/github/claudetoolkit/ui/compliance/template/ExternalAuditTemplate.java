package io.github.claudetoolkit.ui.compliance.template;

import io.github.claudetoolkit.ui.compliance.ComplianceData;
import io.github.claudetoolkit.ui.history.ReviewHistory;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * v4.6.x — *외부감사 대응* 종합 리포트 Markdown 빌더.
 *
 * <p>FSS / PIPA / 정보통신망법 관점을 한 문서로 합친 *총괄 리포트*.
 * 외부감사인 / 회계법인 IT 감리시 대응용. 감사 기간 내 *모든 IT 통제 활동* 을
 * 한 곳에 모아 보여줌.
 *
 * <p>특징:
 * <ul>
 *   <li>각 영역의 핵심 지표만 추려서 *Executive Summary 표* 로 시작</li>
 *   <li>3개 법령 점검 결과를 *횡단* 표로 비교</li>
 *   <li>외부감사인이 추가 질의 가능한 *증빙 위치* 명시</li>
 * </ul>
 */
public final class ExternalAuditTemplate {

    private ExternalAuditTemplate() {}

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String build(ComplianceData d) {
        StringBuilder sb = new StringBuilder(12_288);

        // 표지
        sb.append("# 외부감사 대응 종합 리포트 (IT 통제)\n\n");
        sb.append("> 외부 감사인 (회계법인 IT 감리 / KISA / 금감원 등) 대응용 종합 리포트.\n");
        sb.append("> FSS · PIPA · 정보통신망법 관점을 한 문서로 묶음. *법무·정보보호 최고책임자* 동반 검토 필수.\n\n");

        // 1. Executive Summary
        sb.append("## 1. 경영진 요약 (Executive Summary)\n\n");
        sb.append("| 영역 | 핵심 지표 | 결과 |\n|---|---|---|\n");
        sb.append("| 분석 활동 | 기간 내 분석 건수 | **").append(d.totalAnalysisInPeriod).append("건** (").append(d.totalAnalysisByUserCount).append("명) |\n");
        sb.append("| 보안 점검 | 보안 분석 / HIGH 발견 | ").append(d.security.totalSecurityReviews)
                .append("건 / 🔴 **").append(d.security.highSeverityCount).append("건** |\n");
        sb.append("| 접근 통제 | 로그인 시도 / 실패 | ").append(d.auth.loginAttempts).append(" / ").append(d.auth.loginFailures);
        if (d.auth.loginAttempts > 0) {
            sb.append(" (").append(String.format(Locale.ROOT, "%.1f%%", 100.0 * d.auth.loginFailures / d.auth.loginAttempts)).append(")");
        }
        sb.append(" |\n");
        sb.append("| 권한 통제 | 권한 거부 / 5xx | ").append(d.auth.permissionDenials)
                .append(" / ").append(d.auth.apiCalls5xx).append(" |\n");
        sb.append("| 개인정보 보호 | 마스킹 활동 | ").append(d.masking.maskingAnalyses + d.masking.inputMaskingUses).append("건 |\n");
        sb.append("| 접속기록 보존 | audit_log 누적 | **").append(d.auth.totalAuditEntries).append("건** |\n\n");

        // 2. 감사 메타
        sb.append("## 2. 감사 메타정보\n\n");
        sb.append("| 항목 | 내용 |\n|---|---|\n");
        sb.append("| 감사 기간 | ").append(fmt(d.from)).append(" ~ ").append(fmt(d.to))
                .append(" (").append(java.time.temporal.ChronoUnit.DAYS.between(d.from, d.to) + 1).append("일) |\n");
        sb.append("| 보고서 생성 | ").append(d.generatedAt).append(" |\n");
        sb.append("| 생성자 | ").append(safe(d.generatedBy)).append(" |\n");
        sb.append("| 점검 도구 | Claude Java Toolkit (자동 집계) |\n");
        sb.append("| 데이터 출처 | review_history · audit_log (H2/MySQL/Oracle/PostgreSQL) |\n\n");

        // 3. 영역별 통제 결과 — 3개 법령 횡단 비교
        sb.append("## 3. 법령별 통제 항목 비교\n\n");
        sb.append("| 통제 항목 | FSS 전자금융 | PIPA 개인정보 | 정보통신망법 | 결과 근거 |\n");
        sb.append("|---|:---:|:---:|:---:|---|\n");

        boolean fssAnalysis = d.totalAnalysisInPeriod >= 10;
        boolean fssSec = d.security.totalSecurityReviews >= 1;
        boolean fssHigh = d.security.highSeverityCount == 0;
        boolean authOk = d.auth.loginAttempts == 0
                || d.auth.loginFailures * 100 < d.auth.loginAttempts * 30;
        boolean masking = d.masking.maskingAnalyses + d.masking.inputMaskingUses > 0;
        boolean auditOk = d.auth.totalAuditEntries > 0 || d.totalAnalysisInPeriod == 0;
        boolean noBruteForce = d.auth.loginFailures < 100;

        sb.append("| 정기 보안 분석 | ").append(mark(fssSec)).append(" | ").append(mark(fssSec))
                .append(" | ").append(mark(fssSec)).append(" | 보안 분석 ").append(d.security.totalSecurityReviews).append("건 |\n");
        sb.append("| HIGH 등급 미해결 0 | ").append(mark(fssHigh)).append(" | ").append(mark(fssHigh))
                .append(" | ").append(mark(fssHigh)).append(" | HIGH ").append(d.security.highSeverityCount).append("건 |\n");
        sb.append("| 로그인 실패율 < 30% | ").append(mark(authOk)).append(" | ").append(mark(authOk))
                .append(" | ").append(mark(authOk)).append(" | 실패 ").append(d.auth.loginFailures).append(" / 시도 ").append(d.auth.loginAttempts).append(" |\n");
        sb.append("| 권한 거부 모니터링 | ").append(mark(true)).append(" | ").append(mark(true))
                .append(" | ").append(mark(true)).append(" | 권한 거부 ").append(d.auth.permissionDenials).append("건 (관측 가능) |\n");
        sb.append("| 마스킹 활동 1+ | ").append(mark(masking)).append(" | **").append(mark(masking))
                .append("** | ").append(mark(masking)).append(" | 마스킹 ").append(d.masking.maskingAnalyses + d.masking.inputMaskingUses).append("건 |\n");
        sb.append("| 접속기록 보존 | ").append(mark(auditOk)).append(" | ").append(mark(auditOk))
                .append(" | **").append(mark(auditOk)).append("** | audit_log ").append(d.auth.totalAuditEntries).append("건 |\n");
        sb.append("| Brute-force 방어 | ").append(mark(noBruteForce)).append(" | ").append(mark(noBruteForce))
                .append(" | **").append(mark(noBruteForce)).append("** | 로그인 실패 ").append(d.auth.loginFailures).append("건 |\n");
        sb.append("| 분기 분석 활동 10+ | ").append(mark(fssAnalysis)).append(" | ").append(mark(fssAnalysis))
                .append(" | ").append(mark(fssAnalysis)).append(" | 기간 분석 ").append(d.totalAnalysisInPeriod).append("건 |\n");
        sb.append("\n_**굵은 표시** = 해당 법령에서 특히 강조하는 의무 항목_\n\n");

        // 4. 위험 사항 종합
        sb.append("## 4. 위험 사항 종합\n\n");
        boolean anyRisk = false;
        if (d.security.highSeverityCount > 0) {
            sb.append("### 🔴 4.1 즉시 조치 필요\n");
            sb.append("- HIGH 등급 보안 발견 **").append(d.security.highSeverityCount).append("건** ")
                    .append("(SQL 인젝션 / 코드 보안 취약점). 외부감사인이 사례 상세 요청 가능.\n\n");
            anyRisk = true;
        }
        if (d.auth.loginFailures >= 100) {
            sb.append("### ⚠️ 4.2 비정상 접근 모니터링 강화\n");
            sb.append("- 로그인 실패 **").append(d.auth.loginFailures).append("회** ")
                    .append("— Brute-force 의심. IP 차단·계정 잠금 정책 검토.\n\n");
            anyRisk = true;
        }
        if (d.auth.permissionDenials > 50) {
            sb.append("### ⚠️ 4.3 권한 통제 점검\n");
            sb.append("- 권한 거부 **").append(d.auth.permissionDenials).append("회** ")
                    .append("— 비인가 자원 접근 시도. 권한 정책 / 사용자 RBAC 점검.\n\n");
            anyRisk = true;
        }
        if (d.masking.maskingAnalyses + d.masking.inputMaskingUses == 0) {
            sb.append("### ℹ️ 4.4 개인정보 마스킹 활동 부재\n");
            sb.append("- 기간 내 마스킹 분석 0건 — 운영 절차상 정기 마스킹 필요시 일정 수립.\n\n");
            anyRisk = true;
        }
        if (!anyRisk) {
            sb.append("✅ 자동 점검 결과 *유의미한 위험 사항 없음*. 단, 본 도구가 자동 점검할 수 없는 ")
                    .append("물리적 보안 / 외주 관리 / 사고 대응 매뉴얼 등은 별도 점검 필요.\n\n");
        }

        // 5. 보안 발견 사례 (HIGH)
        if (d.security.recentHighFindings != null && !d.security.recentHighFindings.isEmpty()) {
            sb.append("## 5. HIGH 등급 보안 발견 사례\n\n");
            sb.append("| # | 분석 유형 | 제목 | 사용자 | 일시 |\n|---|---|---|---|---|\n");
            int i = 1;
            for (Map<String, Object> f : d.security.recentHighFindings) {
                sb.append("| ").append(i++).append(" | ")
                        .append(safe(f.get("typeLabel"))).append(" | ")
                        .append(escapePipe(safe(f.get("title")))).append(" | ")
                        .append(safe(f.get("username"))).append(" | ")
                        .append(safe(f.get("createdAt"))).append(" |\n");
            }
            sb.append("\n💡 *외부감사인 추가 자료 요청시* — `리뷰 이력 → 검색` 에서 사례 ID 로 원문 조회 가능.\n\n");
        }

        // 6. 분석 활동 분포
        sb.append("## 6. 분석 활동 분포 (기간 내)\n\n");
        if (!d.activityByType.isEmpty()) {
            sb.append("| 분석 유형 | 건수 |\n|---|---|\n");
            for (Map.Entry<String, Long> e : d.activityByType.entrySet()) {
                sb.append("| ").append(escapePipe(typeLabel(e.getKey())))
                        .append(" | ").append(e.getValue()).append(" |\n");
            }
            sb.append("\n");
        } else {
            sb.append("기간 내 분석 활동 없음.\n\n");
        }

        // 7. 자동 집계 외 별도 점검 필요
        sb.append("## 7. 자동 집계 대상 외 — 별도 점검 / 증빙 필요\n\n");
        sb.append("외부감사인이 추가로 요구할 가능성이 높은 항목 — *증빙 위치를 사전 정리* 권장:\n\n");
        sb.append("### 7.1 IT 거버넌스\n");
        sb.append("- [ ] 정보보호 조직도 (CPO / CISO 직무기술서)\n");
        sb.append("- [ ] 정보보호 정책 / 지침 / 절차서\n");
        sb.append("- [ ] 위탁업체 보안 점검 결과 (외주 인력 포함)\n\n");
        sb.append("### 7.2 시스템 운영\n");
        sb.append("- [ ] 변경관리 결재 이력 (운영 반영 전 검토 / 승인)\n");
        sb.append("- [ ] 백업 / 복구 훈련 결과 (분기 1회 이상)\n");
        sb.append("- [ ] 패치 관리 이력 (OS / 미들웨어 / 라이브러리)\n\n");
        sb.append("### 7.3 보안 통제\n");
        sb.append("- [ ] 망 분리 구성도 (운영 / 개발 / 외부망)\n");
        sb.append("- [ ] 암호화 적용 범위 (저장 + 통신)\n");
        sb.append("- [ ] 침해사고 대응 매뉴얼 + 모의훈련 결과\n");
        sb.append("- [ ] 외부 모의해킹 / 취약점 진단 결과 (연 1회)\n\n");
        sb.append("### 7.4 개인정보\n");
        sb.append("- [ ] 개인정보 처리방침 (홈페이지 게시, 최신본)\n");
        sb.append("- [ ] 동의서 양식 (수집·이용 / 제3자 제공 / 마케팅)\n");
        sb.append("- [ ] 개인정보 영향평가 결과 (PIA, 해당시)\n");
        sb.append("- [ ] 개인정보 안전성 확보조치 점검 결과\n\n");
        sb.append("### 7.5 인증 / 자격\n");
        sb.append("- [ ] ISMS / ISMS-P / ISO 27001 인증서 (유효기간 확인)\n");
        sb.append("- [ ] 정보보호 교육 이수 기록 (연 2회 이상)\n");
        sb.append("- [ ] 비밀유지 서약서 (전 직원 + 외주 인력)\n\n");

        // 8. 종합 의견
        sb.append("## 8. 종합 의견\n\n");
        sb.append("- 본 자동 집계 결과 ").append(anyRisk ? "**일부 위험 사항 존재**" : "**큰 이상 없음**")
                .append(" — 구체적 수치는 위 1\\. 경영진 요약 표 참조.\n");
        sb.append("- 외부감사인이 *원문 / 증빙* 을 요구할 수 있으므로 위 7항 체크리스트 사전 정리 권장.\n");
        sb.append("- *법령별 횡단 표* (위 3항) 결과를 외부감사 인터뷰 자료로 직접 활용 가능.\n\n");

        sb.append("---\n\n");
        sb.append("_본 리포트는 자동 집계 결과로 외부감사 *증빙 자체* 가 아닌 *대응 자료* 입니다. ")
                .append("실제 감사 대응엔 법무 / CPO / CISO / 외부 자문 종합 검토를 받으시기 바랍니다._\n");

        return sb.toString();
    }

    private static String mark(boolean ok) { return ok ? "✅" : "⚠️"; }
    private static String fmt(java.time.LocalDate d) { return d != null ? d.format(DATE_FMT) : "-"; }
    private static String safe(Object o) { if (o == null) return "-"; String s = o.toString(); return s.isEmpty() ? "-" : s; }
    private static String escapePipe(String s) { if (s == null) return "-"; return s.replace("|", "\\|").replace("\n", " "); }
    private static String typeLabel(String type) {
        if (type == null) return "-";
        return ReviewHistory.typeLabelOf(type);
    }
}
