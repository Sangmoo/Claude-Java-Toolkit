package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.OutputStreamWriter;
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
        map.put("id",       String.valueOf(h.getId()));
        map.put("type",     h.getTypeLabel());
        map.put("typeCode", h.getType());
        map.put("title",    h.getTitle());
        map.put("date",     h.getFormattedDate());
        map.put("input",    h.getInputContent());
        map.put("output",   h.getOutputContent());
        return map;
    }

    /**
     * Delete a single entry.
     *
     * <p>v4.2.7: VIEWER 권한은 이력 삭제 금지 — 요청이 들어와도 403 으로 거부한다.
     * 프론트엔드가 삭제 아이콘을 숨기더라도 API 는 독립적으로 권한을 강제해야
     * 브라우저 DevTools/curl 등 우회 경로를 막을 수 있다.
     */
    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> delete(
            @PathVariable long id,
            HttpServletRequest request) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        // ADMIN/REVIEWER 는 허용, 그 외(VIEWER/anonymous)는 거부
        boolean allowed = request.isUserInRole("ADMIN") || request.isUserInRole("REVIEWER");
        if (!allowed) {
            resp.put("success", false);
            resp.put("error",   "VIEWER 권한은 리뷰 이력을 삭제할 수 없습니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }
        historyService.deleteById(id);
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    /**
     * Clear <b>all</b> history across <b>all</b> users.
     *
     * <p>v4.2.7 — 감사 결과: 전사 데이터 삭제 기능. 기존엔 권한 체크 없이 누구든
     * 호출 가능했다. ADMIN 전용으로 제한. 프론트 사용처 없음.
     */
    @PostMapping("/clear")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> clear(HttpServletRequest request) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        if (!request.isUserInRole("ADMIN")) {
            resp.put("success", false);
            resp.put("error",   "ADMIN 권한이 필요합니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }
        historyService.clear();
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    /**
     * Deletes the N most recent entries of a given type.
     * Usage: POST /history/purge?type=HARNESS_REVIEW&amp;count=5
     *
     * <p>v4.2.8 감사: 기존엔 권한 체크 없이 VIEWER 도 호출 가능했고 CSRF 도
     * /history/** 전체가 ignore 돼 있어서 사실상 개방 상태였음.
     * ADMIN 전용으로 제한.
     */
    @PostMapping("/purge")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> purge(
            @RequestParam(defaultValue = "HARNESS_REVIEW") String type,
            @RequestParam(defaultValue = "5")              int    count,
            HttpServletRequest request) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        if (!request.isUserInRole("ADMIN")) {
            resp.put("success", false);
            resp.put("error",   "ADMIN 권한이 필요합니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }
        int deleted = historyService.deleteRecentByType(type, count);
        resp.put("success", true);
        resp.put("deleted", deleted);
        resp.put("type",    type);
        return ResponseEntity.ok(resp);
    }

    /** Export all history as CSV (UTF-8 BOM for Excel compatibility).
     *  StreamingResponseBody 로 행 단위 스트리밍 — 대용량 이력에서 메모리 2배 할당 방지. */
    @GetMapping("/export/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv() {
        List<ReviewHistory> all = historyService.findAll();
        StreamingResponseBody stream = out -> {
            out.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}); // BOM for Excel
            OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            w.write("ID,유형,제목,날짜,입력 미리보기,결과 미리보기\n");
            for (ReviewHistory h : all) {
                w.write(h.getId() + ",");
                w.write(csvEscape(h.getTypeLabel()) + ",");
                w.write(csvEscape(h.getTitle()) + ",");
                w.write(csvEscape(h.getFormattedDate()) + ",");
                w.write(csvEscape(truncate(h.getInputContent(), 300)) + ",");
                w.write(csvEscape(truncate(h.getOutputContent(), 300)) + "\n");
            }
            w.flush();
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"review-history.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(stream);
    }

    /** Single entry Markdown download */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable long id) {
        ReviewHistory h = historyService.findById(id);
        if (h == null) {
            return ResponseEntity.notFound().build();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(h.getTypeLabel()).append(" — ").append(h.getTitle()).append("\n\n");
        sb.append("**분석 일시**: ").append(h.getFormattedDate()).append("\n\n");
        sb.append("## 입력 내용\n\n```\n").append(h.getInputContent()).append("\n```\n\n");
        sb.append("## 분석 결과\n\n").append(h.getOutputContent()).append("\n");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "analysis-" + id + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(bytes);
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
