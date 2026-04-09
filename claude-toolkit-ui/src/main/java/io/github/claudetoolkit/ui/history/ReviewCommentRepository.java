package io.github.claudetoolkit.ui.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    List<ReviewComment> findByHistoryIdOrderByCreatedAtAsc(long historyId);

    long countByHistoryId(long historyId);

    void deleteByHistoryId(long historyId);
}
