package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.migration.DbMigrationExecutor;
import io.github.claudetoolkit.ui.migration.DbMigrationJob;
import io.github.claudetoolkit.ui.migration.DbMigrationJobRepository;
import io.github.claudetoolkit.ui.migration.DbMigrationStreamBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.security.Principal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import io.github.claudetoolkit.ui.migration.DbOverrideConfig;

/**
 * DB 마이그레이션 가이드 컨트롤러 (v2.9.0).
 *
 * <p>ADMIN 전용 페이지로, 현재 사용 중인 DB를 자동 감지하고
 * H2 → PostgreSQL / MySQL / Oracle 11g 로 이전하는 절차를 안내합니다.
 */
@Controller
@RequestMapping("/admin/db-migration")
public class DbMigrationController {

    private static final Logger log = LoggerFactory.getLogger(DbMigrationController.class);

    private final DataSource                dataSource;
    private final DbMigrationExecutor       executor;
    private final DbMigrationJobRepository  jobRepo;
    private final DbMigrationStreamBroker   broker;

    public DbMigrationController(DataSource dataSource,
                                 DbMigrationExecutor executor,
                                 DbMigrationJobRepository jobRepo,
                                 DbMigrationStreamBroker broker) {
        this.dataSource = dataSource;
        this.executor   = executor;
        this.jobRepo    = jobRepo;
        this.broker     = broker;
    }

    /** 현재 DB 연결 정보 (JSON) */
    @GetMapping("/current")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> currentDb() {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            DatabaseMetaData md = conn.getMetaData();
            String product = md.getDatabaseProductName();
            String version = md.getDatabaseProductVersion();
            String url     = maskUrl(md.getURL());
            String user    = md.getUserName();
            String driver  = md.getDriverName() + " " + md.getDriverVersion();

            resp.put("success",        true);
            resp.put("productName",    product);
            resp.put("productVersion", version);
            resp.put("url",            url);
            resp.put("username",       user);
            resp.put("driver",         driver);
            resp.put("dbType",         detectDbType(product));
        } catch (Exception e) {
            log.warn("[DbMigration] DB 정보 조회 실패: {}", e.getMessage());
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
        return ResponseEntity.ok(resp);
    }

    /** 비밀번호 마스킹 */
    private String maskUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("password=([^&;]*)", "password=****")
                  .replaceAll(":[^:@/]+@", ":****@");
    }

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

    // ═══════════════════════════════════════════════════════════════
    // v2.9.5: 자동 이관
    // ═══════════════════════════════════════════════════════════════

    /** 자동 이관 대상 테이블 목록 — 대상 DB 와 충돌 확인용 */
    @GetMapping("/auto/tables")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> migrationTables() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("tables",  executor.getMigrationTableNames());
        resp.put("count",   executor.getMigrationTableNames().size());
        return ResponseEntity.ok(resp);
    }

    /** 이관 사전 검증 — 대상 DB 의 테이블 충돌/데이터 존재 여부 확인 */
    @PostMapping("/auto/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateBeforeMigration(
            @RequestParam String targetType,
            @RequestParam String host,
            @RequestParam int port,
            @RequestParam String dbName,
            @RequestParam String username,
            @RequestParam String password) {
        Map<String, Object> resp = executor.validateTarget(targetType, host, port, dbName, username, password);
        return ResponseEntity.ok(resp);
    }

    /** 타겟 DB 연결 테스트 */
    @PostMapping("/auto/test-connection")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestParam String targetType,
            @RequestParam String host,
            @RequestParam int port,
            @RequestParam String dbName,
            @RequestParam String username,
            @RequestParam String password) {
        Map<String, Object> resp = executor.testConnection(targetType, host, port, dbName, username, password);
        return ResponseEntity.ok(resp);
    }

    /** 이관 시작 */
    @PostMapping("/auto/start")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startMigration(
            @RequestParam String targetType,
            @RequestParam String host,
            @RequestParam int port,
            @RequestParam String dbName,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(defaultValue = "false") boolean overwrite,
            Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            DbMigrationJob job = executor.start(targetType, host, port, dbName, username, password,
                    overwrite, principal.getName());
            resp.put("success", true);
            resp.put("jobId",   job.getId());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 작업 상태 JSON */
    @GetMapping("/auto/jobs/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> jobStatus(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<>();
        DbMigrationJob job = jobRepo.findById(id).orElse(null);
        if (job == null) {
            resp.put("success", false);
            return ResponseEntity.ok(resp);
        }
        resp.put("success",           true);
        resp.put("id",                job.getId());
        resp.put("targetType",        job.getTargetType());
        resp.put("targetUrl",         job.getTargetUrl());
        resp.put("status",            job.getStatus());
        resp.put("totalTables",       job.getTotalTables());
        resp.put("completedTables",   job.getCompletedTables());
        resp.put("progressPercent",   job.getProgressPercent());
        resp.put("currentTable",      job.getCurrentTable());
        resp.put("currentTableTotal", job.getCurrentTableTotal());
        resp.put("currentTableDone",  job.getCurrentTableDone());
        resp.put("errorMessage",      job.getErrorMessage());
        resp.put("warnings",          job.getWarnings());
        return ResponseEntity.ok(resp);
    }

    /** 실시간 진행률 SSE */
    @GetMapping(value = "/auto/jobs/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter jobStream(@PathVariable Long id) {
        return broker.subscribe(id);
    }

    // ═══════════════════════════════════════════════════════════════
    // v4.2.2: 런타임 DB 전환 — 대상 DB 로 전환 / H2 로 복귀
    // ═══════════════════════════════════════════════════════════════

    /**
     * 자동 이관 완료 후, 앱이 사용할 DataSource 를 대상 DB 로 전환한다.
     * 오버라이드 파일을 작성한 뒤 짧은 지연 후 JVM 을 종료 — Docker 의 restart
     * 정책이 컨테이너를 재시작하면서 새 DB 로 부팅된다.
     */
    @PostMapping("/auto/switch-target")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> switchToTarget(
            @RequestParam String targetType,
            @RequestParam String host,
            @RequestParam int port,
            @RequestParam String dbName,
            @RequestParam String username,
            @RequestParam String password) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            String url;
            String driver;
            String dialect;
            switch (targetType.toLowerCase()) {
                case "postgresql":
                    url     = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
                    driver  = "org.postgresql.Driver";
                    dialect = "org.hibernate.dialect.PostgreSQLDialect";
                    break;
                case "mysql":
                    url     = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
                              host, port, dbName);
                    driver  = "com.mysql.cj.jdbc.Driver";
                    dialect = "org.hibernate.dialect.MySQL8Dialect";
                    break;
                case "oracle":
                    url     = String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, dbName);
                    driver  = "oracle.jdbc.OracleDriver";
                    dialect = "org.hibernate.dialect.Oracle12cDialect";
                    break;
                default:
                    resp.put("success", false);
                    resp.put("error",   "지원하지 않는 DB 유형: " + targetType);
                    return ResponseEntity.ok(resp);
            }

            // 연결 가능한지 최종 확인
            Map<String, Object> test = executor.testConnection(targetType, host, port, dbName, username, password);
            if (!Boolean.TRUE.equals(test.get("success"))) {
                resp.put("success", false);
                resp.put("error",   "전환 실패 — 연결 불가: " + test.get("error"));
                return ResponseEntity.ok(resp);
            }

            Properties p = new Properties();
            p.setProperty("url",       url);
            p.setProperty("driver",    driver);
            p.setProperty("username",  username);
            p.setProperty("password",  password);
            p.setProperty("dialect",   dialect);

            File out = new File(DbOverrideConfig.OVERRIDE_FILE);
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(out)) {
                p.store(fos, "DB runtime override — 자동 이관 후 전환 (" + targetType + ")");
            }

            resp.put("success",       true);
            resp.put("restartIn",     3);
            resp.put("targetUrl",     maskUrl(url));
            resp.put("message",       "3초 후 서비스가 재시작됩니다. 재시작 후 대상 DB 로 운영됩니다.");

            scheduleRestart();
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("[DbSwitch] 전환 실패", e);
            resp.put("success", false);
            resp.put("error",   e.getMessage());
            return ResponseEntity.ok(resp);
        }
    }

    /** 오버라이드 파일을 삭제하고 재시작 — 다시 H2 로 복귀 */
    @PostMapping("/auto/switch-h2")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> switchToH2() {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            File out = new File(DbOverrideConfig.OVERRIDE_FILE);
            if (out.exists()) {
                if (!out.delete()) {
                    resp.put("success", false);
                    resp.put("error",   "오버라이드 파일 삭제 실패");
                    return ResponseEntity.ok(resp);
                }
            }
            resp.put("success",   true);
            resp.put("restartIn", 3);
            resp.put("message",   "3초 후 서비스가 재시작됩니다. 재시작 후 H2 로 복귀합니다.");
            scheduleRestart();
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("[DbSwitch] H2 복귀 실패", e);
            resp.put("success", false);
            resp.put("error",   e.getMessage());
            return ResponseEntity.ok(resp);
        }
    }

    /** 오버라이드 파일 현재 상태 */
    @GetMapping("/auto/override-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> overrideStatus() {
        Map<String, Object> resp = new LinkedHashMap<>();
        File out = new File(DbOverrideConfig.OVERRIDE_FILE);
        resp.put("active", out.exists());
        if (out.exists()) {
            Properties p = new Properties();
            try (java.io.FileInputStream in = new java.io.FileInputStream(out)) {
                p.load(in);
                resp.put("url",    maskUrl(p.getProperty("url", "")));
                resp.put("driver", p.getProperty("driver", ""));
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(resp);
    }

    private void scheduleRestart() {
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            log.warn("[DbSwitch] JVM 종료 — 컨테이너 재시작 유도");
            System.exit(0);
        }, "db-switch-restart").start();
    }
}
