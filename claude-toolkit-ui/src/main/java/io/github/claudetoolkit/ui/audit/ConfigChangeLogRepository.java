package io.github.claudetoolkit.ui.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ConfigChangeLogRepository extends JpaRepository<ConfigChangeLog, Long> {

    /**
     * 필터 조건별 페이지네이션 조회.
     * @param category null 이면 전체
     * @param user     null 이면 전체
     * @param since    null 이면 전체 기간
     */
    @Query("SELECT c FROM ConfigChangeLog c WHERE "
         + "(:category IS NULL OR c.category = :category) AND "
         + "(:user IS NULL OR c.changedBy = :user) AND "
         + "(:since IS NULL OR c.changedAt >= :since) AND "
         + "(:until IS NULL OR c.changedAt <= :until) "
         + "ORDER BY c.changedAt DESC")
    Page<ConfigChangeLog> findFiltered(@Param("category") String category,
                                        @Param("user") String user,
                                        @Param("since") LocalDateTime since,
                                        @Param("until") LocalDateTime until,
                                        Pageable pageable);

    /** 변경자별 그룹 카운트 (대시보드/필터 dropdown 용) */
    @Query("SELECT c.changedBy, COUNT(c) FROM ConfigChangeLog c GROUP BY c.changedBy ORDER BY COUNT(c) DESC")
    List<Object[]> countByUser();

    /** 카테고리별 그룹 카운트 */
    @Query("SELECT c.category, COUNT(c) FROM ConfigChangeLog c GROUP BY c.category ORDER BY COUNT(c) DESC")
    List<Object[]> countByCategory();

    /** 보존 정책 — 매우 오래된 (예: 5년+) 로그 삭제 시 사용 (수동 호출, 자동 X) */
    long deleteByChangedAtBefore(LocalDateTime cutoff);
}
