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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.net.ssl.SSLHandshakeException;

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

        // ──────────────────────────────────────────────────────────────────
        //  JDK 8 + 사내망 환경에서 api.anthropic.com 과의 TLS 이슈 방지
        // ──────────────────────────────────────────────────────────────────
        //  • MODERN_TLS(TLSv1.3/1.2) 와 COMPATIBLE_TLS(TLSv1.2) 를 fallback 으로
        //  • 필요하면 system property 로 주입된 proxy 를 사용
        //  • 프록시 host/port 가 application.yml 에 명시되어 있으면 그것을 우선
        ConnectionSpec modern = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                .build();
        ConnectionSpec compatible = new ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build();

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(properties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(properties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(properties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .connectionSpecs(Arrays.asList(modern, compatible))
                .retryOnConnectionFailure(true);

        // ── 프록시 설정 (application.yml > 환경변수 순) ──
        String proxyHost = properties.getProxyHost();
        Integer proxyPort = properties.getProxyPort();

        // 환경변수 HTTPS_PROXY/HTTP_PROXY 지원
        if ((proxyHost == null || proxyHost.isEmpty()) && proxyPort == null) {
            String envProxy = System.getenv("HTTPS_PROXY");
            if (envProxy == null || envProxy.isEmpty()) envProxy = System.getenv("HTTP_PROXY");
            if (envProxy != null && !envProxy.isEmpty()) {
                try {
                    // http://host:port 또는 http://user:pw@host:port
                    String stripped = envProxy.replaceFirst("^https?://", "");
                    int atIdx = stripped.indexOf('@');
                    if (atIdx >= 0) stripped = stripped.substring(atIdx + 1);
                    int colon = stripped.indexOf(':');
                    if (colon > 0) {
                        proxyHost = stripped.substring(0, colon);
                        proxyPort = Integer.parseInt(stripped.substring(colon + 1).replaceAll("/.*$", ""));
                    }
                } catch (Exception ignored) {}
            }
        }
        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort != null && proxyPort > 0) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }

        this.httpClient = builder.build();
    }

    /**
     * 현재 설정된 Claude API 연결 상태를 반환한다 — 진단용.
     * 실제 Claude API 호출 없이 TCP/TLS 연결만 테스트.
     */
    public String diagnose() {
        StringBuilder sb = new StringBuilder();
        sb.append("baseUrl=").append(properties.getBaseUrl()).append('\n');
        sb.append("model=").append(getEffectiveModel()).append('\n');
        String key = getEffectiveApiKey();
        sb.append("apiKey=").append(key == null || key.isEmpty() ? "<none>" : (key.substring(0, Math.min(10, key.length())) + "...")).append('\n');
        sb.append("proxyHost=").append(properties.getProxyHost() == null ? "<none>" : properties.getProxyHost()).append('\n');
        sb.append("proxyPort=").append(properties.getProxyPort() == null ? "<none>" : properties.getProxyPort()).append('\n');
        sb.append("jdk.tls.client.protocols=").append(System.getProperty("jdk.tls.client.protocols", "<default>")).append('\n');
        sb.append("https.protocols=").append(System.getProperty("https.protocols", "<default>")).append('\n');

        // HEAD 요청으로 TCP+TLS 연결만 테스트
        try {
            Request req = new Request.Builder()
                    .url(properties.getBaseUrl() + "/v1/messages")
                    .header("x-api-key", key == null ? "" : key)
                    .header("anthropic-version", properties.getApiVersion())
                    .head()
                    .build();
            try (Response r = httpClient.newCall(req).execute()) {
                sb.append("probe=OK http=").append(r.code()).append('\n');
            }
        } catch (SSLHandshakeException ssl) {
            sb.append("probe=TLS_FAIL message=").append(ssl.getMessage()).append('\n');
            Throwable cause = ssl.getCause();
            if (cause != null) sb.append("cause=").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage()).append('\n');
        } catch (Exception e) {
            sb.append("probe=FAIL class=").append(e.getClass().getSimpleName()).append(" message=").append(e.getMessage()).append('\n');
            Throwable cause = e.getCause();
            if (cause != null) sb.append("cause=").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage()).append('\n');
        }
        return sb.toString();
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
            throw new ClaudeApiException(formatNetworkError(e), e);
        }
    }

    /**
     * 네트워크/TLS 오류를 사용자가 이해할 수 있는 메시지로 변환한다.
     */
    private String formatNetworkError(Throwable e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        String lower = msg.toLowerCase();
        if (lower.contains("handshake_failure") || lower.contains("handshake failure")) {
            return "Claude API 연결 실패 — TLS 핸드셰이크 오류. 사내망 프록시 또는 방화벽에서 api.anthropic.com 을 차단 중일 수 있습니다. " +
                   "Settings 화면에서 claude.proxy-host/port 를 설정하거나 HTTPS_PROXY 환경변수를 지정하세요. " +
                   "원본: " + msg;
        }
        if (lower.contains("unable to find valid certification path") || lower.contains("pkix")) {
            return "Claude API 연결 실패 — 인증서 검증 실패. 사내 TLS 가로채기 프록시의 루트 인증서를 컨테이너 truststore 에 추가해야 합니다. 원본: " + msg;
        }
        if (lower.contains("connection refused") || lower.contains("no route") || lower.contains("unreachable")) {
            return "Claude API 연결 실패 — 네트워크 경로 없음. 컨테이너에서 외부 인터넷으로 나가는 경로가 차단되어 있습니다. 원본: " + msg;
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "Claude API 연결 실패 — 타임아웃. 네트워크가 느리거나 프록시가 응답하지 않습니다. 원본: " + msg;
        }
        if (lower.contains("unknown host") || lower.contains("name or service not known")) {
            return "Claude API 연결 실패 — DNS 조회 실패. 컨테이너에서 api.anthropic.com 을 resolve 할 수 없습니다. 원본: " + msg;
        }
        return "Claude API 호출 실패 — " + msg;
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

        Response response;
        try {
            response = streamClient.newCall(httpRequest).execute();
        } catch (IOException e) {
            // TLS/네트워크 레벨 실패 — 사용자가 이해 가능한 메시지로 변환
            throw new ClaudeApiException(formatNetworkError(e), e);
        }

        try (Response r = response) {
            if (!r.isSuccessful() || r.body() == null) {
                String body = r.body() != null ? r.body().string() : "";
                throw new ClaudeApiException(
                        "Claude streaming API error: HTTP " + r.code() + " - " + body);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(r.body().byteStream(), StandardCharsets.UTF_8));
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
