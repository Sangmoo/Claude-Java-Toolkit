package io.github.claudetoolkit.ui.migration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DbMigrationJobRepository extends JpaRepository<DbMigrationJob, Long> {

    List<DbMigrationJob> findAllByOrderByStartedAtDesc();
}
