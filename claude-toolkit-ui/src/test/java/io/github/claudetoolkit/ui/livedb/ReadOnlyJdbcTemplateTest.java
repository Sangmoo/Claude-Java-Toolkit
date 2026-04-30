package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 0: ReadOnlyJdbcTemplate 보안 게이트 검증.
 *
 * <p>실제 H2 in-memory DB 를 사용 — Spring Boot 컨텍스트 없이 단순한 통합 테스트.
 * 통과해야 할 시나리오 + 거부해야 할 시나리오 모두.
 */
class ReadOnlyJdbcTemplateTest {

    private static DataSource ds;
    private LiveDbConfig config;
    private ReadOnlyJdbcTemplate ro;

    @BeforeAll
    static void setupDb() {
        // 매 테스트마다 같은 H2 in-mem URL — DB_CLOSE_DELAY=-1 로 JVM 종료까지 유지
        DriverManagerDataSource d = new DriverManagerDataSource();
        d.setDriverClassName("org.h2.Driver");
        d.setUrl("jdbc:h2:mem:livedb_ro_test;DB_CLOSE_DELAY=-1;MODE=Oracle");
        d.setUsername("sa");
        d.setPassword("");
        ds = d;

        // 테스트 테이블 1회만 생성
        new org.springframework.jdbc.core.JdbcTemplate(ds).execute(
                "CREATE TABLE IF NOT EXISTS T_TEST (ID INT PRIMARY KEY, NAME VARCHAR(50))");
        new org.springframework.jdbc.core.JdbcTemplate(ds).execute(
                "MERGE INTO T_TEST KEY(ID) VALUES (1, 'Alice')");
        new org.springframework.jdbc.core.JdbcTemplate(ds).execute(
                "MERGE INTO T_TEST KEY(ID) VALUES (2, 'Bob')");
    }

    @BeforeEach
    void setupRo() {
        config = new LiveDbConfig();
        config.setEnabled(true);  // 대부분 테스트는 enabled 가정
        config.setDefaultTimeoutSeconds(30);
        config.setMaxRows(100);
        ro = new ReadOnlyJdbcTemplate(ds, config, "test-profile");
    }

    // ── 통과 케이스 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("SELECT 정상 실행 — 결과 반환")
    void select_works() {
        List<Map<String, Object>> rows = ro.queryForList(
                "SELECT ID, NAME FROM T_TEST ORDER BY ID");
        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).get("NAME"));
    }

    @Test
    @DisplayName("queryForObject — 단일 결과 OK")
    void queryForObject_works() {
        Long count = ro.queryForObject(
                "SELECT COUNT(*) FROM T_TEST", Long.class);
        assertNotNull(count);
        assertEquals(2L, count.longValue());
    }

    @Test
    @DisplayName("WITH (CTE) 통과")
    void withCte_works() {
        List<Map<String, Object>> rows = ro.queryForList(
                "WITH cte AS (SELECT * FROM T_TEST WHERE ID = 1) SELECT NAME FROM cte");
        assertEquals(1, rows.size());
        assertEquals("Alice", rows.get(0).get("NAME"));
    }

    // ── 거부 케이스 (security critical) ──────────────────────────────────

    @Test
    @DisplayName("INSERT 거부 — SecurityException")
    void insert_rejected() {
        SecurityException e = assertThrows(SecurityException.class, () ->
                ro.queryForList("INSERT INTO T_TEST VALUES (3, 'Charlie')"));
        assertTrue(e.getMessage().contains("SELECT"), "에러 메시지에 허용 op 안내: " + e.getMessage());
    }

    @Test
    @DisplayName("UPDATE 거부 — SecurityException")
    void update_rejected() {
        assertThrows(SecurityException.class, () ->
                ro.queryForList("UPDATE T_TEST SET NAME='X' WHERE ID=1"));
    }

    @Test
    @DisplayName("DELETE 거부 — SecurityException")
    void delete_rejected() {
        assertThrows(SecurityException.class, () ->
                ro.queryForList("DELETE FROM T_TEST"));
    }

    @Test
    @DisplayName("DROP TABLE 거부 — SecurityException")
    void drop_rejected() {
        assertThrows(SecurityException.class, () ->
                ro.queryForList("DROP TABLE T_TEST"));
    }

    @Test
    @DisplayName("멀티 statement (SELECT + DELETE) 거부 — SecurityException")
    void multiStatement_rejected() {
        // 이 케이스가 통과하면 운영 사고. SqlClassifier 가 UNKNOWN 으로 분류해서 거부해야 함.
        assertThrows(SecurityException.class, () ->
                ro.queryForList("SELECT 1 FROM T_TEST; DELETE FROM T_TEST"));
    }

    @Test
    @DisplayName("EXEC PROC 거부 — SecurityException")
    void exec_rejected() {
        assertThrows(SecurityException.class, () ->
                ro.queryForList("EXEC dangerous_proc()"));
    }

    @Test
    @DisplayName("BEGIN/END 익명 PL/SQL 블록 거부")
    void anonymousPlsql_rejected() {
        assertThrows(SecurityException.class, () ->
                ro.queryForList("BEGIN DELETE FROM T_TEST; END;"));
    }

    // ── feature flag 비활성 ─────────────────────────────────────────────

    @Test
    @DisplayName("toolkit.livedb.enabled=false 일 땐 SELECT 도 IllegalStateException 으로 거부")
    void disabled_rejectsEvenSelect() {
        config.setEnabled(false);
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                ro.queryForList("SELECT 1 FROM T_TEST"));
        assertTrue(e.getMessage().contains("livedb.enabled"),
                   "kill switch 안내 메시지: " + e.getMessage());
    }

    // ── executeExplain ──────────────────────────────────────────────────

    @Test
    @DisplayName("executeExplain — EXPLAIN 만 통과, 다른 op 거부")
    void executeExplain_onlyExplain() {
        // SELECT 는 executeExplain 으로 시도하면 거부 (EXPLAIN 만 허용)
        assertThrows(SecurityException.class, () ->
                ro.executeExplain("SELECT 1 FROM T_TEST"));
        assertThrows(SecurityException.class, () ->
                ro.executeExplain("DELETE FROM T_TEST"));

        // EXPLAIN 은 통과 (H2 의 EXPLAIN 지원)
        assertDoesNotThrow(() ->
                ro.executeExplain("EXPLAIN SELECT * FROM T_TEST"));
    }
}
