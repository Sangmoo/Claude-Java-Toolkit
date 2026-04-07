package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.harness.HarnessCacheService;
import io.github.claudetoolkit.ui.harness.HarnessCacheService.DbObjectEntry;
import io.github.claudetoolkit.ui.harness.HarnessCacheService.FileEntry;
import io.github.claudetoolkit.ui.harness.HarnessReviewService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Code Review Harness feature (/harness).
 *
 * <p>Analyst → Builder → Reviewer 3-step pipeline with:
 * <ul>
 *   <li>Manual code/SQL input (textarea)</li>
 *   <li>Project Java file browser (backed by {@link HarnessCacheService})</li>
 *   <li>Oracle DB object browser with source fetch</li>
 *   <li>Side-by-side LCS diff view</li>
 *   <li>SSE streaming mode</li>
 * </ul>
 */
@Controller
@RequestMapping("/harness")
public class HarnessController {

    private final HarnessReviewService harnessService;
    private final ReviewHistoryService historyService;
    private final SseStreamController  sseStreamController;
    private final HarnessCacheService  cacheService;

    public HarnessController(HarnessReviewService harnessService,
                             ReviewHistoryService historyService,
                             SseStreamController  sseStreamController,
                             HarnessCacheService  cacheService) {
        this.harnessService      = harnessService;
        this.historyService      = historyService;
        this.sseStreamController = sseStreamController;
        this.cacheService        = cacheService;
    }

    // ── Page ─────────────────────────────────────────────────────────────────

    @GetMapping
    public String index(Model model) {
        return "harness/index";
    }

    // ── Pipeline analysis ─────────────────────────────────────────────────────

    /**
     * Run the full harness pipeline synchronously.
     * Returns JSON: {success, originalCode, improvedCode, fullResponse, language}
     */
    @PostMapping("/analyze")
    @ResponseBody
    public Map<String, Object> analyze(
            @RequestParam("code")                                        String code,
            @RequestParam(value = "language",     defaultValue = "java") String language,
            @RequestParam(value = "templateHint", defaultValue = "")     String templateHint) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            String response     = harnessService.analyze(code, language, templateHint);
            String improvedCode = harnessService.extractImprovedCode(response, language);
            historyService.saveHarness(code, response, language, improvedCode);

            result.put("success",      true);
            result.put("originalCode", code);
            result.put("improvedCode", improvedCode);
            result.put("fullResponse", response);
            result.put("language",     language);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getMessage() != null
                    ? e.getMessage() : "분석 중 오류가 발생했습니다.");
        }
        return result;
    }

    /**
     * Register an SSE stream for real-time streaming harness analysis.
     * After calling this endpoint, open GET /stream/{streamId}.
     */
    @PostMapping("/stream-init")
    @ResponseBody
    public Map<String, Object> streamInit(
            @RequestParam("code")                                        String code,
            @RequestParam(value = "language",     defaultValue = "java") String language,
            @RequestParam(value = "templateHint", defaultValue = "")     String templateHint) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            String streamId = sseStreamController.registerStream(
                    "harness_review", code, templateHint, language);
            result.put("success",  true);
            result.put("streamId", streamId);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getMessage() != null
                    ? e.getMessage() : "스트림 등록 오류");
        }
        return result;
    }

    // ── Cache: Java file browser ──────────────────────────────────────────────

    /**
     * Returns list of Java files from the startup cache.
     * Supports keyword search (filters relativePath and fileName).
     * Response is capped at 200 items; use {@code q} to narrow down.
     */
    @GetMapping("/cache/files")
    @ResponseBody
    public Map<String, Object> getCachedFiles(
            @RequestParam(value = "q", defaultValue = "") String q) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<FileEntry> all = cacheService.getCachedFiles();

        if (!q.trim().isEmpty()) {
            String kw = q.trim().toLowerCase();
            List<FileEntry> filtered = new ArrayList<FileEntry>();
            for (FileEntry f : all) {
                if (f.relativePath.toLowerCase().contains(kw)
                        || f.fileName.toLowerCase().contains(kw)) {
                    filtered.add(f);
                }
            }
            all = filtered;
        }

        int total      = all.size();
        List<FileEntry> page = total > 200 ? all.subList(0, 200) : all;

        result.put("loaded",      cacheService.isFileCacheLoaded());
        result.put("refreshing",  cacheService.isFileRefreshing());
        result.put("totalCount",  cacheService.getCachedFiles().size()); // unfiltered total
        result.put("count",       total);
        result.put("files",       page);
        result.put("lastRefresh", cacheService.getLastFileRefresh());
        return result;
    }

    /**
     * Reads and returns the content of a Java file.
     * Security: path must be under the configured project scan path.
     */
    @GetMapping("/cache/file-content")
    @ResponseBody
    public Map<String, Object> getFileContent(
            @RequestParam("path") String path) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        String content = cacheService.readFileContent(path);
        if (content == null) {
            result.put("success", false);
            result.put("error",   "파일을 읽을 수 없습니다. 경로를 확인하거나 Settings에서 프로젝트 경로를 설정하세요.");
        } else {
            result.put("success", true);
            result.put("content", content);
        }
        return result;
    }

    // ── Cache: Oracle DB object browser ──────────────────────────────────────

    /**
     * Returns list of Oracle DB objects from the startup cache.
     * Supports keyword search and optional type filter.
     * Response is capped at 300 items.
     */
    @GetMapping("/cache/db-objects")
    @ResponseBody
    public Map<String, Object> getCachedDbObjects(
            @RequestParam(value = "q",    defaultValue = "") String q,
            @RequestParam(value = "type", defaultValue = "") String type) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<DbObjectEntry> all = cacheService.getCachedDbObjects();

        if (!q.trim().isEmpty()) {
            String kw = q.trim().toLowerCase();
            List<DbObjectEntry> filtered = new ArrayList<DbObjectEntry>();
            for (DbObjectEntry o : all) {
                if (o.name.toLowerCase().contains(kw)
                        || o.owner.toLowerCase().contains(kw)) {
                    filtered.add(o);
                }
            }
            all = filtered;
        }
        if (!type.trim().isEmpty()) {
            List<DbObjectEntry> filtered = new ArrayList<DbObjectEntry>();
            for (DbObjectEntry o : all) {
                if (type.trim().toUpperCase().equals(o.type)) {
                    filtered.add(o);
                }
            }
            all = filtered;
        }

        int total = all.size();
        List<DbObjectEntry> page = total > 300 ? all.subList(0, 300) : all;

        result.put("loaded",      cacheService.isDbCacheLoaded());
        result.put("refreshing",  cacheService.isDbRefreshing());
        result.put("configured",  cacheService.isDbConfiguredAtLastRefresh());
        result.put("dbError",     cacheService.getLastDbError());
        result.put("totalCount",  cacheService.getCachedDbObjects().size());
        result.put("count",       total);
        result.put("objects",     page);
        result.put("lastRefresh", cacheService.getLastDbRefresh());
        return result;
    }

    /**
     * Fetches the full source code of an Oracle DB object from ALL_SOURCE.
     * This makes a live DB query (not cached) to ensure freshness.
     */
    @GetMapping("/cache/db-source")
    @ResponseBody
    public Map<String, Object> getDbSource(
            @RequestParam("name") String name,
            @RequestParam("type") String type) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        String source = cacheService.getDbObjectSource(name, type);
        if (source == null) {
            result.put("success", false);
            result.put("error",   "소스를 가져올 수 없습니다. DB 연결 상태를 확인하세요.");
        } else {
            result.put("success", true);
            result.put("source",  source);
            result.put("name",    name);
            result.put("type",    type);
        }
        return result;
    }

    /**
     * Exports the full harness analysis result as a self-contained HTML file.
     */
    @PostMapping("/export-html")
    public void exportHtml(
            @RequestParam("code")        String code,
            @RequestParam("improved")    String improved,
            @RequestParam("response")    String response,
            @RequestParam(value = "language", defaultValue = "java") String language,
            javax.servlet.http.HttpServletResponse httpResp) throws Exception {
        httpResp.setContentType("text/html; charset=UTF-8");
        httpResp.setHeader("Content-Disposition",
                "attachment; filename=\"harness-review-" + java.time.LocalDate.now() + ".html\"");
        java.io.PrintWriter out = httpResp.getWriter();
        out.println("<!DOCTYPE html><html lang='ko'><head><meta charset='UTF-8'>");
        out.println("<title>하네스 리뷰 결과</title>");
        out.println("<style>body{font-family:sans-serif;margin:32px;line-height:1.7;color:#1e293b;}");
        out.println("h1{color:#8b5cf6;}h2{color:#6d28d9;border-bottom:1px solid #ddd;padding-bottom:4px;}");
        out.println("pre{background:#f1f5f9;padding:12px;border-radius:8px;overflow-x:auto;font-size:.83rem;}");
        out.println(".diff{display:grid;grid-template-columns:1fr 1fr;gap:12px;}");
        out.println(".orig{border-left:4px solid #f87171;padding:0 12px;}.impr{border-left:4px solid #34d399;padding:0 12px;}");
        out.println("</style></head><body>");
        out.println("<h1>하네스 코드 리뷰 결과</h1>");
        out.println("<p><strong>분석 언어:</strong> " + escapeHtml(language.toUpperCase())
                  + " &nbsp;·&nbsp; <strong>분석 일시:</strong> " + java.time.LocalDateTime.now() + "</p>");
        out.println("<div class='diff'>");
        out.println("<div class='orig'><h2>원본 코드</h2><pre>" + escapeHtml(code) + "</pre></div>");
        out.println("<div class='impr'><h2>개선된 코드</h2><pre>" + escapeHtml(improved) + "</pre></div>");
        out.println("</div>");
        out.println("<h2>분석 결과</h2><pre>" + escapeHtml(response) + "</pre>");
        out.println("</body></html>");
        out.flush();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    /**
     * Triggers asynchronous cache refresh.
     * {@code target}: "all" (default), "files", "db"
     */
    @PostMapping("/cache/refresh")
    @ResponseBody
    public Map<String, Object> refreshCache(
            @RequestParam(value = "target", defaultValue = "all") final String target) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    if ("all".equals(target) || "files".equals(target)) {
                        cacheService.refreshFileCache();
                    }
                    if ("all".equals(target) || "db".equals(target)) {
                        cacheService.refreshDbCache();
                    }
                }
            });
            t.setDaemon(true);
            t.setName("harness-cache-refresh");
            t.start();
            result.put("success", true);
            result.put("message", "캐시 갱신을 백그라운드에서 시작했습니다.");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getMessage() != null ? e.getMessage() : "갱신 실패");
        }
        return result;
    }
}
