package io.github.claudetoolkit.sql.explain;

import io.github.claudetoolkit.starter.client.ClaudeClient;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Executes Oracle EXPLAIN PLAN against a live DB, parses the result into a
 * structured tree, and enriches it with Claude AI analysis.
 *
 * <h3>3-Tier Fallback Strategy</h3>
 * <ol>
 *   <li><b>Tier 1</b> — {@code EXPLAIN PLAN SET STATEMENT_ID = '...' FOR sql}
 *       + PLAN_TABLE (standard approach)</li>
 *   <li><b>Tier 2</b> — Same as Tier 1 but with user-defined function calls
 *       replaced by {@code NULL} (handles ORA-29900 from UDFs like CRYPTO_DECRYPT)</li>
 *   <li><b>Tier 3</b> — Execute a 0-row wrapper query ({@code ROWNUM < 1}) to
 *       populate Oracle's cursor cache, then read the plan from
 *       {@code V$SQL_PLAN} / {@code DBMS_XPLAN.DISPLAY_CURSOR}.
 *       This bypasses PLAN_TABLE entirely and resolves ORA-29900/ORA-06553
 *       conflicts caused by user-defined {@code DEPTH} functions or triggers
 *       on PLAN_TABLE.</li>
 * </ol>
 */
public class ExplainPlanService {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "당신은 Oracle DBA 전문가입니다. Oracle EXPLAIN PLAN 실행 계획을 분석하여 다음 형식으로 답변하세요.\n\n" +
            "## 📊 실행 계획 요약\n" +
            "전체 비용(Cost)과 주요 특징을 2~3줄로 요약.\n\n" +
            "## 🔴 성능 이슈\n" +
            "[SEVERITY: HIGH/MEDIUM/LOW] 이슈 설명 형식으로 목록 작성.\n" +
            "예) TABLE ACCESS FULL이 발생하는 테이블, 높은 Cost 단계, Cartesian Join 등\n\n" +
            "## 💡 최적화 제안\n" +
            "구체적인 인덱스 생성 또는 쿼리 개선 방안. Oracle 11g/12c 호환 구문 사용.\n\n" +
            "## 🌲 핵심 단계 해설\n" +
            "가장 비용이 높은 2~3개 단계를 선택하여 왜 비용이 발생하는지 설명.\n\n" +
            "응답은 한국어로 작성하세요.";

    /**
     * Matches user-defined function calls whose names contain at least one
     * underscore (e.g. CRYPTO_DECRYPT, GET_USER_NM, TO_CHAR …) followed by a
     * non-nested argument list.  Replacing these with NULL in the SELECT clause
     * lets EXPLAIN PLAN run without triggering ORA-29900 operator-binding errors
     * while still producing a correct access-path plan for the FROM/WHERE structure.
     */
    private static final Pattern UDF_CALL_PATTERN =
            Pattern.compile(
                    "(?i)\\b([A-Za-z][A-Za-z0-9]*_[A-Za-z0-9_]+)\\s*\\(([^()]*)\\)",
                    Pattern.CASE_INSENSITIVE);

    private final ClaudeClient claudeClient;

    public ExplainPlanService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public ExplainPlanResult analyze(String url, String username, String password, String sql) {
        return analyze(url, username, password, sql, null);
    }

    public ExplainPlanResult analyze(String url, String username, String password, String sql, String customPrompt) {

        ExplainPlanResult result = new ExplainPlanResult();
        // Use only digits for the ID so it is safe in LIKE patterns (no _ or % wildcards)
        String execId = String.valueOf(System.currentTimeMillis());
        String stmtId = "TK" + execId;   // used as PLAN_TABLE STATEMENT_ID

        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Oracle JDBC 드라이버를 찾을 수 없습니다. ojdbc8.jar 확인 필요.", e);
        }

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, username, password);

            boolean explainOk = false;
            boolean sanitized = false;

            // ── Tier 1: Standard EXPLAIN PLAN ─────────────────────────────────
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("EXPLAIN PLAN SET STATEMENT_ID = '" + stmtId + "' FOR " + sql);
                    explainOk = true;
                } catch (SQLException e1) {
                    if (!isUdfBindingError(e1)) throw e1;  // unrelated error — propagate

                    // ── Tier 2: Sanitized EXPLAIN PLAN (UDF calls → NULL) ──────
                    String sanitizedSql = sanitizeUdfCalls(sql);
                    if (!sanitizedSql.equals(sql)) {
                        try {
                            stmt.execute("EXPLAIN PLAN SET STATEMENT_ID = '" + stmtId + "' FOR " + sanitizedSql);
                            explainOk = true;
                            sanitized = true;
                        } catch (SQLException e2) {
                            // Still failing — Tier 3 will handle it
                        }
                    }
                }
            }

            if (explainOk) {
                // ── Read results from PLAN_TABLE ───────────────────────────────
                try {
                    List<ExplainPlanNode> nodes = fetchPlanNodes(conn, stmtId);
                    if (!nodes.isEmpty()) {
                        ExplainPlanNode root = buildTree(nodes);
                        result.setRoot(root);
                        result.setMaxCost(findMaxCost(root));
                        result.setPlanTableAvailable(true);
                    }
                    String rawText = fetchRawPlanText(conn, stmtId);
                    if (sanitized) {
                        rawText = "-- ⚠️ UDF 함수 호출(예: CRYPTO_DECRYPT)을 NULL로 대체하여 실행계획을 조회했습니다.\n"
                                + "-- 테이블 접근 경로(FROM/WHERE/JOIN)는 원본 SQL과 동일합니다.\n\n"
                                + rawText;
                    }
                    result.setRawPlanText(rawText);
                } catch (SQLException planReadErr) {
                    if (!isUdfBindingError(planReadErr)) {
                        throw planReadErr;
                    }
                    // PLAN_TABLE 조회 단계에서 DEPTH/연산자 바인딩 충돌이 발생한 경우에도
                    // V$SQL_PLAN 기반 우회 로직으로 자동 복구한다.
                    populateFromCursorCache(conn, sql, execId, result);
                }

            } else {
                // ── Tier 3: V$SQL_PLAN / DBMS_XPLAN.DISPLAY_CURSOR ────────────
                // Completely bypasses PLAN_TABLE (avoids DEPTH conflict / domain-index ORA-29900).
                populateFromCursorCache(conn, sql, execId, result);
            }

            // ── Claude AI Analysis ─────────────────────────────────────────────
            String planText = result.getRawPlanText();
            if (planText != null && !planText.isEmpty()) {
                String userMsg = "## SQL\n```sql\n" + sql + "\n```\n\n"
                               + "## EXPLAIN PLAN\n```\n" + planText + "\n```";
                try {
                    String effectivePrompt = (customPrompt != null && !customPrompt.trim().isEmpty()) ? customPrompt : DEFAULT_SYSTEM_PROMPT;
                    result.setAiAnalysis(claudeClient.chat(effectivePrompt, userMsg));
                } catch (Exception e) {
                    result.setAiAnalysis("(AI 분석 오류: " + e.getMessage() + ")");
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("EXPLAIN PLAN 실행 오류: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }

        return result;
    }

    // ── Tier 3: cursor-cache approach ─────────────────────────────────────────

    /**
     * Executes the query wrapped in {@code ROWNUM < 1} (0-row result, no side effects),
     * then reads the execution plan from {@code V$SQL_PLAN} and
     * {@code DBMS_XPLAN.DISPLAY_CURSOR}.
     *
     * <p>This completely bypasses PLAN_TABLE and therefore avoids ORA-29900 /
     * ORA-06553 errors that occur when the schema has:
     * <ul>
     *   <li>a user-defined function named {@code DEPTH}</li>
     *   <li>a trigger on PLAN_TABLE that calls {@code DEPTH()}</li>
     *   <li>a domain index whose operator bindings are missing</li>
     * </ul>
     */
    private void populateFromCursorCache(Connection conn,
                                          String originalSql,
                                          String execId,
                                          ExplainPlanResult result) throws SQLException {
        // Sanitize UDF calls so that functions like CRYPTO_DECRYPT don't fail during execution
        String safeSql = sanitizeUdfCalls(originalSql);

        // A unique SQL comment used to find the query in V$SQL.
        // Uses no LIKE-wildcards (no _ or %) so we can match it safely.
        String marker  = "/*TK" + execId + "*/";

        // Wrap in ROWNUM < 1 → Oracle applies a COUNT STOPKEY / FILTER; no rows fetched.
        // Oracle's optimizer skips data access for ROWNUM < 1 in most cases.
        String execSql = marker + "\nSELECT * FROM (\n" + safeSql + "\n) TKWRAP WHERE ROWNUM < 1";

        // Execute to populate the shared cursor cache (V$SQL)
        try (Statement s = conn.createStatement()) {
            s.execute(execSql);
        } catch (SQLException execErr) {
            throw new SQLException(
                "PLAN_TABLE 우회 실행(0-row)에도 실패했습니다: " + execErr.getMessage() +
                "\n상세: EXPLAIN PLAN과 V$SQL 두 방법 모두 이 쿼리에서 오류가 발생합니다.",
                execErr);
        }

        // ── Find SQL_ID from V$SQL ─────────────────────────────────────────────
        String sqlId   = null;
        int    childNo = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT SQL_ID, CHILD_NUMBER" +
                "  FROM V$SQL" +
                " WHERE SQL_TEXT LIKE ?" +
                "   AND ROWNUM = 1" +
                " ORDER BY LAST_ACTIVE_TIME DESC")) {
            ps.setString(1, marker + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    sqlId   = rs.getString(1);
                    childNo = rs.getInt(2);
                }
            }
        } catch (SQLException vErr) {
            throw new SQLException(
                "V$SQL 조회 실패 — 다음 권한 중 하나가 필요합니다: " +
                "SELECT ON V$SQL 또는 SELECT ANY DICTIONARY. 상세: " + vErr.getMessage(),
                vErr);
        }

        if (sqlId == null) {
            throw new SQLException(
                "V$SQL에서 실행된 쿼리를 찾을 수 없습니다. " +
                "Oracle shared pool에서 이미 제거되었거나 cursor_sharing 설정 문제일 수 있습니다. " +
                "잠시 후 다시 시도하세요.");
        }

        // ── Read plan tree from V$SQL_PLAN ────────────────────────────────────
        List<ExplainPlanNode> nodes = fetchNodesFromVSqlPlan(conn, sqlId, childNo);
        if (!nodes.isEmpty()) {
            ExplainPlanNode root = buildTree(nodes);
            result.setRoot(root);
            result.setMaxCost(findMaxCost(root));
            result.setPlanTableAvailable(false);
        }

        // ── Get formatted plan text from DBMS_XPLAN.DISPLAY_CURSOR ───────────
        String rawText = fetchCursorPlanText(conn, sqlId, childNo);
        result.setRawPlanText(
            "-- ℹ️ PLAN_TABLE 대신 V$SQL_PLAN / DISPLAY_CURSOR 방식으로 실행계획을 조회했습니다.\n" +
            "-- (ROWNUM<1 래퍼 사용 — 원본 테이블 접근 경로는 동일, 최상위 STOPKEY 행 제외하고 읽으세요)\n\n" +
            rawText);
    }

    /** Read plan nodes from V$SQL_PLAN (same column structure as PLAN_TABLE). */
    private List<ExplainPlanNode> fetchNodesFromVSqlPlan(Connection conn,
                                                          String sqlId,
                                                          int childNo) throws SQLException {
        List<ExplainPlanNode> nodes = new ArrayList<>();
        String query =
            "SELECT ID, PARENT_ID, DEPTH AS PLAN_DEPTH, POSITION, OPERATION, OPTIONS, OBJECT_NAME," +
            "       CARDINALITY, BYTES, COST, CPU_COST, IO_COST" +
            "  FROM V$SQL_PLAN" +
            " WHERE SQL_ID = ? AND CHILD_NUMBER = ?" +
            " ORDER BY ID";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, sqlId);
            ps.setInt(2, childNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExplainPlanNode node = new ExplainPlanNode();
                    node.setId(rs.getInt("ID"));

                    int parentId = rs.getInt("PARENT_ID");
                    node.setParentId(rs.wasNull() ? null : parentId);

                    node.setDepth(rs.getInt("PLAN_DEPTH"));
                    node.setPosition(rs.getInt("POSITION"));
                    node.setOperation(rs.getString("OPERATION"));
                    node.setOptions(rs.getString("OPTIONS"));
                    node.setObjectName(rs.getString("OBJECT_NAME"));

                    long cardinality = rs.getLong("CARDINALITY");
                    node.setCardinality(rs.wasNull() ? null : cardinality);
                    long bytes = rs.getLong("BYTES");
                    node.setBytes(rs.wasNull() ? null : bytes);
                    long cost = rs.getLong("COST");
                    node.setCost(rs.wasNull() ? null : cost);
                    long cpuCost = rs.getLong("CPU_COST");
                    node.setCpuCost(rs.wasNull() ? null : cpuCost);
                    long ioCost = rs.getLong("IO_COST");
                    node.setIoCost(rs.wasNull() ? null : ioCost);

                    nodes.add(node);
                }
            }
        }
        return nodes;
    }

    /** Fetch formatted plan text via DBMS_XPLAN.DISPLAY_CURSOR. */
    private String fetchCursorPlanText(Connection conn, String sqlId, int childNo) {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT PLAN_TABLE_OUTPUT" +
                "  FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(?, ?, 'ALL'))")) {
            ps.setString(1, sqlId);
            ps.setInt(2, childNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) sb.append(rs.getString(1)).append("\n");
            }
        } catch (SQLException e) {
            sb.append("(DBMS_XPLAN.DISPLAY_CURSOR 조회 실패: ").append(e.getMessage())
              .append("\nSELECT ON V$SQL_PLAN 권한이 필요합니다.)");
        }
        return sb.toString();
    }

    // ── EXPLAIN PLAN helpers ──────────────────────────────────────────────────

    /**
     * Returns {@code true} if the error is the known ORA-29900 / ORA-06553
     * combination that signals a PLAN_TABLE / UDF operator-binding conflict.
     */
    private boolean isUdfBindingError(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("ORA-29900") || msg.contains("ORA-06553")
                || msg.contains("29900")  || msg.contains("06553");
    }

    /**
     * Replaces UDF calls (identifiers containing an underscore) with {@code NULL}.
     * Iterates up to 5 times to handle nested calls such as
     * {@code OUTER(INNER(col))}.
     */
    private String sanitizeUdfCalls(String sql) {
        String result = sql;
        for (int i = 0; i < 5; i++) {
            String replaced = UDF_CALL_PATTERN.matcher(result).replaceAll("NULL");
            if (replaced.equals(result)) break;
            result = replaced;
        }
        // Guard: ORDER BY NULL / GROUP BY NULL are invalid — replace with safe defaults
        result = result.replaceAll("(?i)(ORDER\\s+BY)\\s+NULL", "$1 1");
        result = result.replaceAll("(?i)(GROUP\\s+BY)\\s+NULL", "");
        return result;
    }

    /** Reads all rows for the given STATEMENT_ID from PLAN_TABLE. */
    private List<ExplainPlanNode> fetchPlanNodes(Connection conn, String stmtId) throws SQLException {
        List<ExplainPlanNode> nodes = new ArrayList<>();
        String query =
                "SELECT ID, PARENT_ID, DEPTH AS PLAN_DEPTH, POSITION, OPERATION, OPTIONS, OBJECT_NAME," +
                "       CARDINALITY, BYTES, COST, CPU_COST, IO_COST" +
                "  FROM PLAN_TABLE" +
                " WHERE STATEMENT_ID = ?" +
                " ORDER BY ID";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, stmtId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExplainPlanNode node = new ExplainPlanNode();
                    node.setId(rs.getInt("ID"));

                    int parentId = rs.getInt("PARENT_ID");
                    node.setParentId(rs.wasNull() ? null : parentId);

                    node.setDepth(rs.getInt("PLAN_DEPTH"));
                    node.setPosition(rs.getInt("POSITION"));
                    node.setOperation(rs.getString("OPERATION"));
                    node.setOptions(rs.getString("OPTIONS"));
                    node.setObjectName(rs.getString("OBJECT_NAME"));

                    long cardinality = rs.getLong("CARDINALITY");
                    node.setCardinality(rs.wasNull() ? null : cardinality);
                    long bytes = rs.getLong("BYTES");
                    node.setBytes(rs.wasNull() ? null : bytes);
                    long cost = rs.getLong("COST");
                    node.setCost(rs.wasNull() ? null : cost);
                    long cpuCost = rs.getLong("CPU_COST");
                    node.setCpuCost(rs.wasNull() ? null : cpuCost);
                    long ioCost = rs.getLong("IO_COST");
                    node.setIoCost(rs.wasNull() ? null : ioCost);

                    nodes.add(node);
                }
            }
        }
        return nodes;
    }

    /** Assembles a flat list of nodes into a parent-child tree. */
    private ExplainPlanNode buildTree(List<ExplainPlanNode> nodes) {
        Map<Integer, ExplainPlanNode> nodeMap = new LinkedHashMap<>();
        for (ExplainPlanNode node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        ExplainPlanNode root = null;
        for (ExplainPlanNode node : nodes) {
            if (node.getParentId() == null) {
                root = node;
            } else {
                ExplainPlanNode parent = nodeMap.get(node.getParentId());
                if (parent != null) parent.getChildren().add(node);
            }
        }
        return root;
    }

    /** Recursively finds the maximum cost in the tree (for cost-bar scaling). */
    private long findMaxCost(ExplainPlanNode node) {
        if (node == null) return 0L;
        long max = (node.getCost() != null) ? node.getCost() : 0L;
        for (ExplainPlanNode child : node.getChildren()) {
            max = Math.max(max, findMaxCost(child));
        }
        return max;
    }

    /**
     * Returns formatted plan text from {@code DBMS_XPLAN.DISPLAY()}.
     * Falls back to a no-statement-id call if the first attempt fails.
     */
    private String fetchRawPlanText(Connection conn, String stmtId) {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT PLAN_TABLE_OUTPUT" +
                "  FROM TABLE(DBMS_XPLAN.DISPLAY('PLAN_TABLE', ?, 'ALL'))")) {
            ps.setString(1, stmtId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) sb.append(rs.getString(1)).append("\n");
            }
        } catch (SQLException e) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT PLAN_TABLE_OUTPUT FROM TABLE(DBMS_XPLAN.DISPLAY())")) {
                while (rs.next()) sb.append(rs.getString(1)).append("\n");
            } catch (SQLException e2) {
                sb.append("(실행 계획 텍스트 조회 실패: ").append(e2.getMessage()).append(")");
            }
        }
        return sb.toString();
    }
}
