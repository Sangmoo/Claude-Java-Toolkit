package io.github.claudetoolkit.ui.flow.history;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Flow Analysis 분석 이력 — 사용자별로 최근 분석 결과 저장.
 *
 * <p>저장 시점: {@link io.github.claudetoolkit.ui.flow.FlowStreamController} 의 SSE 가
 * 정상 완료된 직후 (Phase 1 trace + LLM narrative 모두 확정된 후).
 *
 * <p>traceJson 은 {@link io.github.claudetoolkit.ui.flow.model.FlowAnalysisResult} 의
 * 직렬화된 JSON. 이력 클릭 시 재실행 없이 즉시 다이어그램 + narrative 복원.
 */
@Entity
@Table(name = "flow_history",
       indexes = {
           @Index(name = "idx_flow_history_user_created", columnList = "user_id, created_at DESC"),
       })
public class FlowHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 분석 실행한 사용자명 (Principal.getName) */
    @Column(name = "user_id", nullable = false, length = 80)
    private String userId;

    @Column(nullable = false, length = 500)
    private String query;

    @Column(name = "target_type", length = 32)
    private String targetType;

    /** "INSERT,UPDATE,SELECT" 같은 콤마 결합 (조회 편의) */
    @Column(name = "dml_filters", length = 100)
    private String dmlFilters;

    @Column(name = "nodes_count")  private Integer nodesCount;
    @Column(name = "edges_count")  private Integer edgesCount;
    @Column(name = "elapsed_ms")   private Long    elapsedMs;

    /** FlowAnalysisResult 의 JSON (다이어그램 복원용) */
    @Lob
    @Column(name = "trace_json", nullable = false, columnDefinition = "TEXT")
    private String traceJson;

    /** Claude 가 생성한 markdown narrative */
    @Lob
    @Column(name = "narrative", columnDefinition = "TEXT")
    private String narrative;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public FlowHistory() {}

    public FlowHistory(String userId, String query, String targetType, String dmlFilters,
                       Integer nodesCount, Integer edgesCount, Long elapsedMs,
                       String traceJson, String narrative) {
        this.userId      = userId;
        this.query       = query;
        this.targetType  = targetType;
        this.dmlFilters  = dmlFilters;
        this.nodesCount  = nodesCount;
        this.edgesCount  = edgesCount;
        this.elapsedMs   = elapsedMs;
        this.traceJson   = traceJson;
        this.narrative   = narrative;
        this.createdAt   = LocalDateTime.now();
    }

    public String getFormattedCreatedAt() {
        return createdAt != null
                ? createdAt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "";
    }

    public Long          getId()             { return id; }
    public String        getUserId()         { return userId; }
    public String        getQuery()          { return query; }
    public String        getTargetType()     { return targetType; }
    public String        getDmlFilters()     { return dmlFilters; }
    public Integer       getNodesCount()     { return nodesCount; }
    public Integer       getEdgesCount()     { return edgesCount; }
    public Long          getElapsedMs()      { return elapsedMs; }
    public String        getTraceJson()      { return traceJson; }
    public String        getNarrative()      { return narrative; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
}
