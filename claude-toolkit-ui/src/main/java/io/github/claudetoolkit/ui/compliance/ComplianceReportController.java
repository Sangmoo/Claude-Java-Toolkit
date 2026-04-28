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

    /**
     * v4.6.x Stage 4 — 영구 저장된 리포트 이력 목록 (메타만, markdown 제외).
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        try {
            List<ComplianceReportRecord> rows = service.listHistory(limit);
            List<Map<String, Object>> result = new ArrayList<>(rows.size());
            for (ComplianceReportRecord r : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",                   r.getId());
                m.put("type",                 r.getType());
                m.put("typeLabel",            r.getTypeLabel());
                m.put("auditFrom",            r.getAuditFrom().toString());
                m.put("auditTo",              r.getAuditTo().toString());
                m.put("createdAt",            r.getFormattedCreatedAt());
                m.put("generatedBy",          r.getGeneratedBy());
                m.put("hasExecutiveSummary",  r.isHasExecutiveSummary());
                // 핵심 지표 4개 — 한 줄 미리보기
                m.put("totalAnalysisInPeriod", r.getTotalAnalysisInPeriod());
                m.put("highSeverityCount",     r.getHighSeverityCount());
                m.put("loginFailures",         r.getLoginFailures());
                m.put("maskingActivities",     r.getMaskingActivities());
                result.add(m);
            }
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.warn("[Compliance] /history 실패", e);
            return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>error("이력 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * v4.6.x Stage 4 — 저장된 리포트 다시 로드 (markdown 포함, 결과 패널에 표시용).
     * 다운로드는 기존 {@code /{reportId}/download} 사용 (DB id 도 동일하게 받음).
     */
    @GetMapping("/history/{recordId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> loadHistory(@PathVariable("recordId") long recordId) {
        try {
            ComplianceReportService.GeneratedReport gr = service.loadFromHistory(recordId);
            if (gr == null) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("리포트를 찾을 수 없습니다: " + recordId));
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id",                gr.id);
            data.put("type",              gr.type.getKey());
            data.put("typeLabel",         gr.type.getLabel());
            data.put("from",              gr.from.toString());
            data.put("to",                gr.to.toString());
            data.put("generatedAt",       gr.generatedAt);
            data.put("generatedBy",       gr.generatedBy);
            data.put("markdown",          gr.markdown);
            data.put("hasExecutiveSummary", false);  // 이미 markdown 안에 포함되어 있어 별도 표시 불필요
            data.put("suggestedFilename", gr.suggestedFilename);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[Compliance] /history/{} 실패", recordId, e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("이력 로드 실패: " + e.getMessage()));
        }
    }

    /**
     * v4.6.x Stage 4 — 저장된 리포트 항목 삭제 (실수 생성·테스트 데이터 정리용).
     */
    @DeleteMapping("/history/{recordId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteHistory(@PathVariable("recordId") long recordId) {
        try {
            boolean deleted = service.deleteHistory(recordId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("deleted", deleted);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[Compliance] /history/{} 삭제 실패", recordId, e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("삭제 실패: " + e.getMessage()));
        }
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
            @RequestParam(value = "executiveSummary", defaultValue = "false") boolean executiveSummary,
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
            LocalDate from, to;
            try {
                from = LocalDate.parse(fromStr);
                to   = LocalDate.parse(toStr);
            } catch (java.time.format.DateTimeParseException dpe) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(
                        "날짜 형식이 잘못되었습니다 (yyyy-MM-dd 형식 필요): " + dpe.getParsedString()));
            }
            String generatedBy = auth != null ? auth.getName() : "anonymous";

            ComplianceReportService.GeneratedReport gr =
                    service.generate(type, from, to, generatedBy, executiveSummary);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id",                gr.id);
            data.put("type",              gr.type.getKey());
            data.put("typeLabel",         gr.type.getLabel());
            data.put("from",              gr.from.toString());
            data.put("to",                gr.to.toString());
            data.put("generatedAt",       gr.generatedAt);
            data.put("generatedBy",       gr.generatedBy);
            data.put("markdown",          gr.markdown);
            data.put("hasExecutiveSummary", gr.executiveSummary != null && !gr.executiveSummary.isEmpty());
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
        String fmt = format != null ? format.trim().toLowerCase() : "md";
        switch (fmt) {
            case "md":   return downloadMarkdown(gr, reportId);
            case "xlsx": return downloadExcel   (gr, reportId);
            default: {
                byte[] err = ("# 지원하지 않는 포맷\n\n현재 지원: md, xlsx. PDF 는 *프린트 보기* 에서 브라우저로 직접 저장하세요.").getBytes(StandardCharsets.UTF_8);
                HttpHeaders h = new HttpHeaders();
                h.setContentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"));
                return new ResponseEntity<>(err, h, org.springframework.http.HttpStatus.BAD_REQUEST);
            }
        }
    }

    private ResponseEntity<byte[]> downloadMarkdown(
            ComplianceReportService.GeneratedReport gr, String reportId) {
        byte[] body = gr.markdown.getBytes(StandardCharsets.UTF_8);
        String filename = gr.suggestedFilename != null ? gr.suggestedFilename : ("compliance-" + reportId + ".md");
        return wrapDownload(body, "text/markdown; charset=UTF-8", filename);
    }

    private ResponseEntity<byte[]> downloadExcel(
            ComplianceReportService.GeneratedReport gr, String reportId) {
        try {
            byte[] body = service.toExcel(reportId);
            String filename = String.format("compliance-%s-%s_%s.xlsx",
                    gr.type.getKey(), gr.from, gr.to);
            return wrapDownload(body,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    filename);
        } catch (Exception e) {
            log.warn("[Compliance] Excel 내보내기 실패", e);
            byte[] err = ("# Excel 생성 실패\n\n" + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"));
            return new ResponseEntity<>(err, h, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<byte[]> wrapDownload(byte[] body, String contentType, String filename) {
        String encoded;
        try { encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { encoded = "compliance-report"; }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"compliance-report\"; filename*=UTF-8''" + encoded);
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}
