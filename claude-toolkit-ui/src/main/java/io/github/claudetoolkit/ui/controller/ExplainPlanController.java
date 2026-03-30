package io.github.claudetoolkit.ui.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claudetoolkit.sql.explain.ExplainPlanResult;
import io.github.claudetoolkit.sql.explain.ExplainPlanService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Web controller for Oracle Explain Plan visualization (/explain).
 *
 * <p>Executes {@code EXPLAIN PLAN FOR} on a live Oracle DB, parses the
 * PLAN_TABLE into a JSON tree, and renders an interactive tree UI.
 * Claude AI adds performance insights to each result.
 */
@Controller
@RequestMapping("/explain")
public class ExplainPlanController {

    private final ExplainPlanService  explainPlanService;
    private final ToolkitSettings     settings;
    private final ReviewHistoryService historyService;
    private final ObjectMapper        objectMapper;

    public ExplainPlanController(ExplainPlanService explainPlanService,
                                  ToolkitSettings settings,
                                  ReviewHistoryService historyService,
                                  ObjectMapper objectMapper) {
        this.explainPlanService = explainPlanService;
        this.settings           = settings;
        this.historyService     = historyService;
        this.objectMapper       = objectMapper;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        return "explain/index";
    }

    @PostMapping("/analyze")
    public String analyze(
            @RequestParam("sqlContent") String sqlContent,
            Model model) {

        model.addAttribute("dbConfigured", settings.isDbConfigured());
        model.addAttribute("sqlContent", sqlContent);

        if (!settings.isDbConfigured()) {
            model.addAttribute("error", "Oracle DB 연결 정보가 설정되어 있지 않습니다. Settings에서 먼저 DB를 설정하세요.");
            return "explain/index";
        }

        try {
            ExplainPlanResult result = explainPlanService.analyze(
                    settings.getDb().getUrl(),
                    settings.getDb().getUsername(),
                    settings.getDb().getPassword(),
                    sqlContent);

            // Serialize tree to JSON for JS-side rendering
            String planJson = "null";
            if (result.getRoot() != null) {
                planJson = objectMapper.writeValueAsString(result.getRoot());
            }

            model.addAttribute("result",   result);
            model.addAttribute("planJson", planJson);

            // Save to history
            if (result.getAiAnalysis() != null) {
                historyService.save("EXPLAIN_PLAN", sqlContent, result.getAiAnalysis());
            }

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "explain/index";
    }
}
