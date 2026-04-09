package io.github.claudetoolkit.ui.notification;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 알림 엔티티.
 *
 * <p>타입: COMMENT (댓글), BATCH (배치 완료), SCHEDULE (스케줄 완료), SYSTEM (시스템)
 */
@Entity
@Table(name = "notification", indexes = {
    @Index(name = "idx_noti_recipient", columnList = "recipientUsername,isRead,createdAt")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /** 수신자 username */
    @Column(nullable = false, length = 50)
    private String recipientUsername;

    /** 알림 유형 (COMMENT, BATCH, SCHEDULE, SYSTEM) */
    @Column(nullable = false, length = 30)
    private String type;

    /** 알림 제목 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 알림 상세 내용 */
    @Column(columnDefinition = "TEXT")
    private String message;

    /** 클릭 시 이동할 링크 */
    @Column(length = 300)
    private String link;

    /** 읽음 여부 */
    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Notification() {}

    public Notification(String recipientUsername, String type, String title, String message, String link) {
        this.recipientUsername = recipientUsername;
        this.type      = type;
        this.title     = title;
        this.message   = message;
        this.link      = link;
        this.isRead    = false;
        this.createdAt = LocalDateTime.now();
    }

    public long getId()                     { return id; }
    public String getRecipientUsername()    { return recipientUsername; }
    public String getType()                { return type; }
    public String getTitle()               { return title; }
    public String getMessage()             { return message; }
    public String getLink()                { return link; }
    public boolean isRead()                { return isRead; }
    public LocalDateTime getCreatedAt()    { return createdAt; }

    public void setRead(boolean read)       { this.isRead = read; }

    public String getFormattedDate() {
        return createdAt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    public String getTypeIcon() {
        if ("COMMENT".equals(type)) return "fa-comment";
        if ("BATCH".equals(type))   return "fa-tasks";
        if ("SCHEDULE".equals(type)) return "fa-clock";
        return "fa-bell";
    }
}
