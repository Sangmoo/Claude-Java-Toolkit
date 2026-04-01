package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.sql.advisor.SqlAdvisorService;
import io.github.claudetoolkit.sql.db.OracleMetaService;
import io.github.claudetoolkit.sql.model.AdvisoryResult;
import io.github.claudetoolkit.sql.model.SqlType;
import io.github.claudetoolkit.ui.config.PromptTemplateService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Web controller for SQL Advisor feature (/advisor).
 *
 * Features:
 * - SQL/PL/SQL text or file upload
 * - Optional Oracle DB metadata context (columns, indexes, PKs)
 * - Optional EXPLAIN PLAN execution
 * - Severity filter (HIGH / MEDIUM / LOW) in the result view
 * - Review history auto-save
 */
@Controller
@RequestMapping("/advisor")
public class SqlAdvisorController {

    private final SqlAdvisorService    sqlAdvisorService;
    private final OracleMetaService    oracleMetaService;
    private final ToolkitSettings      settings;
    private final ReviewHistoryService historyService;
    private final PromptTemplateService promptTemplateService;

    public SqlAdvisorController(SqlAdvisorService sqlAdvisorService,
                                OracleMetaService oracleMetaService,
                                ToolkitSettings settings,
                                ReviewHistoryService historyService,
                                PromptTemplateService promptTemplateService) {
        this.sqlAdvisorService    = sqlAdvisorService;
        this.oracleMetaService    = oracleMetaService;
        this.settings             = settings;
        this.historyService       = historyService;
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("sqlTypes",    SqlType.values());
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        return "advisor/index";
    }

    @PostMapping("/review")
    public String review(
            @RequestParam("sqlContent") String sqlContent,
            @RequestParam(value = "sqlType",        required = false)              String sqlTypeStr,
            @RequestParam(value = "useDbContext",    defaultValue = "false") boolean useDbContext,
            @RequestParam(value = "useExplainPlan", defaultValue = "false") boolean useExplainPlan,
            Model model) {

        try {
            SqlType sqlType = (sqlTypeStr != null && !sqlTypeStr.isEmpty())
                    ? SqlType.valueOf(sqlTypeStr)
                    : SqlType.detect(sqlContent);

            // ── DB Metadata ────────────────────────────────────────────────
            String dbContext = "";
            if (useDbContext && settings.isDbConfigured()) {
                try {
                    dbContext = oracleMetaService.buildTableContext(
                            settings.getDb().getUrl(),
                            settings.getDb().getUsername(),
                            settings.getDb().getPassword(),
                            sqlContent);
                    model.addAttribute("dbContextUsed", true);
                    model.addAttribute("tablesFound",
                            oracleMetaService.extractTableNames(sqlContent));
                } catch (Exception e) {
                    model.addAttribute("dbContextWarning",
                            "DB 메타정보 조회 실패 (리뷰는 계속 진행): " + e.getMessage());
                }
            }

            // ── EXPLAIN PLAN ───────────────────────────────────────────────
            String explainPlan = "";
            if (useExplainPlan && settings.isDbConfigured()) {
                try {
                    explainPlan = oracleMetaService.getExplainPlan(
                            settings.getDb().getUrl(),
                            settings.getDb().getUsername(),
                            settings.getDb().getPassword(),
                            sqlContent);
                    if (!explainPlan.isEmpty()) {
                        model.addAttribute("explainPlan", explainPlan);
                    }
                } catch (Exception e) {
                    model.addAttribute("explainPlanWarning",
                            "EXPLAIN PLAN 조회 실패: " + e.getMessage());
                }
            }

            // Append EXPLAIN PLAN to dbContext for Claude prompt
            if (!explainPlan.isEmpty()) {
                dbContext = dbContext + "\n\n## Execution Plan (EXPLAIN PLAN)\n```\n" + explainPlan + "\n```\n";
            }

            String sqlReviewPrompt = promptTemplateService.getPrompt("SQL_REVIEW", SqlAdvisorService.DEFAULT_SYSTEM_PROMPT);
            AdvisoryResult result = sqlAdvisorService.reviewWithContext(sqlContent, sqlType, dbContext, sqlReviewPrompt);

            // ── History ────────────────────────────────────────────────────
            historyService.save("SQL_REVIEW", sqlContent, result.getReviewContent());

            model.addAttribute("result",         result);
            model.addAttribute("sqlContent",     sqlContent);
            model.addAttribute("useDbContext",   useDbContext);
            model.addAttribute("useExplainPlan", useExplainPlan);

        } catch (Exception e) {
            model.addAttribute("error", "Review failed: " + e.getMessage());
            model.addAttribute("sqlContent", sqlContent);
        }

        model.addAttribute("sqlTypes",    SqlType.values());
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        return "advisor/index";
    }

    @PostMapping("/review/file")
    public String reviewFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sqlType",        required = false)              String sqlTypeStr,
            @RequestParam(value = "useDbContext",    defaultValue = "false") boolean useDbContext,
            @RequestParam(value = "useExplainPlan", defaultValue = "false") boolean useExplainPlan,
            Model model) throws IOException {

        String sqlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        return review(sqlContent, sqlTypeStr, useDbContext, useExplainPlan, model);
    }

    @GetMapping("/review/download")
    @ResponseBody
    public String downloadMarkdown(@RequestParam("sqlContent") String sqlContent) {
        AdvisoryResult result = sqlAdvisorService.review(sqlContent);
        return result.toMarkdown();
    }

    /** Index optimization tab — GET */
    @GetMapping("/index")
    public String showIndexForm(Model model) {
        model.addAttribute("sqlTypes",    SqlType.values());
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        model.addAttribute("activeTab",    "index");
        return "advisor/index";
    }

    /** Index optimization tab — POST */
    @PostMapping("/index")
    public String suggestIndexes(
            @RequestParam("sqlContent") String sqlContent,
            Model model) {

        try {
            String indexResult = sqlAdvisorService.suggestIndexes(sqlContent);
            historyService.save("INDEX_OPT", sqlContent, indexResult);

            model.addAttribute("indexResult", indexResult);
            model.addAttribute("sqlContent",  sqlContent);
            model.addAttribute("activeTab",   "index");
        } catch (Exception e) {
            model.addAttribute("error",      "인덱스 최적화 실패: " + e.getMessage());
            model.addAttribute("sqlContent", sqlContent);
            model.addAttribute("activeTab",  "index");
        }

        model.addAttribute("sqlTypes",    SqlType.values());
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        return "advisor/index";
    }

    /** Security audit tab */
    @PostMapping("/security")
    public String security(
            @RequestParam("sqlContent") String sqlContent,
            Model model) {

        try {
            String sqlSecPrompt = promptTemplateService.getPrompt("SQL_SECURITY", SqlAdvisorService.DEFAULT_SYSTEM_PROMPT_SECURITY);
            AdvisoryResult result = sqlAdvisorService.reviewSecurity(sqlContent, sqlSecPrompt);
            historyService.save("SQL_SECURITY", sqlContent, result.getReviewContent());

            model.addAttribute("result",       result);
            model.addAttribute("sqlContent",   sqlContent);
            model.addAttribute("activeTab",    "security");
        } catch (Exception e) {
            model.addAttribute("error",      "보안 감사 실패: " + e.getMessage());
            model.addAttribute("sqlContent", sqlContent);
            model.addAttribute("activeTab",  "security");
        }

        model.addAttribute("sqlTypes",     SqlType.values());
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        return "advisor/index";
    }
}
