package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 3: PostgresLiveDbContextProvider 단위 테스트.
 *
 * <p>실제 PostgreSQL 없이 {@link FakeReadOnlyJdbcTemplate} 으로 응답을 stub.
 * Mockito 의 varargs 매칭 이슈를 회피하기 위해 단순 *상속 기반 fake* 사용.
 */
class PostgresLiveDbContextProviderTest {

    private PostgresLiveDbContextProvider provider;
    private FakeReadOnlyJdbcTemplate fake;

    @BeforeEach
    void setup() {
        provider = new PostgresLiveDbContextProvider();
        fake     = new FakeReadOnlyJdbcTemplate();
    }

    @Test
    @DisplayName("getDbType — 'postgres' 반환")
    void dbType() {
        assertEquals("postgres", provider.getDbType());
    }

    @Test
    @DisplayName("정상 흐름 — 버전 + 테이블 + 인덱스 + EXPLAIN 모두 수집")
    void happyPath() {
        fake.versionAnswer = "PostgreSQL 14.5 on x86_64-pc-linux-gnu, compiled by gcc 9.4.0";
        fake.queryAnswer = sql -> {
            if (sql.contains("FROM pg_class c")) {
                return rows(row(
                        "num_rows",      12_450_000L,
                        "blocks",        142_000L,
                        "last_analyzed", null,
                        "comments",      "주문 마스터"));
            }
            if (sql.contains("FROM pg_class t")) {
                return rows(
                    row("index_name", "idx_date_status",
                        "column_name", "order_date",
                        "column_position", 1,
                        "is_unique", false,
                        "distinct_keys", 5000L),
                    row("index_name", "idx_date_status",
                        "column_name", "status",
                        "column_position", 2,
                        "is_unique", false,
                        "distinct_keys", 5000L));
            }
            if (sql.contains("EXPLAIN")) {
                return rows(
                    row("QUERY PLAN", "Seq Scan on t_order  (cost=0.00..100.00 rows=1000 width=200)"),
                    row("QUERY PLAN", "  Filter: (status = 'Y'::text)"),
                    row("QUERY PLAN", "Planning time: 0.123 ms"));
            }
            return Collections.emptyList();
        };

        LiveDbContext ctx = provider.fetch("SELECT * FROM T_ORDER WHERE STATUS = 'Y'", null, fake);

        assertNotNull(ctx);
        assertTrue(ctx.getDbmsVersion().startsWith("PostgreSQL 14.5"),
                   "버전 prefix 보존: " + ctx.getDbmsVersion());
        assertTrue(ctx.getDbmsVersion().length() < 80, "version() 결과 잘림: " + ctx.getDbmsVersion());

        assertEquals(1, ctx.getTables().size());
        assertEquals("T_ORDER",          ctx.getTables().get(0).name);
        assertEquals(Long.valueOf(12_450_000L), ctx.getTables().get(0).numRows);
        assertEquals("주문 마스터",       ctx.getTables().get(0).comment);

        assertEquals(2, ctx.getIndexes().size());
        assertEquals("idx_date_status", ctx.getIndexes().get(0).indexName);
        assertEquals("order_date",      ctx.getIndexes().get(0).columnName);
        assertEquals(1,                 ctx.getIndexes().get(0).columnPosition);
        assertEquals("status",          ctx.getIndexes().get(1).columnName);
        assertEquals(2,                 ctx.getIndexes().get(1).columnPosition);

        assertNotNull(ctx.getExplainPlanFormatted());
        assertTrue(ctx.getExplainPlanFormatted().contains("Seq Scan"));
        assertTrue(ctx.getExplainPlanFormatted().contains("Planning time"));

        assertTrue(ctx.getWarnings().isEmpty(), "정상 흐름은 warnings 없음, 실제: " + ctx.getWarnings());
    }

    @Test
    @DisplayName("EXPLAIN 실패 — graceful warning + 다른 정보는 보존")
    void explainFails_warningAdded() {
        fake.versionAnswer = "PostgreSQL 13.4";
        fake.queryAnswer = sql -> {
            if (sql.contains("EXPLAIN")) {
                throw new RuntimeException("permission denied for relation t_order");
            }
            return Collections.emptyList();
        };

        LiveDbContext ctx = provider.fetch("SELECT * FROM T_ORDER", null, fake);

        assertNotNull(ctx);
        assertEquals("PostgreSQL 13.4", ctx.getDbmsVersion());
        assertNull(ctx.getExplainPlanFormatted());
        boolean hasExplainWarning = false;
        for (String w : ctx.getWarnings()) {
            if (w.contains("EXPLAIN PLAN 실패")) hasExplainWarning = true;
        }
        assertTrue(hasExplainWarning,
                   "EXPLAIN 실패 warning 누적되어야 함, 실제: " + ctx.getWarnings());
    }

    @Test
    @DisplayName("read-only 가 아닌 SQL — EXPLAIN 스킵 + warning")
    void nonReadOnlySql_skipped() {
        fake.versionAnswer = "PostgreSQL 14";
        fake.queryAnswer = sql -> Collections.emptyList();

        LiveDbContext ctx = provider.fetch("DELETE FROM T_ORDER", null, fake);

        assertNull(ctx.getExplainPlanFormatted());
        boolean hasSkipWarning = false;
        for (String w : ctx.getWarnings()) {
            if (w.contains("EXPLAIN PLAN 스킵")) hasSkipWarning = true;
        }
        assertTrue(hasSkipWarning, "read-only 아님 → EXPLAIN 스킵 warning 필요");

        // EXPLAIN 호출 자체는 안 일어났는지 확인
        boolean explainCalled = false;
        for (String sql : fake.recordedQueries) {
            if (sql.contains("EXPLAIN")) explainCalled = true;
        }
        assertFalse(explainCalled, "EXPLAIN 호출 발생: " + fake.recordedQueries);
    }

    @Test
    @DisplayName("trailing semicolon — EXPLAIN 보낼 때 제거")
    void trailingSemicolonStripped() {
        fake.versionAnswer = "PostgreSQL";
        fake.queryAnswer = sql -> {
            if (sql.contains("EXPLAIN")) return rows(row("QUERY PLAN", "ok"));
            return Collections.emptyList();
        };

        provider.fetch("SELECT 1 FROM T;", null, fake);

        // EXPLAIN 호출의 SQL 인자에 ; 가 없어야 함
        boolean foundTrimmedExplain = false;
        for (String sql : fake.recordedQueries) {
            if ("EXPLAIN (FORMAT TEXT, COSTS true) SELECT 1 FROM T".equals(sql)) {
                foundTrimmedExplain = true; break;
            }
        }
        assertTrue(foundTrimmedExplain,
                   "trailing ; 없는 EXPLAIN 호출 발견되지 않음: " + fake.recordedQueries);
    }

    @Test
    @DisplayName("FROM 없는 SQL — 테이블/인덱스 쿼리 호출 안 함, EXPLAIN 만")
    void noFromClause_skipsCatalog() {
        fake.versionAnswer = "PostgreSQL";
        fake.queryAnswer = sql -> {
            if (sql.contains("EXPLAIN")) return rows(row("QUERY PLAN", "Result  (cost=0..0)"));
            return Collections.emptyList();
        };

        LiveDbContext ctx = provider.fetch("SELECT 1", null, fake);

        // 테이블 추출 0건 → pg_class 쿼리 호출 0회
        for (String sql : fake.recordedQueries) {
            assertFalse(sql.contains("FROM pg_class"),
                        "테이블 없는 SELECT 인데 pg_class 호출됨: " + sql);
        }
        assertNotNull(ctx.getExplainPlanFormatted());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static List<Map<String, Object>> rows(Map<String, Object>... rows) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> r : rows) list.add(r);
        return list;
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /**
     * 단순 상속 기반 fake — Mockito varargs 이슈 회피.
     * SQL 문자열 → 응답 mapping 을 람다로 받아 dispatch.
     */
    static class FakeReadOnlyJdbcTemplate extends ReadOnlyJdbcTemplate {
        String versionAnswer;
        Function<String, List<Map<String, Object>>> queryAnswer = sql -> Collections.emptyList();
        final List<String> recordedQueries = new ArrayList<>();

        FakeReadOnlyJdbcTemplate() {
            super(makeDummyDataSource(), enabledConfig(), "fake-test");
        }

        private static DriverManagerDataSource makeDummyDataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:fake_postgres_provider_test");
            ds.setUsername("sa"); ds.setPassword("");
            return ds;
        }

        private static LiveDbConfig enabledConfig() {
            LiveDbConfig c = new LiveDbConfig();
            c.setEnabled(true);
            return c;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            recordedQueries.add(sql);
            if ("SELECT version()".equals(sql) && type == String.class) {
                return type.cast(versionAnswer);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            recordedQueries.add(sql);
            return queryAnswer.apply(sql);
        }
    }
}
