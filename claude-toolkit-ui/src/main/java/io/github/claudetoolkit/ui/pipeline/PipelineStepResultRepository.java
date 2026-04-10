package io.github.claudetoolkit.ui.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PipelineStepResultRepository extends JpaRepository<PipelineStepResult, Long> {

    List<PipelineStepResult> findByExecutionIdOrderByStepOrderAsc(Long executionId);

    @Modifying
    @Query("DELETE FROM PipelineStepResult r WHERE r.executionId = :executionId")
    void deleteByExecutionId(@Param("executionId") Long executionId);
}
