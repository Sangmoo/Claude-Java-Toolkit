package io.github.claudetoolkit.ui.harness;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.stereotype.Service;

/**
 * Harness pipeline service: Analyst → Builder → Reviewer
 *
 * <p>Runs a 3-step structured AI review pipeline in a single Claude call.
 * The response is divided into clearly-delimited Markdown sections so the
 * UI can render diff view, analysis, change log and review verdict separately.
 *
 * <p>Supports Java/Spring and Oracle SQL inputs.
 */
@Service
public class HarnessReviewService {

    private final ClaudeClient    claudeClient;
    private final ToolkitSettings settings;

    public HarnessReviewService(ClaudeClient claudeClient, ToolkitSettings settings) {
        this.claudeClient = claudeClient;
        this.settings     = settings;
    }

    /**
     * Runs the full harness pipeline and returns the raw structured response.
     *
     * @param code     source code to analyze (Java or SQL)
     * @param language "java" or "sql"
     * @return raw Claude response with ## section headers
     */
    public String analyze(String code, String language) {
        String systemPrompt = buildSystemPrompt(language);
        String memoContext  = settings.getProjectContext();
        if (memoContext != null && !memoContext.trim().isEmpty()) {
            systemPrompt = systemPrompt + "\n\n[프로젝트 컨텍스트]\n" + memoContext;
        }
        // 하네스는 5개 섹션(분석·개선코드·변경내역·기대효과·검토의견)을 한 번에 출력하므로
        // 큰 소스일수록 응답이 길어짐 — 모델 최대값(8192)을 명시해 중간 잘림 방지
        return claudeClient.chat(systemPrompt, buildUserMessage(code, language), 8192);
    }

    /**
     * Extracts the improved code block from the "## 🔧 개선된 코드" section.
     *
     * @param response raw Claude response text
     * @param language "java" or "sql"
     * @return extracted code string, or empty string if not found
     */
    public String extractImprovedCode(String response, String language) {
        // Narrow search to the section starting with 개선된 코드
        String[] markers = {"## 🔧 개선된 코드", "## 개선된 코드"};
        int sectionIdx = -1;
        for (String m : markers) {
            int idx = response.indexOf(m);
            if (idx >= 0) { sectionIdx = idx; break; }
        }
        String searchIn = sectionIdx >= 0 ? response.substring(sectionIdx) : response;

        // Find first code fence in that section (prefer language-tagged fence)
        String langTag = "sql".equalsIgnoreCase(language) ? "```sql" : "```java";
        int start = searchIn.indexOf(langTag);
        if (start == -1) start = searchIn.indexOf("```");
        if (start == -1) return "";

        int codeStart = searchIn.indexOf("\n", start);
        if (codeStart == -1) return "";
        codeStart = codeStart + 1;

        int end = searchIn.indexOf("```", codeStart);
        if (end == -1) return searchIn.substring(codeStart).trim();
        return searchIn.substring(codeStart, end).trim();
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    private String buildSystemPrompt(String language) {
        boolean isSql    = "sql".equalsIgnoreCase(language);
        String langName  = isSql ? "Oracle SQL"   : "Java/Spring";
        String codeBlock = isSql ? "sql"           : "java";

        return "당신은 " + langName + " 코드 품질 개선 파이프라인입니다.\n"
             + "다음 3단계 하네스(Harness) 프로세스를 순서대로 실행하여 코드를 개선하세요:\n\n"
             + "**1단계 — 분석가(Analyst)**: 입력 코드를 꼼꼼히 검토하여 성능 문제, 안티패턴,"
             + " 가독성 문제, 보안 취약점, 개선 가능 지점을 파악합니다.\n"
             + "**2단계 — 개선가(Builder)**: 분석 결과를 토대로 모든 문제를 해결한 개선 코드를"
             + " 작성합니다. 원본의 의도와 기능을 유지하면서 품질을 높입니다.\n"
             + "**3단계 — 검토자(Reviewer)**: 개선 코드의 각 변경점을 검증하고 변경 내역과"
             + " 기대 효과를 정리한 뒤, 최종 합격(APPROVED) 또는 추가 수정 필요(NEEDS_REVISION)"
             + " 판정을 내립니다.\n\n"
             + "반드시 아래 형식을 정확히 지켜서 응답하세요:\n\n"
             + "## 📋 분석 요약\n"
             + "[분석가 결과: 발견된 문제점을 항목 목록(- )으로]\n\n"
             + "## 🔧 개선된 코드\n"
             + "```" + codeBlock + "\n"
             + "[개선가 결과: 완성된 전체 개선 코드]\n"
             + "```\n\n"
             + "## 📝 변경 내역\n"
             + "[검토자 결과: 각 변경 사항과 이유를 항목 목록(- )으로]\n\n"
             + "## 📈 기대 효과\n"
             + "[검토자 결과: 성능·가독성·유지보수성·보안 측면 개선 효과]\n\n"
             + "## ✅ 최종 검토 의견\n"
             + "[검토자 결과: 종합 판정(APPROVED / NEEDS_REVISION), 심각도, 주의 사항]";
    }

    private String buildUserMessage(String code, String language) {
        boolean isSql    = "sql".equalsIgnoreCase(language);
        String langLabel = isSql ? "SQL"  : "Java";
        String codeBlock = isSql ? "sql"  : "java";
        return "다음 " + langLabel + " 코드를 3단계 하네스 파이프라인으로 분석하고 개선해주세요:\n\n"
             + "```" + codeBlock + "\n" + code + "\n```";
    }
}
