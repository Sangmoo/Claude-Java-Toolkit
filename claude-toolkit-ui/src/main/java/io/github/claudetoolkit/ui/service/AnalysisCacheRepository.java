package io.github.claudetoolkit.ui.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AnalysisCacheRepository extends JpaRepository<AnalysisCache, Long> {

    Optional<AnalysisCache> findByCacheKey(String cacheKey);

    long countByExpiresAtBefore(LocalDateTime time);

    @Modifying
    @Query("DELETE FROM AnalysisCache c WHERE c.expiresAt < :time")
    int deleteExpired(@Param("time") LocalDateTime time);
}
