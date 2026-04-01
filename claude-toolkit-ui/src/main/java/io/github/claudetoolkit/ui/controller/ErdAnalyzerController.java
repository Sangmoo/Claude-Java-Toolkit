package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.sql.ddl.DdlGeneratorService;
import io.github.claudetoolkit.sql.erd.ErdAnalyzerService;
import io.github.claudetoolkit.ui.config.PromptTemplateService;
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
 *
 * Also provides ERD → Oracle DDL generation via /erd/ddl (v0.9).
 */
@Controller
@RequestMapping("/erd")
public class ErdAnalyzerController {

    private final ErdAnalyzerService   erdService;
    private final DdlGeneratorService  ddlService;
    private final ToolkitSettings      settings;
    private final ReviewHistoryService historyService;
    private final PromptTemplateService promptTemplateService;

    public ErdAnalyzerController(ErdAnalyzerService erdService,
                                  DdlGeneratorService ddlService,
                                  ToolkitSettings settings,
                                  ReviewHistoryService historyService,
                                  PromptTemplateService promptTemplateService) {
        this.erdService           = erdService;
        this.ddlService           = ddlService;
        this.settings             = settings;
        this.historyService       = historyService;
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        model.addAttribute("activeTab", "erd");
        return "erd/index";
    }

    @PostMapping("/analyze")
    public String analyze(
            @RequestParam(value = "schemaText",  defaultValue = "") String schemaText,
            @RequestParam(value = "useDb",       defaultValue = "false") boolean useDb,
            @RequestParam(value = "schemaOwner", defaultValue = "") String schemaOwner,
            @RequestParam(value = "tableFilter", defaultValue = "") String tableFilter,
            Model model) {

        model.addAttribute("activeTab", "erd");

        try {
            String erdPrompt = promptTemplateService.getPrompt("ERD_ANALYZE", ErdAnalyzerService.DEFAULT_SYSTEM_PROMPT);
            String result;
            if (useDb && settings.isDbConfigured()) {
                result = erdService.generateFromDb(
                        settings.getDb().getUrl(),
                        settings.getDb().getUsername(),
                        settings.getDb().getPassword(),
                        schemaOwner,
                        tableFilter.isEmpty() ? null : tableFilter,
                        erdPrompt);
                model.addAttribute("dbUsed", true);
            } else {
                result = erdService.generateFromText(schemaText, erdPrompt);
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

    /**
     * ERD/Schema → Oracle DDL generation (v0.9).
     * Accepts Mermaid erDiagram, free-form table descriptions, or natural language.
     */
    @PostMapping("/ddl")
    public String generateDdl(
            @RequestParam(value = "ddlSchemaText", defaultValue = "") String schemaText,
            Model model) {

        model.addAttribute("activeTab",     "ddl");
        model.addAttribute("ddlSchemaText", schemaText);

        try {
            String result = ddlService.generateDdl(schemaText);

            String titleSnippet = schemaText.length() > 80
                    ? schemaText.substring(0, 80).replaceAll("\\s+", " ") + "..."
                    : schemaText.replaceAll("\\s+", " ");
            historyService.save("ERD_DDL", titleSnippet, result);

            model.addAttribute("ddlResult", result);

        } catch (Exception e) {
            model.addAttribute("error", "DDL 생성 실패: " + e.getMessage());
        }

        model.addAttribute("dbConfigured", settings.isDbConfigured());
        return "erd/index";
    }
}
