package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.*;

@Controller
@RequestMapping("/usage")
public class UsageController {

    // Claude Sonnet 3.5 pricing (approximate, per 1M tokens)
    private static final double INPUT_COST_PER_1M  = 3.0;   // $3 per 1M input tokens
    private static final double OUTPUT_COST_PER_1M = 15.0;  // $15 per 1M output tokens

    private final ReviewHistoryService historyService;
    private final ClaudeClient         claudeClient;

    public UsageController(ReviewHistoryService historyService, ClaudeClient claudeClient) {
        this.historyService = historyService;
        this.claudeClient   = claudeClient;
    }

    @GetMapping
    public String show(Model model) {
        List<ReviewHistory> all = historyService.findAll();

        long totalRequests   = all.size();
        long totalInputTok   = 0;
        long totalOutputTok  = 0;
        long recordedCount   = 0;

        // Per-date aggregation (last 30 days)
        Map<String, long[]> dailyMap = new LinkedHashMap<>(); // date -> [inputTok, outputTok, count]

        for (ReviewHistory h : all) {
            if (h.getInputTokens() != null || h.getOutputTokens() != null) {
                long in  = h.getInputTokens()  != null ? h.getInputTokens()  : 0;
                long out = h.getOutputTokens() != null ? h.getOutputTokens() : 0;
                totalInputTok  += in;
                totalOutputTok += out;
                recordedCount++;

                String date = h.getCreatedAt().toLocalDate().toString();
                long[] d = dailyMap.getOrDefault(date, new long[]{0, 0, 0});
                d[0] += in; d[1] += out; d[2]++;
                dailyMap.put(date, d);
            }
        }

        // Cost estimation (USD)
        double inputCost  = totalInputTok  / 1_000_000.0 * INPUT_COST_PER_1M;
        double outputCost = totalOutputTok / 1_000_000.0 * OUTPUT_COST_PER_1M;
        double totalCost  = inputCost + outputCost;

        // Top types by token usage
        Map<String, Long> typeTokenMap = new LinkedHashMap<>();
        for (ReviewHistory h : all) {
            if (h.getTotalTokens() > 0) {
                Long prev = typeTokenMap.get(h.getType());
                typeTokenMap.put(h.getType(), (prev != null ? prev : 0L) + h.getTotalTokens());
            }
        }

        model.addAttribute("totalRequests",   totalRequests);
        model.addAttribute("totalInputTok",   totalInputTok);
        model.addAttribute("totalOutputTok",  totalOutputTok);
        model.addAttribute("totalTokens",     totalInputTok + totalOutputTok);
        model.addAttribute("recordedCount",   recordedCount);
        model.addAttribute("inputCost",       String.format("$%.4f", inputCost));
        model.addAttribute("outputCost",      String.format("$%.4f", outputCost));
        model.addAttribute("totalCost",       String.format("$%.4f", totalCost));
        model.addAttribute("dailyMap",        dailyMap);
        model.addAttribute("typeTokenMap",    typeTokenMap);
        model.addAttribute("currentModel",    claudeClient.getEffectiveModel());
        model.addAttribute("allHistory",      all);
        return "usage/index";
    }
}
