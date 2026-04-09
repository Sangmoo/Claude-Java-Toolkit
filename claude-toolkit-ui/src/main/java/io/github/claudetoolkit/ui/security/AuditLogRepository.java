package io.github.claudetoolkit.ui.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** 최신 N건 조회 (하위 호환) */
    List<AuditLog> findTop300ByOrderByCreatedAtDesc();

    /** 페이지네이션 조회 */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 사용자별 + 기간별 페이지네이션 */
    @Query("SELECT a FROM AuditLog a WHERE "
         + "(:username IS NULL OR a.username = :username) AND "
         + "(:since IS NULL OR a.createdAt >= :since) "
         + "ORDER BY a.createdAt DESC")
    Page<AuditLog> findFiltered(@Param("username") String username,
                                @Param("since") LocalDateTime since,
                                Pageable pageable);

    /** 사용자별 활동 요약 */
    @Query("SELECT a.username, COUNT(a) FROM AuditLog a WHERE a.username IS NOT NULL "
         + "GROUP BY a.username ORDER BY COUNT(a) DESC")
    List<Object[]> countByUsername();

    /** 특정 시점 이후 건수 */
    long countByCreatedAtAfter(LocalDateTime since);

    /** 엔드포인트별 호출 횟수 (상위 10개) */
    @Query("SELECT a.endpoint, COUNT(a) FROM AuditLog a GROUP BY a.endpoint ORDER BY COUNT(a) DESC")
    List<Object[]> countByEndpoint();

    /** 전체 건수 */
    long count();

    /** 오래된 로그 삭제 (90일 이전) */
    void deleteByCreatedAtBefore(LocalDateTime before);
}
