package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.loganalyzer.LogAnalyzerService;
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

/**
 * Web controller for Log Analyzer feature (/loganalyzer).
 */
@Controller
@RequestMapping("/loganalyzer")
public class LogAnalyzerController {

    private final LogAnalyzerService   logAnalyzerService;
    private final ReviewHistoryService historyService;
    private final ToolkitSettings      settings;

    public LogAnalyzerController(LogAnalyzerService logAnalyzerService,
                                 ReviewHistoryService historyService,
                                 ToolkitSettings settings) {
        this.logAnalyzerService = logAnalyzerService;
        this.historyService     = historyService;
        this.settings           = settings;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "loganalyzer/index";
    }

    @PostMapping("/analyze")
    public String analyze(
            @RequestParam("logContent")                                      String logContent,
            @RequestParam(value = "analyzeMode", defaultValue = "general")  String analyzeMode,
            Model model) {

        try {
            String result;
            if ("security".equals(analyzeMode)) {
                result = logAnalyzerService.analyzeSecurity(logContent, settings.getProjectContext());
            } else {
                result = logAnalyzerService.analyze(logContent, settings.getProjectContext());
            }

            historyService.save("LOG_ANALYSIS", logContent, result);

            model.addAttribute("result",               result);
            model.addAttribute("logContent",           logContent);
            model.addAttribute("analyzeMode",          analyzeMode);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());

        } catch (Exception e) {
            model.addAttribute("error",                "로그 분석 실패: " + e.getMessage());
            model.addAttribute("logContent",           logContent);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        }

        return "loganalyzer/index";
    }

    @PostMapping("/analyze/file")
    public String analyzeFromFile(
            @RequestParam("file")                                            MultipartFile file,
            @RequestParam(value = "analyzeMode", defaultValue = "general")  String        analyzeMode,
            Model model) throws IOException {

        String logContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        return analyze(logContent, analyzeMode, model);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("logContent")                                     String logContent,
            @RequestParam(value = "analyzeMode", defaultValue = "general") String analyzeMode) {

        String result;
        if ("security".equals(analyzeMode)) {
            result = logAnalyzerService.analyzeSecurity(logContent, settings.getProjectContext());
        } else {
            result = logAnalyzerService.analyze(logContent, settings.getProjectContext());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"log-analysis.md\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }
}
