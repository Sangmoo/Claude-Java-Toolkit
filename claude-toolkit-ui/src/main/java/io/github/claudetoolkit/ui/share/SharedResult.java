package io.github.claudetoolkit.ui.share;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shared_result")
public class SharedResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String inputContent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String outputContent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public SharedResult() {}

    public SharedResult(Long historyId, String type, String title, String inputContent, String outputContent) {
        this.token         = UUID.randomUUID().toString();
        this.type          = type;
        this.title         = title;
        this.inputContent  = inputContent;
        this.outputContent = outputContent;
        this.createdAt     = LocalDateTime.now();
        this.expiresAt     = LocalDateTime.now().plusDays(7);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public String getRemainingDaysText() {
        if (isExpired()) return "만료됨";
        long days = java.time.Duration.between(LocalDateTime.now(), expiresAt).toDays();
        return days + "일 후 만료";
    }

    public String getFormattedCreatedAt() {
        return createdAt != null ? createdAt.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "";
    }

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInputContent() { return inputContent; }
    public void setInputContent(String inputContent) { this.inputContent = inputContent; }
    public String getOutputContent() { return outputContent; }
    public void setOutputContent(String outputContent) { this.outputContent = outputContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
