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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.sql.DataSource;
import java.security.Principal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.Map;

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

    @GetMapping
    public String page(Model model) {
        return "admin/db-migration";
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
}
