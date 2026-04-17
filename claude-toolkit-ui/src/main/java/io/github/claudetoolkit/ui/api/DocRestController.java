package io.github.claudetoolkit.ui.api;

import io.github.claudetoolkit.docgen.codereview.CodeReviewService;
import io.github.claudetoolkit.docgen.generator.DocGeneratorService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.metrics.ToolkitMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for document generation and code review features.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>POST /api/v1/doc/generate  — 소스코드 기술 문서 생성 (Markdown)</li>
 *   <li>POST /api/v1/code/review   — Java/Spring 코드 품질 리뷰</li>
 *   <li>POST /api/v1/code/security — Java/Spring 코드 보안 감사</li>
 * </ul>
 *
 * <h3>요청 형식 (application/json)</h3>
 * <pre>
 * POST /api/v1/doc/generate
 * {
 *   "code":     "public class OrderService { ... }",
 *   "language": "java",               // java | sql | yaml | xml
 *   "context":  "선택사항 — 프로젝트 설명"
 * }
 *
 * POST /api/v1/code/review
 * {
 *   "code":     "...",
 *   "language": "java",
 *   "context":  "선택사항"
 * }
 * </pre>
 */
@RestController
public class DocRestController {

    private final DocGeneratorService docGeneratorService;
    private final CodeReviewService   codeReviewService;
    private final ToolkitSettings     settings;
    /** v4.3.0 — Prometheus 메트릭 (Timer + Counter) */
    private final ToolkitMetrics      metrics;

    public DocRestController(DocGeneratorService docGeneratorService,
                             CodeReviewService codeReviewService,
                             ToolkitSettings settings,
                             ToolkitMetrics metrics) {
        this.docGeneratorService = docGeneratorService;
        this.codeReviewService   = codeReviewService;
        this.settings            = settings;
        this.metrics             = metrics;
    }

    // ── POST /api/v1/doc/generate ─────────────────────────────────────────────

    /**
     * 소스코드를 분석하여 Markdown 형식의 기술 문서를 생성합니다.
     *
     * @param body { "code": "...", "language": "java", "context": "opt" }
     */
    @PostMapping("/api/v1/doc/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateDoc(
            @RequestBody Map<String, String> body) {

        String code     = body.get("code");
        String language = body.getOrDefault("language", "java");
        String context  = body.getOrDefault("context",  settings.getProjectContext());

        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("code 필드는 필수입니다."));
        }

        Timer.Sample sample = metrics != null ? metrics.startAnalysis() : null;
        try {
            String document = context != null && !context.trim().isEmpty()
                    ? docGeneratorService.generateMarkdownWithContext(code, language, context)
                    : docGeneratorService.generateMarkdown(code, language);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("document", document);
            data.put("format",   "markdown");
            data.put("language", language);

            if (metrics != null) metrics.stopAnalysis(sample, "DOC_GEN");
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            if (metrics != null) metrics.stopAnalysis(sample, "DOC_GEN");
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("문서 생성 실패: " + e.getMessage()));
        }
    }

    // ── POST /api/v1/code/review ──────────────────────────────────────────────

    /**
     * Java/Spring 코드 품질·설계 리뷰.
     *
     * @param body { "code": "...", "language": "java", "context": "opt" }
     */
    @PostMapping("/api/v1/code/review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewCode(
            @RequestBody Map<String, String> body) {

        String code     = body.get("code");
        String language = body.getOrDefault("language", "java");
        String context  = body.getOrDefault("context",  settings.getProjectContext());

        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("code 필드는 필수입니다."));
        }

        Timer.Sample sample = metrics != null ? metrics.startAnalysis() : null;
        try {
            String review = context != null && !context.trim().isEmpty()
                    ? codeReviewService.reviewWithContext(code, language, context)
                    : codeReviewService.review(code, language);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("review",   review);
            data.put("language", language);

            if (metrics != null) metrics.stopAnalysis(sample, "CODE_REVIEW");
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            if (metrics != null) metrics.stopAnalysis(sample, "CODE_REVIEW");
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("코드 리뷰 실패: " + e.getMessage()));
        }
    }

    // ── POST /api/v1/code/security ────────────────────────────────────────────

    /**
     * Java/Spring 코드 보안 취약점 감사 (OWASP, SQL Injection, 하드코딩 등).
     *
     * @param body { "code": "...", "language": "java" }
     */
    @PostMapping("/api/v1/code/security")
    public ResponseEntity<ApiResponse<Map<String, Object>>> securityAudit(
            @RequestBody Map<String, String> body) {

        String code     = body.get("code");
        String language = body.getOrDefault("language", "java");

        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("code 필드는 필수입니다."));
        }

        Timer.Sample sample = metrics != null ? metrics.startAnalysis() : null;
        try {
            String review = codeReviewService.reviewSecurity(code, language);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("review",   review);
            data.put("language", language);

            if (metrics != null) metrics.stopAnalysis(sample, "CODE_REVIEW_SEC");
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            if (metrics != null) metrics.stopAnalysis(sample, "CODE_REVIEW_SEC");
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("보안 감사 실패: " + e.getMessage()));
        }
    }
}
