package io.github.claudetoolkit.ui.config;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Claude API 연결 상태 헬스체크.
 * /actuator/health 에서 claudeApi 항목으로 표시됩니다.
 */
@Component("claudeApi")
public class ClaudeApiHealthIndicator implements HealthIndicator {

    private final ClaudeClient claudeClient;

    public ClaudeApiHealthIndicator(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    @Override
    public Health health() {
        String apiKey = claudeClient.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return Health.down()
                    .withDetail("reason", "API 키 미설정")
                    .build();
        }

        long start = System.currentTimeMillis();
        try {
            String result = claudeClient.chat("Respond with only: OK");
            long elapsed = System.currentTimeMillis() - start;
            return Health.up()
                    .withDetail("model", claudeClient.getEffectiveModel())
                    .withDetail("responseTime", elapsed + "ms")
                    .build();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("responseTime", elapsed + "ms")
                    .build();
        }
    }
}
