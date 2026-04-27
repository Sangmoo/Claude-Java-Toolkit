package io.github.claudetoolkit.ui.harness.spmigration;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.harness.HarnessCacheService;
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
 * Phase B — Oracle SP → Java/Spring + MyBatis 마이그레이션 하네스 서비스.
 *
 * <p>4-stage 파이프라인:
 * <ol>
 *   <li><b>Analyst</b>  — SP 의미 분석: 입출력·DB 부수효과·트랜잭션·루프·위험 포인트 (4096 토큰)</li>
 *   <li><b>Builder</b>  — Service + Mapper(IF + XML) + DTO + 단위 테스트 스켈레톤 (8192 × 3 cont)</li>
 *   <li><b>Reviewer</b> — 행위 동등성 검증·N+1 위험·데이터 의미 보존 (4096 토큰)</li>
 *   <li><b>Verifier</b> — 컴파일 가능성·MyBatis XML 검증·위험 변경 감지 (4096 토큰)</li>
 * </ol>
 *
 * <p>입력 키 (HarnessContext.inputs):
 * <ul>
 *   <li>{@code sp_source}        — SP 본문 (필수, 또는 sp_name으로 자동 조회)</li>
 *   <li>{@code sp_name}          — 선택, OWNER.NAME 형식. sp_source가 비면 ALL_SOURCE 조회</li>
 *   <li>{@code sp_type}          — sp_name과 함께. PROCEDURE / FUNCTION / PACKAGE / TRIGGER (기본 PROCEDURE)</li>
 *   <li>{@code table_ddl}        — 선택, 관련 테이블 DDL</li>
 *   <li>{@code index_ddl}        — 선택, 관련 인덱스 DDL</li>
 *   <li>{@code call_example}     — 선택, 호출 예시 (파라미터 값 포함)</li>
 *   <li>{@code business_context} — 선택, 비즈니스 맥락 메모</li>
 * </ul>
 */
@Service
public class HarnessSpMigrationService {

    public static final String HARNESS_NAME = "sp-migration";

    private static final int TOKENS_ANALYST  = 4096;
    private static final int TOKENS_BUILDER  = 8192;
    private static final int TOKENS_REVIEWER = 4096;
    private static final int TOKENS_VERIFIER = 4096;
    private static final int BUILDER_CONTINUATIONS = 3;

    private final HarnessOrchestrator orchestrator;
    private final PromptLoader        prompts;
    private final ToolkitSettings     settings;
    private final HarnessCacheService cacheService;

    public HarnessSpMigrationService(HarnessOrchestrator orchestrator,
                                     PromptLoader prompts,
                                     ToolkitSettings settings,
                                     HarnessCacheService cacheService) {
        this.orchestrator = orchestrator;
        this.prompts      = prompts;
        this.settings     = settings;
        this.cacheService = cacheService;
    }

    // ── 비스트리밍 ────────────────────────────────────────────────────────────

    public HarnessRunResult analyze(String spSource, String spName, String spType,
                                     String tableDdl, String indexDdl,
                                     String callExample, String businessContext) {
        Map<String, Object> inputs = buildInputs(spSource, spName, spType,
                tableDdl, indexDdl, callExample, businessContext);
        return orchestrator.run(HARNESS_NAME, buildStages(),
                inputs, settings.getProjectContext(), "");
    }

    // ── SSE 스트리밍 ──────────────────────────────────────────────────────────

    public void analyzeStream(String spSource, String spName, String spType,
                              String tableDdl, String indexDdl,
                              String callExample, String businessContext,
                              Consumer<String> onChunk) throws IOException {
        Map<String, Object> inputs = buildInputs(spSource, spName, spType,
                tableDdl, indexDdl, callExample, businessContext);
        orchestrator.runStream(HARNESS_NAME, buildStages(),
                inputs, settings.getProjectContext(), "", onChunk);
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    /**
     * 입력 검증 + sp_source 자동 조회.
     * <p>sp_source가 비어 있고 sp_name이 주어지면 {@link HarnessCacheService#getDbObjectSource}로
     * ALL_SOURCE 에서 본문을 가져옵니다 (ToolkitSettings의 Oracle DB 연결 사용).
     */
    private Map<String, Object> buildInputs(String spSource, String spName, String spType,
                                             String tableDdl, String indexDdl,
                                             String callExample, String businessContext) {
        String resolvedSource = (spSource != null && !spSource.trim().isEmpty()) ? spSource : null;

        if (resolvedSource == null) {
            if (spName == null || spName.trim().isEmpty()) {
                throw new IllegalArgumentException("sp_source 또는 sp_name 중 하나는 필수입니다");
            }
            String type = (spType != null && !spType.trim().isEmpty()) ? spType.trim().toUpperCase() : "PROCEDURE";
            String fetched = cacheService.getDbObjectSource(spName.trim(), type);
            if (fetched == null || fetched.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "SP 본문을 가져올 수 없습니다. sp_name=" + spName + ", type=" + type
                                + " — DB 연결을 확인하거나 sp_source를 직접 붙여넣으세요");
            }
            resolvedSource = fetched;
        }

        Map<String, Object> inputs = new LinkedHashMap<String, Object>();
        inputs.put("sp_source",        resolvedSource);
        inputs.put("sp_name",          nullToEmpty(spName));
        inputs.put("sp_type",          nullToEmpty(spType));
        inputs.put("table_ddl",        nullToEmpty(tableDdl));
        inputs.put("index_ddl",        nullToEmpty(indexDdl));
        inputs.put("call_example",     nullToEmpty(callExample));
        inputs.put("business_context", nullToEmpty(businessContext));
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
                "## 🎯 SP 마이그레이션 — 의미 분석\n", "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "다음 Oracle SP를 분석하여 의미·동작·위험 포인트를 정리하세요.\n\n"
                     + section("SP 본문",          ctx.getInputAsString("sp_source"))
                     + section("SP 식별자",        formatSpId(ctx))
                     + section("관련 테이블 DDL",  ctx.getInputAsString("table_ddl"))
                     + section("관련 인덱스 DDL",  ctx.getInputAsString("index_ddl"))
                     + section("호출 예시",        ctx.getInputAsString("call_example"))
                     + section("비즈니스 컨텍스트", ctx.getInputAsString("business_context"));
            }
        };
    }

    private HarnessStage builderStage() {
        return new BaseStage("builder", TOKENS_BUILDER, BUILDER_CONTINUATIONS,
                "\n\n## 🛠 SP 마이그레이션 — Java/MyBatis 변환\n", "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "Analyst의 분석을 바탕으로 Service / Mapper / XML / DTO / 테스트를 작성하세요.\n\n"
                     + "## Analyst 분석\n" + ctx.getStageOutput("analyst") + "\n\n"
                     + section("원본 SP 본문",   ctx.getInputAsString("sp_source"))
                     + section("관련 테이블 DDL", ctx.getInputAsString("table_ddl"));
            }
        };
    }

    private HarnessStage reviewerStage() {
        return new BaseStage("reviewer", TOKENS_REVIEWER, 0,
                "\n\n## ✅ SP 마이그레이션 — 행위 동등성 검증\n", "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "원본 SP와 Builder의 Java/MyBatis를 비교하여 행위 동등성·N+1·데이터 의미 보존을 검증하세요.\n\n"
                     + "## Analyst 분석\n" + ctx.getStageOutput("analyst") + "\n\n"
                     + "## Builder 변환 결과\n" + ctx.getStageOutput("builder") + "\n\n"
                     + section("원본 SP 본문", ctx.getInputAsString("sp_source"));
            }
        };
    }

    private HarnessStage verifierStage() {
        return new BaseStage("verifier", TOKENS_VERIFIER, 0,
                "\n\n## 🔍 SP 마이그레이션 — 정적 검증\n", "") {
            @Override
            public String buildUser(HarnessContext ctx) {
                return "Builder가 작성한 코드의 컴파일 가능성·MyBatis XML 정합성·위험 변경을 정적 분석하세요.\n\n"
                     + "## Builder 변환 결과\n" + ctx.getStageOutput("builder") + "\n\n"
                     + "## Reviewer 검토 결과\n" + ctx.getStageOutput("reviewer");
            }
        };
    }

    /** 시스템 프롬프트는 PromptLoader로 로드, fallback은 안전장치. */
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
                    "당신은 SP 마이그레이션 전문가(" + stageName + ")입니다. 한국어로 응답하세요.");
            return withMemo(base, ctx.getMemo());
        }
    }

    private static String formatSpId(HarnessContext ctx) {
        String name = ctx.getInputAsString("sp_name");
        String type = ctx.getInputAsString("sp_type");
        if (name.isEmpty() && type.isEmpty()) return "";
        return type + " " + name;
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
