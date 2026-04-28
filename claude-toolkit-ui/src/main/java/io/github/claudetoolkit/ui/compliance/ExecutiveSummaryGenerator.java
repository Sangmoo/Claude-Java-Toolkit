package io.github.claudetoolkit.ui.compliance;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * v4.6.x Stage 3 — Claude API 기반 *경영진 요약 (Executive Summary)* 생성기.
 *
 * <p>{@link ComplianceData} 의 핵심 지표를 텍스트 입력으로 변환하여 Claude 에 보내고,
 * 3-5문장의 한국어 요약을 받아서 markdown 리포트 상단에 삽입.
 *
 * <p>옵션 기능 — UI 의 체크박스가 켜져 있을 때만 호출 (Claude API 비용 발생).
 * 실패 시 빈 문자열 반환 → 기본 markdown 으로 fallback.
 */
@Service
public class ExecutiveSummaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(ExecutiveSummaryGenerator.class);

    /** 토큰 한도 — 결과 요약은 짧으니 1000 충분. */
    private static final int MAX_TOKENS = 1000;

    private final ClaudeClient claudeClient;

    public ExecutiveSummaryGenerator(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * 데이터 기반 경영진 요약 생성.
     *
     * @return 3-5문장 한국어 markdown 텍스트, 실패 시 null (호출부에서 빈 처리)
     */
    public String generate(ComplianceData d) {
        if (d == null) return null;
        try {
            String system = buildSystemPrompt(d.type);
            String user   = buildUserMessage(d);
            String result = claudeClient.chat(system, user, MAX_TOKENS);
            if (result == null) return null;
            return result.trim();
        } catch (Exception e) {
            log.warn("[Compliance] Executive Summary 생성 실패 — fallback to no-summary: {}",
                    e.getMessage());
            return null;
        }
    }

    private String buildSystemPrompt(ComplianceReportType type) {
        return "당신은 한국 IT 컴플라이언스·감사 전문가입니다. " + type.getLabel() +
                " 점검 데이터를 보고 *경영진(C-level) 이 1분 안에 읽을 수 있는 요약* 을 작성하세요.\n\n" +
                "반드시 따를 형식:\n" +
                "- 정확히 3~5 문장\n" +
                "- 첫 문장: 한 줄로 전반 상태 (양호 / 주의 / 위험 중 하나로 분류)\n" +
                "- 중간: 가장 중요한 1-2개 수치 + 그 의미\n" +
                "- 마지막: 우선 조치 권고 또는 추가 점검 권고\n" +
                "- 한국어 마크다운, 강조 필요 시 **bold** 사용\n" +
                "- 추측 금지 — 입력에 없는 수치 만들지 마세요\n" +
                "- 결과만 출력 (사족, 제목 헤더 X)";
    }

    private String buildUserMessage(ComplianceData d) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("# 점검 데이터 (").append(d.type.getLabel()).append(")\n\n");
        sb.append("- 감사 기간: ").append(d.from).append(" ~ ").append(d.to).append("\n");
        sb.append("- 기간 내 분석 건수: ").append(d.totalAnalysisInPeriod).append("건\n");
        sb.append("- 분석 수행 사용자: ").append(d.totalAnalysisByUserCount).append("명\n");

        sb.append("\n## 보안 발견\n");
        sb.append("- 보안 분석 누적: ").append(d.security.totalSecurityReviews).append("건\n");
        sb.append("- HIGH 등급 발견: ").append(d.security.highSeverityCount).append("건\n");
        sb.append("- MEDIUM: ").append(d.security.mediumSeverityCount).append("건 / LOW: ")
                .append(d.security.lowSeverityCount).append("건\n");

        sb.append("\n## 인증/권한\n");
        sb.append("- audit_log 누적: ").append(d.auth.totalAuditEntries).append("건\n");
        sb.append("- 로그인 시도: ").append(d.auth.loginAttempts).append("건 / 실패: ")
                .append(d.auth.loginFailures).append("건");
        if (d.auth.loginAttempts > 0) {
            sb.append(String.format(java.util.Locale.ROOT, " (실패율 %.1f%%)",
                    100.0 * d.auth.loginFailures / d.auth.loginAttempts));
        }
        sb.append("\n");
        sb.append("- 권한 거부 (HTTP 403): ").append(d.auth.permissionDenials).append("건\n");
        sb.append("- 서버 오류 (HTTP 5xx): ").append(d.auth.apiCalls5xx).append("건\n");

        sb.append("\n## 개인정보 보호\n");
        sb.append("- 데이터 마스킹 분석: ").append(d.masking.maskingAnalyses).append("건\n");
        sb.append("- 입력 마스킹 사용: ").append(d.masking.inputMaskingUses).append("건\n");

        if (!d.activityByType.isEmpty()) {
            sb.append("\n## 분석 유형별 분포\n");
            int n = 0;
            for (Map.Entry<String, Long> e : d.activityByType.entrySet()) {
                if (n++ >= 5) break;  // top 5 만
                sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("건\n");
            }
        }

        sb.append("\n위 데이터로 경영진 요약을 작성해 주세요.");
        return sb.toString();
    }
}
