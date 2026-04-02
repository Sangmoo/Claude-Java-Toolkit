package io.github.claudetoolkit.starter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claudetoolkit.starter.exception.ClaudeApiException;
import io.github.claudetoolkit.starter.model.*;
import io.github.claudetoolkit.starter.properties.ClaudeProperties;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Core HTTP client for Anthropic Claude API.
 *
 * <p>Supports two interaction modes:
 * <ul>
 *   <li>{@link #chat(String, String)} – standard blocking request</li>
 *   <li>{@link #chatStream(String, String, Consumer)} – Server-Sent Events streaming</li>
 * </ul>
 */
public class ClaudeClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ClaudeProperties  properties;
    private final OkHttpClient      httpClient;
    private final ObjectMapper      objectMapper;
    private volatile String         modelOverride     = null;
    private volatile int            lastInputTokens   = 0;
    private volatile int            lastOutputTokens  = 0;

    public ClaudeClient(ClaudeProperties properties) {
        this.properties   = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient   = new OkHttpClient.Builder()
                .connectTimeout(properties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(properties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(properties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    // ── Model override ───────────────────────────────────────────────────────

    public void setModelOverride(String model) {
        this.modelOverride = (model != null && !model.trim().isEmpty()) ? model.trim() : null;
    }

    public String getEffectiveModel() {
        return modelOverride != null ? modelOverride : properties.getModel();
    }

    // ── Standard (blocking) chat ─────────────────────────────────────────────

    public String chat(String userMessage) {
        ClaudeRequest request = ClaudeRequest.builder()
                .model(getEffectiveModel())
                .maxTokens(properties.getMaxTokens())
                .messages(Collections.singletonList(ClaudeMessage.ofUser(userMessage)))
                .build();
        return sendRequest(request);
    }

    public String chat(String systemPrompt, String userMessage) {
        ClaudeRequest request = ClaudeRequest.builder()
                .model(getEffectiveModel())
                .maxTokens(properties.getMaxTokens())
                .system(systemPrompt)
                .messages(Collections.singletonList(ClaudeMessage.ofUser(userMessage)))
                .build();
        return sendRequest(request);
    }

    public String sendRequest(ClaudeRequest request) {
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            Request httpRequest = new Request.Builder()
                    .url(properties.getBaseUrl() + "/v1/messages")
                    .header("x-api-key", properties.getApiKey())
                    .header("anthropic-version", properties.getApiVersion())
                    .header("content-type", "application/json")
                    .post(RequestBody.create(requestBody, JSON))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new ClaudeApiException(
                            "Claude API error: HTTP " + response.code() + " - " + responseBody);
                }
                ClaudeResponse claudeResponse = objectMapper.readValue(responseBody, ClaudeResponse.class);
                if (claudeResponse.getUsage() != null) {
                    this.lastInputTokens  = claudeResponse.getUsage().getInputTokens();
                    this.lastOutputTokens = claudeResponse.getUsage().getOutputTokens();
                }
                return claudeResponse.getFirstTextContent();
            }
        } catch (IOException e) {
            throw new ClaudeApiException("Failed to call Claude API", e);
        }
    }

    // ── Streaming chat (SSE) ─────────────────────────────────────────────────

    /**
     * Calls the Claude API with {@code stream: true} and invokes {@code onChunk}
     * for every text fragment received in real time.
     *
     * <p>This method <strong>blocks</strong> the calling thread until the stream
     * completes or an error occurs.  Wrap in a daemon thread for non-blocking use.
     *
     * @param systemPrompt  system instruction (may be null)
     * @param userMessage   user message
     * @param onChunk       callback invoked with each text delta chunk
     * @throws IOException  if the HTTP connection fails
     */
    public void chatStream(String systemPrompt, String userMessage,
                           Consumer<String> onChunk) throws IOException {
        ClaudeRequest.Builder builder = ClaudeRequest.builder()
                .model(getEffectiveModel())
                .maxTokens(properties.getMaxTokens())
                .stream(true)
                .messages(Collections.singletonList(ClaudeMessage.ofUser(userMessage)));
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            builder.system(systemPrompt);
        }
        ClaudeRequest request = builder.build();

        String requestBody = objectMapper.writeValueAsString(request);
        Request httpRequest = new Request.Builder()
                .url(properties.getBaseUrl() + "/v1/messages")
                .header("x-api-key", properties.getApiKey())
                .header("anthropic-version", properties.getApiVersion())
                .header("content-type", "application/json")
                .post(RequestBody.create(requestBody, JSON))
                .build();

        // Use a long-timeout client for streaming
        OkHttpClient streamClient = httpClient.newBuilder()
                .readTimeout(180, TimeUnit.SECONDS)
                .build();

        try (Response response = streamClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String body = response.body() != null ? response.body().string() : "";
                throw new ClaudeApiException(
                        "Claude streaming API error: HTTP " + response.code() + " - " + body);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if (data.isEmpty()) continue;
                try {
                    JsonNode node = objectMapper.readTree(data);
                    String type  = node.path("type").asText("");
                    if ("content_block_delta".equals(type)) {
                        JsonNode delta = node.path("delta");
                        if ("text_delta".equals(delta.path("type").asText(""))) {
                            String text = delta.path("text").asText("");
                            if (!text.isEmpty()) {
                                onChunk.accept(text);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // malformed SSE chunk — skip
                }
            }
        }
    }

    /** Returns the API key configured for this client (for validation purposes). */
    public String getApiKey() { return properties.getApiKey(); }

    /** Returns the model name configured for this client. */
    public String getModel()  { return properties.getModel(); }

    /** Returns input token count from the most recent non-streaming request. */
    public int getLastInputTokens()  { return lastInputTokens; }

    /** Returns output token count from the most recent non-streaming request. */
    public int getLastOutputTokens() { return lastOutputTokens; }
}
