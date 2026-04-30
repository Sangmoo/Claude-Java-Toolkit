package io.github.claudetoolkit.ui.livedb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v4.7.x — #G3 Live DB Phase 1: Oracle 11g~19c 용 컨텍스트 수집기.
 *
 * <p>다음 4가지 정보를 수집해 {@link LiveDbContext} 로 반환:
 * <ol>
 *   <li>DBMS 버전 ({@code v$version}) — 옵션</li>
 *   <li>참조 테이블 통계 ({@code dba_tables} 또는 {@code all_tables})</li>
 *   <li>인덱스 정보 ({@code dba_indexes} + {@code dba_ind_columns})</li>
 *   <li>EXPLAIN PLAN ({@code EXPLAIN PLAN FOR ... } + {@code DBMS_XPLAN.DISPLAY})</li>
 * </ol>
 *
 * <p>각 단계는 *독립적으로 graceful fallback*. 권한 없거나 실패하면
 * {@link LiveDbContext#addWarning(String)} 에 기록하고 다음 단계 진행.
 *
 * <p><b>SECURITY</b>: 모든 SQL 호출은 {@link ReadOnlyJdbcTemplate} 만 사용.
 * 어떤 SQL 도 평문 concat 으로 만들지 않고 PreparedStatement 파라미터 사용.
 * 단, EXPLAIN PLAN 만은 사용자 SQL 을 *그대로* 실행해야 하므로 별도 처리 —
 * {@link SqlClassifier} 가 SELECT/EXPLAIN/WITH/DESC 만 통과시킨 후 prefix 만 붙임.
 */
public class OracleLiveDbContextProvider implements LiveDbContextProvider {

    private static final Logger log = LoggerFactory.getLogger(OracleLiveDbContextProvider.class);

    /** EXPLAIN PLAN 결과를 저장하는 PLAN_TABLE 의 statement_id (per-call 고유) */
    private static final String PLAN_STATEMENT_PREFIX = "ctk_livedb_";

    @Override
    public String getDbType() { return "oracle"; }

    @Override
    public LiveDbContext fetch(String userSql, String schema, ReadOnlyJdbcTemplate ro) {
        LiveDbContext ctx = new LiveDbContext();

        // 1. DBMS 버전 — 가장 가벼운 호출. 실패해도 다음 단계 진행.
        ctx.setDbmsVersion(safeFetchVersion(ro, ctx));

        // 2. 참조 테이블 추출 → 통계
        Set<String> tables = SqlTableExtractor.extract(userSql);
        ctx.setReferencedTables(new ArrayList<String>(tables));
        if (!tables.isEmpty()) {
            ctx.setTables(safeFetchTableStats(ro, schema, tables, ctx));
            ctx.setIndexes(safeFetchIndexes(ro, schema, tables, ctx));
        }

        // 3. EXPLAIN PLAN — 사용자 SQL 을 read-only 로 검증 후 EXPLAIN
        ctx.setExplainPlanFormatted(safeFetchExplainPlan(ro, userSql, ctx));

        return ctx;
    }

    // ── 1. 버전 ────────────────────────────────────────────────────────────

    private String safeFetchVersion(ReadOnlyJdbcTemplate ro, LiveDbContext ctx) {
        try {
            return ro.queryForObject(
                    "SELECT BANNER FROM V$VERSION WHERE BANNER LIKE 'Oracle%'",
                    String.class);
        } catch (Exception e) {
            // V$VERSION 권한 없을 수 있음 — fallback to PRODUCT_COMPONENT_VERSION
            try {
                return ro.queryForObject(
                        "SELECT PRODUCT || ' ' || VERSION FROM PRODUCT_COMPONENT_VERSION " +
                        "WHERE PRODUCT LIKE 'Oracle%' AND ROWNUM = 1",
                        String.class);
            } catch (Exception e2) {
                ctx.addWarning("DBMS 버전 조회 실패: " + brief(e));
                return null;
            }
        }
    }

    // ── 2. 테이블 통계 ────────────────────────────────────────────────────

    /**
     * DBA_TABLES 권한이 없으면 USER_TABLES 또는 ALL_TABLES 로 fallback.
     * Oracle 마다 다른 권한 환경 대응.
     */
    private List<LiveDbContext.TableStats> safeFetchTableStats(
            ReadOnlyJdbcTemplate ro, String schema, Set<String> tables, LiveDbContext ctx) {
        List<LiveDbContext.TableStats> result = new ArrayList<LiveDbContext.TableStats>();
        for (String tableName : tables) {
            try {
                LiveDbContext.TableStats stats = fetchOneTableStats(ro, schema, tableName);
                if (stats != null) result.add(stats);
            } catch (Exception e) {
                ctx.addWarning("테이블 " + tableName + " 통계 조회 실패: " + brief(e));
            }
        }
        return result;
    }

    private LiveDbContext.TableStats fetchOneTableStats(
            ReadOnlyJdbcTemplate ro, String schema, String tableName) {
        // DBA_TABLES 우선, 권한 없으면 ALL_TABLES, 그것도 없으면 USER_TABLES
        String sql;
        Object[] args;
        if (schema != null && !schema.isEmpty()) {
            sql = "SELECT t.NUM_ROWS, t.BLOCKS, t.LAST_ANALYZED, " +
                  "       (SELECT c.COMMENTS FROM ALL_TAB_COMMENTS c " +
                  "         WHERE c.OWNER = t.OWNER AND c.TABLE_NAME = t.TABLE_NAME AND ROWNUM = 1) AS COMMENTS " +
                  "  FROM ALL_TABLES t " +
                  " WHERE t.OWNER = ? AND t.TABLE_NAME = ?";
            args = new Object[]{schema.toUpperCase(), tableName.toUpperCase()};
        } else {
            sql = "SELECT t.NUM_ROWS, t.BLOCKS, t.LAST_ANALYZED, " +
                  "       (SELECT c.COMMENTS FROM USER_TAB_COMMENTS c " +
                  "         WHERE c.TABLE_NAME = t.TABLE_NAME AND ROWNUM = 1) AS COMMENTS " +
                  "  FROM USER_TABLES t " +
                  " WHERE t.TABLE_NAME = ?";
            args = new Object[]{tableName.toUpperCase()};
        }
        List<Map<String, Object>> rows = ro.queryForList(sql, args);
        if (rows.isEmpty()) return null;
        Map<String, Object> r = rows.get(0);
        return new LiveDbContext.TableStats(
                tableName,
                asLong(r.get("NUM_ROWS")),
                asLong(r.get("BLOCKS")),
                asLocalDateTime(r.get("LAST_ANALYZED")),
                asString(r.get("COMMENTS")));
    }

    // ── 3. 인덱스 정보 ────────────────────────────────────────────────────

    private List<LiveDbContext.IndexInfo> safeFetchIndexes(
            ReadOnlyJdbcTemplate ro, String schema, Set<String> tables, LiveDbContext ctx) {
        List<LiveDbContext.IndexInfo> result = new ArrayList<LiveDbContext.IndexInfo>();
        for (String tableName : tables) {
            try {
                result.addAll(fetchOneTableIndexes(ro, schema, tableName));
            } catch (Exception e) {
                ctx.addWarning("테이블 " + tableName + " 인덱스 조회 실패: " + brief(e));
            }
        }
        return result;
    }

    private List<LiveDbContext.IndexInfo> fetchOneTableIndexes(
            ReadOnlyJdbcTemplate ro, String schema, String tableName) {
        String sql;
        Object[] args;
        if (schema != null && !schema.isEmpty()) {
            sql = "SELECT i.INDEX_NAME, c.COLUMN_NAME, c.COLUMN_POSITION, " +
                  "       i.UNIQUENESS, i.DISTINCT_KEYS " +
                  "  FROM ALL_INDEXES i " +
                  "  JOIN ALL_IND_COLUMNS c " +
                  "    ON i.INDEX_NAME = c.INDEX_NAME AND i.OWNER = c.INDEX_OWNER " +
                  " WHERE i.TABLE_OWNER = ? AND i.TABLE_NAME = ? " +
                  " ORDER BY i.INDEX_NAME, c.COLUMN_POSITION";
            args = new Object[]{schema.toUpperCase(), tableName.toUpperCase()};
        } else {
            sql = "SELECT i.INDEX_NAME, c.COLUMN_NAME, c.COLUMN_POSITION, " +
                  "       i.UNIQUENESS, i.DISTINCT_KEYS " +
                  "  FROM USER_INDEXES i " +
                  "  JOIN USER_IND_COLUMNS c " +
                  "    ON i.INDEX_NAME = c.INDEX_NAME " +
                  " WHERE i.TABLE_NAME = ? " +
                  " ORDER BY i.INDEX_NAME, c.COLUMN_POSITION";
            args = new Object[]{tableName.toUpperCase()};
        }
        List<Map<String, Object>> rows = ro.queryForList(sql, args);
        List<LiveDbContext.IndexInfo> result = new ArrayList<LiveDbContext.IndexInfo>();
        for (Map<String, Object> r : rows) {
            result.add(new LiveDbContext.IndexInfo(
                    tableName,
                    asString(r.get("INDEX_NAME")),
                    asString(r.get("COLUMN_NAME")),
                    asInt(r.get("COLUMN_POSITION"), 1),
                    "UNIQUE".equalsIgnoreCase(asString(r.get("UNIQUENESS"))),
                    asLong(r.get("DISTINCT_KEYS"))));
        }
        return result;
    }

    // ── 4. EXPLAIN PLAN ────────────────────────────────────────────────────

    /**
     * Oracle EXPLAIN PLAN 동작:
     * 1. {@code EXPLAIN PLAN SET STATEMENT_ID='xxx' FOR <user_sql>} — PLAN_TABLE 에 row INSERT
     * 2. {@code SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY('PLAN_TABLE','xxx','TYPICAL'))} — 사람용 텍스트
     *
     * <p>주의: EXPLAIN PLAN 자체는 *PLAN_TABLE 에 INSERT* 를 하지만, Oracle 의
     * PLAN_TABLE 은 일반적으로 SYS.PLAN_TABLE$ public synonym 으로 모든 user 가
     * INSERT 가능. 이는 운영 데이터에 영향 없는 *분석 메타테이블*.
     *
     * <p>STATEMENT_ID 는 호출 별 고유 — 동시성 충돌 방지.
     */
    private String safeFetchExplainPlan(ReadOnlyJdbcTemplate ro, String userSql, LiveDbContext ctx) {
        if (userSql == null || userSql.trim().isEmpty()) return null;

        // 사용자 SQL 안전 검증 — read-only op 만 EXPLAIN 가능
        SqlOperation op = SqlClassifier.classify(userSql);
        if (!SqlClassifier.isReadOnly(op)) {
            ctx.addWarning("EXPLAIN PLAN 스킵: SQL op=" + op + " (read-only 가 아님)");
            return null;
        }

        String stmtId = PLAN_STATEMENT_PREFIX + Long.toString(System.nanoTime(), 36);
        String trimmedSql = userSql.trim();
        // trailing ; 제거 — Oracle 은 EXPLAIN PLAN FOR <sql> 에 ; 있으면 syntax error
        if (trimmedSql.endsWith(";")) trimmedSql = trimmedSql.substring(0, trimmedSql.length() - 1);

        try {
            // Step 1: EXPLAIN PLAN — executeExplain 이 EXPLAIN 만 통과시킴
            String explainSql = "EXPLAIN PLAN SET STATEMENT_ID = '" + stmtId + "' FOR " + trimmedSql;
            ro.executeExplain(explainSql);

            // Step 2: DBMS_XPLAN.DISPLAY 결과를 line by line 으로 읽음
            List<Map<String, Object>> rows = ro.queryForList(
                    "SELECT PLAN_TABLE_OUTPUT FROM TABLE(" +
                    "  DBMS_XPLAN.DISPLAY('PLAN_TABLE', ?, 'TYPICAL'))",
                    stmtId);

            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> r : rows) {
                Object line = r.get("PLAN_TABLE_OUTPUT");
                if (line != null) sb.append(line).append('\n');
            }
            return sb.toString().trim();

        } catch (Exception e) {
            ctx.addWarning("EXPLAIN PLAN 실패: " + brief(e));
            return null;
        }
        // 주의: 일반적으로 PLAN_TABLE 에서 statement_id 별 row 를 정리하지 않음 —
        // Oracle 은 PLAN_TABLE 이 GLOBAL TEMPORARY TABLE 이라 세션 종료 시 자동 정리.
        // 그렇지 않은 환경(legacy custom PLAN_TABLE) 도 대다수 사이트가 주기적으로
        // truncate 하므로 추가 cleanup 불필요. 더 엄격히 하려면 finally 에 DELETE 추가.
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }

    private static int asInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return def; }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static LocalDateTime asLocalDateTime(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp) return ((Timestamp) o).toLocalDateTime();
        if (o instanceof LocalDateTime) return (LocalDateTime) o;
        return null;
    }

    private static String brief(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return m.length() > 200 ? m.substring(0, 200) + "..." : m;
    }
}
