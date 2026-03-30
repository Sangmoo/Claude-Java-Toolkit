package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.refactoring.RefactoringService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/refactor")
public class RefactoringController {

    private final RefactoringService refactoringService;
    private final ReviewHistoryService historyService;
    private final ToolkitSettings settings;

    public RefactoringController(RefactoringService refactoringService,
                                  ReviewHistoryService historyService,
                                  ToolkitSettings settings) {
        this.refactoringService = refactoringService;
        this.historyService = historyService;
        this.settings = settings;
    }

    @GetMapping
    public String show(Model model) {
        model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "refactor/index";
    }

    @PostMapping("/generate")
    public String generate(@RequestParam String javaCode, Model model) {
        if (javaCode == null || javaCode.trim().isEmpty()) {
            model.addAttribute("error", "Java 소스 코드를 입력하세요.");
            return "refactor/index";
        }
        try {
            String result = refactoringService.refactor(javaCode, settings.getProjectContext());
            historyService.save("REFACTORING", javaCode, result);
            model.addAttribute("javaCode", javaCode);
            model.addAttribute("result", result);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        } catch (Exception e) {
            model.addAttribute("error", "오류: " + e.getMessage());
            model.addAttribute("javaCode", javaCode);
        }
        return "refactor/index";
    }

    @PostMapping("/download")
    public org.springframework.http.ResponseEntity<byte[]> download(
            @RequestParam String javaCode) {
        try {
            String result = refactoringService.refactor(javaCode, settings.getProjectContext());
            byte[] bytes = result.getBytes("UTF-8");
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"refactored-code.java\"")
                    .header("Content-Type", "text/x-java; charset=UTF-8")
                    .body(bytes);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }
    }
}
