package io.github.claudetoolkit.ui.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineExecutionRepository extends JpaRepository<PipelineExecution, Long> {

    List<PipelineExecution> findByUsernameOrderByStartedAtDesc(String username);

    List<PipelineExecution> findTop20ByUsernameOrderByStartedAtDesc(String username);

    long countByPipelineId(Long pipelineId);

    long countByUsernameAndStatus(String username, String status);
}
