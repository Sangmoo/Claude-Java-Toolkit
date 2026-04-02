package io.github.claudetoolkit.ui.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claudetoolkit.sql.explain.ExplainPlanResult;
import io.github.claudetoolkit.sql.explain.ExplainPlanService;
import io.github.claudetoolkit.ui.config.PromptTemplateService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Web controller for Oracle Explain Plan visualization (/explain).
 *
 * <p>Executes {@code EXPLAIN PLAN FOR} on a live Oracle DB, parses the
 * PLAN_TABLE into a JSON tree, and renders an interactive tree UI.
 * Claude AI adds performance insights to each result.
 *
 * <p>v0.9: adds Before/After comparison at /explain/compare.
 */
@Controller
@RequestMapping("/explain")
public class ExplainPlanController {

    private final ExplainPlanService   explainPlanService;
    private final ToolkitSettings      settings;
    private final ReviewHistoryService historyService;
    private final ObjectMapper         objectMapper;
    private final PromptTemplateService promptTemplateService;

    public ExplainPlanController(ExplainPlanService explainPlanService,
                                  ToolkitSettings settings,
                                  ReviewHistoryService historyService,
                                  ObjectMapper objectMapper,
                                  PromptTemplateService promptTemplateService) {
        this.explainPlanService   = explainPlanService;
        this.settings             = settings;
        this.historyService       = historyService;
        this.objectMapper         = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    // ── Single analysis ─────────────────────────────────────────────────────

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
            String explainPrompt = promptTemplateService.getPrompt("SQL_EXPLAIN", ExplainPlanService.DEFAULT_SYSTEM_PROMPT);
            ExplainPlanResult result = explainPlanService.analyze(
                    settings.getDb().getUrl(),
                    settings.getDb().getUsername(),
                    settings.getDb().getPassword(),
                    sqlContent,
                    explainPrompt);

            // Serialize tree to JSON for JS-side rendering
            String planJson = "null";
            if (result.getRoot() != null) {
                planJson = objectMapper.writeValueAsString(result.getRoot());
            }

            model.addAttribute("result",   result);
            model.addAttribute("planJson", planJson);

            // Save to history (with root cost for dashboard chart)
            if (result.getAiAnalysis() != null) {
                Long cost = (result.getRoot() != null && result.getRoot().getCost() != null)
                        ? result.getRoot().getCost() : null;
                historyService.save("EXPLAIN_PLAN", sqlContent, result.getAiAnalysis(), cost);
            }

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "explain/index";
    }

    // ── Performance History Dashboard (v1.2) ────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<ReviewHistory> entries = historyService.findExplainPlanHistory();
        model.addAttribute("entries", entries);
        model.addAttribute("totalCount", entries.size());
        long withCost = entries.stream().filter(e -> e.getCostValue() != null).count();
        model.addAttribute("withCostCount", withCost);
        return "explain/dashboard";
    }

    // ── Before / After comparison (v0.9) ────────────────────────────────────

    @GetMapping("/compare")
    public String showCompareForm(Model model) {
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        return "explain/compare";
    }

    @PostMapping("/compare")
    public String compare(
            @RequestParam("sqlBefore") String sqlBefore,
            @RequestParam("sqlAfter")  String sqlAfter,
            Model model) {

        model.addAttribute("dbConfigured", settings.isDbConfigured());
        model.addAttribute("sqlBefore", sqlBefore);
        model.addAttribute("sqlAfter",  sqlAfter);

        if (!settings.isDbConfigured()) {
            model.addAttribute("error", "Oracle DB 연결 정보가 설정되어 있지 않습니다. Settings에서 먼저 DB를 설정하세요.");
            return "explain/compare";
        }

        try {
            ExplainPlanResult before = explainPlanService.analyze(
                    settings.getDb().getUrl(),
                    settings.getDb().getUsername(),
                    settings.getDb().getPassword(),
                    sqlBefore);

            ExplainPlanResult after = explainPlanService.analyze(
                    settings.getDb().getUrl(),
                    settings.getDb().getUsername(),
                    settings.getDb().getPassword(),
                    sqlAfter);

            String beforeJson = "null";
            if (before.getRoot() != null) {
                beforeJson = objectMapper.writeValueAsString(before.getRoot());
            }
            String afterJson = "null";
            if (after.getRoot() != null) {
                afterJson = objectMapper.writeValueAsString(after.getRoot());
            }

            model.addAttribute("beforeResult",   before);
            model.addAttribute("afterResult",    after);
            model.addAttribute("beforePlanJson", beforeJson);
            model.addAttribute("afterPlanJson",  afterJson);

            // Compute cost delta percentage
            long costBefore = before.getRoot() != null && before.getRoot().getCost() != null
                    ? before.getRoot().getCost() : 0L;
            long costAfter  = after.getRoot()  != null && after.getRoot().getCost()  != null
                    ? after.getRoot().getCost()  : 0L;
            model.addAttribute("costBefore", costBefore);
            model.addAttribute("costAfter",  costAfter);
            if (costBefore > 0) {
                long deltaPct = Math.round((costAfter - costBefore) * 100.0 / costBefore);
                model.addAttribute("costDeltaPct", deltaPct);
            }

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "explain/compare";
    }
}
