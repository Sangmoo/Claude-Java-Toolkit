package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.codereview.CodeReviewService;
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
 * Web controller for Java Code Review feature (/codereview).
 */
@Controller
@RequestMapping("/codereview")
public class CodeReviewController {

    private final CodeReviewService     codeReviewService;
    private final ProjectScannerService projectScannerService;
    private final ToolkitSettings       settings;
    private final ReviewHistoryService  historyService;
    private final PromptTemplateService promptTemplateService;

    public CodeReviewController(CodeReviewService codeReviewService,
                                ProjectScannerService projectScannerService,
                                ToolkitSettings settings,
                                ReviewHistoryService historyService,
                                PromptTemplateService promptTemplateService) {
        this.codeReviewService     = codeReviewService;
        this.projectScannerService = projectScannerService;
        this.settings              = settings;
        this.historyService        = historyService;
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("projectConfigured",    settings.isProjectConfigured());
        model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "codereview/index";
    }

    @PostMapping("/review")
    public String review(
            @RequestParam("sourceCode")                                   String  sourceCode,
            @RequestParam(value = "sourceType", defaultValue = "Java Class") String  sourceType,
            @RequestParam(value = "reviewMode", defaultValue = "full")    String  reviewMode,
            @RequestParam(value = "useProjectContext", defaultValue = "false") boolean useProjectContext,
            @RequestParam(value = "scanPath",    defaultValue = "")       String  scanPath,
            Model model) {

        try {
            String result;
            String memoCtx = settings.getProjectContext();
            String codeReviewPrompt = promptTemplateService.getPrompt("CODE_REVIEW", CodeReviewService.DEFAULT_SYSTEM_PROMPT);
            String codeSecPrompt    = promptTemplateService.getPrompt("CODE_SECURITY", CodeReviewService.DEFAULT_SYSTEM_PROMPT_SECURITY);
            if ("security".equals(reviewMode)) {
                result = codeReviewService.reviewSecurity(sourceCode, sourceType, codeSecPrompt);
                historyService.save("CODE_REVIEW_SEC", sourceCode, result);
            } else if (useProjectContext) {
                String scanCtx = buildContext(scanPath, model);
                String combinedCtx;
                if (!scanCtx.isEmpty() && !memoCtx.isEmpty()) {
                    combinedCtx = scanCtx + "\n\n" + memoCtx;
                } else if (!scanCtx.isEmpty()) {
                    combinedCtx = scanCtx;
                } else {
                    combinedCtx = memoCtx;
                }
                result = codeReviewService.reviewWithContext(sourceCode, sourceType, combinedCtx, codeReviewPrompt);
                historyService.save("CODE_REVIEW", sourceCode, result);
            } else if (settings.isProjectContextSet()) {
                result = codeReviewService.reviewWithContext(sourceCode, sourceType, memoCtx, codeReviewPrompt);
                historyService.save("CODE_REVIEW", sourceCode, result);
            } else {
                result = codeReviewService.reviewWithContext(sourceCode, sourceType, "", codeReviewPrompt);
                historyService.save("CODE_REVIEW", sourceCode, result);
            }

            model.addAttribute("result",               result);
            model.addAttribute("sourceCode",           sourceCode);
            model.addAttribute("sourceType",           sourceType);
            model.addAttribute("reviewMode",           reviewMode);
            model.addAttribute("useProjectContext",    useProjectContext);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());

        } catch (Exception e) {
            model.addAttribute("error",                "리뷰 실패: " + e.getMessage());
            model.addAttribute("sourceCode",           sourceCode);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        }

        model.addAttribute("projectConfigured",    settings.isProjectConfigured());
        model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "codereview/index";
    }

    @PostMapping("/review/file")
    public String reviewFromFile(
            @RequestParam("file")  MultipartFile file,
            @RequestParam(value = "sourceType",      defaultValue = "Java Class") String  sourceType,
            @RequestParam(value = "reviewMode",      defaultValue = "full")       String  reviewMode,
            @RequestParam(value = "useProjectContext", defaultValue = "false")    boolean useProjectContext,
            @RequestParam(value = "scanPath",        defaultValue = "")           String  scanPath,
            Model model) throws IOException {

        String sourceCode = new String(file.getBytes(), StandardCharsets.UTF_8);
        return review(sourceCode, sourceType, reviewMode, useProjectContext, scanPath, model);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("sourceCode")                                       String sourceCode,
            @RequestParam(value = "sourceType", defaultValue = "Java Class")  String sourceType,
            @RequestParam(value = "reviewMode", defaultValue = "full")        String reviewMode) {

        String result;
        if ("security".equals(reviewMode)) {
            result = codeReviewService.reviewSecurity(sourceCode, sourceType);
        } else if (settings.isProjectContextSet()) {
            result = codeReviewService.reviewWithContext(sourceCode, sourceType, settings.getProjectContext());
        } else {
            result = codeReviewService.review(sourceCode, sourceType);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"code-review.md\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private String buildContext(String scanPath, Model model) {
        String effectivePath = (scanPath != null && !scanPath.trim().isEmpty())
                ? scanPath.trim()
                : settings.getProject().getScanPath();
        if (effectivePath.isEmpty()) return "";
        try {
            List<ScannedFile> files = projectScannerService.scanProject(effectivePath);
            model.addAttribute("projectContextUsed", true);
            return projectScannerService.buildContext(files);
        } catch (IOException e) {
            model.addAttribute("scanWarning", "프로젝트 스캔 실패: " + e.getMessage());
            return "";
        }
    }
}
