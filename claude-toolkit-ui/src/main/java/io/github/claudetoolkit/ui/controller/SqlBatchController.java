package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.sql.advisor.SqlAdvisorService;
import io.github.claudetoolkit.sql.model.AdvisoryResult;
import io.github.claudetoolkit.sql.model.SqlType;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Web controller for batch SQL analysis (/sql-batch).
 *
 * <p>Accepts multiple SQL statements either as:
 * <ul>
 *   <li>Text input — blocks separated by {@code ---} or {@code ;} delimiter</li>
 *   <li>CSV file upload — one SQL per line in the first column</li>
 *   <li>Plain text file upload (.sql, .txt) — split by {@code ---} or {@code ;}</li>
 * </ul>
 *
 * Each SQL block is analyzed individually with {@link SqlAdvisorService}
 * and results are returned as an accordion list or a single Markdown report.
 */
@Controller
@RequestMapping("/sql-batch")
public class SqlBatchController {

    private static final int MAX_BATCH_SIZE = 30;

    private final SqlAdvisorService    sqlAdvisorService;
    private final ReviewHistoryService historyService;

    public SqlBatchController(SqlAdvisorService sqlAdvisorService,
                              ReviewHistoryService historyService) {
        this.sqlAdvisorService = sqlAdvisorService;
        this.historyService    = historyService;
    }

    /**
     * Analyze SQL blocks pasted directly into the textarea.
     * Blocks are split by {@code ---} (triple dash) or double semicolon {@code ;;}.
     */
    @PostMapping("/analyze-text")
    public String analyzeText(
            @RequestParam("sqlText")                              String sqlText,
            @RequestParam(value = "delimiter", defaultValue = "---") String delimiter,
            Model model) {

        List<String> blocks = splitByDelimiter(sqlText, delimiter);
        if (blocks.isEmpty()) {
            model.addAttribute("error", "분석할 SQL 블록이 없습니다. 구분자로 SQL을 나눠 입력하세요.");
            return "sql-batch/index";
        }
        if (blocks.size() > MAX_BATCH_SIZE) {
            model.addAttribute("error", "한 번에 최대 " + MAX_BATCH_SIZE + "개 SQL까지 분석 가능합니다. 현재: " + blocks.size() + "개");
            return "sql-batch/index";
        }

        List<BatchItem> results = runBatch(blocks);
        model.addAttribute("results",    results);
        model.addAttribute("totalCount", results.size());
        model.addAttribute("okCount",    results.stream().filter(BatchItem::isSuccess).count());
        model.addAttribute("sqlText",    sqlText);
        model.addAttribute("delimiter",  delimiter);

        // Save summary to history
        saveBatchHistory(results);

        return "sql-batch/index";
    }

    /**
     * Analyze SQL blocks from an uploaded CSV or plain text file.
     * CSV: first column of each row is treated as one SQL block.
     * TXT/SQL: split same as text input.
     */
    @PostMapping("/analyze-file")
    public String analyzeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "delimiter", defaultValue = "---") String delimiter,
            Model model) throws IOException {

        if (file.isEmpty()) {
            model.addAttribute("error", "파일이 비어있습니다.");
            return "sql-batch/index";
        }

        String filename  = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String content   = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<String> blocks;

        if (filename.toLowerCase().endsWith(".csv")) {
            blocks = parseCsv(content);
        } else {
            blocks = splitByDelimiter(content, delimiter);
        }

        if (blocks.isEmpty()) {
            model.addAttribute("error", "파일에서 분석할 SQL을 찾을 수 없습니다.");
            return "sql-batch/index";
        }
        if (blocks.size() > MAX_BATCH_SIZE) {
            model.addAttribute("error", "한 번에 최대 " + MAX_BATCH_SIZE + "개 SQL까지 분석 가능합니다. 파일에 " + blocks.size() + "개가 있습니다.");
            return "sql-batch/index";
        }

        List<BatchItem> results = runBatch(blocks);
        model.addAttribute("results",    results);
        model.addAttribute("totalCount", results.size());
        model.addAttribute("okCount",    results.stream().filter(BatchItem::isSuccess).count());
        model.addAttribute("uploadedFilename", filename);

        saveBatchHistory(results);

        return "sql-batch/index";
    }

    /**
     * Download a combined Markdown report of the last submitted text batch.
     */
    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("sqlText")                              String sqlText,
            @RequestParam(value = "delimiter", defaultValue = "---") String delimiter) {

        List<String> blocks = splitByDelimiter(sqlText, delimiter);
        StringBuilder sb = new StringBuilder("# 배치 SQL 분석 보고서\n\n");
        sb.append("> 총 ").append(blocks.size()).append("개 SQL 일괄 리뷰\n\n");

        for (int i = 0; i < blocks.size(); i++) {
            String sql = blocks.get(i).trim();
            sb.append("---\n\n## SQL #").append(i + 1).append("\n\n```sql\n").append(sql).append("\n```\n\n");
            try {
                AdvisoryResult ar = sqlAdvisorService.review(sql);
                sb.append(ar.getReviewContent()).append("\n\n");
            } catch (Exception e) {
                sb.append("**오류**: ").append(e.getMessage()).append("\n\n");
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sql-batch-report.md\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private List<BatchItem> runBatch(List<String> sqlBlocks) {
        List<BatchItem> results = new ArrayList<BatchItem>();
        for (int i = 0; i < sqlBlocks.size(); i++) {
            String sql = sqlBlocks.get(i).trim();
            if (sql.isEmpty()) continue;
            try {
                AdvisoryResult ar = sqlAdvisorService.review(sql);
                results.add(new BatchItem(i + 1, sql, true, ar.getReviewContent(), ar.getSqlType()));
            } catch (Exception e) {
                results.add(new BatchItem(i + 1, sql, false, "오류: " + e.getMessage(), null));
            }
        }
        return results;
    }

    private void saveBatchHistory(List<BatchItem> results) {
        if (results.isEmpty()) return;
        StringBuilder input  = new StringBuilder();
        StringBuilder output = new StringBuilder("# 배치 SQL 분석 결과 (" + results.size() + "개)\n\n");
        for (BatchItem item : results) {
            input.append("-- SQL #").append(item.getIndex()).append("\n").append(item.getSql()).append("\n\n");
            output.append("## SQL #").append(item.getIndex()).append("\n\n").append(item.getResult()).append("\n\n");
        }
        historyService.save("SQL_BATCH", input.toString(), output.toString());
    }

    /** Split text into SQL blocks using a delimiter line or double semicolon. */
    private List<String> splitByDelimiter(String text, String delimiter) {
        List<String> blocks = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) return blocks;

        String[] parts;
        if (";;".equals(delimiter)) {
            parts = text.split(";;");
        } else if (";".equals(delimiter)) {
            // Split on semicolons at end-of-statement (simple heuristic)
            parts = text.split("(?m);\\s*$");
        } else {
            // Default: triple-dash on its own line
            parts = text.split("(?m)^\\s*---\\s*$");
        }

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                blocks.add(trimmed);
            }
        }
        return blocks;
    }

    /** Parse CSV: each non-empty first-column value is one SQL block. */
    private List<String> parseCsv(String csvContent) throws IOException {
        List<String> blocks = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(
                new java.io.StringReader(csvContent));
        boolean firstLine = true;
        String line;
        while ((line = reader.readLine()) != null) {
            if (firstLine) { firstLine = false; continue; } // skip header
            line = line.trim();
            if (line.isEmpty()) continue;
            // Take the first CSV column (handle quoted values)
            String sql;
            if (line.startsWith("\"")) {
                int end = line.indexOf('"', 1);
                sql = end > 0 ? line.substring(1, end) : line.substring(1);
            } else {
                int comma = line.indexOf(',');
                sql = comma >= 0 ? line.substring(0, comma) : line;
            }
            sql = sql.trim();
            if (!sql.isEmpty()) blocks.add(sql);
        }
        return blocks;
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    public static class BatchItem {
        private final int     index;
        private final String  sql;
        private final boolean success;
        private final String  result;
        private final SqlType sqlType;

        public BatchItem(int index, String sql, boolean success, String result, SqlType sqlType) {
            this.index   = index;
            this.sql     = sql;
            this.success = success;
            this.result  = result;
            this.sqlType = sqlType;
        }

        public int     getIndex()   { return index; }
        public String  getSql()     { return sql; }
        public boolean isSuccess()  { return success; }
        public String  getResult()  { return result; }
        public SqlType getSqlType() { return sqlType; }

        public String getSqlPreview() {
            if (sql == null) return "";
            String s = sql.replaceAll("\\s+", " ").trim();
            return s.length() > 80 ? s.substring(0, 80) + "…" : s;
        }

        public String getSqlTypeName() {
            return sqlType != null ? sqlType.getDisplayName() : "SQL";
        }
    }
}
