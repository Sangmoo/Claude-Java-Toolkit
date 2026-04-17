package io.github.claudetoolkit.ui.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * v4.3.0 — 분석 결과를 외부 표준 포맷으로 내보내는 REST API.
 *
 * <ul>
 *   <li>{@code GET /api/v1/export/sarif/{historyId}} — SARIF 2.1.0 JSON 다운로드</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/export")
public class ExportRestController {

    private static final Logger log = LoggerFactory.getLogger(ExportRestController.class);

    private final ReviewHistoryRepository historyRepository;
    private final SarifExportService sarifExportService;
    private final ExcelExportService excelExportService;
    private final ObjectMapper objectMapper;

    public ExportRestController(ReviewHistoryRepository historyRepository,
                                SarifExportService sarifExportService,
                                ExcelExportService excelExportService) {
        this.historyRepository  = historyRepository;
        this.sarifExportService = sarifExportService;
        this.excelExportService = excelExportService;
        this.objectMapper       = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 단일 이력을 SARIF 2.1.0 JSON 으로 다운로드.
     *
     * @param historyId ReviewHistory PK
     * @return SARIF JSON (Content-Disposition: attachment)
     */
    @GetMapping(value = "/sarif/{historyId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportSarif(@PathVariable("historyId") long historyId) {
        ReviewHistory h = historyRepository.findById(historyId).orElse(null);
        if (h == null) {
            log.warn("SARIF 내보내기 — 존재하지 않는 이력 id={}", historyId);
            return ResponseEntity.notFound().build();
        }

        try {
            Map<String, Object> sarif = sarifExportService.toSarif(h);
            byte[] body = objectMapper.writeValueAsBytes(sarif);

            String filename = "claude-toolkit-" + h.getType() + "-" + historyId + ".sarif";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(body.length);
            // SARIF Viewer 호환을 위해 .sarif 확장자 + UTF-8 명시
            headers.add("X-Content-Type-Options", "nosniff");

            log.info("SARIF 내보내기 성공: id={}, type={}, bytes={}", historyId, h.getType(), body.length);
            return ResponseEntity.ok().headers(headers).body(body);
        } catch (Exception e) {
            log.error("SARIF 내보내기 실패: id={}", historyId, e);
            String err = ("{\"error\": \"SARIF export failed: "
                    + (e.getMessage() != null ? e.getMessage().replace("\"", "'") : "")
                    + "\"}").getBytes(StandardCharsets.UTF_8).toString();
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(err.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 이력 목록을 Excel(.xlsx) 워크북으로 다운로드.
     *
     * @param limit 최대 행 수 (기본 1000, 최대 10000)
     * @return XLSX 파일 (Content-Disposition: attachment)
     */
    @GetMapping(value = "/excel/history",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportHistoryExcel(
            @RequestParam(value = "limit", defaultValue = "1000") int limit) {
        try {
            int safeLimit = Math.max(1, Math.min(limit, 10000));
            List<ReviewHistory> rows = historyRepository.findRecentEntries(PageRequest.of(0, safeLimit));
            byte[] body = excelExportService.toExcel(rows);

            String filename = "claude-toolkit-history-" + System.currentTimeMillis() + ".xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(body.length);

            log.info("Excel 이력 내보내기 성공: rows={}, bytes={}", rows.size(), body.length);
            return ResponseEntity.ok().headers(headers).body(body);
        } catch (Exception e) {
            log.error("Excel 이력 내보내기 실패", e);
            return ResponseEntity.status(500).build();
        }
    }
}
