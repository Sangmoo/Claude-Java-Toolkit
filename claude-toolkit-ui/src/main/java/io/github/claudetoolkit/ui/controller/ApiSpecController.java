package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.apispec.ApiSpecGeneratorService;
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
 * Web controller for API Spec Generator (/apispec).
 * Generates OpenAPI 3.0 YAML or adds Swagger 2.0 annotations.
 */
@Controller
@RequestMapping("/apispec")
public class ApiSpecController {

    private final ApiSpecGeneratorService apiSpecService;
    private final ReviewHistoryService    historyService;
    private final ToolkitSettings         settings;

    public ApiSpecController(ApiSpecGeneratorService apiSpecService,
                             ReviewHistoryService historyService,
                             ToolkitSettings settings) {
        this.apiSpecService = apiSpecService;
        this.historyService = historyService;
        this.settings       = settings;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "apispec/index";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam("sourceCode")                                       String sourceCode,
            @RequestParam(value = "outputType", defaultValue = "openapi") String outputType,
            Model model) {

        try {
            String result = apiSpecService.generate(sourceCode, outputType, settings.getProjectContext());
            historyService.save("API_SPEC", sourceCode, result);

            model.addAttribute("result",               result);
            model.addAttribute("sourceCode",           sourceCode);
            model.addAttribute("outputType",           outputType);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        } catch (Exception e) {
            model.addAttribute("error",                "API 명세 생성 실패: " + e.getMessage());
            model.addAttribute("sourceCode",           sourceCode);
            model.addAttribute("outputType",           outputType);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        }
        return "apispec/index";
    }

    @PostMapping("/generate/file")
    public String generateFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "outputType", defaultValue = "openapi") String outputType,
            Model model) throws IOException {

        String sourceCode = new String(file.getBytes(), StandardCharsets.UTF_8);
        return generate(sourceCode, outputType, model);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("sourceCode")                                       String sourceCode,
            @RequestParam(value = "outputType", defaultValue = "openapi") String outputType) {

        String result   = apiSpecService.generate(sourceCode, outputType, settings.getProjectContext());
        String filename = "swagger".equalsIgnoreCase(outputType) ? "SwaggerAnnotated.java" : "openapi.yaml";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }
}
