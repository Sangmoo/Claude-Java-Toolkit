package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 시스템 헬스 대시보드 컨트롤러 (v2.8.0).
 *
 * <p>ADMIN 전용 모니터링 페이지:
 * <ul>
 *   <li>JVM 힙 메모리 사용량</li>
 *   <li>H2 DB 파일 크기</li>
 *   <li>활성 스레드 수</li>
 *   <li>JVM 업타임</li>
 *   <li>Claude API 상태</li>
 *   <li>사용자 수 / 활성 세션 수</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin/health")
public class SystemHealthController {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthController.class);

    private final ClaudeClient      claudeClient;
    private final AppUserRepository userRepository;
    private final DataSource        dataSource;

    public SystemHealthController(ClaudeClient claudeClient,
                                  AppUserRepository userRepository,
                                  DataSource dataSource) {
        this.claudeClient   = claudeClient;
        this.userRepository = userRepository;
        this.dataSource     = dataSource;
    }

    /**
     * Claude API 연결 진단 — 실제 네트워크/TLS 상태 확인용 (ADMIN 전용).
     * 사내망에서 handshake_failure 등 이슈 발생 시 정확한 원인을 확인.
     */
    @GetMapping("/claude-api-diagnose")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> diagnoseClaudeApi() {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            resp.put("success", true);
            resp.put("report",  claudeClient.diagnose());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 시스템 상태 JSON API (30초마다 갱신용) */
    @GetMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> healthData() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();

        // ── JVM 메모리 ──
        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory();
        long freeMem  = rt.freeMemory();
        long usedMem  = totalMem - freeMem;
        long maxMem   = rt.maxMemory();
        Map<String, Object> memory = new LinkedHashMap<String, Object>();
        memory.put("usedBytes",  usedMem);
        memory.put("totalBytes", totalMem);
        memory.put("maxBytes",   maxMem);
        memory.put("usedMb",     usedMem / (1024 * 1024));
        memory.put("totalMb",    totalMem / (1024 * 1024));
        memory.put("maxMb",      maxMem / (1024 * 1024));
        memory.put("usagePercent", maxMem > 0 ? (int)(usedMem * 100 / maxMem) : 0);
        data.put("memory", memory);

        // ── JVM 업타임 / 스레드 ──
        RuntimeMXBean rtBean  = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long uptimeMs = rtBean.getUptime();
        Map<String, Object> jvm = new LinkedHashMap<String, Object>();
        jvm.put("uptimeMs",       uptimeMs);
        jvm.put("uptimeFormatted", formatDuration(uptimeMs));
        jvm.put("startTime",      Instant.ofEpochMilli(rtBean.getStartTime()).toString());
        jvm.put("javaVersion",    System.getProperty("java.version"));
        jvm.put("javaVendor",     System.getProperty("java.vendor"));
        jvm.put("osName",         System.getProperty("os.name"));
        jvm.put("threadCount",    threadBean.getThreadCount());
        jvm.put("peakThreadCount", threadBean.getPeakThreadCount());
        jvm.put("availableProcessors", rt.availableProcessors());
        data.put("jvm", jvm);

        // ── 활성 DB 정보 (H2 / Oracle / MySQL / PostgreSQL 자동 감지) ──
        // 자동 이관 + 런타임 전환 기능 사용 시, 현재 운영 중인 실제 DB 가
        // 무엇인지 정확히 보여준다. H2 일 때만 파일 경로/크기를 함께 표시.
        Map<String, Object> database = new LinkedHashMap<String, Object>();
        String dbType = "unknown";
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            String product = md.getDatabaseProductName();
            String version = md.getDatabaseProductVersion();
            String url     = maskJdbcUrl(md.getURL());
            String dbUser  = md.getUserName();
            String driver  = md.getDriverName() + " " + md.getDriverVersion();

            dbType = detectDbType(product);
            database.put("type",           dbType);
            database.put("productName",    product);
            database.put("productVersion", version);
            database.put("url",            url);
            database.put("username",       dbUser);
            database.put("driver",         driver);
            database.put("connected",      true);
        } catch (Exception e) {
            log.warn("[SystemHealth] DB 메타데이터 조회 실패: {}", e.getMessage());
            database.put("connected", false);
            database.put("error",     e.getMessage());
        }

        // H2 인 경우에만 파일 경로/크기 노출 (외부 DB 는 의미 없음)
        if ("h2".equals(dbType)) {
            try {
                String home = System.getProperty("user.home");
                File dbFile = new File(home + "/.claude-toolkit/history-db.mv.db");
                if (dbFile.exists()) {
                    long size = dbFile.length();
                    database.put("filePath",   dbFile.getAbsolutePath());
                    database.put("fileBytes",  size);
                    database.put("fileMb",     String.format("%.2f", size / (1024.0 * 1024.0)));
                    database.put("fileExists", true);

                    File disk = dbFile.getParentFile();
                    if (disk != null && disk.exists()) {
                        long free = disk.getFreeSpace();
                        long total = disk.getTotalSpace();
                        database.put("diskFreeBytes",    free);
                        database.put("diskTotalBytes",   total);
                        database.put("diskFreeGb",       String.format("%.1f", free / (1024.0 * 1024.0 * 1024.0)));
                        database.put("diskTotalGb",      String.format("%.1f", total / (1024.0 * 1024.0 * 1024.0)));
                        database.put("diskUsagePercent", total > 0 ? (int)((total - free) * 100 / total) : 0);
                    }
                } else {
                    database.put("fileExists", false);
                }
            } catch (Exception e) {
                database.put("fileError", e.getMessage());
            }
        } else if (database.get("connected") == Boolean.TRUE) {
            // 외부 DB: 사이즈/디스크 정보 의미 없음 — 명시
            database.put("note", "외부 DB 사용 중 — 디스크/파일 정보는 DB 서버에서 확인하세요.");
        }

        // 오버라이드 활성 여부 (자동 이관으로 전환된 상태인지)
        File overrideFile = new File("data/db-override.properties");
        database.put("overrideActive", overrideFile.exists());

        data.put("database", database);

        // ── Claude API 상태 ──
        Map<String, Object> claudeApi = new LinkedHashMap<String, Object>();
        try {
            String model = claudeClient.getEffectiveModel();
            claudeApi.put("model", model != null ? model : "(미설정)");
            String key = claudeClient.getEffectiveApiKey();
            claudeApi.put("keyConfigured", key != null && !key.trim().isEmpty());
            claudeApi.put("keyMasked",
                    key != null && key.length() > 14
                            ? key.substring(0, 10) + "..." + key.substring(key.length() - 4)
                            : (key != null && !key.isEmpty() ? "****" : ""));
            claudeApi.put("status", "UNKNOWN");
            claudeApi.put("note",   "API 호출 시 상태 확인");
        } catch (Exception e) {
            claudeApi.put("status", "ERROR");
            claudeApi.put("error",  e.getMessage());
        }
        data.put("claudeApi", claudeApi);

        // ── 사용자 통계 ──
        Map<String, Object> users = new LinkedHashMap<String, Object>();
        try {
            long total = userRepository.count();
            users.put("total", total);
        } catch (Exception e) {
            users.put("error", e.getMessage());
        }
        data.put("users", users);

        return ResponseEntity.ok(data);
    }

    private String formatDuration(long millis) {
        Duration d = Duration.ofMillis(millis);
        long days    = d.toDays();
        long hours   = d.toHours() % 24;
        long minutes = d.toMinutes() % 60;
        long seconds = d.getSeconds() % 60;
        if (days > 0)    return days + "일 " + hours + "시간 " + minutes + "분";
        if (hours > 0)   return hours + "시간 " + minutes + "분 " + seconds + "초";
        if (minutes > 0) return minutes + "분 " + seconds + "초";
        return seconds + "초";
    }

    /** JDBC URL 의 패스워드 마스킹 */
    private String maskJdbcUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("password=([^&;]*)", "password=****")
                  .replaceAll(":[^:@/]+@", ":****@");
    }

    /** Database product name → 짧은 타입 식별자 */
    private String detectDbType(String product) {
        if (product == null) return "unknown";
        String lower = product.toLowerCase();
        if (lower.contains("h2"))         return "h2";
        if (lower.contains("mysql"))      return "mysql";
        if (lower.contains("postgresql")) return "postgresql";
        if (lower.contains("postgres"))   return "postgresql";
        if (lower.contains("oracle"))     return "oracle";
        return "unknown";
    }
}
