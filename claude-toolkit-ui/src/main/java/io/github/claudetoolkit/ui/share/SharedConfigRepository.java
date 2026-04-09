package io.github.claudetoolkit.ui.share;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SharedConfigRepository extends JpaRepository<SharedConfig, Long> {

    List<SharedConfig> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    List<SharedConfig> findByIsPublicTrueOrderByCreatedAtDesc();
}
