package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.migration.SpringMigrationService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/migrate")
public class SpringMigrateController {

    private final SpringMigrationService migrateService;
    private final ReviewHistoryService historyService;

    public SpringMigrateController(SpringMigrationService migrateService,
                                    ReviewHistoryService historyService) {
        this.migrateService = migrateService;
        this.historyService = historyService;
    }

    @GetMapping
    public String show(Model model) {
        return "migrate/index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam String sourceInput, Model model) {
        if (sourceInput == null || sourceInput.trim().isEmpty()) {
            model.addAttribute("error", "pom.xml 또는 Java 소스를 입력하세요.");
            return "migrate/index";
        }
        try {
            String result = migrateService.analyze(sourceInput);
            historyService.save("SPRING_MIGRATE", sourceInput, result);
            model.addAttribute("sourceInput", sourceInput);
            model.addAttribute("result", result);
        } catch (Exception e) {
            model.addAttribute("error", "오류: " + e.getMessage());
            model.addAttribute("sourceInput", sourceInput);
        }
        return "migrate/index";
    }

    @PostMapping("/download")
    public org.springframework.http.ResponseEntity<byte[]> download(@RequestParam String sourceInput) {
        try {
            String result = migrateService.analyze(sourceInput);
            byte[] bytes = result.getBytes("UTF-8");
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"spring-migration-checklist.md\"")
                    .header("Content-Type", "text/markdown; charset=UTF-8")
                    .body(bytes);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }
    }
}
