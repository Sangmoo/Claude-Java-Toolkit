package io.github.claudetoolkit.ui.cost;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * v4.3.0 — AI 모델 비용 옵티마이저 REST API.
 *
 * <ul>
 *   <li>{@code GET /api/v1/admin/cost-optimizer?days=30} — 분석 유형별 비용 + 추천 모델</li>
 * </ul>
 *
 * SecurityConfig 의 {@code /api/v1/admin/**} 규칙에 따라 ADMIN 권한 필요.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class CostOptimizerController {

    private static final Logger log = LoggerFactory.getLogger(CostOptimizerController.class);

    private final ModelCostService costService;
    private final ClaudeClient claudeClient;

    public CostOptimizerController(ModelCostService costService, ClaudeClient claudeClient) {
        this.costService  = costService;
        this.claudeClient = claudeClient;
    }

    @GetMapping("/cost-optimizer")
    public ResponseEntity<ApiResponse<Map<String, Object>>> costOptimizer(
            @RequestParam(value = "days", defaultValue = "30") int days) {
        try {
            String currentModel = claudeClient.getEffectiveModel();
            Map<String, Object> data = costService.analyze(days, currentModel);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("비용 옵티마이저 분석 실패: days={}", days, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("비용 옵티마이저 분석 실패: " + e.getMessage()));
        }
    }
}
