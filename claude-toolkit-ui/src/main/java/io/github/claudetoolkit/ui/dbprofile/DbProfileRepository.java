package io.github.claudetoolkit.ui.dbprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface DbProfileRepository extends JpaRepository<DbProfile, Long> {

    @Query("SELECT p FROM DbProfile p ORDER BY p.createdAt DESC")
    List<DbProfile> findAllByOrderByCreatedAtDesc();
}
