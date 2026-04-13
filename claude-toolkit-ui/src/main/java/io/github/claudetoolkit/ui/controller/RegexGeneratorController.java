package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.regex.RegexGeneratorService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Web controller for Regex Generator feature (/regex).
 */
@Controller
@RequestMapping("/regex")
public class RegexGeneratorController {

    private final RegexGeneratorService regexGeneratorService;
    private final ReviewHistoryService  historyService;
    private final ToolkitSettings       settings;

    public RegexGeneratorController(RegexGeneratorService regexGeneratorService,
                                    ReviewHistoryService historyService,
                                    ToolkitSettings settings) {
        this.regexGeneratorService = regexGeneratorService;
        this.historyService        = historyService;
        this.settings              = settings;
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam("description")                              String description,
            @RequestParam(value = "language", defaultValue = "java") String language,
            Model model) {

        try {
            String result = regexGeneratorService.generate(description, language, settings.getProjectContext());

            historyService.save("REGEX_GEN", description, result);

            model.addAttribute("result",               result);
            model.addAttribute("description",          description);
            model.addAttribute("language",             language);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());

        } catch (Exception e) {
            model.addAttribute("error",                "정규식 생성 실패: " + e.getMessage());
            model.addAttribute("description",          description);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        }

        return "regex/index";
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("description")                              String description,
            @RequestParam(value = "language", defaultValue = "java") String language) {

        String result = regexGeneratorService.generate(description, language, settings.getProjectContext());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"regex-result.md\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }
}
