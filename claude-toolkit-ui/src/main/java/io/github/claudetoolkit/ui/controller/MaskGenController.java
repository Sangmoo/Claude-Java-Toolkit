package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.masking.DataMaskingService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/maskgen")
public class MaskGenController {

    private final DataMaskingService maskingService;
    private final ReviewHistoryService historyService;

    public MaskGenController(DataMaskingService maskingService,
                              ReviewHistoryService historyService) {
        this.maskingService = maskingService;
        this.historyService = historyService;
    }

    @GetMapping
    public String show(Model model) {
        return "maskgen/index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam String ddl, Model model) {
        if (ddl == null || ddl.trim().isEmpty()) {
            model.addAttribute("error", "CREATE TABLE DDL을 입력하세요.");
            return "maskgen/index";
        }
        try {
            String result = maskingService.generateScript(ddl);
            historyService.save("DATA_MASKING", ddl, result);
            model.addAttribute("ddl", ddl);
            model.addAttribute("result", result);
        } catch (Exception e) {
            model.addAttribute("error", "오류: " + e.getMessage());
            model.addAttribute("ddl", ddl);
        }
        return "maskgen/index";
    }

    @PostMapping("/download")
    public org.springframework.http.ResponseEntity<byte[]> download(@RequestParam String ddl) {
        try {
            String result = maskingService.generateScript(ddl);
            byte[] bytes = result.getBytes("UTF-8");
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"masking-script.sql\"")
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body(bytes);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }
    }
}
