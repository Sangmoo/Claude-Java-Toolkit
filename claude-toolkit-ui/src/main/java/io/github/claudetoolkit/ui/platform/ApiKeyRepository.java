package io.github.claudetoolkit.ui.platform;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * v4.7.x — #M3 Platform: API Key repository.
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /** 해시로 키 조회 — Filter 가 호출. 인덱스 있음. */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /** 발급자별 키 목록 (대장) */
    List<ApiKey> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /** 모든 키 (ADMIN 페이지) */
    List<ApiKey> findAllByOrderByCreatedAtDesc();

    /** lastUsedAt 갱신 — Filter 가 호출. throttle 위해 직접 update 쿼리 사용 (cache 안 거침) */
    @Modifying
    @Transactional
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :now, k.totalCalls = k.totalCalls + 1 WHERE k.id = :id")
    void touchUsage(@Param("id") Long id, @Param("now") LocalDateTime now);
}
