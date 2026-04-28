package io.github.claudetoolkit.ui.compliance;

import io.github.claudetoolkit.ui.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.6.x — 한국 컴플라이언스 리포트 REST API (ADMIN 전용).
 *
 * <p>경로 prefix {@code /api/v1/admin/compliance} → SecurityConfig 의
 * {@code /api/v1/admin/**} 패턴이 ADMIN 권한을 자동 강제.
 *
 * <p>3개 엔드포인트 (Stage 1):
 * <ol>
 *   <li>GET  {@code /types}              — 사용 가능한 리포트 타입 목록</li>
 *   <li>POST {@code /generate}            — 리포트 생성 (본문에 markdown 포함)</li>
 *   <li>GET  {@code /{reportId}/download} — 생성된 리포트 .md 파일 다운로드</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/admin/compliance")
public class ComplianceReportController {

    private static final Logger log = LoggerFactory.getLogger(ComplianceReportController.class);

    private final ComplianceReportService service;

    public ComplianceReportController(ComplianceReportService service) {
        this.service = service;
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> types() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ComplianceReportType t : ComplianceReportType.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key",         t.getKey());
            m.put("label",       t.getLabel());
            m.put("description", t.getDescription());
            m.put("enabled",     t.isEnabled());
            list.add(m);
        }
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generate(
            @RequestParam("type") String typeKey,
            @RequestParam("from") String fromStr,
            @RequestParam("to")   String toStr,
            Authentication auth) {
        try {
            ComplianceReportType type = ComplianceReportType.fromKey(typeKey);
            if (type == null) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(
                        "존재하지 않는 리포트 타입: " + typeKey));
            }
            if (!type.isEnabled()) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(
                        type.getLabel() + " 리포트는 아직 준비 중입니다 (Stage 2 예정)."));
            }
            LocalDate from = LocalDate.parse(fromStr);
            LocalDate to   = LocalDate.parse(toStr);
            String generatedBy = auth != null ? auth.getName() : "anonymous";

            ComplianceReportService.GeneratedReport gr =
                    service.generate(type, from, to, generatedBy);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id",                gr.id);
            data.put("type",              gr.type.getKey());
            data.put("typeLabel",         gr.type.getLabel());
            data.put("from",              gr.from.toString());
            data.put("to",                gr.to.toString());
            data.put("generatedAt",       gr.generatedAt);
            data.put("generatedBy",       gr.generatedBy);
            data.put("markdown",          gr.markdown);
            data.put("suggestedFilename", gr.suggestedFilename);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(e.getMessage()));
        } catch (Exception e) {
            log.warn("[Compliance] 리포트 생성 실패", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(
                    "리포트 생성 중 오류: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
        }
    }

    @GetMapping("/{reportId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable("reportId") String reportId,
            @RequestParam(value = "format", defaultValue = "md") String format) {
        ComplianceReportService.GeneratedReport gr = service.find(reportId);
        if (gr == null) {
            byte[] err = ("# 리포트를 찾을 수 없습니다\n\n생성 후 일정 시간이 지났거나 서버 재시작으로 사라졌을 수 있습니다.\n다시 생성해 주세요.").getBytes(StandardCharsets.UTF_8);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"));
            return new ResponseEntity<>(err, h, org.springframework.http.HttpStatus.NOT_FOUND);
        }
        // Stage 1 — markdown only
        if (!"md".equalsIgnoreCase(format)) {
            byte[] err = ("# 지원하지 않는 포맷\n\nStage 1 은 markdown(.md) 다운로드만 지원합니다.\nPDF / Excel 은 Stage 3 에서 추가 예정.").getBytes(StandardCharsets.UTF_8);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"));
            return new ResponseEntity<>(err, h, org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        byte[] body = gr.markdown.getBytes(StandardCharsets.UTF_8);
        String filename = gr.suggestedFilename != null ? gr.suggestedFilename : ("compliance-" + reportId + ".md");
        String encoded;
        try { encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { encoded = "compliance-report.md"; }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"));
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"compliance-report.md\"; filename*=UTF-8''" + encoded);
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}
