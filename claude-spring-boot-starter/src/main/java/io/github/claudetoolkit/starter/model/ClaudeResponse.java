package io.github.claudetoolkit.starter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from Anthropic Messages API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaudeResponse {

    private String id;
    private String type;
    private String role;
    private List<ContentBlock> content;
    private String model;

    @JsonProperty("stop_reason")
    private String stopReason;

    private Usage usage;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("input_tokens")
        private int inputTokens;
        @JsonProperty("output_tokens")
        private int outputTokens;

        public int getInputTokens()          { return inputTokens; }
        public void setInputTokens(int t)    { this.inputTokens = t; }
        public int getOutputTokens()         { return outputTokens; }
        public void setOutputTokens(int t)   { this.outputTokens = t; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentBlock {
        private String type;
        private String text;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    /**
     * Returns the first text content block. Most use cases only need this.
     */
    public String getFirstTextContent() {
        if (content == null || content.isEmpty()) return "";
        return content.stream()
                .filter(b -> "text".equals(b.getType()))
                .map(ContentBlock::getText)
                .findFirst()
                .orElse("");
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public List<ContentBlock> getContent() { return content; }
    public void setContent(List<ContentBlock> content) { this.content = content; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getStopReason() { return stopReason; }
    public void setStopReason(String stopReason) { this.stopReason = stopReason; }
    public Usage getUsage()       { return usage; }
    public void setUsage(Usage usage) { this.usage = usage; }
}
