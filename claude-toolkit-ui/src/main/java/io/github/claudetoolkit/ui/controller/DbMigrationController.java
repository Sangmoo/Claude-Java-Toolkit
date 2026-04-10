package io.github.claudetoolkit.ui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
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

    private final DataSource dataSource;

    public DbMigrationController(DataSource dataSource) {
        this.dataSource = dataSource;
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
}
