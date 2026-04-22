package io.github.claudetoolkit.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * v4.4.x — 앱 시작 시 Settings + 외부 DB + 프로젝트 경로를 사전 검증하여
 * Kubernetes / Docker 헬스체크가 "정말 사용 가능한 상태" 일 때만 UP 으로 응답.
 *
 * <p>이전 문제:
 * Spring Boot 가 시작되자마자 /actuator/health 가 UP 을 반환했지만
 * Settings 파일 로드 + Oracle DB 연결 + 프로젝트 스캔이 아직 끝나지 않은 상태라
 * 사용자가 분석 페이지에 즉시 들어가면 빈 결과 / 오류를 봄.
 *
 * <p>해결:
 * 1) {@link #onReady(ApplicationReadyEvent)} 에서 비동기로 warmup 수행
 *    - Settings 파일 로드 (SettingsPersistenceService 가 이미 @PostConstruct 로 처리하지만 재확인)
 *    - 외부 DataSource 연결 테스트 (1회)
 *    - 프로젝트 스캔 경로 존재 확인
 * 2) {@link #health()} 가 warmup 중 → DOWN, 완료 → UP 반환
 * 3) Docker / K8s 가 readiness probe (/actuator/health/readiness) 를 보고
 *    트래픽 라우팅 결정 → 사용자는 "준비 완료" 후에만 화면 진입
 *
 * <p>참고: Spring Boot 2.3+ 의 표준 readiness probe 와도 자동 통합됨
 * (management.endpoint.health.probes.enabled=true).
 */
@Component
public class StartupReadiness implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(StartupReadiness.class);

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicReference<String> stage = new AtomicReference<>("STARTING");
    private final AtomicReference<String> lastError = new AtomicReference<>(null);

    private final ToolkitSettings settings;
    private final SettingsPersistenceService persistenceService;
    private final DataSource dataSource;

    @Autowired(required = false)
    public StartupReadiness(ToolkitSettings settings,
                            SettingsPersistenceService persistenceService,
                            DataSource dataSource) {
        this.settings = settings;
        this.persistenceService = persistenceService;
        this.dataSource = dataSource;
    }

    /**
     * 앱이 완전히 시작된 후 (모든 빈 등록 + Tomcat listen 시작) 비동기로 warmup.
     * @Async — 시작 차단 안 함 (Tomcat 은 즉시 listen 시작하지만 health 는 false)
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        log.info("[StartupReadiness] warmup 시작...");
        long start = System.currentTimeMillis();

        // 1. Settings 파일 강제 로드 (PersistenceService 가 이미 했지만 재확인)
        stage.set("LOADING_SETTINGS");
        try {
            persistenceService.load();
            log.info("[StartupReadiness] ✓ Settings 로드 완료");
        } catch (Exception e) {
            log.warn("[StartupReadiness] ⚠ Settings 로드 실패 — 빈 설정으로 진행: {}", e.getMessage());
            // 실패해도 readiness 는 진행 (빈 설정 = 정상 가능)
        }

        // 2. DataSource 연결 테스트 (앱 내부 H2 또는 외부 DB)
        stage.set("TESTING_DATASOURCE");
        try (Connection conn = dataSource.getConnection()) {
            conn.isValid(5);
            log.info("[StartupReadiness] ✓ DataSource 연결 OK ({})", conn.getMetaData().getURL());
        } catch (Exception e) {
            log.error("[StartupReadiness] ✗ DataSource 연결 실패", e);
            lastError.set("DataSource 연결 실패: " + e.getMessage());
            // DB 없으면 앱 무용지물 → ready = false 유지하여 트래픽 차단
            stage.set("FAILED_DATASOURCE");
            return;
        }

        // 3. Settings 의 외부 Oracle DB 연결 사전 테스트 (있는 경우)
        stage.set("TESTING_EXTERNAL_DB");
        if (settings.isDbConfigured()) {
            try {
                Class.forName("oracle.jdbc.OracleDriver");
                java.sql.DriverManager.setLoginTimeout(5);
                try (Connection oc = java.sql.DriverManager.getConnection(
                        settings.getDb().getUrl(),
                        settings.getDb().getUsername(),
                        settings.getDb().getPassword())) {
                    log.info("[StartupReadiness] ✓ Settings 외부 Oracle DB 연결 OK");
                }
            } catch (Exception e) {
                // 외부 DB 실패는 경고 — 인덱스 시뮬레이션 등 일부 기능만 영향. ready=true 유지.
                log.warn("[StartupReadiness] ⚠ Settings 외부 DB 연결 실패 (앱은 정상 동작): {}",
                        e.getMessage());
            }
        }

        // 4. 프로젝트 스캔 경로 존재 확인 (있는 경우)
        stage.set("CHECKING_PROJECT_PATH");
        if (settings.getProject() != null && settings.getProject().getScanPath() != null
                && !settings.getProject().getScanPath().trim().isEmpty()) {
            File f = new File(settings.getProject().getScanPath());
            if (f.exists() && f.isDirectory()) {
                log.info("[StartupReadiness] ✓ 프로젝트 스캔 경로 존재: {}", f.getAbsolutePath());
            } else {
                log.warn("[StartupReadiness] ⚠ 프로젝트 스캔 경로 없음: {}", f.getAbsolutePath());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        stage.set("READY");
        ready.set(true);
        log.info("[StartupReadiness] ✅ Warmup 완료 ({} ms) — 트래픽 수신 준비됨", elapsed);
    }

    @Override
    public Health health() {
        if (ready.get()) {
            return Health.up()
                    .withDetail("stage", stage.get())
                    .withDetail("message", "모든 startup warmup 완료")
                    .build();
        }
        return Health.down()
                .withDetail("stage", stage.get())
                .withDetail("message", "Settings/DB warmup 진행 중 — 잠시 후 다시 시도")
                .withDetail("lastError", lastError.get() != null ? lastError.get() : "(없음)")
                .build();
    }
}
