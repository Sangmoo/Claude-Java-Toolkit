package io.github.claudetoolkit.ui.api;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API health check endpoint.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>GET /api/v1/health — 서버 상태 및 설정 정보 확인</li>
 * </ul>
 *
 * <h3>응답 예시</h3>
 * <pre>
 * {
 *   "success": true,
 *   "data": {
 *     "status":         "UP",
 *     "version":        "1.0.0",
 *     "dbConfigured":   true,
 *     "apiKeySet":      true,
 *     "claudeModel":    "claude-sonnet-4-20250514"
 *   },
 *   "error": null,
 *   "timestamp": "2026-04-01 12:00:00"
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/v1")
public class HealthRestController {

    private final ToolkitSettings settings;
    private final ClaudeClient    claudeClient;

    public HealthRestController(ToolkitSettings settings, ClaudeClient claudeClient) {
        this.settings     = settings;
        this.claudeClient = claudeClient;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        String apiKey = claudeClient.getApiKey();
        boolean apiKeySet = apiKey != null && !apiKey.trim().isEmpty();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status",       "UP");
        data.put("version",      "1.0.0");
        data.put("dbConfigured", settings.isDbConfigured());
        data.put("apiKeySet",    apiKeySet);
        data.put("claudeModel",  claudeClient.getEffectiveModel());

        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
