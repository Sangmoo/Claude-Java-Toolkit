package io.github.claudetoolkit.starter.model;

/**
 * A single message in a Claude conversation.
 */
public class ClaudeMessage {

    private String role;
    private String content;

    public ClaudeMessage() {}

    public ClaudeMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ClaudeMessage ofUser(String content) {
        return new ClaudeMessage("user", content);
    }

    public static ClaudeMessage ofAssistant(String content) {
        return new ClaudeMessage("assistant", content);
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
