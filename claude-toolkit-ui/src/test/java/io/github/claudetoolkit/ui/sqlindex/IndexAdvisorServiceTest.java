package io.github.claudetoolkit.ui.sqlindex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.4.0 — IndexAdvisorService 단위 테스트.
 *
 * <p>SQL 정적 파싱 로직 + DDL 추천 로직을 검증.
 * 메타데이터 조회는 H2 in-memory DB 로 통합 검증.
 */
class IndexAdvisorServiceTest {

    private IndexAdvisorService service;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // H2 in-memory DB 로 실제 메타조회 테스트
        Class.forName("org.h2.Driver");
        dataSource = new SimpleDataSource("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS USERS");
            s.execute("CREATE TABLE USERS (ID BIGINT PRIMARY KEY, EMAIL VARCHAR(200), NAME VARCHAR(100))");
            s.execute("CREATE INDEX IDX_USERS_EMAIL ON USERS(EMAIL)");
        }
        // ClaudeClient/ToolkitSettings는 정적 파싱 테스트에서 사용 안함 → null 안전 처리
        service = new IndexAdvisorService(null, dataSource, null);
    }

    @Test
    @DisplayName("SQL 파싱 — FROM 절에서 테이블명 추출")
    void extractTablesFromSimpleSelect() {
        Map<String, Object> r = service.analyze("SELECT * FROM USERS WHERE ID = 1", "default");
        @SuppressWarnings("unchecked")
        List<String> tables = (List<String>) r.get("tables");
        assertEquals(1, tables.size());
        assertEquals("USERS", tables.get(0).toUpperCase());
    }

    @Test
    @DisplayName("SQL 파싱 — JOIN 다중 테이블 추출")
    void extractTablesFromJoin() {
        Map<String, Object> r = service.analyze(
                "SELECT * FROM ORDERS o JOIN USERS u ON o.user_id = u.id WHERE u.email = 'x@y.z'",
                "default");
        @SuppressWarnings("unchecked")
        List<String> tables = (List<String>) r.get("tables");
        assertTrue(tables.size() >= 2, "ORDERS, USERS 모두 추출되어야 함: " + tables);
    }

    @Test
    @DisplayName("WHERE 조건 컬럼 추출 — 다양한 연산자")
    void extractPredicateColumnsVariousOps() {
        Map<String, Object> r = service.analyze(
                "SELECT * FROM T WHERE A = 1 AND B > 2 AND C LIKE 'x%' AND D IN (1,2)",
                "default");
        @SuppressWarnings("unchecked")
        List<String> cols = (List<String>) r.get("predicateColumns");
        assertTrue(cols.size() >= 4, "A, B, C, D 모두 감지되어야 함: " + cols);
    }

    @Test
    @DisplayName("문자열 리터럴 안의 키워드는 무시")
    void stringLiteralStripped() {
        // 'WHERE FROM' 같은 문자열은 무시되어야 함
        Map<String, Object> r = service.analyze(
                "SELECT * FROM T WHERE NAME = 'WHERE FROM JOIN'", "default");
        @SuppressWarnings("unchecked")
        List<String> tables = (List<String>) r.get("tables");
        assertEquals(1, tables.size(), "문자열 안의 FROM/JOIN 무시: " + tables);
    }

    @Test
    @DisplayName("COMMENT 무시 — 라인/블록 주석")
    void commentsStripped() {
        Map<String, Object> r = service.analyze(
                "SELECT * FROM T -- comment\n WHERE A = 1 /* block */",
                "default");
        @SuppressWarnings("unchecked")
        List<String> cols = (List<String>) r.get("predicateColumns");
        assertTrue(cols.contains("A"), "주석 후 컬럼 감지: " + cols);
    }

    @Test
    @DisplayName("기존 인덱스 매칭 — H2 메타조회로 IDX_USERS_EMAIL 활용 가능 판정")
    void existingIndexMatched() {
        Map<String, Object> r = service.analyze(
                "SELECT * FROM USERS WHERE EMAIL = 'test@example.com'",
                "default");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reports = (List<Map<String, Object>>) r.get("tableReports");
        assertFalse(reports.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> existing = (List<Map<String, Object>>) reports.get(0).get("existingIndexes");
        assertTrue(existing.stream().anyMatch(idx -> Boolean.TRUE.equals(idx.get("usableForQuery"))),
                "EMAIL 인덱스가 활용 가능으로 표시되어야 함");
    }

    @Test
    @DisplayName("미커버 컬럼 — 신규 인덱스 추천 (DDL 생성)")
    void newIndexRecommendation() {
        Map<String, Object> r = service.analyze(
                "SELECT * FROM USERS WHERE NAME = 'Alice'",  // NAME 인덱스 없음
                "default");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reports = (List<Map<String, Object>>) r.get("tableReports");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recs = (List<Map<String, Object>>) reports.get(0).get("recommendations");
        assertFalse(recs.isEmpty(), "NAME 컬럼에 신규 인덱스 추천이 있어야 함");
        String ddl = (String) recs.get(0).get("ddl");
        assertTrue(ddl.startsWith("CREATE INDEX"), "DDL 형식 검증: " + ddl);
        assertTrue(ddl.contains("NAME"), "NAME 컬럼 포함");
    }

    @Test
    @DisplayName("Oracle 30자 제한 — 인덱스명 잘림")
    void oracleIdentifierLimit() throws Exception {
        // private buildRecommendation 직접 호출 (reflection)
        Method m = IndexAdvisorService.class.getDeclaredMethod(
                "buildRecommendation", String.class, Set.class, String.class);
        m.setAccessible(true);
        Set<String> cols = new java.util.LinkedHashSet<>();
        cols.add("VERY_LONG_COLUMN_NAME_FOR_TEST");
        @SuppressWarnings("unchecked")
        Map<String, Object> rec = (Map<String, Object>) m.invoke(service,
                "VERY_LONG_TABLE_NAME_FOR_TEST", cols, "test");
        String idxName = (String) rec.get("indexName");
        assertTrue(idxName.length() <= 30, "Oracle 30자 제한: " + idxName + " (" + idxName.length() + ")");
    }

    @Test
    @DisplayName("빈 SQL 입력 → IllegalArgumentException")
    void emptySqlThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.analyze("", "default"));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(null, "default"));
        assertThrows(IllegalArgumentException.class, () -> service.analyze("   ", "default"));
    }

    @Test
    @DisplayName("describeTargetDb — 외부 DB 미설정 시 default-datasource 표시")
    void describeTargetDbFallback() {
        Map<String, Object> info = service.describeTargetDb();
        // ToolkitSettings 가 null 이라 fallback 으로 빠짐
        assertEquals(false, info.get("hasExternal"));
        assertEquals("default-datasource", info.get("source"));
        assertNotNull(info.get("warning"));
    }

    // ── 미니 DataSource (H2 테스트용) ────────────────────────────────────
    private static class SimpleDataSource implements DataSource {
        private final String url, user, pass;
        SimpleDataSource(String url, String user, String pass) { this.url = url; this.user = user; this.pass = pass; }
        public Connection getConnection() throws java.sql.SQLException { return DriverManager.getConnection(url, user, pass); }
        public Connection getConnection(String u, String p) throws java.sql.SQLException { return DriverManager.getConnection(url, u, p); }
        public java.io.PrintWriter getLogWriter() { return null; }
        public void setLogWriter(java.io.PrintWriter o) {}
        public void setLoginTimeout(int s) {}
        public int getLoginTimeout() { return 0; }
        public java.util.logging.Logger getParentLogger() { return null; }
        public <T> T unwrap(Class<T> c) { return null; }
        public boolean isWrapperFor(Class<?> c) { return false; }
    }
}
