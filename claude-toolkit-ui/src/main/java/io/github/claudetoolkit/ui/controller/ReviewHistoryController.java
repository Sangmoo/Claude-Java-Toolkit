package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web controller for Review History (/history).
 * All entries are in-memory (cleared on server restart).
 */
@Controller
@RequestMapping("/history")
public class ReviewHistoryController {

    private final ReviewHistoryService historyService;

    public ReviewHistoryController(ReviewHistoryService historyService) {
        this.historyService = historyService;
    }

    /** List all history entries */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("histories", historyService.findAll());
        model.addAttribute("count",     historyService.count());
        return "history/index";
    }

    /** View detail of a single entry (AJAX — returns JSON body via responseBody) */
    @GetMapping("/{id}/detail")
    @ResponseBody
    public java.util.Map<String, String> detail(@PathVariable long id) {
        ReviewHistory h = historyService.findById(id);
        java.util.Map<String, String> map = new java.util.LinkedHashMap<String, String>();
        if (h == null) {
            map.put("error", "Not found");
            return map;
        }
        map.put("type",     h.getTypeLabel());
        map.put("typeCode", h.getType());
        map.put("title",    h.getTitle());
        map.put("date",     h.getFormattedDate());
        map.put("input",    h.getInputContent());
        map.put("output",   h.getOutputContent());
        return map;
    }

    /** Delete a single entry */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id) {
        historyService.deleteById(id);
        return "redirect:/history";
    }

    /** Clear all history */
    @PostMapping("/clear")
    public String clear() {
        historyService.clear();
        return "redirect:/history";
    }

    /** Export all history as CSV (UTF-8 BOM for Excel compatibility) */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv() {
        List<ReviewHistory> all = historyService.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("ID,유형,제목,날짜,입력 미리보기,결과 미리보기\n");
        for (ReviewHistory h : all) {
            sb.append(h.getId()).append(",");
            sb.append(csvEscape(h.getTypeLabel())).append(",");
            sb.append(csvEscape(h.getTitle())).append(",");
            sb.append(csvEscape(h.getFormattedDate())).append(",");
            sb.append(csvEscape(truncate(h.getInputContent(), 300))).append(",");
            sb.append(csvEscape(truncate(h.getOutputContent(), 300))).append("\n");
        }
        // BOM for Excel
        byte[] bom  = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out  = new byte[bom.length + body.length];
        System.arraycopy(bom,  0, out, 0,           bom.length);
        System.arraycopy(body, 0, out, bom.length,  body.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"review-history.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(out);
    }

    /** diff 비교: 두 항목 ID를 받아 입력/출력 나란히 비교 */
    @GetMapping("/diff")
    @ResponseBody
    public java.util.Map<String, Object> diff(
            @RequestParam long idA,
            @RequestParam long idB) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
        ReviewHistory a = historyService.findById(idA);
        ReviewHistory b = historyService.findById(idB);
        if (a == null || b == null) {
            result.put("error", "항목을 찾을 수 없습니다.");
            return result;
        }
        result.put("titleA", a.getTitle());
        result.put("titleB", b.getTitle());
        result.put("dateA", a.getFormattedDate());
        result.put("dateB", b.getFormattedDate());
        result.put("inputA", a.getInputContent());
        result.put("inputB", b.getInputContent());
        result.put("outputA", a.getOutputContent());
        result.put("outputB", b.getOutputContent());
        result.put("typeA", a.getTypeLabel());
        result.put("typeB", b.getTypeLabel());
        return result;
    }

    /** 복수 항목 Markdown 보고서 ZIP 다운로드 */
    @PostMapping("/export/bundle")
    public ResponseEntity<byte[]> exportBundle(@RequestParam String ids) {
        String[] idArr = ids.split(",");
        StringBuilder sb = new StringBuilder();
        sb.append("# Claude Java Toolkit — 분석 보고서 묶음\n\n");
        sb.append("생성일시: ").append(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n---\n\n");
        for (String idStr : idArr) {
            idStr = idStr.trim();
            if (idStr.isEmpty()) continue;
            try {
                long id = Long.parseLong(idStr);
                ReviewHistory h = historyService.findById(id);
                if (h == null) continue;
                sb.append("# ").append(h.getTypeLabel()).append(" — ").append(h.getTitle()).append("\n\n");
                sb.append("**분석 일시**: ").append(h.getFormattedDate()).append("\n\n");
                sb.append("## 입력 내용\n\n```\n").append(h.getInputContent()).append("\n```\n\n");
                sb.append("## 분석 결과\n\n").append(h.getOutputContent()).append("\n\n---\n\n");
            } catch (NumberFormatException e) {
                // skip invalid id
            }
        }
        byte[] bytes;
        try {
            bytes = sb.toString().getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            bytes = sb.toString().getBytes();
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"bundle-report.md\"")
                .header("Content-Type", "text/markdown; charset=UTF-8")
                .body(bytes);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private String csvEscape(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\"", "\"\"").replace("\n", " ").replace("\r", "") + "\"";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
