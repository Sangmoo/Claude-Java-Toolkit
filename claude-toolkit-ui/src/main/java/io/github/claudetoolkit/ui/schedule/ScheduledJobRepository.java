package io.github.claudetoolkit.ui.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {
    List<ScheduledJob> findAllByOrderByCreatedAtDesc();
    List<ScheduledJob> findByEnabledTrue();
}
