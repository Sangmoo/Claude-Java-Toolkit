package io.github.claudetoolkit.ui.harness.sqloptimization;

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
 * Phase C — Oracle SQL 성능 최적화 하네스 서비스.
 *
 * <p>4-stage 파이프라인:
 * <ol>
 *   <li><b>Analyst</b>  — 병목 위치·카디널리티 오차·인덱스 활용·안티패턴 (4096 토큰)</li>
 *   <li><b>Builder</b>  — N개 후보 (rewrite + DDL + 힌트, 비용/리스크 분석) (8192 × 3 cont)</li>
 *   <li><b>Reviewer</b> — 결과 동등성·다른 쿼리 영향·후보 점수표·우선순위 (4096 토큰)</li>
 *   <li><b>Verifier</b> — 문법 검증·위험 변경·rollout plan·롤백 가능성 (4096 토큰)</li>
 * </ol>
 *
 * <p>입력 키 (HarnessContext.inputs):
 * <ul>
 *   <li>{@code query}             — 필수, 느린 SQL 또는 SP 본문</li>
 *   <li>{@code execution_plan}    — 선택, EXPLAIN PLAN 결과</li>
 *   <li>{@code table_stats}       — 선택, USER_TABLES / USER_TAB_COLUMNS 통계</li>
 *   <li>{@code existing_indexes}  — 선택, 현재 인덱스 정의</li>
 *   <li>{@code data_volume}       — 선택, 테이블별 행 수·데이터 분포</li>
 *   <li>{@code constraints}       — 선택, 변경 불가 조건 (어떤 테이블 못 건드림 등)</li>
 * </ul>
 */
@Service
public class HarnessSqlOptimizationService {

    public static final String HARNESS_NAME = "sql-optimization";

    private static final int TOKENS_ANALYST  = 4096;
    private static final int TOKENS_BUILDER  = 8192;
    private static final int TOKENS_REVIEWER = 4096;
    private static final int TOKENS_VERIFIER = 4096;
    private static final int BUILDER_CONTINUATIONS = 3;

    private final HarnessOrchestrator orchestrator;
    private final PromptLoader        prompts;
    private final ToolkitSettings     settings;

    public HarnessSqlOptimizationService(HarnessOrchestrator orchestrator,
                                         PromptLoader prompts,
                                         ToolkitSettings settings) {
        this.orchestrator = orchestrator;
        this.prompts      = prompts;
        this.settings     = settings;
    }

    // ── 비스트리밍 ────────────────────────────────────────────────────────────

    public HarnessRunResult analyze(String query, String executionPlan, String tableStats,
                                     String existingIndexes, String dataVolume, String constraints) {
        Map<String, Object> inputs = buildInputs(query, executionPlan, tableStats,
                existingIndexes, dataVolume, constraints);
        return orchestrator.run(HARNESS_NAME, buildStages(),
                inputs, settings.getProjectContext(), "");
    }

    // ── SSE 스트리밍 ──────────────────────────────────────────────────────────

    public void analyzeStream(String query, String executionPlan, String tableStats,
                              String existingIndexes, String dataVolume, String constraints,
                              Consumer<String> onChunk) throws IOException {
        Map<String, Object> inputs = buildInputs(query, executionPlan, tableStats,
                existingIndexes, dataVolume, constraints);
        orchestrator.runStream(HARNESS_NAME, buildStages(),
                inputs, settings.getProjectContext(), "", onChunk);
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private Map<String, Object> buildInputs(String query, String executionPlan, String tableStats,
                                             String existingIndexes, String dataVolume, String constraints) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query는 필수입니다");
        }
        Map<String, Object> inputs = new LinkedHashMap<String, Object>();
        inputs.put("query",            query);
        inputs.put("execution_plan",   nullToEmpty(executionPlan));
        inputs.put("table_stats",      nullToEmpty(tableStats));
        inputs.put("existing_indexes", nullToEmpty(existingIndexes));
        inputs.put("data_volume",      nullToEmpty(dataVolume));
        inputs.put("constraints",      nullToEmpty(constraints));
        return inputs;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

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
                "## 🎯 SQL 최적화 — 병목 분석\n", "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "다음 Oracle SQL의 병목 위치·카디널리티·인덱스 활용·안티패턴을 분석하세요.\n\n"
                     + section("쿼리",         ctx.getInputAsString("query"))
                     + section("실행계획",     ctx.getInputAsString("execution_plan"))
                     + section("테이블 통계",   ctx.getInputAsString("table_stats"))
                     + section("기존 인덱스",   ctx.getInputAsString("existing_indexes"))
                     + section("데이터 볼륨",   ctx.getInputAsString("data_volume"))
                     + section("변경 불가 제약", ctx.getInputAsString("constraints"));
            }
        };
    }

    private HarnessStage builderStage() {
        return new BaseStage("builder", TOKENS_BUILDER, BUILDER_CONTINUATIONS,
                "\n\n## 🔧 SQL 최적화 — 개선 후보 N개\n", "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "Analyst의 분석을 바탕으로 개선 후보(N개)를 작성하세요. "
                     + "각 후보의 변경 사항·비용 추정·리스크·롤백 절차를 명시하세요.\n\n"
                     + "## Analyst 분석\n" + ctx.getStageOutput("analyst") + "\n\n"
                     + section("원본 쿼리", ctx.getInputAsString("query"))
                     + section("변경 불가 제약", ctx.getInputAsString("constraints"));
            }
        };
    }

    private HarnessStage reviewerStage() {
        return new BaseStage("reviewer", TOKENS_REVIEWER, 0,
                "\n\n## 📊 SQL 최적화 — 후보 평가·우선순위\n", "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "Builder의 후보들에 대해 결과 동등성·다른 쿼리 영향·점수표·우선순위를 평가하세요.\n\n"
                     + "## Analyst 분석\n"     + ctx.getStageOutput("analyst") + "\n\n"
                     + "## Builder 후보 목록\n" + ctx.getStageOutput("builder") + "\n\n"
                     + section("원본 쿼리",        ctx.getInputAsString("query"))
                     + section("기존 인덱스",      ctx.getInputAsString("existing_indexes"));
            }
        };
    }

    private HarnessStage verifierStage() {
        return new BaseStage("verifier", TOKENS_VERIFIER, 0,
                "\n\n## ✅ SQL 최적화 — 정적 검증·Rollout Plan\n", "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "Reviewer가 1순위 권장한 후보의 SQL/DDL을 정적 검증하고 단계별 rollout plan을 작성하세요.\n\n"
                     + "## Builder 후보 목록\n"   + ctx.getStageOutput("builder")  + "\n\n"
                     + "## Reviewer 평가·우선순위\n" + ctx.getStageOutput("reviewer");
            }
        };
    }

    /** 시스템 프롬프트 로더 + 프로젝트 메모 결합 (다른 하네스와 동일 패턴). */
    private abstract class BaseStage implements HarnessStage {
        private final String stageName;
        private final int    maxTokens;
        private final int    continuations;
        private final String header;
        private final String footer;

        BaseStage(String name, int maxTokens, int continuations, String header, String footer) {
            this.stageName = name; this.maxTokens = maxTokens;
            this.continuations = continuations;
            this.header = header; this.footer = footer;
        }
        public String name()          { return stageName; }
        public int    maxTokens()     { return maxTokens; }
        public int    continuations() { return continuations; }
        public String streamHeader()  { return header; }
        public String streamFooter()  { return footer; }
        public String buildSystem(HarnessContext ctx) {
            String base = prompts.loadOrDefault(HARNESS_NAME, stageName,
                    "당신은 Oracle SQL 튜닝 전문가(" + stageName + ")입니다. 한국어로 응답하세요.");
            return withMemo(base, ctx.getMemo());
        }
    }

    private static String section(String label, String body) {
        if (body == null || body.trim().isEmpty()) return "";
        return "## " + label + "\n```\n" + body + "\n```\n\n";
    }

    private static String withMemo(String systemPrompt, String memo) {
        if (memo == null || memo.trim().isEmpty()) return systemPrompt;
        return systemPrompt + "\n\n[프로젝트 컨텍스트]\n" + memo;
    }
}
