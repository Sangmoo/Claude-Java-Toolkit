package io.github.claudetoolkit.ui.harness;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchHistoryRepository extends JpaRepository<BatchHistory, Long> {

    @Query("SELECT b FROM BatchHistory b ORDER BY b.startedAt DESC")
    List<BatchHistory> findRecentBatches(Pageable pageable);

    Optional<BatchHistory> findByBatchUuid(String batchUuid);
}
