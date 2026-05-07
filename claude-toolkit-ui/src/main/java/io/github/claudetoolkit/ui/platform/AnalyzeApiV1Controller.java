package io.github.claudetoolkit.ui.platform;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.controller.SseStreamController;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import io.github.claudetoolkit.ui.service.AnalysisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.7.x — #M3 Phase 2: 외부 client (CLI / MCP / SDK / curl) 용 *동기* 분석 endpoint.
 *
 * <p>기존 SSE 기반 {@code /stream/init + /stream/{id}} 와 별개 — *블로킹 응답*.
 * 외부 도구가 처리하기 쉬움.
 *
 * <p>MCP server 가 호출하는 주 endpoint: {@code POST /api/v1/analyze}
 *
 * <p><b>인증</b>: {@link PlatformAuthFilter} 가 X-Api-Key 헤더 검증 후 SecurityContext set.
 */
@RestController
@RequestMapping("/api/v1")
public class AnalyzeApiV1Controller {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeApiV1Controller.class);

    private final ClaudeClient            claudeClient;
    private final ToolkitSettings         settings;
    private final AnalysisCacheService    cacheService;
    private final ReviewHistoryService    historyService;
    private final SseStreamController     promptBuilder;  // package-private 메서드 재사용

    @Autowired(required = false)
    private io.github.claudetoolkit.ui.livedb.LiveDbContextService liveDbContextService;

    public AnalyzeApiV1Controller(ClaudeClient claudeClient,
                                   ToolkitSettings settings,
                                   AnalysisCacheService cacheService,
                                   ReviewHistoryService historyService,
                                   SseStreamController promptBuilder) {
        this.claudeClient   = claudeClient;
        this.settings       = settings;
        this.cacheService   = cacheService;
        this.historyService = historyService;
        this.promptBuilder  = promptBuilder;
    }

    /**
     * 동기 분석 — MCP / CLI / curl 진입점.
     *
     * @return JSON: { id, feature, result, tokens, costUsd, elapsedMs, cached }
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(
            @RequestBody AnalyzeRequest req,
            Authentication auth) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (req == null || req.feature == null || req.feature.isEmpty()
                || req.input == null || req.input.isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "feature + input 필수");
            return ResponseEntity.badRequest().body(resp);
        }

        long t0 = System.currentTimeMillis();
        try {
            // 1. 캐시 hit 검사 (4-arg 키)
            String cached = cacheService.get(
                    req.feature, req.input, req.input2, req.sourceType);
            if (cached != null) {
                resp.put("success",   true);
                resp.put("feature",   req.feature);
                resp.put("result",    cached);
                resp.put("cached",    true);
                Map<String, Object> tk0 = new LinkedHashMap<>();
                tk0.put("input", 0); tk0.put("output", 0);
                resp.put("tokens",    tk0);
                resp.put("costUsd",   0.0);
                resp.put("elapsedMs", System.currentTimeMillis() - t0);
                return ResponseEntity.ok(resp);
            }

            // 2. system prompt 조립 (Live DB 컨텍스트 포함)
            String systemPrompt = promptBuilder.resolveSystemPrompt(req.feature, req.sourceType);
            String memoCtx = settings.getProjectContext();
            if (memoCtx != null && !memoCtx.trim().isEmpty()) {
                systemPrompt = systemPrompt + "\n\n[프로젝트 컨텍스트]\n" + memoCtx;
            }
            String effectiveInput2 = req.input2;
            if (liveDbContextService != null && req.dbProfileId != null
                    && io.github.claudetoolkit.ui.livedb.SqlAnalysisFeatures.shouldAttachLiveDbContext(req.feature)) {
                try {
                    io.github.claudetoolkit.ui.livedb.LiveDbContext lc =
                            liveDbContextService.fetch(req.input, req.dbProfileId);
                    if (lc != null && !lc.isEmpty()) {
                        String md = io.github.claudetoolkit.ui.livedb.LiveDbContextFormatter.format(lc);
                        if (!md.isEmpty()) systemPrompt = systemPrompt + "\n\n" + md;
                        // explain_plan 자동 채움 (#G3 Phase 2 동일 동작)
                        if ("explain_plan".equals(req.feature)
                                && (effectiveInput2 == null || effectiveInput2.isEmpty())
                                && lc.getExplainPlanFormatted() != null) {
                            effectiveInput2 = lc.getExplainPlanFormatted();
                        }
                    }
                } catch (Exception ignored) { /* graceful */ }
            }
            String userMessage = promptBuilder.buildUserMessage(
                    req.feature, req.input, effectiveInput2, req.sourceType);

            // 3. Claude 동기 호출
            String result = claudeClient.chat(
                    systemPrompt, userMessage, claudeClient.getProperties().getMaxTokens());

            // 4. 캐시 + history 저장
            long inTok  = claudeClient.getLastInputTokens();
            long outTok = claudeClient.getLastOutputTokens();
            cacheService.put(req.feature, req.input, req.input2, req.sourceType, result);

            ReviewHistory history = null;
            try {
                String type  = req.feature.toUpperCase();
                String title = req.input.length() > 60
                        ? req.input.substring(0, 60) + "..."
                        : req.input;
                historyService.save(type, req.input, result, auth != null ? auth.getName() : null);
            } catch (Exception ignored) { /* history 저장 실패가 응답을 막지는 않음 */ }

            // 5. 응답 조립
            double costUsd = estimateCost(claudeClient.getEffectiveModel(), inTok, outTok);
            Map<String, Object> tokens = new LinkedHashMap<>();
            tokens.put("input",  inTok);
            tokens.put("output", outTok);

            resp.put("success",   true);
            resp.put("feature",   req.feature);
            resp.put("result",    result);
            resp.put("cached",    false);
            resp.put("tokens",    tokens);
            resp.put("costUsd",   Math.round(costUsd * 10000) / 10000.0);
            resp.put("elapsedMs", System.currentTimeMillis() - t0);
            resp.put("model",     claudeClient.getEffectiveModel());
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.warn("[AnalyzeApi] 분석 실패: feature={} : {}", req.feature, e.getMessage(), e);
            resp.put("success",   false);
            resp.put("error",     "분석 실패: " + e.getMessage());
            resp.put("elapsedMs", System.currentTimeMillis() - t0);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    /**
     * 사용 가능한 feature 목록 — MCP discovery / CLI auto-complete 용.
     */
    @GetMapping("/features")
    public Map<String, Object> features() {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new java.util.ArrayList<>();

        // 핵심 feature 카탈로그 — frontend LIVE_DB_FEATURES + 그 외 주요 분석
        String[][] cat = {
            { "sql_review",      "SQL 리뷰",         "성능/보안/가독성 이슈 분석" },
            { "explain_plan",    "실행계획 분석",      "EXPLAIN PLAN 해석 + 튜닝 제안" },
            { "index_advisor",   "인덱스 추천",        "WHERE/JOIN 분석 → 인덱스 DDL" },
            { "sql_translate",   "SQL DB 번역",       "Oracle ↔ MySQL ↔ PostgreSQL ↔ MSSQL" },
            { "sql_batch",       "배치 SQL 분석",     "여러 SQL 일괄 분석" },
            { "erd_analysis",    "ERD 분석",         "테이블 관계 + Mermaid 다이어그램" },
            { "code_review",     "Java 코드 리뷰",    "성능/보안/가독성 이슈" },
            { "doc_gen",         "기술 문서 생성",     "Java 코드 → 마크다운 문서" },
            { "complexity",      "복잡도 분석",       "Cyclomatic / Halstead / 유지보수 점수" },
            { "commit_msg",      "커밋 메시지 생성",   "diff → conventional commits" },
            { "regex_gen",       "정규식 생성",       "자연어 → 정규식 + 테스트 케이스" },
            { "test_gen",        "단위 테스트 생성",   "Java 코드 → JUnit 5" },
        };
        for (String[] f : cat) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("feature",     f[0]);
            m.put("label",       f[1]);
            m.put("description", f[2]);
            m.put("liveDbCapable",
                    io.github.claudetoolkit.ui.livedb.SqlAnalysisFeatures.shouldAttachLiveDbContext(f[0]));
            rows.add(m);
        }
        resp.put("success",  true);
        resp.put("features", rows);
        return resp;
    }

    /** 토큰 → USD 비용 추정 (Sonnet 4 기준 — Opus/Haiku 도 비슷) */
    private double estimateCost(String model, long inTok, long outTok) {
        // Sonnet 4: input $3/MTok, output $15/MTok
        double inputRate  = 3.0  / 1_000_000;
        double outputRate = 15.0 / 1_000_000;
        if (model != null) {
            String m = model.toLowerCase();
            if (m.contains("haiku")) { inputRate = 0.25 / 1_000_000; outputRate = 1.25 / 1_000_000; }
            else if (m.contains("opus")) { inputRate = 15.0 / 1_000_000; outputRate = 75.0 / 1_000_000; }
        }
        return inTok * inputRate + outTok * outputRate;
    }

    // ── DTO ─────────────────────────────────────────────────────────

    public static class AnalyzeRequest {
        public String feature;       // 필수 — sql_review / explain_plan / ...
        public String input;         // 필수 — 분석 대상 코드/SQL
        public String input2;        // 옵션 — feature 별 보조 입력 (e.g. EXPLAIN PLAN 본문)
        public String sourceType;    // 옵션 — review/security/java/sql/Oracle 등
        public Long   dbProfileId;   // 옵션 — Live DB 컨텍스트 자동 첨부
    }
}
