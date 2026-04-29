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

    /**
     * v4.7.x — 콤마 구분 tags 컬럼에서 특정 태그가 들어있는 이력 조회.
     *
     * <p>콤마 구분 문자열을 LIKE 로 검색하므로 부분 일치 (예: "DB" 가 "DBA" 도 매칭) 를
     * 피하려면 호출부에서 패턴을 {@code ",tag,"} 형태로 감싼 뒤 컬럼 양 끝에도 콤마를
     * 붙여서 비교해야 한다. 여기선 단순 LIKE 만 제공하고 정확 매칭은 서비스 레이어가 책임.
     */
    @Query("SELECT h FROM ReviewHistory h WHERE h.username = :u " +
           "AND LOWER(CONCAT(',', COALESCE(h.tags, ''), ',')) LIKE LOWER(CONCAT('%,', :tag, ',%')) " +
           "ORDER BY h.createdAt DESC")
    List<ReviewHistory> findByUsernameAndTag(
            @org.springframework.data.repository.query.Param("u") String username,
            @org.springframework.data.repository.query.Param("tag") String tag,
            Pageable pageable);

    /** v4.7.x — 사용자가 가진 모든 이력의 tags 컬럼 (집계용). null 제외. */
    @Query("SELECT h.tags FROM ReviewHistory h WHERE h.username = :u AND h.tags IS NOT NULL AND h.tags <> ''")
    List<String> findAllTagsByUsername(@org.springframework.data.repository.query.Param("u") String username);
}
