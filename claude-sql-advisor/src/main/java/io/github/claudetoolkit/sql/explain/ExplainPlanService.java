package io.github.claudetoolkit.sql.explain;

import io.github.claudetoolkit.starter.client.ClaudeClient;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Executes Oracle {@code EXPLAIN PLAN} against a live DB, parses the PLAN_TABLE
 * into a structured tree, and enriches the result with a Claude AI analysis.
 *
 * <p>Flow:
 * <ol>
 *   <li>Run {@code EXPLAIN PLAN SET STATEMENT_ID='TOOLKIT_xxx' FOR &lt;sql&gt;}</li>
 *   <li>If ORA-29900 (extensible operator binding error due to User-Defined Functions),
 *       automatically sanitize the SQL (replace UDF calls with NULL) and retry.</li>
 *   <li>Read PLAN_TABLE rows → build {@link ExplainPlanNode} tree</li>
 *   <li>Fetch formatted text via {@code DBMS_XPLAN.DISPLAY()}</li>
 *   <li>Call Claude for performance insights in Markdown</li>
 * </ol>
 */
public class ExplainPlanService {

    private static final String SYSTEM_PROMPT =
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
     * Matches user-defined function calls: identifiers that contain at least one
     * underscore (e.g. CRYPTO_DECRYPT, GET_USER_NM) followed by a simple
     * argument list that contains no nested parentheses.
     *
     * Standard Oracle built-ins (TO_DATE, TO_CHAR, NVL, DECODE …) also contain
     * underscores, but replacing them with NULL in the SELECT list is harmless for
     * execution-plan purposes — the optimizer still sees the full FROM / WHERE /
     * JOIN structure that determines the access path.
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

    /**
     * Runs EXPLAIN PLAN on the given SQL, builds a structured tree,
     * fetches the text output, and asks Claude for insights.
     *
     * @param url      JDBC URL  (e.g. {@code jdbc:oracle:thin:@//host:1521/ORCL})
     * @param username Oracle username
     * @param password Oracle password
     * @param sql      The SELECT statement to analyze
     * @return populated {@link ExplainPlanResult}; never {@code null}
     * @throws RuntimeException wraps driver/SQL errors with a user-friendly message
     */
    public ExplainPlanResult analyze(String url, String username, String password, String sql) {
        ExplainPlanResult result = new ExplainPlanResult();
        String stmtId = "TOOLKIT_" + System.currentTimeMillis();

        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Oracle JDBC 드라이버를 찾을 수 없습니다. ojdbc8.jar 확인 필요.", e);
        }

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, username, password);

            // Step 1: EXPLAIN PLAN (with automatic UDF-sanitize retry on ORA-29900)
            String effectiveSql = sql;
            boolean sanitized   = false;

            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("EXPLAIN PLAN SET STATEMENT_ID = '" + stmtId + "' FOR " + sql);
                } catch (SQLException ora) {
                    // ORA-29900 : extensible operator binding error
                    //   — usually caused by User-Defined Functions (e.g. CRYPTO_DECRYPT)
                    //   that Oracle cannot resolve during EXPLAIN PLAN parsing.
                    // ORA-06553 / PLS-306 with 'DEPTH' is a secondary symptom of the same issue.
                    if (isUdfBindingError(ora)) {
                        effectiveSql = sanitizeUdfCalls(sql);
                        sanitized    = true;
                        // Retry with sanitized SQL (UDF calls replaced with NULL)
                        stmt.execute("EXPLAIN PLAN SET STATEMENT_ID = '" + stmtId + "' FOR " + effectiveSql);
                    } else {
                        throw ora;  // unrelated error — propagate
                    }
                }
            }

            // Step 2: Structured PLAN_TABLE rows → tree
            List<ExplainPlanNode> nodes = fetchPlanNodes(conn, stmtId);
            if (!nodes.isEmpty()) {
                ExplainPlanNode root = buildTree(nodes);
                result.setRoot(root);
                result.setMaxCost(findMaxCost(root));
                result.setPlanTableAvailable(true);
            }

            // Step 3: Raw text from DBMS_XPLAN.DISPLAY()
            result.setRawPlanText(fetchRawPlanText(conn, stmtId));

            // Step 4: Claude AI analysis
            String planForAi = result.getRawPlanText();
            if (planForAi != null && !planForAi.isEmpty()) {
                StringBuilder userMsg = new StringBuilder();
                userMsg.append("## SQL\n```sql\n").append(sql).append("\n```\n\n");
                if (sanitized) {
                    userMsg.append("> ⚠️ 사용자 정의 함수(UDF) 호출이 NULL로 대체된 SQL로 실행계획을 조회했습니다.\n")
                           .append("> 테이블 접근 경로(FROM/WHERE/JOIN)는 원본과 동일합니다.\n\n");
                }
                userMsg.append("## EXPLAIN PLAN\n```\n").append(planForAi).append("\n```");
                try {
                    result.setAiAnalysis(claudeClient.chat(SYSTEM_PROMPT, userMsg.toString()));
                } catch (Exception e) {
                    result.setAiAnalysis("(AI 분석 오류: " + e.getMessage() + ")");
                }
            }

            // Append sanitization notice to raw plan text so the user can see it
            if (sanitized && result.getRawPlanText() != null) {
                result.setRawPlanText(
                        "-- ⚠️ UDF 함수 호출(예: CRYPTO_DECRYPT)을 NULL로 대체하여 EXPLAIN PLAN을 실행했습니다.\n" +
                        "-- 테이블 접근 경로(FROM/WHERE/JOIN)는 원본 SQL과 동일합니다.\n\n" +
                        result.getRawPlanText());
            }

        } catch (SQLException e) {
            throw new RuntimeException("EXPLAIN PLAN 실행 오류: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }

        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the SQLException is caused by Oracle's extensible
     * operator binding error (ORA-29900) or the related PLS-306/DEPTH symptom,
     * which indicates that a User-Defined Function cannot be resolved during
     * EXPLAIN PLAN parsing.
     */
    private boolean isUdfBindingError(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        // ORA-29900: operator binding does not exist
        // ORA-06553: PLS-306 wrong number or types of arguments in call to 'DEPTH'
        return msg.contains("ORA-29900") || msg.contains("ORA-06553")
                || msg.contains("29900")  || msg.contains("06553");
    }

    /**
     * Replaces user-defined function calls (identifiers that contain underscores)
     * with {@code NULL} so that {@code EXPLAIN PLAN} can proceed without needing
     * the function bindings.
     *
     * <p>Example:
     * <pre>
     *   CRYPTO_DECRYPT(RECEIVER_NAME) AS NAME  →  NULL AS NAME
     * </pre>
     *
     * <p>Oracle built-ins with underscores (e.g. {@code TO_DATE}, {@code TO_CHAR},
     * {@code TRUNC}) are also replaced, but that is harmless for EXPLAIN PLAN since
     * the optimizer determines the access path from the FROM / WHERE / JOIN clauses.
     *
     * <p>Iterates up to 5 times to handle nested calls such as
     * {@code OUTER_FUNC(INNER_FUNC(col))}.
     */
    private String sanitizeUdfCalls(String sql) {
        String result = sql;
        for (int i = 0; i < 5; i++) {
            String replaced = UDF_CALL_PATTERN.matcher(result).replaceAll("NULL");
            if (replaced.equals(result)) break;  // no more matches
            result = replaced;
        }
        // Guard: if ORDER BY / GROUP BY ends up with "NULL" replace with "1"
        result = result.replaceAll("(?i)(ORDER\\s+BY)\\s+NULL", "$1 1");
        result = result.replaceAll("(?i)(GROUP\\s+BY)\\s+NULL", "");
        return result;
    }

    /** Reads all rows for the given STATEMENT_ID from PLAN_TABLE. */
    private List<ExplainPlanNode> fetchPlanNodes(Connection conn, String stmtId) throws SQLException {
        List<ExplainPlanNode> nodes = new ArrayList<>();
        String query =
                "SELECT ID, PARENT_ID, DEPTH, POSITION, OPERATION, OPTIONS, OBJECT_NAME, " +
                "       CARDINALITY, BYTES, COST, CPU_COST, IO_COST " +
                "  FROM PLAN_TABLE " +
                " WHERE STATEMENT_ID = ? " +
                " ORDER BY ID";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, stmtId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExplainPlanNode node = new ExplainPlanNode();
                    node.setId(rs.getInt("ID"));

                    int parentId = rs.getInt("PARENT_ID");
                    node.setParentId(rs.wasNull() ? null : parentId);

                    node.setDepth(rs.getInt("DEPTH"));
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

    /**
     * Assembles a flat list of nodes (ordered by ID) into a parent-child tree.
     * The root node has {@code parentId == null} (ID = 0).
     */
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
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        return root;
    }

    /** Recursively finds the maximum cost value in the tree (used for cost-bar scaling). */
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
                "SELECT PLAN_TABLE_OUTPUT " +
                "  FROM TABLE(DBMS_XPLAN.DISPLAY('PLAN_TABLE', ?, 'ALL'))")) {
            ps.setString(1, stmtId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(rs.getString(1)).append("\n");
                }
            }
        } catch (SQLException e) {
            // Fallback: DISPLAY() without statement_id (picks most recent plan)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT PLAN_TABLE_OUTPUT FROM TABLE(DBMS_XPLAN.DISPLAY())")) {
                while (rs.next()) {
                    sb.append(rs.getString(1)).append("\n");
                }
            } catch (SQLException e2) {
                sb.append("(실행 계획 텍스트 조회 실패: ").append(e2.getMessage()).append(")");
            }
        }
        return sb.toString();
    }
}
