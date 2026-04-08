package io.github.claudetoolkit.ui.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** 최신 N건 조회 */
    List<AuditLog> findTop300ByOrderByCreatedAtDesc();

    /** 특정 시점 이후 건수 */
    long countByCreatedAtAfter(LocalDateTime since);

    /** 엔드포인트별 호출 횟수 (상위 10개) */
    @Query("SELECT a.endpoint, COUNT(a) FROM AuditLog a GROUP BY a.endpoint ORDER BY COUNT(a) DESC")
    List<Object[]> countByEndpoint();

    /** 오래된 로그 삭제 (90일 이전) */
    void deleteByCreatedAtBefore(LocalDateTime before);
}
