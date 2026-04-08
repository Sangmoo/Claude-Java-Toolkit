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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web controller for Doc Generator feature (/docgen).
 *
 * Post-Redirect-Get 패턴 적용:
 * - POST /docgen/generate → 생성 후 redirect:/docgen (F5 재실행 방지)
 * - 결과는 flash attributes로 전달 (1회 세션 저장 후 GET 시 소비)
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

    /** GET /docgen — 폼 표시. flash attributes에 이전 결과가 있으면 함께 표시 */
    @GetMapping
    public String showForm(Model model) {
        // flash attributes(result, sourceCode 등)는 Spring이 model에 자동 병합.
        // settings 값만 추가 (덮어쓰기 방지: containsAttribute 체크)
        if (!model.containsAttribute("projectConfigured"))
            model.addAttribute("projectConfigured", settings.isProjectConfigured());
        if (!model.containsAttribute("currentScanPath"))
            model.addAttribute("currentScanPath", settings.getProject().getScanPath());
        if (!model.containsAttribute("projectContextActive"))
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "docgen/index";
    }

    /** POST /docgen/generate — PRG: 생성 후 redirect */
    @PostMapping("/generate")
    public String generate(
            @RequestParam("sourceCode") String sourceCode,
            @RequestParam(value = "sourceType",       defaultValue = "Oracle Stored Procedure") String sourceType,
            @RequestParam(value = "format",           defaultValue = "md")    String format,
            @RequestParam(value = "outputLang",       defaultValue = "ko")    String outputLang,
            @RequestParam(value = "scanPath",         defaultValue = "")      String scanPath,
            @RequestParam(value = "useProjectContext",defaultValue = "false") boolean useProjectContext,
            RedirectAttributes redirectAttrs) {

        // 빈 입력 방어
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            redirectAttrs.addFlashAttribute("error", "소스 코드를 입력하세요.");
            return "redirect:/docgen";
        }

        // 공통 설정 속성
        redirectAttrs.addFlashAttribute("projectConfigured",    settings.isProjectConfigured());
        redirectAttrs.addFlashAttribute("currentScanPath",      settings.getProject().getScanPath());
        redirectAttrs.addFlashAttribute("projectContextActive", settings.isProjectContextSet());
        // 폼 상태 복원용
        redirectAttrs.addFlashAttribute("sourceCode",           sourceCode);
        redirectAttrs.addFlashAttribute("sourceType",           sourceType);
        redirectAttrs.addFlashAttribute("format",               format);
        redirectAttrs.addFlashAttribute("outputLang",           outputLang);
        redirectAttrs.addFlashAttribute("scanPath",             scanPath);
        redirectAttrs.addFlashAttribute("useProjectContext",    useProjectContext);

        try {
            ScanContext sc = buildScanContext(scanPath, useProjectContext);
            if (sc.summary   != null) redirectAttrs.addFlashAttribute("scanSummary",        sc.summary);
            if (sc.contextUsed)       redirectAttrs.addFlashAttribute("projectContextUsed", true);

            String memoContext  = settings.getProjectContext();
            String projectContext;
            if (!sc.context.isEmpty() && !memoContext.isEmpty()) {
                projectContext = sc.context + "\n\n" + memoContext;
            } else if (!sc.context.isEmpty()) {
                projectContext = sc.context;
            } else {
                projectContext = memoContext;
            }

            String result = generateDoc(sourceCode, sourceType, format, outputLang, projectContext);
            historyService.save("DOC_GEN", sourceCode, result);
            redirectAttrs.addFlashAttribute("result", result);

        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("error", "Generation failed: " + e.getMessage());
        }

        return "redirect:/docgen";
    }

    /** POST /docgen/generate/file — 파일 업로드 후 PRG */
    @PostMapping("/generate/file")
    public String generateFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format",           defaultValue = "md")    String format,
            @RequestParam(value = "outputLang",       defaultValue = "ko")    String outputLang,
            @RequestParam(value = "scanPath",         defaultValue = "")      String scanPath,
            @RequestParam(value = "useProjectContext",defaultValue = "false") boolean useProjectContext,
            RedirectAttributes redirectAttrs) throws IOException {

        String sourceCode = new String(file.getBytes(), StandardCharsets.UTF_8);
        String sourceType = detectType(file.getOriginalFilename());
        return generate(sourceCode, sourceType, format, outputLang, scanPath, useProjectContext, redirectAttrs);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("sourceCode")                                      String sourceCode,
            @RequestParam(value = "sourceType", defaultValue = "Oracle Stored Procedure") String sourceType,
            @RequestParam(value = "format",     defaultValue = "md")         String format) {

        String result   = generateDoc(sourceCode, sourceType, format, "ko", settings.getProjectContext());
        String filename = "documentation." + ("html".equals(format) ? "html" : format.equals("typst") ? "typ" : "md");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String generateDoc(String sourceCode, String sourceType, String format,
                               String outputLang, String projectContext) {
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

    /** 스캔 컨텍스트 빌드 결과 DTO */
    private static class ScanContext {
        final String  context;
        final String  summary;
        final boolean contextUsed;
        final String  scanWarning;

        ScanContext(String context, String summary, boolean contextUsed, String scanWarning) {
            this.context     = context;
            this.summary     = summary;
            this.contextUsed = contextUsed;
            this.scanWarning = scanWarning;
        }
    }

    private ScanContext buildScanContext(String scanPath, boolean useProjectContext) {
        if (!useProjectContext) return new ScanContext("", null, false, null);

        String effectivePath = (scanPath != null && !scanPath.trim().isEmpty())
                ? scanPath.trim()
                : settings.getProject().getScanPath();

        if (effectivePath.isEmpty()) return new ScanContext("", null, false, null);

        try {
            List<ScannedFile> files = projectScannerService.scanProject(effectivePath);
            String summary          = projectScannerService.getScanSummary(files);
            String context          = projectScannerService.buildContext(files);
            return new ScanContext(context, summary, true, null);
        } catch (IOException e) {
            String warn = "프로젝트 스캔 실패 (문서 생성은 계속 진행): " + e.getMessage();
            return new ScanContext("", null, false, warn);
        }
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

    private String detectType(String filename) {
        if (filename == null) return "Source Code";
        String upper = filename.toUpperCase();
        if (upper.endsWith(".SQL"))  return "Oracle Stored Procedure / SQL";
        if (upper.endsWith(".JAVA")) return "Java Source";
        if (upper.endsWith(".XML"))  return "iBatis/MyBatis Mapper XML";
        return "Source Code";
    }
}
