package io.github.claudetoolkit.starter.config;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.starter.properties.ClaudeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot Auto-Configuration for Claude API.
 *
 * Activated when 'claude.api-key' is set in application properties.
 * Registers {@link ClaudeClient} as a Spring Bean automatically.
 */
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
@ConditionalOnProperty(prefix = "claude", name = "api-key")
public class ClaudeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ClaudeClient claudeClient(ClaudeProperties properties) {
        return new ClaudeClient(properties);
    }
}
