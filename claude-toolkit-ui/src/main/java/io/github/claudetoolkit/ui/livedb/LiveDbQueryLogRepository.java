package io.github.claudetoolkit.ui.livedb;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LiveDbQueryLogRepository extends JpaRepository<LiveDbQueryLog, Long> {

    @Query("SELECT l FROM LiveDbQueryLog l ORDER BY l.executedAt DESC")
    List<LiveDbQueryLog> findRecent(Pageable pageable);

    @Query("SELECT l FROM LiveDbQueryLog l WHERE l.profileId = :profileId ORDER BY l.executedAt DESC")
    List<LiveDbQueryLog> findRecentByProfile(Long profileId, Pageable pageable);
}
