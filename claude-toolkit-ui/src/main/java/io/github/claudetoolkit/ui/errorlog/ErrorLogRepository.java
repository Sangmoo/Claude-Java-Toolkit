package io.github.claudetoolkit.ui.errorlog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    /** dedupeKey 로 기존 그룹 검색 (있으면 occurrence 증가, 없으면 신규 생성) */
    Optional<ErrorLog> findByDedupeKey(String dedupeKey);

    /** 최근 발생 순 — 미해결 우선 */
    @Query("SELECT e FROM ErrorLog e ORDER BY e.resolved ASC, e.lastOccurredAt DESC")
    List<ErrorLog> findRecent(Pageable pageable);

    /** 미해결 오류만 */
    @Query("SELECT e FROM ErrorLog e WHERE e.resolved = false ORDER BY e.lastOccurredAt DESC")
    List<ErrorLog> findUnresolved(Pageable pageable);

    /** 보존 정책 — N일 이전 + 해결됨 자동 삭제 */
    @Modifying
    @Transactional
    @Query("DELETE FROM ErrorLog e WHERE e.resolved = true AND e.lastOccurredAt < :cutoff")
    int deleteResolvedOlderThan(@Param("cutoff") LocalDateTime cutoff);

    long countByResolvedFalse();
}
