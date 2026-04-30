package io.github.claudetoolkit.ui.livedb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * v4.7.x — #G3 Live DB Phase 0: 읽기 전용 JDBC 게이트.
 *
 * <p>일반 {@link JdbcTemplate} 의 *대체재가 아닌 wrapper* — 모든 호출 직전에
 * {@link SqlClassifier} 검증을 강제. SELECT/EXPLAIN/DESC/WITH 만 통과,
 * 나머지는 {@link SecurityException}.
 *
 * <p><b>설계 원칙:</b>
 * <ul>
 *   <li>Bean 자체가 {@link LiveDbConfig#isEnabled()} = false 일 땐 query() 가
 *       즉시 throw — 활성화 안 된 환경에선 어떤 호출도 진행 X</li>
 *   <li>Statement timeout 강제 — 사용자가 우회 불가</li>
 *   <li>Max rows 강제 — 메모리 폭발 방지</li>
 *   <li>모든 호출 + 결과 row 수 + latency 를 audit 로그로 (Phase 5 에서 wiring)</li>
 *   <li>이 클래스는 *공개되어 있지만* 호출자는 livedb 패키지 내부의
 *       Provider 들 뿐 — controller 에서 직접 쓰지 못하도록 제약</li>
 * </ul>
 *
 * <p>이 클래스 자체는 DataSource 를 받아 만들어지므로, *분석 전용 DataSource* 를
 * 별도 bean 으로 등록해 운영 풀과 격리 가능 (Phase 1 에서 OracleProvider 가 활용).
 */
public class ReadOnlyJdbcTemplate {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyJdbcTemplate.class);

    private final JdbcTemplate   jdbc;
    private final LiveDbConfig   config;
    private final String         profileLabel;   // 감사 로그용 식별자 (예: "production-readonly")

    public ReadOnlyJdbcTemplate(DataSource dataSource, LiveDbConfig config, String profileLabel) {
        this.jdbc         = new JdbcTemplate(dataSource);
        this.config       = config;
        this.profileLabel = profileLabel != null ? profileLabel : "(unnamed)";
        applyDefaultLimits();
    }

    private void applyDefaultLimits() {
        // Statement timeout — 사용자/profile 이 override 하지 않는 한 글로벌 default
        jdbc.setQueryTimeout(config.getDefaultTimeoutSeconds());
        // Max rows — fetch 시 자동 절단. 통계 조회는 작은 결과만 반환하므로 영향 없음.
        jdbc.setMaxRows(config.getMaxRows());
    }

    /**
     * SELECT/EXPLAIN/DESC/WITH 만 허용. 그 외 시도는 {@link SecurityException}.
     *
     * @throws SecurityException 비-읽기 SQL 시도, 멀티 statement, 분류 실패
     * @throws IllegalStateException Live DB 채널이 비활성화된 상태에서 호출
     */
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        verifySafe(sql);
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        logCall(sql, rows.size(), System.currentTimeMillis() - t0);
        return rows;
    }

    /**
     * RowMapper 기반 query — 동일 검증 적용.
     */
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        verifySafe(sql);
        long t0 = System.currentTimeMillis();
        List<T> rows = jdbc.query(sql, mapper, args);
        logCall(sql, rows.size(), System.currentTimeMillis() - t0);
        return rows;
    }

    /**
     * 단일 결과 — null 가능. 동일 검증.
     */
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        verifySafe(sql);
        long t0 = System.currentTimeMillis();
        try {
            T result = jdbc.queryForObject(sql, type, args);
            logCall(sql, result != null ? 1 : 0, System.currentTimeMillis() - t0);
            return result;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            logCall(sql, 0, System.currentTimeMillis() - t0);
            return null;
        }
    }

    /**
     * EXPLAIN PLAN FOR 같은 *결과를 fetch 안 하는* 명령 (Oracle 의 EXPLAIN PLAN 은
     * row 를 반환하지 않고 PLAN_TABLE 에 행을 INSERT 한다 — 그 후 별도 SELECT 로 조회).
     *
     * <p>이 메서드는 SqlClassifier 를 통과한 EXPLAIN 만 허용하고, 일반 update 는
     * 거부. update() 라는 이름이지만 read-only 채널 안에서만 동작.
     *
     * @throws SecurityException EXPLAIN 이 아닌 명령 시도
     */
    public void executeExplain(String sql) {
        SqlOperation op = SqlClassifier.classify(sql);
        if (op != SqlOperation.EXPLAIN) {
            throw new SecurityException(
                "ReadOnlyJdbcTemplate.executeExplain 은 EXPLAIN 만 허용 — 받은 op: "
                + op + " / sql preview: " + previewSql(sql));
        }
        verifyEnabled();
        long t0 = System.currentTimeMillis();
        jdbc.execute(sql);
        logCall(sql, -1, System.currentTimeMillis() - t0);
    }

    // ── private helpers ───────────────────────────────────────────────────

    private void verifySafe(String sql) {
        verifyEnabled();
        SqlOperation op = SqlClassifier.classify(sql);
        if (!SqlClassifier.isReadOnly(op)) {
            throw new SecurityException(
                "ReadOnlyJdbcTemplate 는 SELECT / EXPLAIN / DESC / WITH 만 허용 — "
                + "받은 op: " + op + " / sql preview: " + previewSql(sql));
        }
    }

    private void verifyEnabled() {
        if (!config.isEnabled()) {
            throw new IllegalStateException(
                "Live DB 채널이 비활성 상태입니다 (toolkit.livedb.enabled=false). "
                + "ADMIN 이 application.yml 또는 환경변수 TOOLKIT_LIVEDB_ENABLED=true 로 활성화 필요.");
        }
    }

    private void logCall(String sql, int rows, long elapsedMs) {
        // Phase 5 에서 audit_log 테이블에 기록 — 지금은 디버그 로그만
        log.debug("[LiveDb][{}] rows={} elapsed={}ms sql={}",
                  profileLabel, rows, elapsedMs, previewSql(sql));
    }

    private static String previewSql(String sql) {
        if (sql == null) return "(null)";
        String s = sql.replaceAll("\\s+", " ").trim();
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }
}
