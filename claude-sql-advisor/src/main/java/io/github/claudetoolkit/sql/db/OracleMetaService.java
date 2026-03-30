package io.github.claudetoolkit.sql.db;

import java.sql.*;
import java.util.*;
import java.util.regex.*;

/**
 * Connects to an Oracle DB at runtime and retrieves table/column/index metadata
 * to enrich SQL review prompts sent to Claude.
 */
public class OracleMetaService {

    /**
     * Tests whether the given credentials can open a JDBC connection.
     *
     * @return true if connection succeeds within 5 seconds
     */
    public boolean testConnection(String url, String username, String password) {
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            return false;
        }
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Extracts table names referenced in the SQL, then fetches column/PK/index
     * metadata from ALL_COLUMNS, ALL_CONSTRAINTS, ALL_INDEXES.
     *
     * @return a formatted Markdown string describing the tables, or empty string
     *         if no tables found or DB is unreachable.
     */
    public String buildTableContext(String url, String username, String password, String sql) {
        List<String> tableNames = extractTableNames(sql);
        if (tableNames.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## Oracle DB Table Metadata\n\n");
        sb.append("The following table information was retrieved from the connected Oracle DB:\n\n");

        try {
            Class.forName("oracle.jdbc.OracleDriver");
            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                for (String table : tableNames) {
                    sb.append(fetchTableInfo(conn, table));
                }
            }
        } catch (ClassNotFoundException e) {
            sb.append("(Oracle JDBC 드라이버를 찾을 수 없습니다)\n");
        } catch (SQLException e) {
            sb.append("(DB 연결 실패: ").append(e.getMessage()).append(")\n");
        }

        return sb.toString();
    }

    /**
     * Extracts table/view names following FROM and JOIN keywords.
     * Strips schema prefixes (e.g. SCHEMA.TABLE_NAME → TABLE_NAME).
     * Skips SQL reserved words.
     */
    public List<String> extractTableNames(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Pattern p = Pattern.compile(
            "(?i)(?:FROM|JOIN)\\s+([A-Z_][A-Z0-9_$#]*(?:\\.[A-Z_][A-Z0-9_$#]*)?)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(sql);
        Set<String> skip = new HashSet<>(Arrays.asList(
            "SELECT", "WHERE", "AND", "OR", "ON", "SET", "INTO", "VALUES", "DUAL",
            "TABLE", "VIEW", "INDEX", "PROCEDURE", "FUNCTION", "PACKAGE", "TRIGGER"
        ));
        while (m.find()) {
            String name = m.group(1).toUpperCase();
            // strip schema prefix
            if (name.contains(".")) {
                name = name.substring(name.indexOf('.') + 1);
            }
            if (!skip.contains(name)) {
                tables.add(name);
            }
        }
        return new ArrayList<>(tables);
    }

    /**
     * Runs EXPLAIN PLAN FOR &lt;sql&gt; on Oracle and returns the formatted plan
     * from DBMS_XPLAN.DISPLAY().
     *
     * @return plan text, or an error message string if the query fails
     */
    public String getExplainPlan(String url, String username, String password, String sql) {
        StringBuilder sb = new StringBuilder();
        Connection conn = null;
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            conn = DriverManager.getConnection(url, username, password);

            // Execute EXPLAIN PLAN
            java.sql.Statement stmt = conn.createStatement();
            stmt.execute("EXPLAIN PLAN FOR " + sql);
            stmt.close();

            // Read plan from DBMS_XPLAN.DISPLAY()
            java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT PLAN_TABLE_OUTPUT FROM TABLE(DBMS_XPLAN.DISPLAY())");
            java.sql.ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                sb.append(rs.getString(1)).append("\n");
            }
            rs.close();
            ps.close();
        } catch (ClassNotFoundException e) {
            sb.append("Oracle JDBC 드라이버를 찾을 수 없습니다.");
        } catch (SQLException e) {
            sb.append("EXPLAIN PLAN 실행 오류: ").append(e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
        return sb.toString();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String fetchTableInfo(Connection conn, String tableName) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(tableName).append("\n");

        appendColumns(conn, tableName, sb);
        appendPrimaryKey(conn, tableName, sb);
        appendIndexes(conn, tableName, sb);
        sb.append("\n");
        return sb.toString();
    }

    private void appendColumns(Connection conn, String tableName, StringBuilder sb) {
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE " +
                     "FROM ALL_COLUMNS WHERE TABLE_NAME = UPPER(?) ORDER BY COLUMN_ID";
        sb.append("**Columns:**\n");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    String dataType = rs.getString("DATA_TYPE");
                    String typeDesc = buildTypeDesc(dataType, rs.getInt("DATA_LENGTH"),
                            rs.getObject("DATA_PRECISION"), rs.getObject("DATA_SCALE"));
                    sb.append("- `").append(rs.getString("COLUMN_NAME")).append("` ")
                      .append(typeDesc)
                      .append("  N".equals(rs.getString("NULLABLE")) ? " NOT NULL" : "")
                      .append("\n");
                }
                if (!any) sb.append("- (컬럼 정보 없음)\n");
            }
        } catch (SQLException e) {
            sb.append("- (조회 실패: ").append(e.getMessage()).append(")\n");
        }
    }

    private void appendPrimaryKey(Connection conn, String tableName, StringBuilder sb) {
        String sql = "SELECT cc.COLUMN_NAME FROM ALL_CONSTRAINTS c " +
                     "JOIN ALL_CONS_COLUMNS cc " +
                     "  ON c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME AND c.OWNER = cc.OWNER " +
                     "WHERE c.TABLE_NAME = UPPER(?) AND c.CONSTRAINT_TYPE = 'P' " +
                     "ORDER BY cc.POSITION";
        List<String> pkCols = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) pkCols.add(rs.getString("COLUMN_NAME"));
            }
        } catch (SQLException ignored) {}
        if (!pkCols.isEmpty()) {
            sb.append("**PK:** ").append(String.join(", ", pkCols)).append("\n");
        }
    }

    private void appendIndexes(Connection conn, String tableName, StringBuilder sb) {
        String sql = "SELECT i.INDEX_NAME, i.UNIQUENESS, ic.COLUMN_NAME " +
                     "FROM ALL_INDEXES i " +
                     "JOIN ALL_IND_COLUMNS ic " +
                     "  ON i.INDEX_NAME = ic.INDEX_NAME AND i.OWNER = ic.INDEX_OWNER " +
                     "WHERE i.TABLE_NAME = UPPER(?) " +
                     "ORDER BY i.INDEX_NAME, ic.COLUMN_POSITION";
        Map<String, List<String>> indexes = new LinkedHashMap<>();
        Map<String, String> uniq = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String idx = rs.getString("INDEX_NAME");
                    indexes.computeIfAbsent(idx, k -> new ArrayList<>()).add(rs.getString("COLUMN_NAME"));
                    uniq.put(idx, rs.getString("UNIQUENESS"));
                }
            }
        } catch (SQLException ignored) {}
        if (!indexes.isEmpty()) {
            sb.append("**Indexes:**\n");
            for (Map.Entry<String, List<String>> e : indexes.entrySet()) {
                sb.append("- ").append(e.getKey())
                  .append(" (").append(uniq.get(e.getKey())).append("): ")
                  .append(String.join(", ", e.getValue())).append("\n");
            }
        }
    }

    private String buildTypeDesc(String dataType, int dataLength, Object precision, Object scale) {
        switch (dataType) {
            case "VARCHAR2":
            case "CHAR":
            case "NVARCHAR2":
                return dataType + "(" + dataLength + ")";
            case "NUMBER":
                if (precision != null && scale != null) return "NUMBER(" + precision + "," + scale + ")";
                if (precision != null) return "NUMBER(" + precision + ")";
                return "NUMBER";
            default:
                return dataType;
        }
    }
}
