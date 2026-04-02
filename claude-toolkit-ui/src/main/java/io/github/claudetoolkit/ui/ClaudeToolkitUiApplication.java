package io.github.claudetoolkit.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
public class ClaudeToolkitUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaudeToolkitUiApplication.class, args);
    }
}
