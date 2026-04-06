package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.harness.HarnessReviewService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller for the Code Review Harness feature (/harness).
 *
 * <p>Implements a 3-step AI pipeline (Analyst → Builder → Reviewer) that:
 * <ol>
 *   <li>Accepts Java or SQL source code input</li>
 *   <li>Returns improved code with a side-by-side diff view</li>
 *   <li>Provides structured explanation of all changes</li>
 * </ol>
 *
 * <p>Supports both synchronous (full result at once) and SSE streaming modes.
 */
@Controller
@RequestMapping("/harness")
public class HarnessController {

    private final HarnessReviewService harnessService;
    private final ReviewHistoryService historyService;
    private final SseStreamController  sseStreamController;

    public HarnessController(HarnessReviewService harnessService,
                             ReviewHistoryService historyService,
                             SseStreamController sseStreamController) {
        this.harnessService      = harnessService;
        this.historyService      = historyService;
        this.sseStreamController = sseStreamController;
    }

    /** Show the harness input/result page. */
    @GetMapping
    public String index(Model model) {
        return "harness/index";
    }

    /**
     * Run the full harness pipeline synchronously.
     * Returns JSON with originalCode, improvedCode, fullResponse.
     */
    @PostMapping("/analyze")
    @ResponseBody
    public Map<String, Object> analyze(
            @RequestParam("code")                                    String code,
            @RequestParam(value = "language", defaultValue = "java") String language) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            String response     = harnessService.analyze(code, language);
            String improvedCode = harnessService.extractImprovedCode(response, language);
            historyService.save("HARNESS_REVIEW", code, response);

            result.put("success",      true);
            result.put("originalCode", code);
            result.put("improvedCode", improvedCode);
            result.put("fullResponse", response);
            result.put("language",     language);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getMessage() != null
                    ? e.getMessage() : "분석 중 오류가 발생했습니다.");
        }
        return result;
    }

    /**
     * Register an SSE stream for streaming harness analysis.
     * After calling this, open GET /stream/{streamId} for real-time output.
     */
    @PostMapping("/stream-init")
    @ResponseBody
    public Map<String, Object> streamInit(
            @RequestParam("code")                                    String code,
            @RequestParam(value = "language", defaultValue = "java") String language) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            String streamId = sseStreamController.registerStream(
                    "harness_review", code, "", language);
            result.put("success",  true);
            result.put("streamId", streamId);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getMessage() != null
                    ? e.getMessage() : "스트림 등록 오류");
        }
        return result;
    }
}
