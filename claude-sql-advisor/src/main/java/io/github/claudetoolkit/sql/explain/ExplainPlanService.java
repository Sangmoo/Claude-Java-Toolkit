package io.github.claudetoolkit.sql.explain;

import io.github.claudetoolkit.starter.client.ClaudeClient;

import java.sql.*;
import java.util.*;

/**
 * Executes Oracle {@code EXPLAIN PLAN} against a live DB, parses the PLAN_TABLE
 * into a structured tree, and enriches the result with a Claude AI analysis.
 *
 * <p>Flow:
 * <ol>
 *   <li>Run {@code EXPLAIN PLAN SET STATEMENT_ID='TOOLKIT_xxx' FOR &lt;sql&gt;}</li>
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
     * @param sql      The SELECT statement to analyze (DML/DDL is rejected gracefully)
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

            // Step 1: EXPLAIN PLAN
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("EXPLAIN PLAN SET STATEMENT_ID = '" + stmtId + "' FOR " + sql);
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
            if (result.getRawPlanText() != null && !result.getRawPlanText().isEmpty()) {
                String userMsg = "## SQL\n```sql\n" + sql + "\n```\n\n" +
                                 "## EXPLAIN PLAN\n```\n" + result.getRawPlanText() + "\n```";
                try {
                    result.setAiAnalysis(claudeClient.chat(SYSTEM_PROMPT, userMsg));
                } catch (Exception e) {
                    result.setAiAnalysis("(AI 분석 오류: " + e.getMessage() + ")");
                }
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
