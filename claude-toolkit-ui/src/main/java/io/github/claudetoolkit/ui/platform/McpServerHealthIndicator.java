package io.github.claudetoolkit.ui.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * MCP Server 프로세스 생존 + HTTP 포트 리스닝 여부를 /actuator/health 에 노출.
 *
 * <p>auto-start=false 이면 MCP 가 없는 환경이므로 UP(disabled) 반환 — readiness 에 영향 없음.
 * auto-start=true 인데:
 * <ul>
 *   <li>프로세스가 살아 있고 포트가 열려 있으면 → UP(running)</li>
 *   <li>프로세스가 살아 있지만 아직 포트 미오픈 (startup 중) → UP(starting)</li>
 *   <li>프로세스가 죽어 있으면 → DOWN</li>
 * </ul>
 */
@Component
public class McpServerHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(McpServerHealthIndicator.class);
    private static final int PORT_CHECK_TIMEOUT_MS = 500;

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
        if (!launcher.isRunning()) {
            return Health.down()
                    .withDetail("status", "not running")
                    .withDetail("action", "MCP Server 가 종료됨 — 재시작 또는 toolkit.mcp.auto-start=false 설정")
                    .build();
        }

        // 프로세스는 살아 있음 — 포트가 실제로 리스닝 중인지 추가 확인
        int port = launcher.getPort();
        boolean portOpen = isPortListening(port);
        Health.Builder b = portOpen
                ? Health.up().withDetail("status", "running")
                : Health.up().withDetail("status", "starting");  // 프로세스 기동 중 (아직 포트 미오픈)

        Long pid = launcher.getPid();
        if (pid != null) b.withDetail("pid", pid);
        b.withDetail("port",        port);
        b.withDetail("portListening", portOpen);
        return b.build();
    }

    /**
     * 지정된 로컬 포트에 TCP 연결이 가능한지 500ms 내에 확인.
     * MCP 는 Streamable HTTP transport 이므로 포트 open = 요청 수신 가능.
     */
    private boolean isPortListening(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", port), PORT_CHECK_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            log.debug("[McpHealthIndicator] 포트 {} 미오픈 — {}", port, e.getMessage());
            return false;
        }
    }
}
