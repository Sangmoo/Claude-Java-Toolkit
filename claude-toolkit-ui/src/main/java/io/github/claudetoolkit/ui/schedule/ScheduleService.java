package io.github.claudetoolkit.ui.schedule;

import io.github.claudetoolkit.sql.advisor.SqlAdvisorService;
import io.github.claudetoolkit.ui.email.EmailService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class ScheduleService {

    private final ScheduledJobRepository  repository;
    private final SqlAdvisorService       sqlAdvisorService;
    private final ReviewHistoryService    historyService;
    private final TaskScheduler           taskScheduler;
    private final EmailService            emailService;
    private final Map<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<Long, ScheduledFuture<?>>();

    public ScheduleService(ScheduledJobRepository repository,
                           SqlAdvisorService sqlAdvisorService,
                           ReviewHistoryService historyService,
                           TaskScheduler taskScheduler,
                           EmailService emailService) {
        this.repository        = repository;
        this.sqlAdvisorService = sqlAdvisorService;
        this.historyService    = historyService;
        this.taskScheduler     = taskScheduler;
        this.emailService      = emailService;
    }

    @PostConstruct
    public void init() {
        // Re-schedule all enabled jobs on startup
        List<ScheduledJob> jobs = repository.findByEnabledTrue();
        for (ScheduledJob job : jobs) {
            scheduleJob(job);
        }
    }

    @Transactional(readOnly = true)
    public List<ScheduledJob> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public ScheduledJob save(ScheduledJob job) {
        ScheduledJob saved = repository.save(job);
        if (saved.isEnabled()) {
            scheduleJob(saved);
        }
        return saved;
    }

    @Transactional
    public void toggle(Long id) {
        ScheduledJob job = repository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    @Override public RuntimeException get() { return new RuntimeException("Job not found: " + id); }
                });
        job.setEnabled(!job.isEnabled());
        repository.save(job);
        if (job.isEnabled()) {
            scheduleJob(job);
        } else {
            cancelJob(id);
        }
    }

    @Transactional
    public void delete(Long id) {
        cancelJob(id);
        repository.deleteById(id);
    }

    @Transactional
    public String runNow(Long id) {
        ScheduledJob job = repository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    @Override public RuntimeException get() { return new RuntimeException("Job not found: " + id); }
                });
        return executeJob(job);
    }

    @Transactional(readOnly = true)
    public ScheduledJob findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    // ── internal ────────────────────────────────────────────────────────────

    private void scheduleJob(final ScheduledJob job) {
        cancelJob(job.getId());
        try {
            CronTrigger trigger = new CronTrigger(job.getCronExpression());
            final Long jobId = job.getId();
            ScheduledFuture<?> future = taskScheduler.schedule(
                new Runnable() {
                    @Override
                    public void run() {
                        ScheduledJob j = repository.findById(jobId).orElse(null);
                        if (j != null && j.isEnabled()) executeJob(j);
                    }
                },
                trigger
            );
            futures.put(job.getId(), future);
        } catch (Exception e) {
            System.err.println("[ScheduleService] Failed to schedule job " + job.getId() + ": " + e.getMessage());
        }
    }

    private void cancelJob(Long id) {
        ScheduledFuture<?> f = futures.remove(id);
        if (f != null) f.cancel(false);
    }

    @Transactional
    String executeJob(ScheduledJob job) {
        try {
            String result = sqlAdvisorService.review(job.getSqlContent()).getReviewContent();
            job.setLastRunAt(LocalDateTime.now());
            job.setLastResult(result);
            repository.save(job);
            historyService.save("SQL_REVIEW", job.getSqlContent(), result);
            // Send email notification if configured
            if (job.getNotifyEmail() != null && !job.getNotifyEmail().trim().isEmpty()) {
                String subject = "[Claude Toolkit] 스케줄 분석 완료: " + job.getName();
                String body = "스케줄 작업이 완료되었습니다.\n\n"
                        + "작업명: " + job.getName() + "\n"
                        + "실행시각: " + job.getLastRunAt() + "\n\n"
                        + "=== 분석 결과 ===\n" + result;
                emailService.sendJobResult(job.getNotifyEmail().trim(), subject, body);
            }
            return result;
        } catch (Exception e) {
            String err = "[자동 실행 오류] " + e.getMessage();
            job.setLastRunAt(LocalDateTime.now());
            job.setLastResult(err);
            repository.save(job);
            return err;
        }
    }
}
