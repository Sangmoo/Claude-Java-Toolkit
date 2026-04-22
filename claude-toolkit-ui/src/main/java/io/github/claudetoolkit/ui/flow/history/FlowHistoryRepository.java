package io.github.claudetoolkit.ui.flow.history;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Flow Analysis 이력 JPA 레포지토리. */
public interface FlowHistoryRepository extends JpaRepository<FlowHistory, Long> {

    List<FlowHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<FlowHistory> findByIdAndUserId(Long id, String userId);

    long countByUserId(String userId);

    /** 사용자별 보관 상한 초과분 삭제 (기본 50개) */
    @Modifying
    @Transactional
    @Query("DELETE FROM FlowHistory h WHERE h.userId = :userId AND h.id IN ("
         + "  SELECT h2.id FROM FlowHistory h2 WHERE h2.userId = :userId "
         + "  ORDER BY h2.createdAt DESC"
         + ")")
    void pruneExcess(@Param("userId") String userId);
    // 위 JPA HQL 은 OFFSET 미지원이라 실제 prune 은 service 에서 따로 처리.

    /** 30일 이상 된 이력 일괄 삭제 (스케줄러용) */
    @Modifying
    @Transactional
    @Query("DELETE FROM FlowHistory h WHERE h.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
