package io.github.claudetoolkit.ui.harness.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Phase A — 하네스 실행 감사 로그(stage별 입출력 + 경과 시간) 파일 기록기.
 *
 * <h3>활성 조건 (기본은 비활성)</h3>
 * {@code application.yml}에서 명시적으로 켜야 동작합니다:
 * <pre>
 *   toolkit:
 *     harness:
 *       audit: true
 *       audit-dir: /var/log/claude-toolkit/harness   # optional, 기본 ${user.home}/.claude-toolkit/harness-runs
 * </pre>
 *
 * <h3>파일 레이아웃</h3>
 * <pre>
 *   {audit-dir}/{harnessName}/{runId}/
 *     ├── 01-analyst.txt
 *     ├── 02-builder.txt
 *     └── …
 * </pre>
 * 파일 내용은 단순 plaintext (system/user/output 섹션 + meta).
 * YAML이 아닌 이유: 코드/SP를 그대로 담으면 YAML escape가 까다롭고 가독성도 떨어짐.
 *
 * <h3>보안</h3>
 * <ul>
 *   <li>API 키·세션 토큰은 stage 입출력에 포함되지 않으므로 별도 마스킹 없음.</li>
 *   <li>감사 디렉토리는 OS 권한으로 보호 — 운영 시 root/app 사용자만 읽도록 설정 권장.</li>
 *   <li>경로 인젝션 방지 — runId는 UUID, harness/stage 이름은 PromptLoader와 동일 규칙으로 검증됨.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "toolkit.harness", name = "audit", havingValue = "true")
public class HarnessAuditWriter {

    private static final Logger log = LoggerFactory.getLogger(HarnessAuditWriter.class);

    @Value("${toolkit.harness.audit-dir:#{null}}")
    private String auditDirOverride;

    private Path baseDir;
    private int  stageCounter; // not used cross-run, just for filename ordering hint

    @PostConstruct
    void init() {
        String dir = auditDirOverride;
        if (dir == null || dir.trim().isEmpty()) {
            dir = System.getProperty("user.home") + "/.claude-toolkit/harness-runs";
        }
        this.baseDir = Paths.get(dir);
        try {
            Files.createDirectories(baseDir);
            log.info("[HarnessAudit] 감사 로그 디렉토리: {}", baseDir.toAbsolutePath());
        } catch (IOException e) {
            log.warn("[HarnessAudit] 디렉토리 생성 실패 — 감사 비활성: {}", e.getMessage());
            this.baseDir = null;
        }
    }

    /**
     * stage 실행 결과를 파일로 기록합니다 — 실패는 silent (감사가 본 기능을 막지 않음).
     */
    public synchronized void writeStage(String harnessName, String runId, String stageName,
                                         String system, String user, String output,
                                         long elapsedMs, String error) {
        if (baseDir == null) return;
        try {
            Path runDir = baseDir.resolve(safe(harnessName)).resolve(safe(runId));
            Files.createDirectories(runDir);
            int idx = stageOrdinalFor(runDir);
            String fileName = String.format("%02d-%s.txt", idx, safe(stageName));
            Path file = runDir.resolve(fileName);

            StringBuilder sb = new StringBuilder(8192);
            sb.append("# Harness Audit\n");
            sb.append("harness     : ").append(harnessName).append('\n');
            sb.append("runId       : ").append(runId).append('\n');
            sb.append("stage       : ").append(stageName).append('\n');
            sb.append("timestamp   : ").append(Instant.now()).append('\n');
            sb.append("elapsedMs   : ").append(elapsedMs).append('\n');
            if (error != null) sb.append("error       : ").append(error).append('\n');
            sb.append("\n--- SYSTEM PROMPT ---\n").append(nullToEmpty(system)).append('\n');
            sb.append("\n--- USER MESSAGE ---\n").append(nullToEmpty(user)).append('\n');
            sb.append("\n--- OUTPUT ---\n").append(nullToEmpty(output)).append('\n');

            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("[HarnessAudit] write 실패 stage={}: {}", stageName, e.getMessage());
        }
    }

    private static int stageOrdinalFor(Path runDir) {
        try {
            return (int) Files.list(runDir).count() + 1;
        } catch (IOException e) {
            return 1;
        }
    }

    private static String safe(String s) {
        if (s == null) return "_";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                      || (c >= '0' && c <= '9') || c == '-' || c == '_';
            b.append(ok ? c : '_');
        }
        return b.length() == 0 ? "_" : b.toString();
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
