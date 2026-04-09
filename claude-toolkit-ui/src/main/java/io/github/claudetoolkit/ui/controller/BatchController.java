package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.testgen.TestGeneratorService;
import io.github.claudetoolkit.sql.advisor.SqlAdvisorService;
import io.github.claudetoolkit.sql.model.AdvisoryResult;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Web controller for batch (multi-file) processing (/batch).
 *
 * <p>Supports:
 * <ul>
 *   <li>SQL Batch Review — upload multiple .sql files, get one combined Markdown report</li>
 *   <li>Test Generation Batch — upload multiple .java files, download a zip of test files</li>
 * </ul>
 */
@Controller
@RequestMapping("/batch")
public class BatchController {

    private final SqlAdvisorService    sqlAdvisorService;
    private final TestGeneratorService testGeneratorService;
    private final ReviewHistoryService historyService;

    public BatchController(SqlAdvisorService sqlAdvisorService,
                           TestGeneratorService testGeneratorService,
                           ReviewHistoryService historyService) {
        this.sqlAdvisorService   = sqlAdvisorService;
        this.testGeneratorService = testGeneratorService;
        this.historyService       = historyService;
    }

    @GetMapping
    public String showForm(Model model) {
        return "batch/index";
    }

    // ── SQL Batch Review ──────────────────────────────────────────────────────

    @PostMapping("/sql-review")
    public String sqlReview(
            @RequestParam("files") List<MultipartFile> files,
            Model model) throws IOException {

        List<BatchResult> results = new ArrayList<BatchResult>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String name    = file.getOriginalFilename();
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            try {
                AdvisoryResult ar = sqlAdvisorService.review(content);
                results.add(new BatchResult(name, true, ar.getReviewContent()));
                historyService.save("SQL_REVIEW", content, ar.getReviewContent());
            } catch (Exception e) {
                results.add(new BatchResult(name, false, "오류: " + e.getMessage()));
            }
        }

        int successCount = 0, failCount = 0;
        for (BatchResult r : results) { if (r.isSuccess()) successCount++; else failCount++; }
        model.addAttribute("mode",         "sql");
        model.addAttribute("results",      results);
        model.addAttribute("count",        results.size());
        model.addAttribute("successCount", successCount);
        model.addAttribute("failCount",    failCount);
        return "batch/index";
    }

    @PostMapping("/sql-review/download")
    public ResponseEntity<byte[]> downloadSqlReport(
            @RequestParam("files") List<MultipartFile> files) throws IOException {

        StringBuilder sb = new StringBuilder("# SQL 일괄 리뷰 보고서\n\n");
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String name    = file.getOriginalFilename();
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            try {
                AdvisoryResult ar = sqlAdvisorService.review(content);
                sb.append("---\n\n## ").append(name).append("\n\n")
                  .append(ar.getReviewContent()).append("\n\n");
            } catch (Exception e) {
                sb.append("---\n\n## ").append(name).append(" — 오류\n\n").append(e.getMessage()).append("\n\n");
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sql-batch-report.md\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ── Test Generation Batch ─────────────────────────────────────────────────

    @PostMapping("/testgen")
    public String testGen(
            @RequestParam("files")                                       List<MultipartFile> files,
            @RequestParam(value = "sourceType", defaultValue = "General") String sourceType,
            Model model) throws IOException {

        List<BatchResult> results = new ArrayList<BatchResult>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String name    = file.getOriginalFilename();
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            try {
                String test = testGeneratorService.generateTest(content, sourceType);
                results.add(new BatchResult(name, true, test));
                historyService.save("TEST_GEN", content, test);
            } catch (Exception e) {
                results.add(new BatchResult(name, false, "오류: " + e.getMessage()));
            }
        }

        int successCount = 0, failCount = 0;
        for (BatchResult r : results) { if (r.isSuccess()) successCount++; else failCount++; }
        model.addAttribute("mode",         "testgen");
        model.addAttribute("results",      results);
        model.addAttribute("count",        results.size());
        model.addAttribute("successCount", successCount);
        model.addAttribute("failCount",    failCount);
        model.addAttribute("sourceType",   sourceType);
        return "batch/index";
    }

    @PostMapping("/testgen/download-zip")
    public ResponseEntity<byte[]> downloadTestZip(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "sourceType", defaultValue = "General") String sourceType) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                String originalName = file.getOriginalFilename();
                String content      = new String(file.getBytes(), StandardCharsets.UTF_8);
                try {
                    String test   = testGeneratorService.generateTest(content, sourceType);
                    String base   = (originalName != null && originalName.endsWith(".java"))
                            ? originalName.substring(0, originalName.length() - 5)
                            : originalName;
                    zip.putNextEntry(new ZipEntry(base + "Test.java"));
                    zip.write(test.getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                } catch (Exception e) {
                    zip.putNextEntry(new ZipEntry((originalName != null ? originalName : "unknown") + ".error.txt"));
                    zip.write(("생성 실패: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test-batch.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(baos.toByteArray());
    }

    // ── Inner result DTO ──────────────────────────────────────────────────────

    public static class BatchResult {
        private final String  filename;
        private final boolean success;
        private final String  content;

        public BatchResult(String filename, boolean success, String content) {
            this.filename = filename;
            this.success  = success;
            this.content  = content;
        }

        public String  getFilename() { return filename; }
        public boolean isSuccess()   { return success; }
        public String  getContent()  { return content; }
        public String  getPreview()  {
            if (content == null) return "";
            String c = content.replaceAll("[#*`>]", "").replaceAll("\\s+", " ").trim();
            return c.length() > 200 ? c.substring(0, 200) + "…" : c;
        }
    }
}
