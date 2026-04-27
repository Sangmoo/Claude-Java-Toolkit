package io.github.claudetoolkit.ui.harness.logrca;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.harness.core.HarnessContext;
import io.github.claudetoolkit.ui.harness.core.HarnessOrchestrator;
import io.github.claudetoolkit.ui.harness.core.HarnessRunResult;
import io.github.claudetoolkit.ui.harness.core.HarnessStage;
import io.github.claudetoolkit.ui.harness.core.PromptLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Phase D — 오류 로그 RCA 하네스 서비스.
 *
 * <p>4-stage 파이프라인:
 * <ol>
 *   <li><b>Analyst</b>  — 증상·타임라인·가설 정리 (3072 토큰)</li>
 *   <li><b>Builder</b>  — 가설별 검증 절차 + 패치 + 롤백 계획 (8192 × 3 cont)</li>
 *   <li><b>Reviewer</b> — 가설 우도 + 부작용 + 권장 우선순위 (4096 토큰)</li>
 *   <li><b>Verifier</b> — 사내 표준 RCA 보고서 + 재발 방지 체크리스트 (4096 토큰)</li>
 * </ol>
 *
 * <p>입력 키 (HarnessContext.inputs):
 * <ul>
 *   <li>{@code error_log}    — 필수, 오류 로그 본문</li>
 *   <li>{@code timeline}     — 선택, 발생 시각/영향 범위 메모</li>
 *   <li>{@code related_code} — 선택, 관련 Java/SQL/SP 코드</li>
 *   <li>{@code env}          — 선택, JDK/DB 버전 등 환경 정보</li>
 *   <li>{@code analysis_mode}— 선택, "general" (기본) 또는 "security"</li>
 * </ul>
 *
 * <p>프롬프트는 {@code prompts/harness/log-rca/{stage}.md}에서 로드되며,
 * {@link PromptLoader}가 캐시합니다.
 */
@Service
public class HarnessLogRcaService {

    public static final String HARNESS_NAME = "log-rca";

    private static final int TOKENS_ANALYST   = 3072;
    private static final int TOKENS_BUILDER   = 8192;
    private static final int TOKENS_REVIEWER  = 4096;
    private static final int TOKENS_VERIFIER  = 4096;
    private static final int BUILDER_CONTINUATIONS = 3;

    private final HarnessOrchestrator orchestrator;
    private final PromptLoader        prompts;
    private final ToolkitSettings     settings;

    public HarnessLogRcaService(HarnessOrchestrator orchestrator,
                                PromptLoader prompts,
                                ToolkitSettings settings) {
        this.orchestrator = orchestrator;
        this.prompts      = prompts;
        this.settings     = settings;
    }

    // ── 비스트리밍 ────────────────────────────────────────────────────────────

    public HarnessRunResult analyze(String errorLog, String timeline,
                                     String relatedCode, String env, String analysisMode) {
        Map<String, Object> inputs = buildInputs(errorLog, timeline, relatedCode, env, analysisMode);
        return orchestrator.run(HARNESS_NAME, buildStages(),
                inputs, settings.getProjectContext(), analysisMode);
    }

    // ── SSE 스트리밍 ──────────────────────────────────────────────────────────

    public void analyzeStream(String errorLog, String timeline,
                              String relatedCode, String env, String analysisMode,
                              Consumer<String> onChunk) throws IOException {
        Map<String, Object> inputs = buildInputs(errorLog, timeline, relatedCode, env, analysisMode);
        orchestrator.runStream(HARNESS_NAME, buildStages(),
                inputs, settings.getProjectContext(), analysisMode, onChunk);
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private Map<String, Object> buildInputs(String errorLog, String timeline,
                                             String relatedCode, String env, String analysisMode) {
        if (errorLog == null || errorLog.trim().isEmpty()) {
            throw new IllegalArgumentException("errorLog must not be empty");
        }
        Map<String, Object> inputs = new LinkedHashMap<String, Object>();
        inputs.put("error_log",     errorLog);
        inputs.put("timeline",      timeline    == null ? "" : timeline);
        inputs.put("related_code",  relatedCode == null ? "" : relatedCode);
        inputs.put("env",           env         == null ? "" : env);
        inputs.put("analysis_mode", "security".equals(analysisMode) ? "security" : "general");
        return inputs;
    }

    /** 4-stage 정의 — 매 호출마다 새 인스턴스 (PromptLoader가 내부 캐시 보유). */
    private List<HarnessStage> buildStages() {
        return Arrays.asList(
                analystStage(),
                builderStage(),
                reviewerStage(),
                verifierStage()
        );
    }

    private HarnessStage analystStage() {
        return new BaseStage("analyst", TOKENS_ANALYST, 0,
                "## 🔍 RCA — 증상·가설 분석\n",
                "") {
            /**
             * security 모드에서는 analyst-security.md를 로드 (보안 위협 관점 분석).
             * 그 외 모드는 기본 analyst.md 사용.
             */
            @Override
            protected String promptKey(HarnessContext ctx) {
                return "security".equals(ctx.getTemplateHint()) ? "analyst-security" : "analyst";
            }

            @Override
            public String buildUser(HarnessContext ctx) {
                String hint = "security".equals(ctx.getTemplateHint())
                        ? "다음 정보에서 보안 위협(SQL 인젝션, XSS, 인증 우회 등)을 중심으로 증상·타임라인·가설을 정리하세요.\n\n"
                        : "다음 정보를 종합하여 증상·타임라인·가설을 정리하세요.\n\n";
                return hint
                     + section("오류 로그",      ctx.getInputAsString("error_log"))
                     + section("타임라인 메모",  ctx.getInputAsString("timeline"))
                     + section("관련 코드/SQL", ctx.getInputAsString("related_code"))
                     + section("환경 정보",      ctx.getInputAsString("env"));
            }
        };
    }

    private HarnessStage builderStage() {
        return new BaseStage("builder", TOKENS_BUILDER, BUILDER_CONTINUATIONS,
                "\n\n## 🛠 RCA — 가설별 검증·패치 계획\n",
                "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "이전 단계(Analyst)의 가설들에 대해 검증 절차·패치·롤백 계획을 작성하세요.\n\n"
                     + "## Analyst 분석 결과\n"
                     + ctx.getStageOutput("analyst") + "\n\n"
                     + section("원본 오류 로그", ctx.getInputAsString("error_log"))
                     + section("관련 코드/SQL", ctx.getInputAsString("related_code"));
            }
        };
    }

    private HarnessStage reviewerStage() {
        return new BaseStage("reviewer", TOKENS_REVIEWER, 0,
                "\n\n## 📊 RCA — 가설 우도 평가\n",
                "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "Analyst의 가설과 Builder의 패치 계획을 검토하여 우도·부작용·우선순위를 평가하세요.\n\n"
                     + "## Analyst 분석\n"     + ctx.getStageOutput("analyst")  + "\n\n"
                     + "## Builder 패치 계획\n" + ctx.getStageOutput("builder");
            }
        };
    }

    private HarnessStage verifierStage() {
        return new BaseStage("verifier", TOKENS_VERIFIER, 0,
                "\n\n## ✅ RCA — 표준 보고서 + 재발 방지\n",
                "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "이전 3단계 결과를 사내 표준 RCA 보고서로 통합하고 재발 방지 체크리스트를 작성하세요.\n\n"
                     + "## Analyst 분석\n"      + ctx.getStageOutput("analyst")  + "\n\n"
                     + "## Builder 패치 계획\n"  + ctx.getStageOutput("builder")  + "\n\n"
                     + "## Reviewer 우도·부작용\n" + ctx.getStageOutput("reviewer");
            }
        };
    }

    /**
     * 시스템 프롬프트는 PromptLoader로 로드 — fallback은 Stage 이름만 알리는 1줄.
     *
     * <p>{@link #promptKey(HarnessContext)}를 오버라이드하여 모드별로 다른 프롬프트 파일을
     * 사용할 수 있습니다 (예: analyst → analyst-security).
     */
    private abstract class BaseStage implements HarnessStage {
        private final String stageName;
        private final int    maxTokens;
        private final int    continuations;
        private final String header;
        private final String footer;

        BaseStage(String name, int maxTokens, int continuations, String header, String footer) {
            this.stageName     = name;
            this.maxTokens     = maxTokens;
            this.continuations = continuations;
            this.header        = header;
            this.footer        = footer;
        }
        public String name() { return stageName; }
        public int    maxTokens()     { return maxTokens; }
        public int    continuations() { return continuations; }
        public String streamHeader()  { return header; }
        public String streamFooter()  { return footer; }

        /**
         * 이 stage가 로드할 prompt 파일명 (확장자 제외).
         * 기본은 {@link #stageName}; 모드별 분기 시 오버라이드 (analyst-security 등).
         */
        protected String promptKey(HarnessContext ctx) {
            return stageName;
        }

        public String buildSystem(HarnessContext ctx) {
            String key  = promptKey(ctx);
            String base = prompts.loadOrDefault(HARNESS_NAME, key,
                    "당신은 RCA 분석가(" + stageName + ")입니다. 한국어로 응답하세요.");
            return withMemo(base, ctx.getMemo());
        }
    }

    /** 시스템 프롬프트에 프로젝트 컨텍스트 메모를 추가합니다 (HarnessReviewService와 동일 패턴). */
    private static String withMemo(String systemPrompt, String memo) {
        if (memo == null || memo.trim().isEmpty()) return systemPrompt;
        return systemPrompt + "\n\n[프로젝트 컨텍스트]\n" + memo;
    }

    /** 입력 섹션을 마크다운으로 포맷 — 빈 값은 출력하지 않음. */
    private static String section(String label, String body) {
        if (body == null || body.trim().isEmpty()) return "";
        return "## " + label + "\n```\n" + body + "\n```\n\n";
    }
}
