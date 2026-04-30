package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 0: SqlClassifier 의 *security boundary* 검증.
 *
 * <p>이 클래스는 운영 사고를 직접 막는 게이트키퍼이므로 *모든* 위험 케이스를
 * 명시적으로 거부 (UNKNOWN/DML/DDL/CALL 등) 해야 한다. 새로운 SQL injection
 * 회피 케이스를 발견하면 *반드시 여기 추가*.
 */
class SqlClassifierTest {

    // ── 통과 (read-only) ─────────────────────────────────────────────────

    @Test
    @DisplayName("SELECT 단순 — SELECT 분류")
    void select_simple() {
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify("SELECT * FROM T"));
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify("select 1 from dual"));
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify("Select COUNT(*) FROM T"));
    }

    @Test
    @DisplayName("EXPLAIN PLAN — EXPLAIN 분류")
    void explain() {
        assertEquals(SqlOperation.EXPLAIN, SqlClassifier.classify("EXPLAIN PLAN FOR SELECT * FROM T"));
        assertEquals(SqlOperation.EXPLAIN, SqlClassifier.classify("explain analyze select 1"));
        assertEquals(SqlOperation.EXPLAIN, SqlClassifier.classify("EXPLAIN (ANALYZE, VERBOSE) SELECT 1"));
    }

    @Test
    @DisplayName("DESC / DESCRIBE — DESC 분류")
    void desc() {
        assertEquals(SqlOperation.DESC, SqlClassifier.classify("DESC T_ORDER"));
        assertEquals(SqlOperation.DESC, SqlClassifier.classify("DESCRIBE T_ORDER"));
    }

    @Test
    @DisplayName("WITH (CTE) — WITH 분류")
    void cte_with() {
        assertEquals(SqlOperation.WITH, SqlClassifier.classify(
                "WITH cte AS (SELECT id FROM t) SELECT * FROM cte"));
    }

    @Test
    @DisplayName("isReadOnly — SELECT/WITH/EXPLAIN/DESC 만 true")
    void isReadOnly_whitelist() {
        assertTrue(SqlClassifier.isReadOnly(SqlOperation.SELECT));
        assertTrue(SqlClassifier.isReadOnly(SqlOperation.WITH));
        assertTrue(SqlClassifier.isReadOnly(SqlOperation.EXPLAIN));
        assertTrue(SqlClassifier.isReadOnly(SqlOperation.DESC));

        assertFalse(SqlClassifier.isReadOnly(SqlOperation.DML));
        assertFalse(SqlClassifier.isReadOnly(SqlOperation.DDL));
        assertFalse(SqlClassifier.isReadOnly(SqlOperation.CALL));
        assertFalse(SqlClassifier.isReadOnly(SqlOperation.DCL));
        assertFalse(SqlClassifier.isReadOnly(SqlOperation.TCL));
        assertFalse(SqlClassifier.isReadOnly(SqlOperation.UNKNOWN));
    }

    // ── 거부 (data modification) ─────────────────────────────────────────

    @Test
    @DisplayName("INSERT/UPDATE/DELETE/MERGE — DML 분류 (거부)")
    void dml() {
        assertEquals(SqlOperation.DML, SqlClassifier.classify("INSERT INTO T VALUES(1)"));
        assertEquals(SqlOperation.DML, SqlClassifier.classify("UPDATE T SET a=1 WHERE id=1"));
        assertEquals(SqlOperation.DML, SqlClassifier.classify("DELETE FROM T"));
        assertEquals(SqlOperation.DML, SqlClassifier.classify("MERGE INTO T USING ..."));
        assertEquals(SqlOperation.DML, SqlClassifier.classify("delete from t where 1=1"));
    }

    @Test
    @DisplayName("CREATE/DROP/ALTER/TRUNCATE — DDL 분류 (거부)")
    void ddl() {
        assertEquals(SqlOperation.DDL, SqlClassifier.classify("CREATE TABLE X (id NUMBER)"));
        assertEquals(SqlOperation.DDL, SqlClassifier.classify("DROP TABLE T"));
        assertEquals(SqlOperation.DDL, SqlClassifier.classify("ALTER TABLE T ADD col VARCHAR2(10)"));
        assertEquals(SqlOperation.DDL, SqlClassifier.classify("TRUNCATE TABLE T"));
        assertEquals(SqlOperation.DDL, SqlClassifier.classify("RENAME T TO T_OLD"));
        assertEquals(SqlOperation.DDL, SqlClassifier.classify("CREATE INDEX IDX ON T(col)"));
    }

    @Test
    @DisplayName("EXEC / CALL / 단순 BEGIN — CALL 분류 (거부)")
    void call() {
        assertEquals(SqlOperation.CALL, SqlClassifier.classify("EXEC sp_my_proc"));
        assertEquals(SqlOperation.CALL, SqlClassifier.classify("EXECUTE my_proc(1, 2)"));
        assertEquals(SqlOperation.CALL, SqlClassifier.classify("CALL my_proc()"));
        // 단순 BEGIN 블록 (내부에 ; 없음) — CALL
        assertEquals(SqlOperation.CALL, SqlClassifier.classify("BEGIN do_thing END"));
    }

    @Test
    @DisplayName("PL/SQL anonymous block (내부 ;) — 보수적으로 UNKNOWN 처리 (그래도 거부)")
    void plsqlAnonymousBlock_rejected() {
        // 정책: 내부 ; 가 있으면 멀티 statement 회피 시도와 구분 어려우므로 보수 거부.
        // CALL 로 분류되든 UNKNOWN 으로 분류되든 ReadOnlyJdbcTemplate 가 어차피 거부.
        // 핵심은: read-only 게이트를 통과하면 안 됨.
        assertFalse(SqlClassifier.isReadOnly(
                SqlClassifier.classify("BEGIN do_thing(); END;")));
        assertFalse(SqlClassifier.isReadOnly(
                SqlClassifier.classify("DECLARE x NUMBER; BEGIN x := 1; END;")));
    }

    @Test
    @DisplayName("GRANT/REVOKE — DCL 분류 (거부)")
    void dcl() {
        assertEquals(SqlOperation.DCL, SqlClassifier.classify("GRANT SELECT ON T TO U"));
        assertEquals(SqlOperation.DCL, SqlClassifier.classify("REVOKE ALL ON T FROM U"));
    }

    @Test
    @DisplayName("COMMIT/ROLLBACK/SET — TCL 분류 (거부)")
    void tcl() {
        assertEquals(SqlOperation.TCL, SqlClassifier.classify("COMMIT"));
        assertEquals(SqlOperation.TCL, SqlClassifier.classify("ROLLBACK"));
        assertEquals(SqlOperation.TCL, SqlClassifier.classify("SAVEPOINT sp1"));
        assertEquals(SqlOperation.TCL, SqlClassifier.classify("SET TRANSACTION READ WRITE"));
    }

    // ── 회피 시도 (security critical) ─────────────────────────────────────

    @Test
    @DisplayName("멀티 statement (; 으로 두 statement) — UNKNOWN 거부")
    void multiStatement_rejected() {
        // 이게 통과해버리면 운영 사고. 절대 SELECT 분기 타면 안 됨.
        assertEquals(SqlOperation.UNKNOWN,
                SqlClassifier.classify("SELECT 1 FROM dual; DELETE FROM T_ORDER"));
        assertEquals(SqlOperation.UNKNOWN,
                SqlClassifier.classify("SELECT 1; DROP TABLE T"));
        assertEquals(SqlOperation.UNKNOWN,
                SqlClassifier.classify("SELECT 1;UPDATE T SET a=1"));
    }

    @Test
    @DisplayName("Trailing 단일 ; 만 — 정상 분류 (멀티 statement 아님)")
    void trailingSemicolon_ok() {
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify("SELECT * FROM T;"));
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify("SELECT * FROM T;   "));
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify("SELECT * FROM T;\n"));
    }

    @Test
    @DisplayName("문자열 리터럴 안의 ; 는 멀티 statement 아님")
    void semicolonInsideStringLiteral_ok() {
        // Oracle 문법: WHERE name = ';test;'
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify(
                "SELECT * FROM T WHERE name = ';semicolon; in string'"));
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify(
                "SELECT \";col;\" FROM T"));
    }

    @Test
    @DisplayName("주석으로 첫 키워드 우회 시도 — 정확히 분류")
    void commentBeforeKeyword() {
        // 주석 제거 후 첫 단어로 분류하므로 우회 안 됨
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify("/* comment */ SELECT 1"));
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify("-- header\nSELECT 1"));
        assertEquals(SqlOperation.DML,    SqlClassifier.classify("/* try sneaking */ DELETE FROM T"));
        assertEquals(SqlOperation.DML,    SqlClassifier.classify("-- innocuous\nUPDATE T SET a=1"));
    }

    @Test
    @DisplayName("주석으로 키워드 분할 시도 — 정확히 첫 키워드 인식")
    void commentInsideKeyword_treatedAsBoundary() {
        // SE/**/LECT 는 SELECT 가 아님 — first word 가 "SE" 라서 UNKNOWN 또는 안전 거부.
        // 주석은 공백으로 치환되므로 "SE LECT 1" → 첫 단어 "SE" → UNKNOWN.
        SqlOperation op = SqlClassifier.classify("SE/**/LECT 1 FROM T");
        assertEquals(SqlOperation.UNKNOWN, op);
    }

    // ── edge cases ────────────────────────────────────────────────────────

    @Test
    @DisplayName("빈 / null / 공백만 입력 — UNKNOWN")
    void emptyInputs() {
        assertEquals(SqlOperation.UNKNOWN, SqlClassifier.classify(null));
        assertEquals(SqlOperation.UNKNOWN, SqlClassifier.classify(""));
        assertEquals(SqlOperation.UNKNOWN, SqlClassifier.classify("   "));
        assertEquals(SqlOperation.UNKNOWN, SqlClassifier.classify("\n\t  "));
        assertEquals(SqlOperation.UNKNOWN, SqlClassifier.classify("/* only comment */"));
        assertEquals(SqlOperation.UNKNOWN, SqlClassifier.classify("-- only line comment"));
    }

    @Test
    @DisplayName("인식 못한 첫 키워드 — UNKNOWN (안전 측 거부)")
    void unknownFirstKeyword() {
        assertEquals(SqlOperation.UNKNOWN, SqlClassifier.classify("FOO BAR"));
        assertEquals(SqlOperation.UNKNOWN, SqlClassifier.classify("123 SELECT 1"));    // 숫자로 시작
        assertEquals(SqlOperation.UNKNOWN, SqlClassifier.classify("--SELECT"));        // 주석으로 가려진 SELECT
    }

    @Test
    @DisplayName("선행 공백/개행 정상 처리")
    void leadingWhitespace() {
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify("\n\n\t  SELECT 1"));
        assertEquals(SqlOperation.SELECT, SqlClassifier.classify("   SELECT 1   "));
    }
}
