package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.sql.erd.ErdAnalyzerService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Web controller for ERD Analyzer feature (/erd).
 *
 * Supports two modes:
 * - DB mode  : reads schema from Oracle (requires Settings DB configuration)
 * - Text mode: user pastes schema description manually
 */
@Controller
@RequestMapping("/erd")
public class ErdAnalyzerController {

    private final ErdAnalyzerService   erdService;
    private final ToolkitSettings      settings;
    private final ReviewHistoryService historyService;

    public ErdAnalyzerController(ErdAnalyzerService erdService,
                                  ToolkitSettings settings,
                                  ReviewHistoryService historyService) {
        this.erdService     = erdService;
        this.settings       = settings;
        this.historyService = historyService;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        return "erd/index";
    }

    @PostMapping("/analyze")
    public String analyze(
            @RequestParam(value = "schemaText",  defaultValue = "") String schemaText,
            @RequestParam(value = "useDb",       defaultValue = "false") boolean useDb,
            @RequestParam(value = "schemaOwner", defaultValue = "") String schemaOwner,
            @RequestParam(value = "tableFilter", defaultValue = "") String tableFilter,
            Model model) {

        try {
            String result;
            if (useDb && settings.isDbConfigured()) {
                result = erdService.generateFromDb(
                        settings.getDb().getUrl(),
                        settings.getDb().getUsername(),
                        settings.getDb().getPassword(),
                        schemaOwner,
                        tableFilter.isEmpty() ? null : tableFilter);
                model.addAttribute("dbUsed", true);
            } else {
                result = erdService.generateFromText(schemaText);
            }

            String historyInput = useDb
                    ? "DB 자동 스캔 (owner: " + (schemaOwner.isEmpty() ? settings.getDb().getUsername() : schemaOwner) + ")"
                            + (tableFilter.isEmpty() ? "" : ", 필터: " + tableFilter)
                    : schemaText;
            historyService.save("ERD", historyInput, result);

            model.addAttribute("result",      result);
            model.addAttribute("schemaText",  schemaText);
            model.addAttribute("schemaOwner", schemaOwner);
            model.addAttribute("tableFilter", tableFilter);
            model.addAttribute("useDb",       useDb);

        } catch (Exception e) {
            model.addAttribute("error",      "ERD 생성 실패: " + e.getMessage());
            model.addAttribute("schemaText", schemaText);
        }

        model.addAttribute("dbConfigured", settings.isDbConfigured());
        return "erd/index";
    }
}
