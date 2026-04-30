package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 1: SqlTableExtractor 단위 테스트.
 *
 * <p>완벽한 SQL 파서가 아니므로 *현실적으로 자주 나오는 패턴* 만 검증.
 * 노이즈 (false-positive) 는 graceful fallback 되므로 *false-negative* 가 더 나쁨 —
 * 이쪽을 더 엄격히 검증.
 */
class SqlTableExtractorTest {

    @Test
    @DisplayName("단순 SELECT — FROM 절 단일 테이블")
    void simpleFrom() {
        Set<String> tables = SqlTableExtractor.extract("SELECT * FROM T_ORDER");
        assertEquals(setOf("T_ORDER"), tables);
    }

    @Test
    @DisplayName("schema-qualified — schema 제거 후 테이블만")
    void schemaQualified() {
        Set<String> tables = SqlTableExtractor.extract("SELECT * FROM SCH.T_ORDER");
        assertEquals(setOf("T_ORDER"), tables);
    }

    @Test
    @DisplayName("JOIN — 여러 테이블 추출")
    void joinMultipleTables() {
        Set<String> tables = SqlTableExtractor.extract(
                "SELECT * FROM T_ORDER o JOIN T_CUSTOMER c ON o.cid = c.id");
        assertEquals(setOf("T_ORDER", "T_CUSTOMER"), tables);
    }

    @Test
    @DisplayName("LEFT/RIGHT/INNER JOIN — 모두 동일하게 추출")
    void variousJoins() {
        Set<String> tables = SqlTableExtractor.extract(
                "SELECT * FROM A " +
                "LEFT JOIN B ON A.id = B.id " +
                "INNER JOIN C ON B.cid = C.id " +
                "RIGHT OUTER JOIN D ON C.did = D.id");
        assertEquals(setOf("A", "B", "C", "D"), tables);
    }

    @Test
    @DisplayName("alias 는 추출 결과에 포함 X")
    void aliasIgnored() {
        Set<String> tables = SqlTableExtractor.extract(
                "SELECT * FROM T_ORDER ord JOIN T_CUSTOMER cust ON ord.cid = cust.id");
        // alias (ord, cust) 가 결과에 들어가면 false-positive
        assertEquals(setOf("T_ORDER", "T_CUSTOMER"), tables);
    }

    @Test
    @DisplayName("WITH (CTE) — CTE 안의 base table 도 추출")
    void cteIncluded() {
        Set<String> tables = SqlTableExtractor.extract(
                "WITH cte AS (SELECT id FROM T_ORDER) " +
                "SELECT * FROM cte JOIN T_CUSTOMER ON cte.id = T_CUSTOMER.id");
        // T_ORDER 가 CTE 안에 있어도 추출 됨. cte 자체는 alias 라 그대로 포함되어도 OK
        // (실제 통계 조회 시 빈 결과 → graceful fallback)
        assertTrue(tables.contains("T_ORDER"), "CTE 내부 테이블 추출: " + tables);
        assertTrue(tables.contains("T_CUSTOMER"), "CTE 외부 테이블 추출: " + tables);
    }

    @Test
    @DisplayName("subquery — 안쪽 FROM 도 추출")
    void subquery() {
        Set<String> tables = SqlTableExtractor.extract(
                "SELECT * FROM (SELECT id FROM T_ORDER) sub");
        assertTrue(tables.contains("T_ORDER"));
    }

    @Test
    @DisplayName("주석 제거 후 추출")
    void commentsStripped() {
        Set<String> tables = SqlTableExtractor.extract(
                "SELECT * FROM /* T_FAKE */ T_ORDER -- ignore this T_OTHER");
        assertEquals(setOf("T_ORDER"), tables);
    }

    @Test
    @DisplayName("대소문자 — 항상 대문자로 정규화")
    void caseNormalization() {
        Set<String> tables = SqlTableExtractor.extract("select * from t_order Join T_Customer ON 1=1");
        assertEquals(setOf("T_ORDER", "T_CUSTOMER"), tables);
    }

    @Test
    @DisplayName("중복 — 한 번만")
    void deduplication() {
        Set<String> tables = SqlTableExtractor.extract(
                "SELECT * FROM T_ORDER o1 " +
                "WHERE EXISTS (SELECT 1 FROM T_ORDER o2 WHERE o1.id = o2.id)");
        assertEquals(setOf("T_ORDER"), tables);
    }

    @Test
    @DisplayName("null / 빈 입력 — 빈 set")
    void nullOrEmpty() {
        assertTrue(SqlTableExtractor.extract(null).isEmpty());
        assertTrue(SqlTableExtractor.extract("").isEmpty());
        assertTrue(SqlTableExtractor.extract("   ").isEmpty());
    }

    @Test
    @DisplayName("FROM/JOIN 없는 SQL — 빈 set (DUAL 테이블 미사용 한정)")
    void noFromClause() {
        // 'SELECT 1' 같은 FROM 없는 쿼리 — 추출 결과 없음
        assertTrue(SqlTableExtractor.extract("SELECT 1").isEmpty());
    }

    private static Set<String> setOf(String... items) {
        return new java.util.LinkedHashSet<String>(Arrays.asList(items));
    }
}
