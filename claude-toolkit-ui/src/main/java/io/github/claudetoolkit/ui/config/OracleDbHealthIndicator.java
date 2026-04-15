package io.github.claudetoolkit.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Oracle DB 연결 상태 헬스체크.
 *
 * <p>v4.2.7 — 프로브 수행을 {@code health()} 호출과 완전히 분리:
 * <ul>
 *   <li><b>시작 1회</b>: {@code @PostConstruct} 에서 백그라운드 스레드로 최초 프로브</li>
 *   <li><b>1시간 주기</b>: {@code @Scheduled(fixedRate = 1h)} 로 주기적 갱신</li>
 *   <li><b>health() 는 순수 조회</b>: 캐시된 결과만 반환하며 절대 DB 를 건드리지 않는다.
 *       따라서 Tomcat 워커 스레드가 DB 지연으로 점유되는 일이 없다.</li>
 * </ul>
 *
 * <p>이전 버전(v4.2.6)은 60초 TTL + 동기 프로브였는데, 가끔 프로브 자체가 3초 이상 걸려
 * 사용자에게 {@code /actuator/health} 응답이 지연되거나 워커 스레드가 물리는 문제가 있었다.
 * 본 버전에서는 실제 DB 상태 변화는 최대 1시간 지연되지만, 이 프로젝트 용도에서는 그 정도
 * 해상도로 충분하고 성능/안정성이 훨씬 낫다.
 *
 * <p>프로브 동시 실행은 {@link java.util.concurrent.atomic.AtomicInteger} 로 가드하여
 * {@code @PostConstruct} 초기 실행과 첫 스케줄 실행이 겹쳐도 1회만 돌도록 한다.
 */
@Component("oracleDb")
public class OracleDbHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(OracleDbHealthIndicator.class);

    /** Connection 시도 timeout — 10 초 (주기 프로브는 조금 여유 있게) */
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** SQL read timeout — 10 초 */
    private static final int READ_TIMEOUT_MS    = 10_000;
    /** DriverManager 로그인 timeout (전역) — 10 초 */
    private static final int LOGIN_TIMEOUT_SEC  = 10;

    /** 1 시간 단위 자동 갱신 (ms). @Scheduled fixedRate 값으로 사용. */
    public  static final long REFRESH_INTERVAL_MS = 60L * 60L * 1000L; // 1h

    private final ToolkitSettings settings;

    /** 마지막 프로브 결과 — health() 가 반환하는 유일한 소스 */
    private volatile Health cachedHealth;
    /** 마지막 프로브 완료 시각 (ms). 디버그용 */
    private volatile long   cachedAt = 0L;
    /** 동시 프로브 방지 — 0 이면 idle, 1 이면 진행 중 */
    private final AtomicInteger inFlight = new AtomicInteger(0);

    /**
     * 최초 프로브 전용 싱글 스레드 executor — 컨텍스트 초기화 블로킹 방지.
     * 주기 프로브는 Spring 의 @Scheduled 를 쓴다.
     */
    private final ScheduledExecutorService bootstrap = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "oracle-health-bootstrap");
            t.setDaemon(true);
            return t;
        }
    });

    public OracleDbHealthIndicator(ToolkitSettings settings) {
        this.settings = settings;
        // 초기값: 아직 프로브 전 — UNKNOWN 상태로 시작해 actuator 가 의미 있는 응답을 줄 수 있게 한다.
        this.cachedHealth = Health.unknown()
                .withDetail("reason", "초기 프로브 대기 중")
                .build();
    }

    /**
     * 애플리케이션 시작 시 단 1회 — 컨텍스트 초기화가 끝난 뒤 백그라운드 스레드에서 프로브.
     * 메인 초기화 쓰레드를 블로킹하지 않도록 약간의 딜레이 후 실행한다.
     */
    @PostConstruct
    public void scheduleInitialProbe() {
        bootstrap.schedule(new Runnable() {
            public void run() {
                probe("startup");
            }
        }, 2, TimeUnit.SECONDS);
    }

    /**
     * 1 시간마다 백그라운드에서 프로브. Spring Task Scheduler 가 제공하는 별도 스레드에서
     * 실행되므로 Tomcat 워커에는 영향 없음.
     *
     * <p>{@code initialDelay} 는 @PostConstruct 와 중복되지 않도록 1 시간으로 둔다.
     * 첫 실행은 @PostConstruct 의 bootstrap 에서 이미 처리됨.
     */
    @Scheduled(fixedRate = REFRESH_INTERVAL_MS, initialDelay = REFRESH_INTERVAL_MS)
    public void scheduledRefresh() {
        probe("scheduled");
    }

    /**
     * health() 는 항상 캐시만 반환한다. **절대 DB 를 건드리지 않는다.**
     * 이 결정이 이 리팩터링의 핵심이며, 이로 인해 /actuator/health 나 시스템 헬스 페이지가
     * 아무리 자주 호출되어도 워커 스레드가 DB 에 블로킹되지 않는다.
     */
    @Override
    public Health health() {
        Health h = cachedHealth;
        return h != null ? h : Health.unknown().withDetail("reason", "프로브 미실행").build();
    }

    // ── 내부 프로브 로직 ──────────────────────────────────────────────────────

    private void probe(String trigger) {
        // 동시 프로브 방지 — 이미 돌고 있으면 skip (startup 과 schedule 이 겹치는 드문 경우)
        if (!inFlight.compareAndSet(0, 1)) {
            log.debug("[OracleDbHealth] probe skip (already running), trigger={}", trigger);
            return;
        }
        try {
            String url = settings.getDb().getUrl();
            if (url == null || url.isEmpty()) {
                cachedHealth = Health.unknown()
                        .withDetail("reason", "Oracle DB 미설정")
                        .build();
                cachedAt = System.currentTimeMillis();
                log.info("[OracleDbHealth] probe({}) — DB 미설정", trigger);
                return;
            }

            long start = System.currentTimeMillis();
            try {
                DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SEC);

                Properties props = new Properties();
                props.setProperty("user",     settings.getDb().getUsername() != null ? settings.getDb().getUsername() : "");
                props.setProperty("password", settings.getDb().getPassword() != null ? settings.getDb().getPassword() : "");
                props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(CONNECT_TIMEOUT_MS));
                props.setProperty("oracle.jdbc.ReadTimeout",    String.valueOf(READ_TIMEOUT_MS));
                props.setProperty("oracle.net.READ_TIMEOUT",    String.valueOf(READ_TIMEOUT_MS));

                try (Connection conn = DriverManager.getConnection(url, props);
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("SELECT 1 FROM DUAL");
                }
                long elapsed = System.currentTimeMillis() - start;
                cachedHealth = Health.up()
                        .withDetail("url",           maskUrl(url))
                        .withDetail("responseTime",  elapsed + "ms")
                        .withDetail("lastProbe",     trigger)
                        .withDetail("refreshEvery",  "1h")
                        .build();
                cachedAt = System.currentTimeMillis();
                log.info("[OracleDbHealth] probe({}) — UP, {}ms", trigger, elapsed);
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                cachedHealth = Health.down()
                        .withDetail("url",           maskUrl(url))
                        .withDetail("error",         e.getMessage())
                        .withDetail("responseTime",  elapsed + "ms")
                        .withDetail("lastProbe",     trigger)
                        .withDetail("refreshEvery",  "1h")
                        .build();
                cachedAt = System.currentTimeMillis();
                log.warn("[OracleDbHealth] probe({}) — DOWN, {}ms: {}", trigger, elapsed, e.getMessage());
            }
        } finally {
            inFlight.set(0);
        }
    }

    private String maskUrl(String url) {
        int atIdx = url.indexOf('@');
        if (atIdx < 0) return "***";
        String prefix = url.substring(0, atIdx + 1);
        String rest = url.substring(atIdx + 1);
        int slashIdx = rest.lastIndexOf('/');
        if (slashIdx > 0) {
            return prefix + rest.substring(0, slashIdx) + "/***";
        }
        return prefix + "***";
    }
}
