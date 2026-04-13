package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.complexity.ComplexityAnalyzerService;
import io.github.claudetoolkit.docgen.scanner.ProjectScannerService;
import io.github.claudetoolkit.docgen.scanner.ScannedFile;
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
 * Web controller for Code Complexity Analyzer (/complexity).
 */
@Controller
@RequestMapping("/complexity")
public class ComplexityController {

    private final ComplexityAnalyzerService complexityService;
    private final ProjectScannerService     projectScannerService;
    private final ToolkitSettings           settings;
    private final ReviewHistoryService      historyService;

    public ComplexityController(ComplexityAnalyzerService complexityService,
                                ProjectScannerService projectScannerService,
                                ToolkitSettings settings,
                                ReviewHistoryService historyService) {
        this.complexityService     = complexityService;
        this.projectScannerService = projectScannerService;
        this.settings              = settings;
        this.historyService        = historyService;
    }

    @PostMapping("/analyze")
    public String analyze(
            @RequestParam(value = "sourceCode",       defaultValue = "") String  sourceCode,
            @RequestParam(value = "analyzeMode",      defaultValue = "file") String analyzeMode,
            @RequestParam(value = "scanPath",         defaultValue = "") String  scanPath,
            Model model) {

        try {
            String result;
            if ("project".equals(analyzeMode)) {
                String effectivePath = (scanPath != null && !scanPath.trim().isEmpty())
                        ? scanPath.trim()
                        : settings.getProject().getScanPath();
                if (effectivePath.isEmpty()) {
                    model.addAttribute("error", "프로젝트 스캔 경로가 설정되지 않았습니다. Settings에서 먼저 설정하세요.");
                    model.addAttribute("projectConfigured", settings.isProjectConfigured());
                    return "complexity/index";
                }
                List<ScannedFile> files = projectScannerService.scanProject(effectivePath);
                String ctx              = projectScannerService.buildContext(files);
                String summary          = projectScannerService.getScanSummary(files);
                result = complexityService.analyzeProject(ctx);
                model.addAttribute("scanSummary", summary);
                historyService.save("COMPLEXITY", "[프로젝트: " + effectivePath + "]", result);
            } else {
                result = complexityService.analyze(sourceCode);
                historyService.save("COMPLEXITY", sourceCode, result);
            }

            model.addAttribute("result",      result);
            model.addAttribute("sourceCode",  sourceCode);
            model.addAttribute("analyzeMode", analyzeMode);
            model.addAttribute("scanPath",    scanPath);

        } catch (IOException e) {
            model.addAttribute("error", "프로젝트 스캔 실패: " + e.getMessage());
        } catch (Exception e) {
            model.addAttribute("error", "분석 실패: " + e.getMessage());
            model.addAttribute("sourceCode", sourceCode);
        }

        model.addAttribute("projectConfigured", settings.isProjectConfigured());
        model.addAttribute("currentScanPath",   settings.getProject().getScanPath());
        return "complexity/index";
    }

    @PostMapping("/analyze/file")
    public String analyzeFromFile(
            @RequestParam("file") MultipartFile file,
            Model model) throws IOException {
        String sourceCode = new String(file.getBytes(), StandardCharsets.UTF_8);
        return analyze(sourceCode, "file", "", model);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam(value = "sourceCode", defaultValue = "") String sourceCode,
            @RequestParam(value = "analyzeMode", defaultValue = "file") String analyzeMode,
            @RequestParam(value = "scanPath",   defaultValue = "") String scanPath) throws IOException {

        String result;
        if ("project".equals(analyzeMode)) {
            String effectivePath = (scanPath != null && !scanPath.trim().isEmpty())
                    ? scanPath.trim() : settings.getProject().getScanPath();
            List<ScannedFile> files = projectScannerService.scanProject(effectivePath);
            result = complexityService.analyzeProject(projectScannerService.buildContext(files));
        } else {
            result = complexityService.analyze(sourceCode);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"complexity-report.md\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }
}
