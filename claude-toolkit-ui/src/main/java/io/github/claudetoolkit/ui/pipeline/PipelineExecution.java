package io.github.claudetoolkit.ui.pipeline;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 파이프라인 실행 이력 (v2.9.5).
 */
@Entity
@Table(name = "pipeline_execution", indexes = {
    @Index(name = "idx_pipeexec_user",     columnList = "username,startedAt"),
    @Index(name = "idx_pipeexec_pipeline", columnList = "pipelineId")
})
public class PipelineExecution {

    public static final String STATUS_RUNNING   = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED    = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pipelineId;

    @Column(nullable = false, length = 100)
    private String pipelineName;  // snapshot

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String inputText;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int totalSteps;

    @Column(nullable = false)
    private int completedSteps = 0;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    protected PipelineExecution() {}

    public PipelineExecution(PipelineDefinition def, String inputText, String username, int totalSteps) {
        this.pipelineId    = def.getId();
        this.pipelineName  = def.getName();
        this.inputText     = inputText;
        this.username      = username;
        this.totalSteps    = totalSteps;
        this.status        = STATUS_RUNNING;
        this.startedAt     = LocalDateTime.now();
    }

    public void incrementCompletedSteps() {
        this.completedSteps++;
    }

    public void markCompleted() {
        this.status      = STATUS_COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String err) {
        this.status       = STATUS_FAILED;
        this.errorMessage = err;
        this.completedAt  = LocalDateTime.now();
    }

    public int getProgressPercent() {
        if (totalSteps == 0) return 0;
        return (completedSteps * 100) / totalSteps;
    }

    public String getFormattedStartedAt() {
        return startedAt == null ? "" : startedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ── getters ──
    public Long          getId()             { return id; }
    public Long          getPipelineId()     { return pipelineId; }
    public String        getPipelineName()   { return pipelineName; }
    public String        getUsername()       { return username; }
    public String        getInputText()      { return inputText; }
    public String        getStatus()         { return status; }
    public int           getTotalSteps()     { return totalSteps; }
    public int           getCompletedSteps() { return completedSteps; }
    public LocalDateTime getStartedAt()      { return startedAt; }
    public LocalDateTime getCompletedAt()    { return completedAt; }
    public String        getErrorMessage()   { return errorMessage; }
}
