package io.github.claudetoolkit.ui.share;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SharedResultRepository extends JpaRepository<SharedResult, Long> {
    Optional<SharedResult> findByToken(String token);

    @Query("SELECT s FROM SharedResult s WHERE s.expiresAt < :now")
    List<SharedResult> findExpired(@org.springframework.data.repository.query.Param("now") LocalDateTime now);
}
