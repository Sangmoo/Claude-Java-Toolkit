package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.generator.DocGeneratorService;
import io.github.claudetoolkit.docgen.scanner.ProjectScannerService;
import io.github.claudetoolkit.docgen.scanner.ScannedFile;
import io.github.claudetoolkit.ui.config.PromptTemplateService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web controller for Doc Generator feature (/docgen).
 *
 * Improvements:
 * - HTML output format added
 * - Output language selection
 * - Review history auto-save
 */
@Controller
@RequestMapping("/docgen")
public class DocGeneratorController {

    private final DocGeneratorService   docGeneratorService;
    private final ProjectScannerService projectScannerService;
    private final ToolkitSettings       settings;
    private final ReviewHistoryService  historyService;
    private final PromptTemplateService promptTemplateService;

    public DocGeneratorController(DocGeneratorService docGeneratorService,
                                  ProjectScannerService projectScannerService,
                                  ToolkitSettings settings,
                                  ReviewHistoryService historyService,
                                  PromptTemplateService promptTemplateService) {
        this.docGeneratorService   = docGeneratorService;
        this.projectScannerService = projectScannerService;
        this.settings              = settings;
        this.historyService        = historyService;
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("projectConfigured",    settings.isProjectConfigured());
        model.addAttribute("currentScanPath",      settings.getProject().getScanPath());
        model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "docgen/index";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam("sourceCode") String sourceCode,
            @RequestParam(value = "sourceType",       defaultValue = "Oracle Stored Procedure") String sourceType,
            @RequestParam(value = "format",           defaultValue = "md")    String format,
            @RequestParam(value = "outputLang",       defaultValue = "ko")    String outputLang,
            @RequestParam(value = "scanPath",         defaultValue = "")      String scanPath,
            @RequestParam(value = "useProjectContext",defaultValue = "false") boolean useProjectContext,
            Model model) {

        try {
            String scanContext  = buildProjectContext(scanPath, useProjectContext, model);
            String memoContext  = settings.getProjectContext();
            String projectContext;
            if (!scanContext.isEmpty() && !memoContext.isEmpty()) {
                projectContext = scanContext + "\n\n" + memoContext;
            } else if (!scanContext.isEmpty()) {
                projectContext = scanContext;
            } else {
                projectContext = memoContext;
            }
            String result = generateDoc(sourceCode, sourceType, format, outputLang, projectContext);

            historyService.save("DOC_GEN", sourceCode, result);

            model.addAttribute("result",               result);
            model.addAttribute("sourceCode",           sourceCode);
            model.addAttribute("sourceType",           sourceType);
            model.addAttribute("format",               format);
            model.addAttribute("outputLang",           outputLang);
            model.addAttribute("scanPath",             scanPath);
            model.addAttribute("useProjectContext",    useProjectContext);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());

        } catch (Exception e) {
            model.addAttribute("error",                "Generation failed: " + e.getMessage());
            model.addAttribute("sourceCode",           sourceCode);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        }

        model.addAttribute("projectConfigured",    settings.isProjectConfigured());
        model.addAttribute("currentScanPath",      settings.getProject().getScanPath());
        model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "docgen/index";
    }

    @PostMapping("/generate/file")
    public String generateFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format",           defaultValue = "md")    String format,
            @RequestParam(value = "outputLang",       defaultValue = "ko")    String outputLang,
            @RequestParam(value = "scanPath",         defaultValue = "")      String scanPath,
            @RequestParam(value = "useProjectContext",defaultValue = "false") boolean useProjectContext,
            Model model) throws IOException {

        String sourceCode = new String(file.getBytes(), StandardCharsets.UTF_8);
        String sourceType = detectType(file.getOriginalFilename());
        return generate(sourceCode, sourceType, format, outputLang, scanPath, useProjectContext, model);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("sourceCode")                                      String sourceCode,
            @RequestParam(value = "sourceType", defaultValue = "Oracle Stored Procedure") String sourceType,
            @RequestParam(value = "format",     defaultValue = "md")         String format) {

        String result = generateDoc(sourceCode, sourceType, format, "ko", settings.getProjectContext());
        String filename = "documentation." + ("html".equals(format) ? "html" : format.equals("typst") ? "typ" : "md");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String generateDoc(String sourceCode, String sourceType, String format,
                               String outputLang, String projectContext) {
        // Prepend language instruction to context
        String langHint = "en".equals(outputLang)
                ? "\n\n[INSTRUCTION: Write the documentation in English.]\n\n"
                : "";
        String ctx = langHint + (projectContext == null ? "" : projectContext);
        String docPrompt = promptTemplateService.getPrompt("DOC_GENERATE", DocGeneratorService.DEFAULT_SYSTEM_PROMPT_MARKDOWN);

        if ("typst".equals(format)) {
            return docGeneratorService.generateTypstWithContext(sourceCode, sourceType, ctx);
        }
        if ("html".equals(format)) {
            String md = docGeneratorService.generateMarkdownWithContext(sourceCode, sourceType, ctx, docPrompt);
            return wrapInHtml(md, sourceType);
        }
        return docGeneratorService.generateMarkdownWithContext(sourceCode, sourceType, ctx, docPrompt);
    }

    private String wrapInHtml(String markdownContent, String title) {
        return "<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<title>" + title + " - 기술 문서</title>\n"
                + "<style>body{font-family:sans-serif;max-width:900px;margin:40px auto;padding:0 20px;line-height:1.7;}"
                + "pre{background:#f4f4f4;padding:14px;border-radius:6px;overflow-x:auto;}"
                + "code{background:#f0f0f0;padding:2px 5px;border-radius:3px;}"
                + "table{border-collapse:collapse;width:100%;}td,th{border:1px solid #ddd;padding:8px;}"
                + "</style>\n</head>\n<body>\n"
                + "<pre id=\"md-src\" style=\"display:none\">" + escapeHtml(markdownContent) + "</pre>\n"
                + "<div id=\"content\"></div>\n"
                + "<script src=\"https://cdn.jsdelivr.net/npm/marked@9.1.6/marked.min.js\"></script>\n"
                + "<script>document.getElementById('content').innerHTML="
                + "marked.parse(document.getElementById('md-src').textContent);</script>\n"
                + "</body>\n</html>";
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String buildProjectContext(String scanPath, boolean useProjectContext, Model model) {
        if (!useProjectContext) return "";

        String effectivePath = (scanPath != null && !scanPath.trim().isEmpty())
                ? scanPath.trim()
                : settings.getProject().getScanPath();

        if (effectivePath.isEmpty()) return "";

        try {
            List<ScannedFile> files  = projectScannerService.scanProject(effectivePath);
            String summary           = projectScannerService.getScanSummary(files);
            model.addAttribute("scanSummary", summary);
            model.addAttribute("projectContextUsed", true);
            return projectScannerService.buildContext(files);
        } catch (IOException e) {
            model.addAttribute("scanWarning",
                    "프로젝트 스캔 실패 (문서 생성은 계속 진행): " + e.getMessage());
            return "";
        }
    }

    private String detectType(String filename) {
        if (filename == null) return "Source Code";
        String upper = filename.toUpperCase();
        if (upper.endsWith(".SQL"))  return "Oracle Stored Procedure / SQL";
        if (upper.endsWith(".JAVA")) return "Java Source";
        if (upper.endsWith(".XML"))  return "iBatis/MyBatis Mapper XML";
        return "Source Code";
    }
}
