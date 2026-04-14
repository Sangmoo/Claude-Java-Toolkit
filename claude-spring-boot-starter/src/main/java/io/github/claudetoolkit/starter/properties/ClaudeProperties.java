package io.github.claudetoolkit.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Claude API.
 *
 * <pre>
 * # application.yml example
 * claude:
 *   api-key: ${CLAUDE_API_KEY}
 *   model: claude-sonnet-4-20250514
 *   max-tokens: 4096
 *   timeout-seconds: 60
 * </pre>
 */
@ConfigurationProperties(prefix = "claude")
public class ClaudeProperties {

    /** Anthropic API Key. Recommended: set via environment variable CLAUDE_API_KEY */
    private String apiKey;

    /** Claude model ID. Default: claude-sonnet-4-20250514 */
    private String model = "claude-sonnet-4-20250514";

    /** Maximum tokens in response. Default: 4096 */
    private int maxTokens = 4096;

    /** HTTP request timeout in seconds. Default: 60 */
    private int timeoutSeconds = 60;

    /** Base URL for Anthropic API. Override for proxy environments */
    private String baseUrl = "https://api.anthropic.com";

    /** Anthropic API version header value */
    private String apiVersion = "2023-06-01";

    /** HTTP/HTTPS proxy host (사내망 forward proxy 사용 시) */
    private String proxyHost;

    /** HTTP/HTTPS proxy port */
    private Integer proxyPort;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    public String getProxyHost() { return proxyHost; }
    public void setProxyHost(String proxyHost) { this.proxyHost = proxyHost; }

    public Integer getProxyPort() { return proxyPort; }
    public void setProxyPort(Integer proxyPort) { this.proxyPort = proxyPort; }
}
