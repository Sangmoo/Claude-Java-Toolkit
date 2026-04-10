package io.github.claudetoolkit.ui.migration;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * DB 자동 마이그레이션 작업 이력 (v2.9.5).
 */
@Entity
@Table(name = "db_migration_job", indexes = {
    @Index(name = "idx_dbmig_started", columnList = "startedAt")
})
public class DbMigrationJob {

    public static final String STATUS_RUNNING   = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED    = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String targetType;  // postgresql / mysql / oracle

    @Column(length = 200)
    private String targetUrl;   // masked

    @Column(length = 50)
    private String username;    // 실행 사용자

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int totalTables;

    @Column(nullable = false)
    private int completedTables = 0;

    @Column(length = 100)
    private String currentTable;

    @Column
    private Long currentTableTotal;

    @Column
    private Long currentTableDone;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String warnings;

    protected DbMigrationJob() {}

    public DbMigrationJob(String targetType, String targetUrl, String username, int totalTables) {
        this.targetType  = targetType;
        this.targetUrl   = targetUrl;
        this.username    = username;
        this.status      = STATUS_RUNNING;
        this.totalTables = totalTables;
        this.startedAt   = LocalDateTime.now();
    }

    public void setCurrentTable(String name, long total) {
        this.currentTable      = name;
        this.currentTableTotal = total;
        this.currentTableDone  = 0L;
    }

    public void setCurrentTableDone(long done) {
        this.currentTableDone = done;
    }

    public void incrementCompletedTables() {
        this.completedTables++;
    }

    public void markCompleted() {
        this.status      = STATUS_COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String err) {
        this.status       = STATUS_FAILED;
        this.errorMessage = err != null && err.length() > 2000 ? err.substring(0, 2000) : err;
        this.completedAt  = LocalDateTime.now();
    }

    public int getProgressPercent() {
        if (totalTables == 0) return 0;
        return (completedTables * 100) / totalTables;
    }

    // ── getters ──
    public Long          getId()             { return id; }
    public String        getTargetType()     { return targetType; }
    public String        getTargetUrl()      { return targetUrl; }
    public String        getUsername()       { return username; }
    public String        getStatus()         { return status; }
    public int           getTotalTables()    { return totalTables; }
    public int           getCompletedTables(){ return completedTables; }
    public String        getCurrentTable()   { return currentTable; }
    public Long          getCurrentTableTotal() { return currentTableTotal; }
    public Long          getCurrentTableDone()  { return currentTableDone; }
    public LocalDateTime getStartedAt()      { return startedAt; }
    public LocalDateTime getCompletedAt()    { return completedAt; }
    public String        getErrorMessage()   { return errorMessage; }
    public String        getWarnings()       { return warnings; }

    public void setWarnings(String v) { this.warnings = v; }
}
