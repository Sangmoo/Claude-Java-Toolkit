package io.github.claudetoolkit.ui.api;

import io.github.claudetoolkit.sql.advisor.SqlAdvisorService;
import io.github.claudetoolkit.sql.explain.ExplainPlanResult;
import io.github.claudetoolkit.sql.explain.ExplainPlanService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.metrics.ToolkitMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for SQL analysis features.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>POST /api/v1/sql/review   — SQL 성능·품질 리뷰</li>
 *   <li>POST /api/v1/sql/security — SQL 보안 취약점 검사</li>
 *   <li>POST /api/v1/sql/explain  — Oracle 실행계획 분석</li>
 * </ul>
 *
 * <h3>공통 요청 형식 (application/json)</h3>
 * <pre>
 * POST /api/v1/sql/review
 * {
 *   "sql":     "SELECT * FROM ORDERS WHERE ...",
 *   "context": "선택사항 — 프로젝트 컨텍스트"
 * }
 *
 * POST /api/v1/sql/explain
 * {
 *   "sql":        "SELECT ...",
 *   "dbUrl":      "선택사항 — 미입력 시 Settings DB 사용",
 *   "dbUsername": "선택사항",
 *   "dbPassword": "선택사항"
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/sql")
public class SqlRestController {

    private final SqlAdvisorService  advisorService;
    private final ExplainPlanService explainPlanService;
    private final ToolkitSettings    settings;
    /** v4.3.0 — Prometheus 메트릭 (Timer + Counter) */
    private final ToolkitMetrics     metrics;

    public SqlRestController(SqlAdvisorService advisorService,
                             ExplainPlanService explainPlanService,
                             ToolkitSettings settings,
                             ToolkitMetrics metrics) {
        this.advisorService    = advisorService;
        this.explainPlanService = explainPlanService;
        this.settings          = settings;
        this.metrics           = metrics;
    }

    // ── POST /api/v1/sql/review ───────────────────────────────────────────────

    /**
     * SQL 성능·품질 리뷰.
     *
     * @param body { "sql": "...", "context": "optional" }
     */
    @PostMapping("/review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> review(
            @RequestBody Map<String, String> body) {

        String sql     = body.get("sql");
        String context = body.getOrDefault("context", "");

        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("sql 필드는 필수입니다."));
        }

        Timer.Sample sample = metrics != null ? metrics.startAnalysis() : null;
        try {
            io.github.claudetoolkit.sql.model.AdvisoryResult result =
                    advisorService.reviewWithContext(sql, io.github.claudetoolkit.sql.model.SqlType.detect(sql), context);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sqlType",    result.getSqlType().name());
            data.put("review",     result.getReviewContent());
            data.put("reviewedAt", result.getReviewedAt());

            if (metrics != null) metrics.stopAnalysis(sample, "SQL_REVIEW");
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            if (metrics != null) metrics.stopAnalysis(sample, "SQL_REVIEW");
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("SQL 리뷰 실패: " + e.getMessage()));
        }
    }

    // ── POST /api/v1/sql/security ─────────────────────────────────────────────

    /**
     * SQL 보안 취약점 검사 (SQL Injection, 권한 노출 등).
     *
     * @param body { "sql": "..." }
     */
    @PostMapping("/security")
    public ResponseEntity<ApiResponse<Map<String, Object>>> security(
            @RequestBody Map<String, String> body) {

        String sql = body.get("sql");

        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("sql 필드는 필수입니다."));
        }

        Timer.Sample sample = metrics != null ? metrics.startAnalysis() : null;
        try {
            io.github.claudetoolkit.sql.model.AdvisoryResult result =
                    advisorService.reviewSecurity(sql);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sqlType",    result.getSqlType().name());
            data.put("review",     result.getReviewContent());
            data.put("reviewedAt", result.getReviewedAt());

            if (metrics != null) metrics.stopAnalysis(sample, "SQL_SECURITY");
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            if (metrics != null) metrics.stopAnalysis(sample, "SQL_SECURITY");
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("SQL 보안 검사 실패: " + e.getMessage()));
        }
    }

    // ── POST /api/v1/sql/explain ──────────────────────────────────────────────

    /**
     * Oracle 실행계획 분석.
     * dbUrl/dbUsername/dbPassword 미입력 시 Settings에 저장된 DB 정보를 사용합니다.
     *
     * @param body { "sql": "...", "dbUrl": "opt", "dbUsername": "opt", "dbPassword": "opt" }
     */
    @PostMapping("/explain")
    public ResponseEntity<ApiResponse<Map<String, Object>>> explain(
            @RequestBody Map<String, String> body) {

        String sql        = body.get("sql");
        String dbUrl      = body.getOrDefault("dbUrl",      settings.getDb().getUrl());
        String dbUsername = body.getOrDefault("dbUsername", settings.getDb().getUsername());
        String dbPassword = body.getOrDefault("dbPassword", settings.getDb().getPassword());

        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("sql 필드는 필수입니다."));
        }
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("dbUrl 또는 Settings DB 설정이 필요합니다."));
        }

        Timer.Sample sample = metrics != null ? metrics.startAnalysis() : null;
        try {
            ExplainPlanResult result = explainPlanService.analyze(dbUrl, dbUsername, dbPassword, sql);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalCost",   result.getRoot() != null && result.getRoot().getCost() != null
                                        ? result.getRoot().getCost() : null);
            data.put("maxCost",     result.getMaxCost());
            data.put("rawPlanText", result.getRawPlanText());
            data.put("aiAnalysis",  result.getAiAnalysis());
            data.put("analyzedAt",  result.getAnalyzedAt());

            if (metrics != null) metrics.stopAnalysis(sample, "EXPLAIN_PLAN");
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            if (metrics != null) metrics.stopAnalysis(sample, "EXPLAIN_PLAN");
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("실행계획 분석 실패: " + e.getMessage()));
        }
    }
}
