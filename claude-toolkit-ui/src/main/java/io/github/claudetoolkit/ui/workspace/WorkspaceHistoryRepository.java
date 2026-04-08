package io.github.claudetoolkit.ui.workspace;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkspaceHistoryRepository extends JpaRepository<WorkspaceHistory, Long> {

    /** 최근 N건 조회 (createdAt DESC) */
    List<WorkspaceHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
