package io.github.claudetoolkit.ui.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRequestRepository extends JpaRepository<ReviewRequest, Long> {

    List<ReviewRequest> findByAuthorUsernameOrderByCreatedAtDesc(String authorUsername);

    List<ReviewRequest> findByReviewerUsernameOrderByCreatedAtDesc(String reviewerUsername);

    List<ReviewRequest> findByAuthorUsernameAndStatusOrderByCreatedAtDesc(String authorUsername, String status);

    List<ReviewRequest> findByReviewerUsernameAndStatusOrderByCreatedAtDesc(String reviewerUsername, String status);

    List<ReviewRequest> findByHistoryIdOrderByCreatedAtDesc(Long historyId);

    long countByReviewerUsernameAndStatus(String reviewerUsername, String status);

    long countByAuthorUsernameAndStatus(String authorUsername, String status);
}
