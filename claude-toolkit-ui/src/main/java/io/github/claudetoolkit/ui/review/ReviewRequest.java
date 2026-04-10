package io.github.claudetoolkit.ui.review;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 팀 코드 리뷰 요청 엔티티 (v2.9.0).
 *
 * <p>작성자(author)가 분석 이력({@link io.github.claudetoolkit.ui.history.ReviewHistory})에 대해
 * 리뷰어(reviewer)에게 승인을 요청하고, 리뷰어가 승인/반려하는 워크플로우를 관리합니다.
 *
 * <p>상태: PENDING → APPROVED 또는 REJECTED
 */
@Entity
@Table(name = "review_request", indexes = {
    @Index(name = "idx_reqreq_author",   columnList = "authorUsername"),
    @Index(name = "idx_reqreq_reviewer", columnList = "reviewerUsername"),
    @Index(name = "idx_reqreq_history",  columnList = "historyId"),
    @Index(name = "idx_reqreq_status",   columnList = "status")
})
public class ReviewRequest {

    public static final String STATUS_PENDING  = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연결된 분석 이력 ID */
    @Column(nullable = false)
    private Long historyId;

    /** 요청 작성자 username */
    @Column(nullable = false, length = 50)
    private String authorUsername;

    /** 리뷰어 username */
    @Column(nullable = false, length = 50)
    private String reviewerUsername;

    /** 상태: PENDING / APPROVED / REJECTED */
    @Column(nullable = false, length = 20)
    private String status;

    /** 요청자 메모 */
    @Column(columnDefinition = "TEXT")
    private String requestComment;

    /** 리뷰어 응답 코멘트 */
    @Column(columnDefinition = "TEXT")
    private String reviewComment;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 응답 시각 (승인/반려 시) */
    @Column
    private LocalDateTime respondedAt;

    protected ReviewRequest() {}

    public ReviewRequest(Long historyId, String authorUsername, String reviewerUsername,
                         String requestComment) {
        this.historyId        = historyId;
        this.authorUsername   = authorUsername;
        this.reviewerUsername = reviewerUsername;
        this.status           = STATUS_PENDING;
        this.requestComment   = requestComment;
        this.createdAt        = LocalDateTime.now();
    }

    // ── getters ──
    public Long          getId()                { return id; }
    public Long          getHistoryId()         { return historyId; }
    public String        getAuthorUsername()    { return authorUsername; }
    public String        getReviewerUsername()  { return reviewerUsername; }
    public String        getStatus()            { return status; }
    public String        getRequestComment()    { return requestComment; }
    public String        getReviewComment()     { return reviewComment; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public LocalDateTime getRespondedAt()       { return respondedAt; }

    // ── setters ──
    public void setStatus(String v)              { this.status = v; }
    public void setReviewComment(String v)       { this.reviewComment = v; }
    public void setRespondedAt(LocalDateTime v)  { this.respondedAt = v; }

    // ── helpers ──
    public boolean isPending()  { return STATUS_PENDING.equals(status); }
    public boolean isApproved() { return STATUS_APPROVED.equals(status); }
    public boolean isRejected() { return STATUS_REJECTED.equals(status); }

    public String getStatusLabel() {
        if (isApproved()) return "승인됨";
        if (isRejected()) return "반려됨";
        return "대기 중";
    }

    public String getStatusColor() {
        if (isApproved()) return "#10b981";
        if (isRejected()) return "#ef4444";
        return "#f59e0b";
    }

    public String getFormattedCreatedAt() {
        return createdAt == null ? "" : createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public String getFormattedRespondedAt() {
        return respondedAt == null ? "-" : respondedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
