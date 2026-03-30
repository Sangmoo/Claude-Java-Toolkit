package io.github.claudetoolkit.ui.favorites;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/**
 * Spring Data JPA repository for favorites persistence.
 * Data is stored in H2 file database at ~/.claude-toolkit/history-db.
 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /** Most recent entries first, limited by Pageable */
    @Query("SELECT f FROM Favorite f ORDER BY f.createdAt DESC")
    List<Favorite> findRecentEntries(Pageable pageable);

    /** Entries matching the given tag substring, most recent first */
    @Query("SELECT f FROM Favorite f WHERE f.tag LIKE %:tag% ORDER BY f.createdAt DESC")
    List<Favorite> findByTagContaining(String tag, Pageable pageable);

    /** Oldest entry (for trimming when MAX_FAVORITES is exceeded) */
    Favorite findTopByOrderByCreatedAtAsc();
}
