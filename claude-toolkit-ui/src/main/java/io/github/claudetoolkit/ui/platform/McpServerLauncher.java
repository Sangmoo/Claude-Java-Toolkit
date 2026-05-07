package io.github.claudetoolkit.ui.platform;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * v4.7.x — #M3 사내망 공유 MCP Server 자동 기동.
 *
 * <p>Spring Boot 가 startup 완료(ApplicationReadyEvent)되면 claude-toolkit-mcp 의
 * Streamable HTTP transport(node dist/index.js, MCP_TRANSPORT=http)를 child process 로 spawn.
 * Spring context 가 닫힐 때(@PreDestroy) 동시에 종료 — WAS 수명주기에 묶임.
 *
 * <h3>설정 (application.yml)</h3>
 * <pre>
 * toolkit:
 *   mcp:
 *     auto-start: true              # default true. 비활성화하려면 false
 *     project-root: ../claude-toolkit-mcp   # mvn 실행 위치 기준 상대경로
 *     host: 0.0.0.0                 # 사내망 노출. 본인만이면 127.0.0.1
 *     port: 8028
 *     node: node                    # PATH 의 node 또는 절대경로
 *     log-prefix: "[MCP]"
 * </pre>
 *
 * <h3>사용자별 Claude Desktop 설정</h3>
 * <pre>
 * "claude-toolkit": {
 *   "command": "npx",
 *   "args": ["-y", "mcp-remote", "http://&lt;호스트IP&gt;:8028/mcp",
 *            "--header", "X-Api-Key:ctk_live_..."]
 * }
 * </pre>
 *
 * <p>주의: 환경변수에 CTK_API_KEY 는 <b>설정하지 않음</b>. 클라이언트 헤더의
 * X-Api-Key 가 per-request 로 backend 에 패스스루되어야 audit 가 사람별로 분리됨.
 */
@Component
public class McpServerLauncher {

    private static final Logger log = LoggerFactory.getLogger(McpServerLauncher.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    @Value("${toolkit.mcp.auto-start:true}")
    private boolean autoStart;

    @Value("${toolkit.mcp.project-root:../claude-toolkit-mcp}")
    private String projectRoot;

    @Value("${toolkit.mcp.host:0.0.0.0}")
    private String host;

    @Value("${toolkit.mcp.port:8028}")
    private int port;

    @Value("${toolkit.mcp.node:node}")
    private String nodeExecutable;

    @Value("${toolkit.mcp.log-prefix:[MCP]}")
    private String logPrefix;

    /** backend base URL — 같은 WAS 안이므로 자기 자신의 server.port 로 호출 */
    @Value("${server.port:8027}")
    private int backendPort;

    private volatile Process process;
    private volatile Thread stdoutPump;
    private volatile Thread stderrPump;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!autoStart) {
            log.info("{} auto-start 비활성 (toolkit.mcp.auto-start=false) — 수동 기동 필요", logPrefix);
            return;
        }

        Path resolvedRoot = resolveProjectRoot();
        Path entry = resolvedRoot.resolve("dist").resolve("index.js");
        if (!Files.exists(entry)) {
            log.warn("{} dist/index.js 미존재 — '{}' 에서 'npm install && npm run build' 먼저 실행 필요. MCP 자동 기동 스킵.",
                    logPrefix, resolvedRoot);
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(nodeExecutable, entry.toString());
        pb.directory(resolvedRoot.toFile());
        pb.redirectErrorStream(false);

        Map<String, String> env = pb.environment();
        env.put("MCP_TRANSPORT", "http");
        env.put("MCP_HTTP_HOST", host);
        env.put("MCP_HTTP_PORT", String.valueOf(port));
        env.put("CTK_URL", "http://localhost:" + backendPort);
        // CTK_API_KEY 는 *의도적으로* 안 넣음 — per-request 헤더 패스스루.
        // 혹시 외부에서 주입돼 있으면 제거 (공유 키로 audit 가 망가지는 것 방지).
        env.remove("CTK_API_KEY");

        try {
            this.process = pb.start();
            this.stdoutPump = pumpToLog(process.getInputStream(), false);
            this.stderrPump = pumpToLog(process.getErrorStream(), true);
            log.info("{} 자동 기동 성공 — pid={}, listening on {}:{}, backend=http://localhost:{}",
                    logPrefix, process.pid(), host, port, backendPort);
        } catch (IOException e) {
            log.error("{} 기동 실패 — '{}'. node 실행파일 경로 확인 (toolkit.mcp.node). cause: {}",
                    logPrefix, nodeExecutable, e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        Process p = this.process;
        if (p == null || !p.isAlive()) {
            return;
        }
        log.info("{} 종료 신호 (pid={})", logPrefix, p.pid());
        p.destroy();
        try {
            if (!p.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("{} graceful 종료 타임아웃 — 강제 종료", logPrefix);
                p.destroyForcibly();
            }
            log.info("{} 종료 완료 (exit={})", logPrefix, p.exitValue());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
        interruptQuietly(stdoutPump);
        interruptQuietly(stderrPump);
    }

    /**
     * project-root 가 상대경로면 다음 순서로 시도:
     *   1) user.dir 기준 (mvn 실행 위치 — 일반적)
     *   2) user.dir/.. (multi-module 빌드 등)
     *   3) 그대로 절대경로 변환 (마지막 fallback)
     */
    private Path resolveProjectRoot() {
        Path raw = Paths.get(projectRoot);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        File userDir = new File(System.getProperty("user.dir"));
        Path[] candidates = {
                userDir.toPath().resolve(raw).normalize(),
                userDir.toPath().resolve("..").resolve(raw).normalize(),
        };
        for (Path c : candidates) {
            if (Files.isDirectory(c)) {
                return c;
            }
        }
        return raw.toAbsolutePath().normalize();
    }

    /**
     * MCP child process 의 stdout/stderr 를 한 줄씩 SLF4J 로 옮김.
     * stderr 는 WARN, stdout 은 INFO.
     */
    private Thread pumpToLog(java.io.InputStream in, boolean isStderr) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (isStderr) {
                        // 서버의 정상 startup 메시지도 stderr 로 나오므로 INFO 로 격하
                        if (line.contains("ready") || line.contains("listening")) {
                            log.info("{} {}", logPrefix, line);
                        } else {
                            log.warn("{} {}", logPrefix, line);
                        }
                    } else {
                        log.info("{} {}", logPrefix, line);
                    }
                }
            } catch (IOException ignored) {
                // child process 종료 시 자연 발생
            }
        }, "mcp-log-pump-" + (isStderr ? "err" : "out"));
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void interruptQuietly(Thread t) {
        if (t != null && t.isAlive()) {
            t.interrupt();
        }
    }

    /** 진단용 — health check 컨트롤러 등에서 노출 가능 */
    public boolean isRunning() {
        Process p = this.process;
        return p != null && p.isAlive();
    }

    public Long getPid() {
        Process p = this.process;
        return (p != null && p.isAlive()) ? p.pid() : null;
    }

    /** 외부 표시용 — 헬스/관리 페이지에서 사용 */
    public Map<String, Object> describe() {
        Map<String, Object> m = new HashMap<>();
        m.put("autoStart", autoStart);
        m.put("running", isRunning());
        m.put("pid", getPid());
        m.put("host", host);
        m.put("port", port);
        m.put("projectRoot", resolveProjectRoot().toString());
        m.put("node", nodeExecutable);
        return m;
    }
}
