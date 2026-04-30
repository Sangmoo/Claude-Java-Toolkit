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
 * v4.7.x — #G3 Live DB Phase 3: PostgreSQL 11+ 용 컨텍스트 수집기.
 *
 * <p>Oracle 과 동일한 4가지 정보 수집:
 * <ol>
 *   <li>버전 ({@code SELECT version()})</li>
 *   <li>테이블 통계 ({@code pg_class.reltuples} + {@code pg_stat_user_tables})</li>
 *   <li>인덱스 ({@code pg_index} + {@code pg_attribute} + 컬럼 위치 보존)</li>
 *   <li>EXPLAIN ({@code EXPLAIN (FORMAT TEXT) <sql>} — Oracle 처럼 PLAN_TABLE 사용 X,
 *       row 결과를 직접 반환받아 join)</li>
 * </ol>
 *
 * <p><b>Oracle 과의 차이점:</b>
 * <ul>
 *   <li>EXPLAIN 이 *row 결과를 직접 반환* — DBMS_XPLAN 같은 후속 호출 불필요</li>
 *   <li>cardinality 정보가 {@code pg_stats.n_distinct} 에 (음수면 비율)</li>
 *   <li>schema 는 *current_schema()* 가 default — DbProfile.username 이 schema 가 아님</li>
 * </ul>
 *
 * <p>Phase 3 1차 출시 시 schema 처리는 *현재 search_path* 의존 — 사용자가 다른
 * schema 의 테이블을 보려면 SET search_path 또는 schema-qualified 이름 필요.
 */
public class PostgresLiveDbContextProvider implements LiveDbContextProvider {

    private static final Logger log = LoggerFactory.getLogger(PostgresLiveDbContextProvider.class);

    @Override
    public String getDbType() { return "postgres"; }

    @Override
    public LiveDbContext fetch(String userSql, String schema, ReadOnlyJdbcTemplate ro) {
        LiveDbContext ctx = new LiveDbContext();

        ctx.setDbmsVersion(safeFetchVersion(ro, ctx));

        Set<String> tables = SqlTableExtractor.extract(userSql);
        ctx.setReferencedTables(new ArrayList<String>(tables));
        if (!tables.isEmpty()) {
            ctx.setTables(safeFetchTableStats(ro, schema, tables, ctx));
            ctx.setIndexes(safeFetchIndexes(ro, schema, tables, ctx));
        }

        ctx.setExplainPlanFormatted(safeFetchExplainPlan(ro, userSql, ctx));

        return ctx;
    }

    // ── 1. 버전 ───────────────────────────────────────────────────────────

    private String safeFetchVersion(ReadOnlyJdbcTemplate ro, LiveDbContext ctx) {
        try {
            // PostgreSQL 의 version() 출력은 매우 길어서 첫 줄만 잘라 사용 (전체 ~100자)
            String v = ro.queryForObject("SELECT version()", String.class);
            if (v == null) return null;
            // "PostgreSQL 14.5 on x86_64-pc-linux-gnu, ..." → 처음 30자
            int comma = v.indexOf(',');
            return comma > 0 ? v.substring(0, comma) : (v.length() > 60 ? v.substring(0, 60) + "..." : v);
        } catch (Exception e) {
            ctx.addWarning("PostgreSQL 버전 조회 실패: " + brief(e));
            return null;
        }
    }

    // ── 2. 테이블 통계 ────────────────────────────────────────────────────

    /**
     * PostgreSQL 의 통계는 <b>autovacuum 시점</b> 에 갱신되므로 reltuples 가
     * 추정치 (정확하지 않을 수 있음 — 마지막 ANALYZE 시점이 중요).
     *
     * <p>PostgreSQL 의 NUM_ROWS 대응: {@code pg_class.reltuples}.
     * BLOCKS 대응: {@code pg_class.relpages}.
     * LAST_ANALYZED: {@code pg_stat_user_tables.last_analyze}
     * (autovacuum 만 동작했으면 last_autoanalyze 사용).
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
        // PostgreSQL 식별자는 *대소문자 구분* — 일반적으로 lowercase 가 표준
        String lname = tableName.toLowerCase();

        // pg_class + pg_stat_user_tables + pg_description 조인
        // schema 가 null 이면 current_schema() 사용
        String sql =
            "SELECT c.reltuples::bigint AS num_rows, " +
            "       c.relpages::bigint  AS blocks, " +
            "       GREATEST(s.last_analyze, s.last_autoanalyze) AS last_analyzed, " +
            "       obj_description(c.oid, 'pg_class') AS comments " +
            "  FROM pg_class c " +
            "  JOIN pg_namespace n ON n.oid = c.relnamespace " +
            "  LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid " +
            " WHERE c.relkind IN ('r', 'p') " +
            "   AND c.relname = ? " +
            "   AND n.nspname = COALESCE(?, current_schema())";

        Object[] args = new Object[]{lname, schema != null && !schema.isEmpty() ? schema.toLowerCase() : null};
        List<Map<String, Object>> rows = ro.queryForList(sql, args);
        if (rows.isEmpty()) return null;
        Map<String, Object> r = rows.get(0);
        return new LiveDbContext.TableStats(
                tableName,
                asLong(r.get("num_rows")),
                asLong(r.get("blocks")),
                asLocalDateTime(r.get("last_analyzed")),
                asString(r.get("comments")));
    }

    // ── 3. 인덱스 ────────────────────────────────────────────────────────

    /**
     * PostgreSQL 인덱스는 {@code pg_index.indkey} (smallint[]) 에 컬럼 attnum 이
     * 순서대로 저장. {@code unnest WITH ORDINALITY} 로 컬럼 위치 추출.
     *
     * <p>distinct_keys 는 {@code pg_stats.n_distinct} — 음수면 행 비율 (e.g. -0.5
     * 는 행의 50% 가 distinct), 양수면 절대 추정값. UI 단순화를 위해 양수만 채택.
     */
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
        String lname = tableName.toLowerCase();

        String sql =
            "SELECT i.relname AS index_name, " +
            "       a.attname AS column_name, " +
            "       k.col_pos AS column_position, " +
            "       ix.indisunique AS is_unique, " +
            "       CASE WHEN s.n_distinct > 0 THEN s.n_distinct::bigint ELSE NULL END AS distinct_keys " +
            "  FROM pg_class t " +
            "  JOIN pg_namespace n ON n.oid = t.relnamespace " +
            "  JOIN pg_index ix ON t.oid = ix.indrelid " +
            "  JOIN pg_class i ON i.oid = ix.indexrelid " +
            "  JOIN LATERAL unnest(ix.indkey::int[]) WITH ORDINALITY AS k(attnum, col_pos) ON true " +
            "  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum " +
            "  LEFT JOIN pg_stats s ON s.tablename = t.relname AND s.attname = a.attname " +
            " WHERE t.relkind IN ('r', 'p') " +
            "   AND t.relname = ? " +
            "   AND n.nspname = COALESCE(?, current_schema()) " +
            " ORDER BY i.relname, k.col_pos";

        Object[] args = new Object[]{lname, schema != null && !schema.isEmpty() ? schema.toLowerCase() : null};
        List<Map<String, Object>> rows = ro.queryForList(sql, args);
        List<LiveDbContext.IndexInfo> result = new ArrayList<LiveDbContext.IndexInfo>();
        for (Map<String, Object> r : rows) {
            result.add(new LiveDbContext.IndexInfo(
                    tableName,
                    asString(r.get("index_name")),
                    asString(r.get("column_name")),
                    asInt(r.get("column_position"), 1),
                    Boolean.TRUE.equals(r.get("is_unique")),
                    asLong(r.get("distinct_keys"))));
        }
        return result;
    }

    // ── 4. EXPLAIN ────────────────────────────────────────────────────────

    /**
     * PostgreSQL 의 EXPLAIN 은 row 결과를 *직접* 반환 (Oracle 처럼 PLAN_TABLE 우회 불필요).
     *
     * <p>{@code EXPLAIN (FORMAT TEXT, COSTS true) <user_sql>} 사용 — ANALYZE 옵션은
     * 쿼리를 *실제로 실행* 하므로 사용 X (read-only 라도 부하 발생 가능).
     */
    private String safeFetchExplainPlan(ReadOnlyJdbcTemplate ro, String userSql, LiveDbContext ctx) {
        if (userSql == null || userSql.trim().isEmpty()) return null;

        SqlOperation op = SqlClassifier.classify(userSql);
        if (!SqlClassifier.isReadOnly(op)) {
            ctx.addWarning("EXPLAIN PLAN 스킵: SQL op=" + op + " (read-only 가 아님)");
            return null;
        }

        String trimmedSql = userSql.trim();
        if (trimmedSql.endsWith(";")) trimmedSql = trimmedSql.substring(0, trimmedSql.length() - 1);

        try {
            // EXPLAIN 결과는 row 별로 한 줄씩 반환. column 이름은 "QUERY PLAN" (공백 포함).
            // queryForList 는 SqlClassifier.isReadOnly 가 EXPLAIN 도 통과시키므로 OK.
            List<Map<String, Object>> rows = ro.queryForList(
                    "EXPLAIN (FORMAT TEXT, COSTS true) " + trimmedSql);

            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> r : rows) {
                // column 이름이 환경에 따라 "QUERY PLAN" / "QUERY_PLAN" / 다를 수 있어 첫 value 사용
                if (r.isEmpty()) continue;
                Object first = r.values().iterator().next();
                if (first != null) sb.append(first).append('\n');
            }
            return sb.toString().trim();

        } catch (Exception e) {
            ctx.addWarning("EXPLAIN PLAN 실패: " + brief(e));
            return null;
        }
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
