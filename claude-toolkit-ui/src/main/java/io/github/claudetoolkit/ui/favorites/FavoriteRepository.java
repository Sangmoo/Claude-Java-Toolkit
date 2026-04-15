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

    /** Entries of a specific type, most recent first */
    @Query("SELECT f FROM Favorite f WHERE f.type = :type ORDER BY f.createdAt DESC")
    List<Favorite> findByType(@org.springframework.data.repository.query.Param("type") String type, Pageable pageable);

    /** Full-text search across title and inputContent */
    @Query("SELECT f FROM Favorite f WHERE LOWER(f.title) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(f.inputContent) LIKE LOWER(CONCAT('%',:q,'%')) ORDER BY f.createdAt DESC")
    List<Favorite> searchByKeyword(@org.springframework.data.repository.query.Param("q") String q, Pageable pageable);

    /** Oldest entry (for trimming when MAX_FAVORITES is exceeded) */
    Favorite findTopByOrderByCreatedAtAsc();

    /**
     * v4.2.7 — 같은 사용자 + 같은 이력에 대한 기존 즐겨찾기 조회.
     * /favorites/star 호출시 중복 저장을 막기 위해 사용.
     */
    @Query("SELECT f FROM Favorite f WHERE f.username = :username AND f.historyId = :historyId")
    java.util.Optional<Favorite> findByUsernameAndHistoryId(
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("historyId") Long historyId);
}
