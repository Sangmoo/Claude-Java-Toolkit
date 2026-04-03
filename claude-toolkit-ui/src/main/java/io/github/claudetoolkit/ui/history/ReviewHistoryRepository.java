package io.github.claudetoolkit.ui.history;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/**
 * Spring Data JPA repository for review history persistence.
 * Data is stored in H2 file database at ~/.claude-toolkit/history-db.
 */
public interface ReviewHistoryRepository extends JpaRepository<ReviewHistory, Long> {

    /** Most recent entries first, limited by Pageable */
    @Query("SELECT h FROM ReviewHistory h ORDER BY h.createdAt DESC")
    List<ReviewHistory> findRecentEntries(Pageable pageable);

    /** Oldest entry (for trimming when MAX_HISTORY is exceeded) */
    ReviewHistory findTopByOrderByCreatedAtAsc();

    /** All EXPLAIN_PLAN entries ordered by time (for dashboard chart) */
    @Query("SELECT h FROM ReviewHistory h WHERE h.type = :type ORDER BY h.createdAt ASC")
    List<ReviewHistory> findByTypeOrderByCreatedAtAsc(@org.springframework.data.repository.query.Param("type") String type);

    /** Entries that have token usage recorded */
    @Query("SELECT h FROM ReviewHistory h WHERE h.inputTokens IS NOT NULL OR h.outputTokens IS NOT NULL ORDER BY h.createdAt DESC")
    List<ReviewHistory> findWithTokenUsage();

    /** Full-text search across title and inputContent */
    @Query("SELECT h FROM ReviewHistory h WHERE LOWER(h.title) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(h.inputContent) LIKE LOWER(CONCAT('%',:q,'%')) ORDER BY h.createdAt DESC")
    List<ReviewHistory> searchByKeyword(@org.springframework.data.repository.query.Param("q") String q, Pageable pageable);
}
