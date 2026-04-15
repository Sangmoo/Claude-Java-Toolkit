package io.github.claudetoolkit.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

/**
 * Oracle DB 연결 상태 헬스체크.
 *
 * <p>v4.2.6 — 다음 3가지 문제를 해결:
 * <ol>
 *   <li><b>Connection timeout 명시</b>: Oracle JDBC 기본은 OS TCP timeout (75초~수 분).
 *       이전엔 DB 가 잠시라도 느리면 health 호출이 80초 이상 걸려 Tomcat 워커 스레드를
 *       점유 → 다른 요청들 (소스 선택기, /api/v1/* 등) 이 응답 못 받았음.
 *       이제 oracle.net.CONNECT_TIMEOUT/oracle.jdbc.ReadTimeout 을 3초로 강제.</li>
 *   <li><b>결과 캐싱 (TTL 60초)</b>: 매 호출마다 새 JDBC 연결을 만들지 않고 마지막 결과를
 *       60초 동안 재사용. /actuator/health 가 docker HEALTHCHECK + 시스템 헬스 페이지에서
 *       자주 호출되어도 부하 최소화.</li>
 *   <li><b>비동기 검사 차단</b>: 캐시 만료 시 Health 호출 스레드가 직접 검사하지 않고
 *       빠르게 stale 결과를 반환한 뒤 백그라운드 스레드가 갱신. (옵션, 단순화를 위해 현재는
 *       동기 검사 + 짧은 timeout 으로 처리)</li>
 * </ol>
 */
@Component("oracleDb")
public class OracleDbHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(OracleDbHealthIndicator.class);

    /** 캐시 TTL — 60초 동안은 마지막 결과 재사용 */
    private static final long CACHE_TTL_MS = 60_000L;

    /** Connection 시도 timeout — 3초 이상 걸리면 즉시 실패 */
    private static final String CONNECT_TIMEOUT_SEC = "3";
    /** SQL read timeout — 3초 */
    private static final String READ_TIMEOUT_MS = "3000";
    /** 로그인 시도 timeout — DriverManager 레벨 */
    private static final int LOGIN_TIMEOUT_SEC = 3;

    private final ToolkitSettings settings;

    private volatile Health   cachedHealth;
    private volatile long     cachedAt = 0L;

    public OracleDbHealthIndicator(ToolkitSettings settings) {
        this.settings = settings;
    }

    @Override
    public Health health() {
        // 캐시 hit — TTL 내라면 즉시 반환 (DB 호출 없음)
        long now = System.currentTimeMillis();
        Health cached = cachedHealth;
        if (cached != null && (now - cachedAt) < CACHE_TTL_MS) {
            return cached;
        }

        String url = settings.getDb().getUrl();
        if (url == null || url.isEmpty()) {
            Health h = Health.unknown()
                    .withDetail("reason", "Oracle DB 미설정")
                    .build();
            cachedHealth = h;
            cachedAt     = now;
            return h;
        }

        long start = System.currentTimeMillis();
        try {
            // ── Driver 레벨 timeout (전역) ──
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SEC);

            // ── 연결별 timeout properties ──
            Properties props = new Properties();
            props.setProperty("user",     settings.getDb().getUsername() != null ? settings.getDb().getUsername() : "");
            props.setProperty("password", settings.getDb().getPassword() != null ? settings.getDb().getPassword() : "");
            // Oracle JDBC 표준 timeout 속성
            props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(Integer.parseInt(CONNECT_TIMEOUT_SEC) * 1000));
            props.setProperty("oracle.jdbc.ReadTimeout",    READ_TIMEOUT_MS);
            // SocketChannel timeout (driver 가 구버전이어도 작동)
            props.setProperty("oracle.net.READ_TIMEOUT",    READ_TIMEOUT_MS);

            Health h;
            try (Connection conn = DriverManager.getConnection(url, props);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1 FROM DUAL");
                long elapsed = System.currentTimeMillis() - start;
                h = Health.up()
                        .withDetail("url",          maskUrl(url))
                        .withDetail("responseTime", elapsed + "ms")
                        .withDetail("cached",       false)
                        .build();
            }
            cachedHealth = h;
            cachedAt     = now;
            return h;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[OracleDbHealth] DB 연결 실패: {}ms — {}", elapsed, e.getMessage());
            Health h = Health.down()
                    .withDetail("url",          maskUrl(url))
                    .withDetail("error",        e.getMessage())
                    .withDetail("responseTime", elapsed + "ms")
                    .withDetail("cached",       false)
                    .build();
            // 실패도 캐시 (60초간 재시도 안 함 — 빠른 응답 우선)
            cachedHealth = h;
            cachedAt     = now;
            return h;
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
