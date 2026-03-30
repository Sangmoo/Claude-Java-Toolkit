package io.github.claudetoolkit.sql.erd;

import io.github.claudetoolkit.starter.client.ClaudeClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Analyzes Oracle DB schema to generate Mermaid ERD diagrams
 * and Korean relationship descriptions using Claude API.
 *
 * Supports two modes:
 * - DB mode : connects to Oracle, fetches ALL_TABLES/ALL_CONSTRAINTS
 * - Text mode: accepts manually entered schema text (no DB required)
 */
public class ErdAnalyzerService {

    private static final String SYSTEM_PROMPT =
            "당신은 데이터베이스 설계 전문가입니다.\n" +
            "Oracle DB 테이블 스키마 정보를 분석하여 Mermaid ERD 다이어그램과 설명을 생성합니다.\n\n" +
            "반드시 다음 형식으로 출력하세요:\n\n" +
            "## Mermaid ERD\n" +
            "```mermaid\nerDiagram\n    CUSTOMER ||--o{ ORDER : places\n    ...\n```\n\n" +
            "## 테이블 관계 설명\n" +
            "각 테이블의 역할과 관계를 한국어로 설명\n\n" +
            "## 주요 설계 포인트\n" +
            "스키마 설계의 특징, 정규화 수준, 개선 제안\n\n" +
            "Mermaid erDiagram 문법 규칙 (반드시 준수):\n" +
            "1. 관계 표기:\n" +
            "   - 1:N 관계: PARENT ||--o{ CHILD : label\n" +
            "   - 1:1 관계: A ||--|| B : label\n" +
            "   - N:M 관계: A }o--o{ B : label\n" +
            "   - 관계 label은 영어 단어 하나 또는 언더스코어로 연결된 단어 사용 (공백 없이)\n" +
            "2. 속성 정의:\n" +
            "   - PK 컬럼: TYPE COLUMN_NAME PK\n" +
            "   - FK 컬럼: TYPE COLUMN_NAME FK\n" +
            "   - PK이자 FK인 컬럼: TYPE COLUMN_NAME PK, FK\n" +
            "   - 일반 컬럼: TYPE COLUMN_NAME\n" +
            "   - TYPE은 반드시 공백, 괄호, 특수문자 없는 단일 단어 사용 (예: varchar2 -> VARCHAR2, number -> NUMBER, date -> DATE)\n" +
            "   - COLUMN_NAME은 영문자, 숫자, 언더스코어만 사용\n" +
            "   - 한국어 코멘트를 속성에 포함하려면 반드시 큰따옴표로 감쌀 것: TYPE COLUMN_NAME \"한국어설명\"\n" +
            "   - 단, 코멘트 없이 속성만 정의하는 것을 권장\n" +
            "3. 금지 사항:\n" +
            "   - PK/FK 슬래시 표기 금지 (PK, FK 중 하나만 또는 둘 다 쓸 때는 'PK, FK' 형식)\n" +
            "   - 속성 타입에 괄호 사용 금지 (NUMBER(10) -> NUMBER)\n" +
            "   - 관계 label에 한국어 사용 금지\n" +
            "   - 관계 label에 공백 사용 금지\n" +
            "정확한 Mermaid 문법을 사용하세요.";

    private final ClaudeClient claudeClient;

    public ErdAnalyzerService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Connect to Oracle DB, fetch full schema, and generate ERD.
     *
     * @param url          JDBC URL
     * @param username     DB user
     * @param password     DB password
     * @param schemaOwner  owner/schema name (defaults to username if empty)
     */
    public String generateFromDb(String url, String username, String password, String schemaOwner) {
        return generateFromDb(url, username, password, schemaOwner, null);
    }

    /**
     * Connect to Oracle DB, fetch filtered schema, and generate ERD.
     *
     * @param url          JDBC URL
     * @param username     DB user
     * @param password     DB password
     * @param schemaOwner  owner/schema name (defaults to username if empty)
     * @param tableFilter  comma-separated table name list; null or empty = all tables
     */
    public String generateFromDb(String url, String username, String password,
                                 String schemaOwner, String tableFilter) {
        String schemaInfo = fetchSchemaInfo(url, username, password, schemaOwner, tableFilter);
        return generateFromText(schemaInfo);
    }

    /**
     * Generate ERD from manually entered schema description text.
     */
    public String generateFromText(String schemaText) {
        String userMessage = "다음 Oracle DB 스키마 정보를 분석하여 ERD와 관계 설명을 생성해주세요:\n\n" + schemaText;
        return claudeClient.chat(SYSTEM_PROMPT, userMessage);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String fetchSchemaInfo(String url, String username, String password,
                                   String schemaOwner, String tableFilter) {
        StringBuilder sb = new StringBuilder();
        String owner = (schemaOwner != null && !schemaOwner.trim().isEmpty())
                ? schemaOwner.trim().toUpperCase()
                : username.toUpperCase();

        // Parse comma-separated table filter into uppercase list
        java.util.List<String> filterList = new java.util.ArrayList<String>();
        if (tableFilter != null && !tableFilter.trim().isEmpty()) {
            for (String t : tableFilter.split(",")) {
                String trimmed = t.trim().toUpperCase();
                if (!trimmed.isEmpty()) filterList.add(trimmed);
            }
        }

        Connection conn = null;
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            conn = DriverManager.getConnection(url, username, password);
            appendTables(conn, owner, filterList, sb);
        } catch (ClassNotFoundException e) {
            sb.append("(Oracle JDBC 드라이버를 찾을 수 없습니다)\n");
        } catch (SQLException e) {
            sb.append("(DB 연결 또는 조회 실패: ").append(e.getMessage()).append(")\n");
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
        return sb.toString();
    }

    private void appendTables(Connection conn, String owner,
                               java.util.List<String> filterList, StringBuilder sb) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT t.TABLE_NAME, tc.COMMENTS " +
                "FROM ALL_TABLES t LEFT JOIN ALL_TAB_COMMENTS tc " +
                "ON t.OWNER = tc.OWNER AND t.TABLE_NAME = tc.TABLE_NAME " +
                "WHERE t.OWNER = ?");
        if (!filterList.isEmpty()) {
            sql.append(" AND t.TABLE_NAME IN (");
            for (int i = 0; i < filterList.size(); i++) {
                if (i > 0) sql.append(",");
                sql.append("?");
            }
            sql.append(")");
        }
        sql.append(" ORDER BY t.TABLE_NAME");
        PreparedStatement ps = conn.prepareStatement(sql.toString());
        ps.setString(1, owner);
        for (int i = 0; i < filterList.size(); i++) {
            ps.setString(2 + i, filterList.get(i));
        }
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String tableName = rs.getString("TABLE_NAME");
            String comment   = rs.getString("COMMENTS");
            sb.append("=== ").append(tableName);
            if (comment != null && !comment.isEmpty()) {
                sb.append(" (").append(comment).append(")");
            }
            sb.append(" ===\n");
            appendColumns(conn, owner, tableName, sb);
            appendConstraints(conn, owner, tableName, sb);
            sb.append("\n");
        }
        rs.close();
        ps.close();
    }

    private void appendColumns(Connection conn, String owner, String tableName, StringBuilder sb) throws SQLException {
        String sql = "SELECT c.COLUMN_NAME, c.DATA_TYPE, c.DATA_LENGTH, c.NULLABLE, cc.COMMENTS " +
                "FROM ALL_TAB_COLUMNS c LEFT JOIN ALL_COL_COMMENTS cc " +
                "ON c.OWNER = cc.OWNER AND c.TABLE_NAME = cc.TABLE_NAME AND c.COLUMN_NAME = cc.COLUMN_NAME " +
                "WHERE c.OWNER = ? AND c.TABLE_NAME = ? ORDER BY c.COLUMN_ID";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, owner);
        ps.setString(2, tableName);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            sb.append("  COL: ").append(rs.getString("COLUMN_NAME"))
              .append(" ").append(rs.getString("DATA_TYPE"));
            if ("N".equals(rs.getString("NULLABLE"))) sb.append(" NOT NULL");
            String cmt = rs.getString("COMMENTS");
            if (cmt != null && !cmt.isEmpty()) sb.append(" -- ").append(cmt);
            sb.append("\n");
        }
        rs.close();
        ps.close();
    }

    private void appendConstraints(Connection conn, String owner, String tableName, StringBuilder sb) throws SQLException {
        String sql = "SELECT c.CONSTRAINT_TYPE, cc.COLUMN_NAME, c.R_CONSTRAINT_NAME, " +
                "(SELECT MIN(c2.TABLE_NAME) FROM ALL_CONSTRAINTS c2 " +
                " WHERE c2.CONSTRAINT_NAME = c.R_CONSTRAINT_NAME AND c2.OWNER = c.R_OWNER) AS R_TABLE " +
                "FROM ALL_CONSTRAINTS c JOIN ALL_CONS_COLUMNS cc " +
                "ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME " +
                "WHERE c.OWNER = ? AND c.TABLE_NAME = ? AND c.CONSTRAINT_TYPE IN ('P','R') " +
                "ORDER BY c.CONSTRAINT_TYPE, cc.POSITION";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, owner);
        ps.setString(2, tableName);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String cType = rs.getString("CONSTRAINT_TYPE");
            if ("P".equals(cType)) {
                sb.append("  PK: ").append(rs.getString("COLUMN_NAME")).append("\n");
            } else {
                sb.append("  FK: ").append(rs.getString("COLUMN_NAME"))
                  .append(" -> ").append(rs.getString("R_TABLE")).append("\n");
            }
        }
        rs.close();
        ps.close();
    }
}
