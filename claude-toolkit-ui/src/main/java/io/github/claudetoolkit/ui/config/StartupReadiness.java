package io.github.claudetoolkit.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
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
 * <p><b>구조 — 두 클래스로 분리</b>:
 * <ul>
 *   <li>{@link StartupWarmup} (별도 빈) — {@code @EventListener} + {@code @Async} 로
 *       비동기 warmup 수행. 인터페이스를 구현하지 않아 JDK 동적 프록시 이슈 없음.</li>
 *   <li>{@link StartupReadiness} (이 클래스) — {@link HealthIndicator} 구현체.
 *       {@code StartupWarmup} 의 상태를 읽어 health() 응답.</li>
 * </ul>
 *
 * <p>이전 (v4.4.0 초기 시도) 에러 원인:
 * <pre>
 *   "Need to invoke method 'onReady' declared on target class 'StartupReadiness',
 *    but not found in any interface(s) of the exposed proxy type."
 * </pre>
 * 한 클래스에서 HealthIndicator 구현 + @EventListener 를 동시에 가지면
 * Spring 이 JDK 동적 프록시를 만들고 인터페이스에 없는 onReady 를 못 찾음.
 * 책임을 분리하여 해결.
 */
@Component
public class StartupReadiness implements HealthIndicator {

    private final StartupWarmup warmup;

    public StartupReadiness(StartupWarmup warmup) {
        this.warmup = warmup;
    }

    @Override
    public Health health() {
        if (warmup.isReady()) {
            return Health.up()
                    .withDetail("stage", warmup.getStage())
                    .withDetail("message", "모든 startup warmup 완료")
                    .build();
        }
        return Health.down()
                .withDetail("stage", warmup.getStage())
                .withDetail("message", "Settings/DB warmup 진행 중 — 잠시 후 다시 시도")
                .withDetail("lastError", warmup.getLastErrorOrNone())
                .build();
    }

    /**
     * Warmup 실행 책임만 담당하는 별도 빈 — HealthIndicator 미구현이라
     * JDK 프록시 이슈 없이 @EventListener + @Async 사용 가능.
     */
    @Component
    public static class StartupWarmup {

        private static final Logger log = LoggerFactory.getLogger(StartupWarmup.class);

        private final AtomicBoolean ready = new AtomicBoolean(false);
        private final AtomicReference<String> stage = new AtomicReference<>("STARTING");
        private final AtomicReference<String> lastError = new AtomicReference<>(null);

        private final ToolkitSettings settings;
        private final SettingsPersistenceService persistenceService;
        private final DataSource dataSource;

        @Autowired(required = false)
        public StartupWarmup(ToolkitSettings settings,
                             SettingsPersistenceService persistenceService,
                             DataSource dataSource) {
            this.settings = settings;
            this.persistenceService = persistenceService;
            this.dataSource = dataSource;
        }

        public boolean isReady()           { return ready.get(); }
        public String  getStage()          { return stage.get(); }
        public String  getLastErrorOrNone() {
            String e = lastError.get();
            return e != null ? e : "(없음)";
        }

        /**
         * 앱이 완전히 시작된 후 (모든 빈 등록 + Tomcat listen 시작) 비동기로 warmup.
         * @Async — 시작 차단 안 함 (Tomcat 은 즉시 listen 시작하지만 readiness 는 false)
         */
        @Async
        @EventListener(ApplicationReadyEvent.class)
        public void onReady(ApplicationReadyEvent event) {
            log.info("[StartupWarmup] warmup 시작...");
            long start = System.currentTimeMillis();

            // 1. Settings 파일 강제 로드 (PersistenceService 가 이미 했지만 재확인)
            stage.set("LOADING_SETTINGS");
            try {
                if (persistenceService != null) {
                    persistenceService.load();
                    log.info("[StartupWarmup] ✓ Settings 로드 완료");
                }
            } catch (Exception e) {
                log.warn("[StartupWarmup] ⚠ Settings 로드 실패 — 빈 설정으로 진행: {}", e.getMessage());
                // 실패해도 readiness 는 진행 (빈 설정 = 정상 가능)
            }

            // 2. DataSource 연결 테스트 (앱 내부 H2 또는 외부 DB)
            stage.set("TESTING_DATASOURCE");
            try (Connection conn = dataSource.getConnection()) {
                conn.isValid(5);
                log.info("[StartupWarmup] ✓ DataSource 연결 OK ({})",
                        conn.getMetaData().getURL());
            } catch (Exception e) {
                log.error("[StartupWarmup] ✗ DataSource 연결 실패", e);
                lastError.set("DataSource 연결 실패: " + e.getMessage());
                // DB 없으면 앱 무용지물 → ready = false 유지하여 트래픽 차단
                stage.set("FAILED_DATASOURCE");
                return;
            }

            // 3. Settings 의 외부 Oracle DB 연결 사전 테스트 (있는 경우)
            stage.set("TESTING_EXTERNAL_DB");
            if (settings != null && settings.isDbConfigured()) {
                try {
                    Class.forName("oracle.jdbc.OracleDriver");
                    java.sql.DriverManager.setLoginTimeout(5);
                    try (Connection oc = java.sql.DriverManager.getConnection(
                            settings.getDb().getUrl(),
                            settings.getDb().getUsername(),
                            settings.getDb().getPassword())) {
                        log.info("[StartupWarmup] ✓ Settings 외부 Oracle DB 연결 OK");
                    }
                } catch (Exception e) {
                    // 외부 DB 실패는 경고 — 인덱스 시뮬레이션 등 일부 기능만 영향. ready=true 유지.
                    log.warn("[StartupWarmup] ⚠ Settings 외부 DB 연결 실패 (앱은 정상 동작): {}",
                            e.getMessage());
                }
            }

            // 4. 프로젝트 스캔 경로 존재 확인 (있는 경우)
            stage.set("CHECKING_PROJECT_PATH");
            if (settings != null && settings.getProject() != null
                    && settings.getProject().getScanPath() != null
                    && !settings.getProject().getScanPath().trim().isEmpty()) {
                File f = new File(settings.getProject().getScanPath());
                if (f.exists() && f.isDirectory()) {
                    log.info("[StartupWarmup] ✓ 프로젝트 스캔 경로 존재: {}", f.getAbsolutePath());
                } else {
                    log.warn("[StartupWarmup] ⚠ 프로젝트 스캔 경로 없음: {}", f.getAbsolutePath());
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            stage.set("READY");
            ready.set(true);
            log.info("[StartupWarmup] ✅ Warmup 완료 ({} ms) — 트래픽 수신 준비됨", elapsed);
        }
    }
}
