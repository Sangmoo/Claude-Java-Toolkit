package io.github.claudetoolkit.ui.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 앱 시작 시 기존 DB 스키마에 누락된 컬럼을 자동 추가합니다.
 * ddl-auto: update가 NOT NULL + DEFAULT 조합을 처리하지 못하는 경우 보완.
 */
@Component
@Order(1)
public class SchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    public SchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfNotExists("APP_USER", "RATE_LIMIT_PER_MINUTE", "INTEGER DEFAULT 0");
        addColumnIfNotExists("APP_USER", "RATE_LIMIT_PER_HOUR",   "INTEGER DEFAULT 0");
        addColumnIfNotExists("APP_USER", "PERSONAL_API_KEY",      "VARCHAR(500)");
        addColumnIfNotExists("APP_USER", "TOTP_SECRET",           "VARCHAR(500)");
        addColumnIfNotExists("APP_USER", "MUST_CHANGE_PASSWORD",  "BOOLEAN DEFAULT FALSE");
        addColumnIfNotExists("AUDIT_LOG", "USERNAME",             "VARCHAR(50)");
        addColumnIfNotExists("AUDIT_LOG", "DURATION_MS",          "BIGINT");

        // 기존 VARCHAR(200)/VARCHAR(64) → VARCHAR(500) 확장 (암호화 데이터 수용)
        alterColumnType("APP_USER", "PERSONAL_API_KEY", "VARCHAR(500)");
        alterColumnType("APP_USER", "TOTP_SECRET",      "VARCHAR(500)");

        // ReviewHistory에 username 추가 (v2.4.0 댓글/알림용)
        addColumnIfNotExists("REVIEW_HISTORY", "USERNAME", "VARCHAR(50)");

        // AuditLog UserAgent 300→500 확장
        alterColumnType("AUDIT_LOG", "USER_AGENT", "VARCHAR(500)");

        // v2.6.0 — 보안 강화 필드
        addColumnIfNotExists("APP_USER", "FAILED_LOGIN_ATTEMPTS",  "INTEGER DEFAULT 0");
        addColumnIfNotExists("APP_USER", "LOCKED_UNTIL",           "TIMESTAMP");
        addColumnIfNotExists("APP_USER", "LAST_PASSWORD_CHANGE_AT","TIMESTAMP");
        addColumnIfNotExists("APP_USER", "PASSWORD_SNOOZE_AT",     "TIMESTAMP");
        addColumnIfNotExists("APP_USER", "IP_WHITELIST",           "VARCHAR(500)");
        addColumnIfNotExists("APP_USER", "DAILY_API_LIMIT",        "INTEGER DEFAULT 0");
        addColumnIfNotExists("APP_USER", "MONTHLY_API_LIMIT",      "INTEGER DEFAULT 0");
    }

    private void addColumnIfNotExists(String table, String column, String type) {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + type);
        } catch (Exception e) {
            // 테이블이 아직 없거나 이미 존재하는 경우 무시
        } finally {
            closeQuietly(stmt);
            closeQuietly(conn);
        }
    }

    private void alterColumnType(String table, String column, String type) {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            stmt.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " " + type);
        } catch (Exception e) {
            // 지원하지 않는 DB이거나 변경 불필요한 경우 무시
        } finally {
            closeQuietly(stmt);
            closeQuietly(conn);
        }
    }

    private void closeQuietly(AutoCloseable c) {
        if (c != null) { try { c.close(); } catch (Exception ignored) {} }
    }
}
