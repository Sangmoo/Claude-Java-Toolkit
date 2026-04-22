package io.github.claudetoolkit.ui.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.claudetoolkit.sql.erd.ErdAnalyzerService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.metrics.ToolkitMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for ERD analysis features.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>POST /api/v1/erd/analyze — 스키마 텍스트 또는 DB 직접 연결 ERD 분석</li>
 * </ul>
 *
 * <h3>요청 형식 (application/json)</h3>
 * <pre>
 * // 스키마 텍스트 입력 방식
 * POST /api/v1/erd/analyze
 * {
 *   "schemaText": "CREATE TABLE ORDERS ( ... );\nCREATE TABLE ORDER_ITEMS ( ... );"
 * }
 *
 * // DB 직접 연결 방식 (schemaText 없을 때 사용)
 * POST /api/v1/erd/analyze
 * {
 *   "dbUrl":       "jdbc:oracle:thin:@//host:1521/ORCL",
 *   "dbUsername":  "MYUSER",
 *   "dbPassword":  "password",
 *   "schemaOwner": "MYUSER",
 *   "tableFilter": "선택사항 — 테이블명 필터 (예: T_ORDER%)"
 * }
 * </pre>
 */
@Tag(name = "ERD", description = "ERD 분석 + DDL 생성")
@RestController
@RequestMapping("/api/v1/erd")
public class ErdRestController {

    private final ErdAnalyzerService erdAnalyzerService;
    private final ToolkitSettings    settings;
    /** v4.3.0 — Prometheus 메트릭 (Timer + Counter) */
    private final ToolkitMetrics     metrics;

    public ErdRestController(ErdAnalyzerService erdAnalyzerService,
                             ToolkitSettings settings,
                             ToolkitMetrics metrics) {
        this.erdAnalyzerService = erdAnalyzerService;
        this.settings           = settings;
        this.metrics            = metrics;
    }

    // ── POST /api/v1/erd/analyze ──────────────────────────────────────────────

    /**
     * ERD 분석 — 스키마 텍스트 또는 DB 직접 연결 방식 지원.
     *
     * <p>schemaText가 있으면 텍스트 방식, 없으면 DB 연결 방식을 사용합니다.
     */
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyze(
            @RequestBody Map<String, String> body) {

        String schemaText   = body.get("schemaText");
        String dbUrl        = body.getOrDefault("dbUrl",        settings.getDb().getUrl());
        String dbUsername   = body.getOrDefault("dbUsername",   settings.getDb().getUsername());
        String dbPassword   = body.getOrDefault("dbPassword",   settings.getDb().getPassword());
        String schemaOwner  = body.getOrDefault("schemaOwner",  dbUsername);
        String tableFilter  = body.get("tableFilter");

        boolean useText = schemaText != null && !schemaText.trim().isEmpty();
        boolean useDb   = dbUrl != null && !dbUrl.trim().isEmpty();

        if (!useText && !useDb) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("schemaText 또는 DB 연결 정보(dbUrl/dbUsername/dbPassword)가 필요합니다."));
        }

        Timer.Sample sample = metrics != null ? metrics.startAnalysis() : null;
        try {
            String erd;
            String mode;
            if (useText) {
                erd  = erdAnalyzerService.generateFromText(schemaText);
                mode = "text";
            } else if (tableFilter != null && !tableFilter.trim().isEmpty()) {
                erd  = erdAnalyzerService.generateFromDb(dbUrl, dbUsername, dbPassword, schemaOwner, tableFilter);
                mode = "db";
            } else {
                erd  = erdAnalyzerService.generateFromDb(dbUrl, dbUsername, dbPassword, schemaOwner);
                mode = "db";
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("erd",  erd);
            data.put("mode", mode);

            if (metrics != null) metrics.stopAnalysis(sample, "ERD");
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            if (metrics != null) metrics.stopAnalysis(sample, "ERD");
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("ERD 분석 실패: " + e.getMessage()));
        }
    }
}
