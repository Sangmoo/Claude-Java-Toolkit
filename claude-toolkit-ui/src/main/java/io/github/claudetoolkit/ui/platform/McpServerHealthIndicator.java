package io.github.claudetoolkit.ui.platform;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MCP Server 프로세스 생존 여부를 /actuator/health 에 노출.
 *
 * <p>auto-start=false 이면 MCP 가 없는 환경이므로 UP(disabled) 반환 — readiness 에 영향 없음.
 * auto-start=true 인데 프로세스가 죽어있으면 DOWN — 운영팀 알람 트리거 가능.
 */
@Component
public class McpServerHealthIndicator implements HealthIndicator {

    private final McpServerLauncher launcher;

    public McpServerHealthIndicator(McpServerLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public Health health() {
        if (!launcher.isAutoStart()) {
            return Health.up()
                    .withDetail("status", "disabled")
                    .withDetail("reason", "toolkit.mcp.auto-start=false")
                    .build();
        }
        if (launcher.isRunning()) {
            Health.Builder b = Health.up().withDetail("status", "running");
            Long pid = launcher.getPid();
            if (pid != null) b.withDetail("pid", pid);
            return b.withDetail("port", launcher.getPort()).build();
        }
        return Health.down()
                .withDetail("status", "not running")
                .withDetail("action", "MCP Server 가 종료됨 — 재시작 또는 toolkit.mcp.auto-start=false 설정")
                .build();
    }
}
