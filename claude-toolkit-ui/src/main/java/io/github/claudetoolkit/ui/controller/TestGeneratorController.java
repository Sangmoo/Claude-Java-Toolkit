package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.testgen.TestGeneratorService;
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
 * Web controller for Test Code Generator (/testgen).
 * Generates JUnit 5 tests for Controller / Service / Mapper / General Java.
 */
@Controller
@RequestMapping("/testgen")
public class TestGeneratorController {

    private final TestGeneratorService testGenService;
    private final ReviewHistoryService historyService;
    private final ToolkitSettings      settings;

    public TestGeneratorController(TestGeneratorService testGenService,
                                   ReviewHistoryService historyService,
                                   ToolkitSettings settings) {
        this.testGenService = testGenService;
        this.historyService = historyService;
        this.settings       = settings;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "testgen/index";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam("sourceCode")                                    String sourceCode,
            @RequestParam(value = "sourceType", defaultValue = "Service") String sourceType,
            Model model) {

        try {
            String result = testGenService.generateTest(sourceCode, sourceType, settings.getProjectContext());
            historyService.save("TEST_GEN", sourceCode, result);

            model.addAttribute("result",               result);
            model.addAttribute("sourceCode",           sourceCode);
            model.addAttribute("sourceType",           sourceType);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        } catch (Exception e) {
            model.addAttribute("error",                "테스트 생성 실패: " + e.getMessage());
            model.addAttribute("sourceCode",           sourceCode);
            model.addAttribute("sourceType",           sourceType);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        }
        return "testgen/index";
    }

    @PostMapping("/generate/file")
    public String generateFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceType", defaultValue = "Service") String sourceType,
            Model model) throws IOException {

        String sourceCode = new String(file.getBytes(), StandardCharsets.UTF_8);
        return generate(sourceCode, sourceType, model);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("sourceCode")                                    String sourceCode,
            @RequestParam(value = "sourceType", defaultValue = "Service") String sourceType) {

        String result = testGenService.generateTest(sourceCode, sourceType, settings.getProjectContext());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"GeneratedTest.java\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }
}
