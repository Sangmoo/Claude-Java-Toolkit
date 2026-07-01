package io.github.claudetoolkit.ui.livedb;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Live DB 에 실행된 쿼리 이력 — ADMIN 감사·성능 트래킹용.
 *
 * <p>DDL {@code ddl-auto: update} 로 자동 생성됨.
 */
@Entity
@Table(name = "livedb_query_log",
       indexes = @Index(name = "idx_livedb_qlog_exec", columnList = "executed_at DESC"))
public class LiveDbQueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "sql_text", columnDefinition = "TEXT")
    private String sqlText;

    /** 쿼리 왕복 시간 (ms) */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** EXPLAIN/SELECT 에서 반환된 행 수 */
    @Column(name = "row_count")
    private Integer rowCount;

    /** OK / ERROR / BLOCKED(회로차단기) / TIMEOUT */
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    protected LiveDbQueryLog() {}

    public LiveDbQueryLog(Long profileId, LocalDateTime executedAt, String username,
                          String sqlText, long durationMs, Integer rowCount,
                          String status, String errorMessage) {
        this.profileId    = profileId;
        this.executedAt   = executedAt;
        this.username     = username;
        this.sqlText      = sqlText;
        this.durationMs   = durationMs;
        this.rowCount     = rowCount;
        this.status       = status;
        this.errorMessage = errorMessage;
    }

    public Long          getId()          { return id; }
    public Long          getProfileId()   { return profileId; }
    public LocalDateTime getExecutedAt()  { return executedAt; }
    public String        getUsername()    { return username; }
    public String        getSqlText()     { return sqlText; }
    public Long          getDurationMs()  { return durationMs; }
    public Integer       getRowCount()    { return rowCount; }
    public String        getStatus()      { return status; }
    public String        getErrorMessage(){ return errorMessage; }
}
