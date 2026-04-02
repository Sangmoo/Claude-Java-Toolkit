package io.github.claudetoolkit.ui.schedule;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "scheduled_job")
public class ScheduledJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** SQL content to review on schedule */
    @Lob
    @Column(nullable = false)
    private String sqlContent;

    /** Cron expression e.g. "0 0 9 * * MON-FRI" */
    @Column(nullable = false, length = 100)
    private String cronExpression;

    /** true = active, false = paused */
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = true)
    private LocalDateTime lastRunAt;

    @Lob
    @Column(nullable = true)
    private String lastResult;

    /** Optional email for result notification */
    @Column(nullable = true, length = 200)
    private String notifyEmail;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ScheduledJob() { this.createdAt = LocalDateTime.now(); }

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getSqlContent() { return sqlContent; }
    public void setSqlContent(String s) { this.sqlContent = s; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String c) { this.cronExpression = c; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime t) { this.lastRunAt = t; }
    public String getLastResult() { return lastResult; }
    public void setLastResult(String r) { this.lastResult = r; }
    public String getNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(String e) { this.notifyEmail = e; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }

    public String getFormattedLastRun() {
        if (lastRunAt == null) return "실행 전";
        return lastRunAt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    public String getLastResultPreview() {
        if (lastResult == null || lastResult.isEmpty()) return "-";
        String r = lastResult.replaceAll("[#*`>\\-]", "").trim();
        return r.length() > 100 ? r.substring(0, 97) + "..." : r;
    }

    public String getStatusText() {
        return enabled ? "활성" : "일시중지";
    }

    public String getStatusColor() {
        return enabled ? "#22c55e" : "#94a3b8";
    }
}
