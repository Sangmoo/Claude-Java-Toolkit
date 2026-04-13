package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.depcheck.DependencyAnalyzerService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/depcheck")
public class DepCheckController {

    private final DependencyAnalyzerService depService;
    private final ReviewHistoryService historyService;

    public DepCheckController(DependencyAnalyzerService depService,
                               ReviewHistoryService historyService) {
        this.depService = depService;
        this.historyService = historyService;
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam String pomXml, Model model) {
        if (pomXml == null || pomXml.trim().isEmpty()) {
            model.addAttribute("error", "pom.xml 내용을 입력하세요.");
            return "depcheck/index";
        }
        try {
            String result = depService.analyze(pomXml);
            historyService.save("DEP_CHECK", pomXml, result);
            model.addAttribute("pomXml", pomXml);
            model.addAttribute("result", result);
        } catch (Exception e) {
            model.addAttribute("error", "오류: " + e.getMessage());
            model.addAttribute("pomXml", pomXml);
        }
        return "depcheck/index";
    }

    @PostMapping("/download")
    public org.springframework.http.ResponseEntity<byte[]> download(@RequestParam String pomXml) {
        try {
            String result = depService.analyze(pomXml);
            byte[] bytes = result.getBytes("UTF-8");
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"dependency-report.md\"")
                    .header("Content-Type", "text/markdown; charset=UTF-8")
                    .body(bytes);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }
    }
}
