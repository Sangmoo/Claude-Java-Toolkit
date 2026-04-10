package io.github.claudetoolkit.ui.chat;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * AI 채팅 메시지 엔티티 (v2.7.0).
 *
 * <p>{@link ChatSession}에 소속되며 user/assistant 역할별로 저장됩니다.
 */
@Entity
@Table(name = "chat_message", indexes = {
    @Index(name = "idx_chatmsg_session", columnList = "sessionId,createdAt")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 세션 ID (FK — ChatSession.id) */
    @Column(nullable = false)
    private Long sessionId;

    /** 'user' 또는 'assistant' */
    @Column(nullable = false, length = 20)
    private String role;

    /** 메시지 내용 (TEXT 타입) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {}

    public ChatMessage(Long sessionId, String role, String content) {
        this.sessionId = sessionId;
        this.role      = role;
        this.content   = content;
        this.createdAt = LocalDateTime.now();
    }

    // ── getters ──
    public Long          getId()        { return id; }
    public Long          getSessionId() { return sessionId; }
    public String        getRole()      { return role; }
    public String        getContent()   { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
