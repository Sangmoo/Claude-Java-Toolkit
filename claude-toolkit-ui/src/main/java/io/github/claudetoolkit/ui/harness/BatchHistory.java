package io.github.claudetoolkit.ui.harness;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JPA entity storing the result of a completed batch analysis job.
 * Persisted to the same H2 file DB as ReviewHistory.
 */
@Entity
@Table(name = "batch_history")
public class BatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /** UUID assigned when batch was started (used as anchor in email links). */
    @Column(nullable = false, length = 36)
    private String batchUuid;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = true)
    private LocalDateTime finishedAt;

    @Column(nullable = false)
    private int totalCount;

    @Column(nullable = false)
    private int successCount;

    @Column(nullable = false)
    private int failedCount;

    /**
     * JSON blob storing per-item summary:
     * [{"label":"...","language":"java","status":"success","verdict":"APPROVED"},...]
     */
    @Lob
    @Column(nullable = true)
    private String itemsSummaryJson;

    // --- constructors ---
    public BatchHistory() {}

    // --- getters / setters ---
    public long getId() { return id; }
    public String getBatchUuid() { return batchUuid; }
    public void setBatchUuid(String batchUuid) { this.batchUuid = batchUuid; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public String getItemsSummaryJson() { return itemsSummaryJson; }
    public void setItemsSummaryJson(String itemsSummaryJson) { this.itemsSummaryJson = itemsSummaryJson; }

    /** Formatted startedAt for display (yyyy-MM-dd HH:mm:ss). */
    public String getFormattedStartedAt() {
        if (startedAt == null) return "-";
        return startedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** Formatted finishedAt for display. */
    public String getFormattedFinishedAt() {
        if (finishedAt == null) return "-";
        return finishedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
