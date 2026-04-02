package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.codereview.CodeReviewService;
import io.github.claudetoolkit.docgen.generator.DocGeneratorService;
import io.github.claudetoolkit.sql.advisor.SqlAdvisorService;
import io.github.claudetoolkit.sql.erd.ErdAnalyzerService;
import io.github.claudetoolkit.sql.explain.ExplainPlanService;
import io.github.claudetoolkit.ui.config.PromptTemplateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web controller for Prompt Template management (/prompts).
 *
 * Allows users to view and override the system prompts used by each AI feature.
 * Feature codes: SQL_REVIEW, SQL_SECURITY, SQL_EXPLAIN, CODE_REVIEW, CODE_SECURITY,
 *                DOC_GENERATE, ERD_ANALYZE
 */
@Controller
@RequestMapping("/prompts")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    /** Default prompts by feature code — sourced from service constants. */
    private static final Map<String, String> DEFAULT_PROMPTS = new LinkedHashMap<>();
    static {
        DEFAULT_PROMPTS.put("SQL_REVIEW",    SqlAdvisorService.DEFAULT_SYSTEM_PROMPT);
        DEFAULT_PROMPTS.put("SQL_SECURITY",  SqlAdvisorService.DEFAULT_SYSTEM_PROMPT_SECURITY);
        DEFAULT_PROMPTS.put("SQL_EXPLAIN",   ExplainPlanService.DEFAULT_SYSTEM_PROMPT);
        DEFAULT_PROMPTS.put("CODE_REVIEW",   CodeReviewService.DEFAULT_SYSTEM_PROMPT);
        DEFAULT_PROMPTS.put("CODE_SECURITY", CodeReviewService.DEFAULT_SYSTEM_PROMPT_SECURITY);
        DEFAULT_PROMPTS.put("DOC_GENERATE",  DocGeneratorService.DEFAULT_SYSTEM_PROMPT_MARKDOWN);
        DEFAULT_PROMPTS.put("ERD_ANALYZE",   ErdAnalyzerService.DEFAULT_SYSTEM_PROMPT);
    }

    /** Human-readable labels for each feature code. */
    private static final Map<String, String> FEATURE_LABELS = new LinkedHashMap<>();
    static {
        FEATURE_LABELS.put("SQL_REVIEW",    "SQL 성능·품질 리뷰");
        FEATURE_LABELS.put("SQL_SECURITY",  "SQL 보안 취약점 검사");
        FEATURE_LABELS.put("SQL_EXPLAIN",   "Oracle 실행계획 AI 분석");
        FEATURE_LABELS.put("CODE_REVIEW",   "Java/Spring 코드 리뷰");
        FEATURE_LABELS.put("CODE_SECURITY", "Java/Spring 보안 감사");
        FEATURE_LABELS.put("DOC_GENERATE",  "소스코드 기술 문서 생성");
        FEATURE_LABELS.put("ERD_ANALYZE",   "ERD 분석");
    }

    /** Inline CSS style string per feature code (avoids multi-${} ternary in Thymeleaf 3.0). */
    private static final Map<String, String> FEATURE_COLORS = new LinkedHashMap<>();
    static {
        FEATURE_COLORS.put("SQL_REVIEW",    "background:rgba(239,68,68,.12);color:#ef4444;");
        FEATURE_COLORS.put("SQL_SECURITY",  "background:rgba(249,115,22,.12);color:#f97316;");
        FEATURE_COLORS.put("SQL_EXPLAIN",   "background:rgba(59,130,246,.12);color:#3b82f6;");
        FEATURE_COLORS.put("CODE_REVIEW",   "background:rgba(139,92,246,.12);color:#8b5cf6;");
        FEATURE_COLORS.put("CODE_SECURITY", "background:rgba(16,185,129,.12);color:#10b981;");
        FEATURE_COLORS.put("DOC_GENERATE",  "background:rgba(20,184,166,.12);color:#14b8a6;");
        FEATURE_COLORS.put("ERD_ANALYZE",   "background:rgba(236,72,153,.12);color:#ec4899;");
    }

    /** Font Awesome icon class per feature code (avoids multi-${} ternary in Thymeleaf 3.0). */
    private static final Map<String, String> FEATURE_ICONS = new LinkedHashMap<>();
    static {
        FEATURE_ICONS.put("SQL_REVIEW",    "fas fa-magnifying-glass-chart");
        FEATURE_ICONS.put("SQL_SECURITY",  "fas fa-shield-halved");
        FEATURE_ICONS.put("SQL_EXPLAIN",   "fas fa-sitemap");
        FEATURE_ICONS.put("CODE_REVIEW",   "fas fa-code");
        FEATURE_ICONS.put("CODE_SECURITY", "fas fa-shield-virus");
        FEATURE_ICONS.put("DOC_GENERATE",  "fas fa-file-lines");
        FEATURE_ICONS.put("ERD_ANALYZE",   "fas fa-diagram-project");
    }

    public PromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    public String showTemplates(Model model) {
        model.addAttribute("featureCodes",   DEFAULT_PROMPTS.keySet());
        model.addAttribute("featureLabels",  FEATURE_LABELS);
        model.addAttribute("featureColors",  FEATURE_COLORS);
        model.addAttribute("featureIcons",   FEATURE_ICONS);
        model.addAttribute("defaultPrompts", DEFAULT_PROMPTS);
        model.addAttribute("customPrompts",  promptTemplateService.getAll());
        return "prompts/index";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam("featureCode") String featureCode,
            @RequestParam("promptText")  String promptText,
            RedirectAttributes redirectAttributes) {

        if (!DEFAULT_PROMPTS.containsKey(featureCode)) {
            redirectAttributes.addFlashAttribute("error", "알 수 없는 기능 코드: " + featureCode);
            return "redirect:/prompts";
        }

        promptTemplateService.setPrompt(featureCode, promptText);
        String label = FEATURE_LABELS.getOrDefault(featureCode, featureCode);
        redirectAttributes.addFlashAttribute("success", "[" + label + "] 프롬프트가 저장되었습니다.");
        return "redirect:/prompts";
    }

    @PostMapping("/reset")
    public String reset(
            @RequestParam("featureCode") String featureCode,
            RedirectAttributes redirectAttributes) {

        promptTemplateService.resetPrompt(featureCode);
        String label = FEATURE_LABELS.getOrDefault(featureCode, featureCode);
        redirectAttributes.addFlashAttribute("success", "[" + label + "] 프롬프트가 기본값으로 초기화되었습니다.");
        return "redirect:/prompts";
    }
}
