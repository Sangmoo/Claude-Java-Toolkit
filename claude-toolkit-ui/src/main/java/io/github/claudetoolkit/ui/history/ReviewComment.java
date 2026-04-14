package io.github.claudetoolkit.ui.history;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 분석 이력에 대한 댓글 엔티티.
 *
 * <p>v4.2.x — 대댓글 지원을 위해 parentId 컬럼 추가.
 * parentId 가 null 이면 최상위 댓글, 값이 있으면 해당 댓글에 대한 대댓글.
 */
@Entity
@Table(name = "review_comment", indexes = {
    @Index(name = "idx_comment_history", columnList = "historyId"),
    @Index(name = "idx_comment_parent",  columnList = "parentId")
})
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /** 연결된 ReviewHistory ID */
    @Column(nullable = false)
    private long historyId;

    /** 부모 댓글 ID — null 이면 최상위 댓글, 값이 있으면 대댓글 (v4.2.x) */
    @Column
    private Long parentId;

    /** 작성자 username */
    @Column(nullable = false, length = 50)
    private String username;

    /** 댓글 내용 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ReviewComment() {}

    public ReviewComment(long historyId, String username, String content) {
        this.historyId = historyId;
        this.username  = username;
        this.content   = content;
        this.createdAt = LocalDateTime.now();
    }

    public ReviewComment(long historyId, Long parentId, String username, String content) {
        this.historyId = historyId;
        this.parentId  = parentId;
        this.username  = username;
        this.content   = content;
        this.createdAt = LocalDateTime.now();
    }

    public long          getId()         { return id; }
    public long          getHistoryId()  { return historyId; }
    public Long          getParentId()   { return parentId; }
    public String        getUsername()   { return username; }
    public String        getContent()    { return content; }
    public LocalDateTime getCreatedAt()  { return createdAt; }

    public String getFormattedDate() {
        return createdAt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
}
