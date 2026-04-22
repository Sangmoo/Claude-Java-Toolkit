package io.github.claudetoolkit.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Claude Toolkit Web UI — Spring Boot Application entry point.
 *
 * <p>Start the application and open: http://localhost:8027
 *
 * <p>Required environment variable or application.yml:
 * <pre>
 *   CLAUDE_API_KEY=sk-ant-...
 *   # or
 *   claude.api-key=sk-ant-...
 * </pre>
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync   // v4.4.x — StartupReadiness 비동기 warmup 용
public class ClaudeToolkitUiApplication {

    public static void main(String[] args) {
        // ── Oracle JDBC + Docker timezone 이슈 (ORA-01882) 방어 ──
        // 컨테이너 환경의 JVM timezone region 을 Oracle 이 못 찾는 문제를
        // 막기 위해, JDBC 드라이버가 timezone 을 region 명이 아닌 offset 으로
        // 전송하도록 강제. -D 옵션이 누락된 환경에서도 안전하도록 코드 레벨에서도 보장.
        if (System.getProperty("oracle.jdbc.timezoneAsRegion") == null) {
            System.setProperty("oracle.jdbc.timezoneAsRegion", "false");
        }
        if (System.getProperty("user.timezone") == null || System.getProperty("user.timezone").isEmpty()) {
            System.setProperty("user.timezone", "Asia/Seoul");
        }
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"));

        SpringApplication.run(ClaudeToolkitUiApplication.class, args);
    }
}
