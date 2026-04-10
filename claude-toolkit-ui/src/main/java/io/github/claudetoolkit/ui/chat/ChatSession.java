package io.github.claudetoolkit.ui.chat;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * AI 채팅 세션 엔티티 (v2.7.0).
 *
 * <p>사용자별로 여러 대화를 저장하고 서버 재시작 후에도 유지되도록
 * H2 테이블에 영속화합니다.
 */
@Entity
@Table(name = "chat_session", indexes = {
    @Index(name = "idx_chatsess_user_updated", columnList = "username,updatedAt")
})
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 세션 소유자 (사용자명) */
    @Column(nullable = false, length = 50)
    private String username;

    /** 대화 제목 (첫 메시지로부터 자동 생성 또는 사용자 편집) */
    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ChatSession() {}

    public ChatSession(String username, String title) {
        this.username  = username;
        this.title     = title;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // ── getters / setters ──
    public Long          getId()        { return id; }
    public String        getUsername()  { return username; }
    public String        getTitle()     { return title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setTitle(String title)          { this.title = title; }
    public void setUpdatedAt(LocalDateTime t)   { this.updatedAt = t; }
    public void touch()                         { this.updatedAt = LocalDateTime.now(); }
}
