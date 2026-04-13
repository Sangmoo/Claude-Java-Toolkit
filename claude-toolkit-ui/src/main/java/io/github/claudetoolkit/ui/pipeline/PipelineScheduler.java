package io.github.claudetoolkit.ui.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 파이프라인 스케줄러 (v3.0).
 *
 * <p>매분 DB에서 {@code scheduleEnabled = true}이고 {@code scheduleCron}이 설정된
 * 파이프라인을 조회하여 cron 표현식과 현재 시각이 매칭되면 자동 실행합니다.
 *
 * <p>Spring 6-필드 cron 표현식 사용: {@code 초 분 시 일 월 요일}
 */
@Component
public class PipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(PipelineScheduler.class);

    private final PipelineDefinitionRepository definitionRepo;
    private final PipelineExecutor             executor;

    public PipelineScheduler(PipelineDefinitionRepository definitionRepo,
                             PipelineExecutor executor) {
        this.definitionRepo = definitionRepo;
        this.executor       = executor;
    }

    /**
     * 매분 실행 — 활성 스케줄 파이프라인의 cron을 평가하고 매칭 시 실행.
     */
    @Scheduled(fixedRate = 60_000)
    public void checkSchedules() {
        try {
            List<PipelineDefinition> scheduled =
                    definitionRepo.findByScheduleEnabledTrueAndScheduleCronIsNotNull();
            if (scheduled.isEmpty()) return;

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

            for (PipelineDefinition def : scheduled) {
                try {
                    String cron = def.getScheduleCron();
                    if (cron == null || cron.trim().isEmpty()) continue;

                    CronExpression expr = CronExpression.parse(cron);
                    // next 실행 시각을 구하여 현재 분과 비교
                    LocalDateTime prev = now.minusMinutes(1);
                    LocalDateTime next = expr.next(prev);
                    if (next == null) continue;

                    // next가 현재 분 범위 내면 실행
                    if (next.truncatedTo(ChronoUnit.MINUTES).equals(now)) {
                        String input = def.getScheduleInput();
                        if (input == null || input.trim().isEmpty()) {
                            log.warn("[PipelineScheduler] 입력 없음, 건너뜀: id={}, name={}",
                                    def.getId(), def.getName());
                            continue;
                        }
                        log.info("[PipelineScheduler] 스케줄 실행: id={}, name={}, cron={}",
                                def.getId(), def.getName(), cron);
                        executor.start(def.getId(), input, "SCHEDULER");
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("[PipelineScheduler] cron 파싱 실패: id={}, cron='{}', error={}",
                            def.getId(), def.getScheduleCron(), e.getMessage());
                } catch (Exception e) {
                    log.error("[PipelineScheduler] 실행 실패: id={}, error={}",
                            def.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("[PipelineScheduler] 스케줄 체크 오류: {}", e.getMessage());
        }
    }
}
