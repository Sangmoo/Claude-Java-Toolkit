package io.github.claudetoolkit.ui.pipeline;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 파이프라인 단계별 실행 결과 (v2.9.5).
 */
@Entity
@Table(name = "pipeline_step_result", indexes = {
    @Index(name = "idx_pipestep_exec", columnList = "executionId,stepOrder")
})
public class PipelineStepResult {

    public static final String STATUS_PENDING   = "PENDING";
    public static final String STATUS_RUNNING   = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED    = "FAILED";
    public static final String STATUS_SKIPPED   = "SKIPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long executionId;

    @Column(nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 50)
    private String stepId;

    @Column(nullable = false, length = 50)
    private String analysisType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String inputContent;

    @Column(columnDefinition = "TEXT")
    private String outputContent;

    @Column(length = 200)
    private String skipReason;

    @Column(length = 500)
    private String errorMessage;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private Long durationMs;

    protected PipelineStepResult() {}

    public PipelineStepResult(Long executionId, int order, String stepId, String analysisType) {
        this.executionId  = executionId;
        this.stepOrder    = order;
        this.stepId       = stepId;
        this.analysisType = analysisType;
        this.status       = STATUS_PENDING;
    }

    public void markRunning() {
        this.status    = STATUS_RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void markCompleted(String output) {
        this.status        = STATUS_COMPLETED;
        this.outputContent = output;
        this.completedAt   = LocalDateTime.now();
        if (startedAt != null) {
            this.durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    /** v4.2.x: 스트리밍 도중 진행적 저장 — 상태는 RUNNING 유지 */
    public void setOutputContent(String content) {
        this.outputContent = content;
    }

    public void markFailed(String errorMessage) {
        this.status       = STATUS_FAILED;
        this.errorMessage = errorMessage != null
                ? (errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage)
                : null;
        this.completedAt  = LocalDateTime.now();
        if (startedAt != null) {
            this.durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    public void markSkipped(String reason) {
        this.status       = STATUS_SKIPPED;
        this.skipReason   = reason != null && reason.length() > 200 ? reason.substring(0, 200) : reason;
        this.completedAt  = LocalDateTime.now();
    }

    public void setInputContent(String input) { this.inputContent = input; }

    // ── getters ──
    public Long          getId()             { return id; }
    public Long          getExecutionId()    { return executionId; }
    public int           getStepOrder()      { return stepOrder; }
    public String        getStepId()         { return stepId; }
    public String        getAnalysisType()   { return analysisType; }
    public String        getStatus()         { return status; }
    public String        getInputContent()   { return inputContent; }
    public String        getOutputContent()  { return outputContent; }
    public String        getSkipReason()     { return skipReason; }
    public String        getErrorMessage()   { return errorMessage; }
    public LocalDateTime getStartedAt()      { return startedAt; }
    public LocalDateTime getCompletedAt()    { return completedAt; }
    public Long          getDurationMs()     { return durationMs; }
}
