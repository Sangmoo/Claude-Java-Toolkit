package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 4: OracleIndexSimulator 의 *순수 함수* + 안전장치 검증.
 *
 * <p>실제 Oracle 없이 검증할 수 있는 부분만 단위 테스트:
 * <ul>
 *   <li>{@link OracleIndexSimulator#rewriteToSimIndex} — DDL 재작성 로직</li>
 *   <li>read-only 검증 — DML 거부</li>
 *   <li>인덱스 5개 상한 — 6개 보내면 SecurityException</li>
 *   <li>빈 인덱스 리스트 거부</li>
 * </ul>
 *
 * <p>실제 EXPLAIN PLAN + INVISIBLE INDEX 시퀀스는 Oracle 통합 테스트 (별도) 가 검증.
 * H2 는 INVISIBLE INDEX / DBMS_XPLAN 미지원이라 단위 테스트로는 한계.
 */
class OracleIndexSimulatorTest {

    private final OracleIndexSimulator simulator = new OracleIndexSimulator();

    // ── DDL 재작성 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("rewriteToSimIndex — 사용자 이름 무시 + CTK_SIM_ prefix 강제")
    void rewriteRenamesIndex() {
        OracleIndexSimulator.SimIndex out = OracleIndexSimulator.rewriteToSimIndex(
                "CREATE INDEX MY_USER_INDEX ON T_ORDER (ORDER_DATE, STATUS)", 0);
        assertTrue(out.simName.startsWith(OracleIndexSimulator.SIM_INDEX_PREFIX),
                   "이름 prefix: " + out.simName);
        assertFalse(out.simName.contains("MY_USER_INDEX"),
                    "사용자 이름이 사라져야 — 충돌 방지: " + out.simName);
    }

    @Test
    @DisplayName("rewriteToSimIndex — INVISIBLE 옵션 자동 추가 (운영 영향 0)")
    void rewriteAddsInvisible() {
        OracleIndexSimulator.SimIndex out = OracleIndexSimulator.rewriteToSimIndex(
                "CREATE INDEX X ON T (A, B)", 0);
        assertTrue(out.simDdl.toUpperCase().endsWith("INVISIBLE"),
                   "INVISIBLE 옵션 강제: " + out.simDdl);
    }

    @Test
    @DisplayName("rewriteToSimIndex — UNIQUE INDEX 보존")
    void rewriteUniquePreserved() {
        OracleIndexSimulator.SimIndex out = OracleIndexSimulator.rewriteToSimIndex(
                "CREATE UNIQUE INDEX UQ_X ON T (A)", 0);
        assertTrue(out.simDdl.toUpperCase().contains("UNIQUE"),
                   "UNIQUE 보존: " + out.simDdl);
    }

    @Test
    @DisplayName("rewriteToSimIndex — 테이블 + 컬럼 보존")
    void rewriteTableColumnsPreserved() {
        OracleIndexSimulator.SimIndex out = OracleIndexSimulator.rewriteToSimIndex(
                "CREATE INDEX X ON T_ORDER (ORDER_DATE, STATUS, CUSTOMER_ID)", 0);
        assertTrue(out.simDdl.contains("T_ORDER"),         "테이블 보존: " + out.simDdl);
        assertTrue(out.simDdl.contains("ORDER_DATE"),      "컬럼 1 보존: " + out.simDdl);
        assertTrue(out.simDdl.contains("STATUS"),          "컬럼 2 보존: " + out.simDdl);
        assertTrue(out.simDdl.contains("CUSTOMER_ID"),     "컬럼 3 보존: " + out.simDdl);
    }

    @Test
    @DisplayName("rewriteToSimIndex — trailing semicolon 제거")
    void rewriteStripsTrailingSemicolon() {
        OracleIndexSimulator.SimIndex out = OracleIndexSimulator.rewriteToSimIndex(
                "CREATE INDEX X ON T (A);", 0);
        assertFalse(out.simDdl.contains(";"),
                    "; 는 INVISIBLE 뒤에 와도 위험 — 제거: " + out.simDdl);
    }

    @Test
    @DisplayName("rewriteToSimIndex — 잘못된 DDL 거부 (CREATE INDEX 패턴 아님)")
    void rewriteRejectsBadDdl() {
        assertThrows(IllegalArgumentException.class, () ->
                OracleIndexSimulator.rewriteToSimIndex("DROP INDEX X", 0));
        assertThrows(IllegalArgumentException.class, () ->
                OracleIndexSimulator.rewriteToSimIndex("DELETE FROM T", 0));
        assertThrows(IllegalArgumentException.class, () ->
                OracleIndexSimulator.rewriteToSimIndex("not even sql", 0));
        assertThrows(IllegalArgumentException.class, () ->
                OracleIndexSimulator.rewriteToSimIndex(null, 0));
    }

    // ── 안전장치: simulate() 의 검증만 ──────────────────────────────────────

    @Test
    @DisplayName("simulate — read-only 가 아닌 SQL 거부 (SecurityException)")
    void simulateRejectsNonReadOnly() {
        DataSource ds = dummyDs();
        SecurityException e = assertThrows(SecurityException.class, () ->
                simulator.simulate(
                        "DELETE FROM T_ORDER",
                        Collections.singletonList("CREATE INDEX X ON T_ORDER (A)"),
                        ds));
        assertTrue(e.getMessage().contains("read-only"));
    }

    @Test
    @DisplayName("simulate — 인덱스 5개 초과 거부")
    void simulateRejectsMoreThanFiveIndexes() {
        DataSource ds = dummyDs();
        List<String> tooMany = Arrays.asList(
                "CREATE INDEX A ON T (A)",
                "CREATE INDEX B ON T (B)",
                "CREATE INDEX C ON T (C)",
                "CREATE INDEX D ON T (D)",
                "CREATE INDEX E ON T (E)",
                "CREATE INDEX F ON T (F)");  // 6개
        SecurityException e = assertThrows(SecurityException.class, () ->
                simulator.simulate("SELECT 1 FROM DUAL", tooMany, ds));
        assertTrue(e.getMessage().contains("5"), "5 상한 안내 메시지: " + e.getMessage());
        assertTrue(e.getMessage().contains("6"), "요청 수 표시: " + e.getMessage());
    }

    @Test
    @DisplayName("simulate — 빈 인덱스 리스트 거부")
    void simulateRejectsEmptyIndexes() {
        DataSource ds = dummyDs();
        assertThrows(SecurityException.class, () ->
                simulator.simulate("SELECT 1 FROM DUAL", Collections.emptyList(), ds));
        assertThrows(SecurityException.class, () ->
                simulator.simulate("SELECT 1 FROM DUAL", null, ds));
    }

    @Test
    @DisplayName("simulate — 5개 정확히 (경계값) 는 통과 — 잘못된 SQL 검증을 통과해 DataSource 에서 실패")
    void simulateAllowsExactlyFive() {
        DataSource ds = dummyDs();  // H2 — Oracle SQL 문법은 실패할 것
        List<String> five = Arrays.asList(
                "CREATE INDEX A ON T (A)",
                "CREATE INDEX B ON T (B)",
                "CREATE INDEX C ON T (C)",
                "CREATE INDEX D ON T (D)",
                "CREATE INDEX E ON T (E)");
        // SecurityException 은 던지지 않아야 — H2 가 ALTER SESSION 등에서 실패해 warning 누적은 OK
        IndexSimulationResult r = simulator.simulate("SELECT 1 FROM DUAL", five, ds);
        assertNotNull(r);
        // simulatedIndexes 가 5개로 채워졌는지 (재작성 후) — 실제 실행 실패해도 재작성은 됨
        assertEquals(5, r.getSimulatedIndexes().size());
        for (String d : r.getSimulatedIndexes()) {
            assertTrue(d.toUpperCase().contains("INVISIBLE"), "INVISIBLE 강제: " + d);
            assertTrue(d.contains(OracleIndexSimulator.SIM_INDEX_PREFIX), "prefix 강제: " + d);
        }
    }

    @Test
    @DisplayName("getDbType — 'oracle' 반환")
    void dbType() {
        assertEquals("oracle", simulator.getDbType());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private DataSource dummyDs() {
        // 실제 SQL 실행은 하지 않지만 DataSource 인스턴스 필요
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:idx_sim_test");
        ds.setUsername("sa"); ds.setPassword("");
        return ds;
    }
}
