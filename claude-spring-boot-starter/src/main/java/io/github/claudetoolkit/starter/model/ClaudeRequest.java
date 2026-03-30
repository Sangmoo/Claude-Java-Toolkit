package io.github.claudetoolkit.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request payload for Anthropic Messages API.
 * Supports both standard (non-streaming) and streaming ({@code stream: true}) modes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeRequest {

    private String model;

    @JsonProperty("max_tokens")
    private int maxTokens;

    private String system;

    private List<ClaudeMessage> messages;

    /** When {@code true}, the API returns a Server-Sent Events stream. */
    private Boolean stream;

    private ClaudeRequest() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ClaudeRequest request = new ClaudeRequest();

        public Builder model(String model)                    { request.model      = model;      return this; }
        public Builder maxTokens(int maxTokens)               { request.maxTokens  = maxTokens;  return this; }
        public Builder system(String system)                  { request.system     = system;     return this; }
        public Builder messages(List<ClaudeMessage> messages) { request.messages   = messages;   return this; }
        public Builder stream(boolean stream)                 { request.stream     = stream;     return this; }

        public ClaudeRequest build() {
            if (request.model == null)
                throw new IllegalStateException("model is required");
            if (request.messages == null || request.messages.isEmpty())
                throw new IllegalStateException("messages is required");
            return request;
        }
    }

    public String              getModel()      { return model; }
    public int                 getMaxTokens()  { return maxTokens; }
    public String              getSystem()     { return system; }
    public List<ClaudeMessage> getMessages()   { return messages; }
    public Boolean             getStream()     { return stream; }
}
