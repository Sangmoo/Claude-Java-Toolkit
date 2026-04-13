package io.github.claudetoolkit.ui.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineDefinitionRepository extends JpaRepository<PipelineDefinition, Long> {

    List<PipelineDefinition> findAllByOrderByCreatedAtDesc();

    List<PipelineDefinition> findByIsBuiltinOrderByCreatedAtAsc(boolean isBuiltin);

    List<PipelineDefinition> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    Optional<PipelineDefinition> findByName(String name);

    long countByIsBuiltin(boolean isBuiltin);

    /** v3.0: 스케줄 활성화된 파이프라인 조회 */
    List<PipelineDefinition> findByScheduleEnabledTrueAndScheduleCronIsNotNull();
}
