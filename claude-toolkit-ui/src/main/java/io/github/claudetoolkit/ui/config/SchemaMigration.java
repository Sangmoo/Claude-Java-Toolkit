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
        addColumnIfNotExists("APP_USER", "PERSONAL_API_KEY",      "VARCHAR(200)");
        addColumnIfNotExists("AUDIT_LOG", "USERNAME",             "VARCHAR(50)");
        addColumnIfNotExists("AUDIT_LOG", "DURATION_MS",          "BIGINT");
    }

    private void addColumnIfNotExists(String table, String column, String type) {
        try {
            Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement();
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + type);
            stmt.close();
            conn.close();
        } catch (Exception e) {
            // 테이블이 아직 없거나 이미 존재하는 경우 무시
        }
    }
}
