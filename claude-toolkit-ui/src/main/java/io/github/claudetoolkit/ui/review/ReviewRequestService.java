package io.github.claudetoolkit.ui.review;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import io.github.claudetoolkit.ui.notification.Notification;
import io.github.claudetoolkit.ui.notification.NotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 팀 코드 리뷰 요청 서비스 (v2.9.0).
 *
 * <p>요청 생성, 승인/반려, 조회, 삭제와 {@link NotificationPublisher}를 통한 실시간 알림 전송.
 */
@Service
public class ReviewRequestService {

    private static final Logger log = LoggerFactory.getLogger(ReviewRequestService.class);

    private final ReviewRequestRepository  repository;
    private final ReviewHistoryRepository  historyRepository;
    private final NotificationPublisher    notificationPublisher;

    public ReviewRequestService(ReviewRequestRepository repository,
                                ReviewHistoryRepository historyRepository,
                                NotificationPublisher notificationPublisher) {
        this.repository            = repository;
        this.historyRepository     = historyRepository;
        this.notificationPublisher = notificationPublisher;
    }

    // ── 조회 ──────────────────────────────────────────────────────────────

    public List<ReviewRequest> findRequestedByMe(String username) {
        return repository.findByAuthorUsernameOrderByCreatedAtDesc(username);
    }

    public List<ReviewRequest> findAssignedToMe(String username) {
        return repository.findByReviewerUsernameOrderByCreatedAtDesc(username);
    }

    public ReviewRequest findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public long countPendingForReviewer(String reviewerUsername) {
        return repository.countByReviewerUsernameAndStatus(reviewerUsername, ReviewRequest.STATUS_PENDING);
    }

    /** 특정 사용자가 요청(author)이거나 리뷰어(reviewer)인지 확인 */
    public boolean canAccess(ReviewRequest req, String username, boolean isAdmin) {
        if (req == null || username == null) return false;
        if (isAdmin) return true;
        return username.equals(req.getAuthorUsername())
                || username.equals(req.getReviewerUsername());
    }

    // ── 생성 ──────────────────────────────────────────────────────────────

    /**
     * 신규 리뷰 요청 생성 + 리뷰어에게 알림 발송.
     */
    @Transactional
    public ReviewRequest create(Long historyId, String authorUsername, String reviewerUsername,
                                String comment) {
        ReviewHistory history = historyRepository.findById(historyId).orElse(null);
        if (history == null) {
            throw new IllegalArgumentException("분석 이력을 찾을 수 없습니다: " + historyId);
        }
        if (reviewerUsername == null || reviewerUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("리뷰어를 선택해주세요.");
        }
        if (reviewerUsername.equals(authorUsername)) {
            throw new IllegalArgumentException("본인에게 리뷰 요청할 수 없습니다.");
        }

        ReviewRequest req = new ReviewRequest(historyId, authorUsername, reviewerUsername, comment);
        ReviewRequest saved = repository.save(req);

        // 리뷰어에게 SSE 실시간 알림
        try {
            String title = authorUsername + "님이 리뷰를 요청했습니다";
            String msg   = history.getTypeLabel() + " · " + history.getTitle();
            if (msg.length() > 150) msg = msg.substring(0, 150) + "...";
            Notification noti = new Notification(
                reviewerUsername,
                "COMMENT",  // 기존 타입 재사용
                title,
                msg,
                "/review-requests/" + saved.getId()
            );
            notificationPublisher.publish(noti);
        } catch (Exception e) {
            log.warn("[ReviewRequest] 알림 발송 실패: {}", e.getMessage());
        }

        return saved;
    }

    // ── 승인 ──────────────────────────────────────────────────────────────

    @Transactional
    public ReviewRequest approve(Long id, String reviewerUsername, String comment) {
        ReviewRequest req = getAndVerifyReviewer(id, reviewerUsername);
        if (!req.isPending()) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }
        req.setStatus(ReviewRequest.STATUS_APPROVED);
        req.setReviewComment(comment);
        req.setRespondedAt(LocalDateTime.now());
        ReviewRequest saved = repository.save(req);

        // 요청자에게 알림
        notifyAuthor(saved, "승인", "🟢");
        return saved;
    }

    // ── 반려 ──────────────────────────────────────────────────────────────

    @Transactional
    public ReviewRequest reject(Long id, String reviewerUsername, String comment) {
        ReviewRequest req = getAndVerifyReviewer(id, reviewerUsername);
        if (!req.isPending()) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }
        if (comment == null || comment.trim().isEmpty()) {
            throw new IllegalArgumentException("반려 시 사유를 입력해주세요.");
        }
        req.setStatus(ReviewRequest.STATUS_REJECTED);
        req.setReviewComment(comment);
        req.setRespondedAt(LocalDateTime.now());
        ReviewRequest saved = repository.save(req);

        // 요청자에게 알림
        notifyAuthor(saved, "반려", "🔴");
        return saved;
    }

    // ── 삭제 ──────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id, String username, boolean isAdmin) {
        ReviewRequest req = repository.findById(id).orElse(null);
        if (req == null) return;
        if (!isAdmin && !username.equals(req.getAuthorUsername())) {
            throw new IllegalStateException("본인이 요청한 건만 취소할 수 있습니다.");
        }
        if (!isAdmin && !req.isPending()) {
            throw new IllegalStateException("이미 처리된 요청은 취소할 수 없습니다.");
        }
        repository.delete(req);
    }

    // ── 이력별 요청 조회 ──────────────────────────────────────────────────

    public List<ReviewRequest> findByHistoryId(Long historyId) {
        return repository.findByHistoryIdOrderByCreatedAtDesc(historyId);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────

    private ReviewRequest getAndVerifyReviewer(Long id, String reviewerUsername) {
        ReviewRequest req = repository.findById(id).orElse(null);
        if (req == null) {
            throw new IllegalArgumentException("요청을 찾을 수 없습니다.");
        }
        if (!reviewerUsername.equals(req.getReviewerUsername())) {
            throw new IllegalStateException("지정된 리뷰어만 응답할 수 있습니다.");
        }
        return req;
    }

    private void notifyAuthor(ReviewRequest req, String action, String emoji) {
        try {
            String title = emoji + " " + req.getReviewerUsername() + "님이 리뷰를 " + action + "했습니다";
            String msg = req.getReviewComment() != null && !req.getReviewComment().isEmpty()
                    ? (req.getReviewComment().length() > 120
                        ? req.getReviewComment().substring(0, 120) + "..."
                        : req.getReviewComment())
                    : "코멘트 없음";
            Notification noti = new Notification(
                req.getAuthorUsername(),
                "COMMENT",
                title,
                msg,
                "/review-requests/" + req.getId()
            );
            notificationPublisher.publish(noti);
        } catch (Exception e) {
            log.warn("[ReviewRequest] 응답 알림 실패: {}", e.getMessage());
        }
    }
}
