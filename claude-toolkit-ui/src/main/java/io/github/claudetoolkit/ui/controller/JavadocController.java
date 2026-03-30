package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.javadoc.JavadocGeneratorService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/javadoc")
public class JavadocController {

    private final JavadocGeneratorService javadocService;
    private final ReviewHistoryService historyService;
    private final ToolkitSettings settings;

    public JavadocController(JavadocGeneratorService javadocService,
                              ReviewHistoryService historyService,
                              ToolkitSettings settings) {
        this.javadocService = javadocService;
        this.historyService = historyService;
        this.settings = settings;
    }

    @GetMapping
    public String show(Model model) {
        model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "javadoc/index";
    }

    @PostMapping("/generate")
    public String generate(@RequestParam String javaSource, Model model) {
        if (javaSource == null || javaSource.trim().isEmpty()) {
            model.addAttribute("error", "Java 소스 코드를 입력하세요.");
            return "javadoc/index";
        }
        try {
            String result = javadocService.generate(javaSource, settings.getProjectContext());
            historyService.save("JAVADOC", javaSource, result);
            model.addAttribute("javaSource", javaSource);
            model.addAttribute("result", result);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        } catch (Exception e) {
            model.addAttribute("error", "오류: " + e.getMessage());
            model.addAttribute("javaSource", javaSource);
        }
        return "javadoc/index";
    }

    @PostMapping("/download")
    public org.springframework.http.ResponseEntity<byte[]> download(
            @RequestParam String javaSource) {
        try {
            String result = javadocService.generate(javaSource, settings.getProjectContext());
            byte[] bytes = result.getBytes("UTF-8");
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"javadoc-result.java\"")
                    .header("Content-Type", "text/x-java; charset=UTF-8")
                    .body(bytes);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }
    }
}
