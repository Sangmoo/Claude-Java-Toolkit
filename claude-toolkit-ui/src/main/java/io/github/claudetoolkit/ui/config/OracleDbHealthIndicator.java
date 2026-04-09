package io.github.claudetoolkit.ui.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Oracle DB 연결 상태 헬스체크.
 * Oracle 연결이 설정된 경우에만 활성화됩니다.
 * /actuator/health 에서 oracleDb 항목으로 표시됩니다.
 */
@Component("oracleDb")
public class OracleDbHealthIndicator implements HealthIndicator {

    private final ToolkitSettings settings;

    public OracleDbHealthIndicator(ToolkitSettings settings) {
        this.settings = settings;
    }

    @Override
    public Health health() {
        String url = settings.getDb().getUrl();
        if (url == null || url.isEmpty()) {
            return Health.unknown()
                    .withDetail("reason", "Oracle DB 미설정")
                    .build();
        }

        long start = System.currentTimeMillis();
        try {
            Connection conn = DriverManager.getConnection(
                    url, settings.getDb().getUsername(), settings.getDb().getPassword());
            Statement stmt = conn.createStatement();
            stmt.execute("SELECT 1 FROM DUAL");
            stmt.close();
            conn.close();
            long elapsed = System.currentTimeMillis() - start;
            return Health.up()
                    .withDetail("url", maskUrl(url))
                    .withDetail("responseTime", elapsed + "ms")
                    .build();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return Health.down()
                    .withDetail("url", maskUrl(url))
                    .withDetail("error", e.getMessage())
                    .withDetail("responseTime", elapsed + "ms")
                    .build();
        }
    }

    private String maskUrl(String url) {
        // jdbc:oracle:thin:@//host:1521/DB → jdbc:oracle:thin:@//host:***
        int atIdx = url.indexOf('@');
        if (atIdx < 0) return "***";
        String prefix = url.substring(0, atIdx + 1);
        String rest = url.substring(atIdx + 1);
        int slashIdx = rest.lastIndexOf('/');
        if (slashIdx > 0) {
            return prefix + rest.substring(0, slashIdx) + "/***";
        }
        return prefix + "***";
    }
}
