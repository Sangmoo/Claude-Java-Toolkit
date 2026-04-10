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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Core HTTP client for Anthropic Claude API.
 *
 * <p>Supports four interaction modes:
 * <ul>
 *   <li>{@link #chat(String, String)} – standard blocking request</li>
 *   <li>{@link #chatWithContinuation} – blocking request with auto-continuation when truncated</li>
 *   <li>{@link #chatStream} – Server-Sent Events streaming</li>
 *   <li>{@link #chatStreamWithContinuation} – SSE streaming with auto-continuation when truncated</li>
 * </ul>
 *
 * <h3>Auto-continuation</h3>
 * <p>When Claude's response is cut off at {@code max_tokens} (i.e. {@code stop_reason = "max_tokens"}),
 * the continuation variants automatically send the partial response back as an assistant turn and
 * request Claude to continue from where it stopped. This repeats up to {@code maxContinuations}
 * times, effectively removing the output token limit for long responses such as large stored
 * procedures or full-class refactors.
 */
public class ClaudeClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ClaudeProperties  properties;
    private final OkHttpClient      httpClient;
    private final ObjectMapper      objectMapper;
    private volatile String         modelOverride     = null;
    private volatile int            lastInputTokens   = 0;
    private volatile int            lastOutputTokens  = 0;

    /**
     * 요청별 API 키 오버라이드.
     * <p>{@link InheritableThreadLocal}이라 SSE 백그라운드 스레드({@code new Thread()})에서도
     * 부모 스레드의 값이 상속됩니다. 멀티유저 동시 요청 시 공유 상태(ClaudeProperties)를
     * 수정하지 않고 요청별로 개인 API 키를 격리합니다.
     */
    private final InheritableThreadLocal<String> apiKeyOverride = new InheritableThreadLocal<String>();

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

    // ── API key override (요청별 개인 API 키 격리) ────────────────────────────

    /** 현재 스레드(및 자식 스레드)에서 사용할 API 키를 설정합니다. */
    public void setApiKeyOverride(String key) {
        if (key != null && !key.trim().isEmpty()) {
            apiKeyOverride.set(key.trim());
        } else {
            apiKeyOverride.remove();
        }
    }

    /** 현재 스레드의 API 키 오버라이드를 제거합니다. */
    public void clearApiKeyOverride() {
        apiKeyOverride.remove();
    }

    /** 현재 요청에 실제 사용될 API 키 (오버라이드 우선, 없으면 properties). */
    public String getEffectiveApiKey() {
        String override = apiKeyOverride.get();
        return override != null ? override : properties.getApiKey();
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

    /** Variant that overrides max_tokens for this call only. */
    public String chat(String systemPrompt, String userMessage, int maxTokens) {
        ClaudeRequest request = ClaudeRequest.builder()
                .model(getEffectiveModel())
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .messages(Collections.singletonList(ClaudeMessage.ofUser(userMessage)))
                .build();
        return sendRequest(request);
    }

    // ── Auto-continuation (blocking) ─────────────────────────────────────────

    /**
     * Calls Claude and automatically continues if the response is cut off at {@code max_tokens}.
     *
     * <p>When {@code stop_reason = "max_tokens"}, the partial response is sent back as an
     * {@code assistant} turn and a new user turn asks Claude to continue.  This repeats up to
     * {@code maxContinuations} times.  The final result is the concatenation of all turns.
     *
     * @param systemPrompt     system instruction
     * @param userMessage      initial user message
     * @param maxTokens        max output tokens per call
     * @param maxContinuations maximum number of additional continuation calls (e.g. 3)
     * @return complete (possibly multi-turn-assembled) response text
     */
    public String chatWithContinuation(String systemPrompt, String userMessage,
                                       int maxTokens, int maxContinuations) {
        List<ClaudeMessage> messages = new ArrayList<ClaudeMessage>();
        messages.add(ClaudeMessage.ofUser(userMessage));

        StringBuilder accumulated = new StringBuilder();

        for (int turn = 0; turn <= maxContinuations; turn++) {
            ClaudeRequest.Builder builder = ClaudeRequest.builder()
                    .model(getEffectiveModel())
                    .maxTokens(maxTokens)
                    .messages(new ArrayList<ClaudeMessage>(messages));
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                builder.system(systemPrompt);
            }
            ClaudeResponse response = sendRequestFull(builder.build());
            String text = response.getFirstTextContent();
            accumulated.append(text);

            // Normal completion or exhausted retries — stop
            if (!"max_tokens".equals(response.getStopReason()) || turn == maxContinuations) {
                break;
            }
            // Truncated: add partial assistant response and ask to continue
            messages.add(ClaudeMessage.ofAssistant(text));
            messages.add(ClaudeMessage.ofUser(
                    "계속 작성해주세요. 중단된 부분부터 바로 이어서 작성하세요."));
        }
        return accumulated.toString();
    }

    // ── Low-level: request with full response ────────────────────────────────

    public String sendRequest(ClaudeRequest request) {
        return sendRequestFull(request).getFirstTextContent();
    }

    /**
     * Sends a request and returns the full {@link ClaudeResponse} (including stop_reason, usage, etc.).
     */
    public ClaudeResponse sendRequestFull(ClaudeRequest request) {
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            Request httpRequest = new Request.Builder()
                    .url(properties.getBaseUrl() + "/v1/messages")
                    .header("x-api-key", getEffectiveApiKey())
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
                ClaudeResponse claudeResponse =
                        objectMapper.readValue(responseBody, ClaudeResponse.class);
                if (claudeResponse.getUsage() != null) {
                    this.lastInputTokens  = claudeResponse.getUsage().getInputTokens();
                    this.lastOutputTokens = claudeResponse.getUsage().getOutputTokens();
                }
                return claudeResponse;
            }
        } catch (IOException e) {
            throw new ClaudeApiException("Failed to call Claude API", e);
        }
    }

    // ── Streaming chat (SSE) ─────────────────────────────────────────────────

    public void chatStream(String systemPrompt, String userMessage,
                           Consumer<String> onChunk) throws IOException {
        chatStreamInternal(systemPrompt,
                Collections.singletonList(ClaudeMessage.ofUser(userMessage)),
                properties.getMaxTokens(), onChunk);
    }

    public void chatStream(String systemPrompt, String userMessage,
                           int maxTokens, Consumer<String> onChunk) throws IOException {
        chatStreamInternal(systemPrompt,
                Collections.singletonList(ClaudeMessage.ofUser(userMessage)),
                maxTokens, onChunk);
    }

    /**
     * 멀티턴 대화를 네이티브 Messages API 형식으로 스트리밍합니다.
     * <p>대화 히스토리를 텍스트로 이어붙이지 않고 {@code messages} 배열로 직접 전달하여
     * 토큰 절감 및 Claude prompt caching 활용이 가능합니다.
     * user/assistant 역할이 엄격히 교대되어야 합니다.
     */
    public void chatStream(String systemPrompt, List<ClaudeMessage> messages,
                           int maxTokens, Consumer<String> onChunk) throws IOException {
        chatStreamInternal(systemPrompt, messages, maxTokens, onChunk);
    }

    // ── Auto-continuation (streaming) ────────────────────────────────────────

    /**
     * Streams a response and automatically continues streaming if truncated at {@code max_tokens}.
     *
     * <p>Uses the same multi-turn approach as {@link #chatWithContinuation}: the partial text
     * accumulated so far is sent back as an assistant turn and streaming resumes from where it
     * stopped.  All chunks across all turns flow through the single {@code onChunk} consumer,
     * so the caller sees one continuous stream.
     *
     * @param systemPrompt     system instruction
     * @param userMessage      initial user message
     * @param maxTokens        max output tokens per streaming call
     * @param maxContinuations maximum number of additional continuation calls (e.g. 3)
     * @param onChunk          callback invoked with each text delta chunk
     */
    public void chatStreamWithContinuation(String systemPrompt, String userMessage,
                                           int maxTokens, int maxContinuations,
                                           Consumer<String> onChunk) throws IOException {
        List<ClaudeMessage> messages = new ArrayList<ClaudeMessage>();
        messages.add(ClaudeMessage.ofUser(userMessage));

        for (int turn = 0; turn <= maxContinuations; turn++) {
            final StringBuilder partialBuf = new StringBuilder();
            final Consumer<String> bufferingChunk = new Consumer<String>() {
                public void accept(String chunk) {
                    partialBuf.append(chunk);
                    onChunk.accept(chunk);
                }
            };

            String stopReason = chatStreamInternal(systemPrompt, messages, maxTokens, bufferingChunk);

            if (!"max_tokens".equals(stopReason) || turn == maxContinuations) {
                break;
            }
            // Truncated: prepare continuation turn
            messages.add(ClaudeMessage.ofAssistant(partialBuf.toString()));
            messages.add(ClaudeMessage.ofUser(
                    "계속 작성해주세요. 중단된 부분부터 바로 이어서 작성하세요."));
        }
    }

    // ── Internal streaming core ───────────────────────────────────────────────

    /**
     * Core SSE streaming implementation.
     * Parses {@code content_block_delta} for text chunks and {@code message_delta} for stop_reason.
     *
     * @return stop_reason: {@code "end_turn"} (normal) or {@code "max_tokens"} (truncated)
     */
    private String chatStreamInternal(String systemPrompt, List<ClaudeMessage> messages,
                                      int maxTokens, Consumer<String> onChunk) throws IOException {
        ClaudeRequest.Builder builder = ClaudeRequest.builder()
                .model(getEffectiveModel())
                .maxTokens(maxTokens)
                .stream(true)
                .messages(new ArrayList<ClaudeMessage>(messages));
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            builder.system(systemPrompt);
        }
        ClaudeRequest request = builder.build();

        String requestBody = objectMapper.writeValueAsString(request);
        Request httpRequest = new Request.Builder()
                .url(properties.getBaseUrl() + "/v1/messages")
                .header("x-api-key", getEffectiveApiKey())
                .header("anthropic-version", properties.getApiVersion())
                .header("content-type", "application/json")
                .post(RequestBody.create(requestBody, JSON))
                .build();

        OkHttpClient streamClient = httpClient.newBuilder()
                .readTimeout(180, TimeUnit.SECONDS)
                .build();

        String stopReason = "end_turn";

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
                            if (!text.isEmpty()) onChunk.accept(text);
                        }
                    } else if ("message_delta".equals(type)) {
                        // Capture stop_reason to detect truncation
                        String sr = node.path("delta").path("stop_reason").asText("");
                        if (!sr.isEmpty()) stopReason = sr;
                    }
                } catch (Exception ignored) {
                    // malformed SSE chunk — skip
                }
            }
        }
        return stopReason;
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /** Returns the API key configured for this client (for validation purposes). */
    public String getApiKey() { return properties.getApiKey(); }

    /** Returns the model name configured for this client. */
    public String getModel()  { return properties.getModel(); }

    /** Returns input token count from the most recent non-streaming request. */
    public int getLastInputTokens()  { return lastInputTokens; }

    /** Returns output token count from the most recent non-streaming request. */
    public int getLastOutputTokens() { return lastOutputTokens; }

    /** Returns the underlying properties (for callers needing maxTokens etc.). */
    public ClaudeProperties getProperties() { return properties; }
}
