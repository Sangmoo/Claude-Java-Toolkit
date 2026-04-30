package io.github.claudetoolkit.ui.livedb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v4.7.x — #G3 Live DB Phase 4: Oracle INVISIBLE INDEX 시뮬레이터.
 *
 * <p>다음 시퀀스로 운영 영향 0 시뮬레이션:
 * <ol>
 *   <li>{@code ALTER SESSION SET optimizer_use_invisible_indexes = TRUE} — 현 세션만 영향</li>
 *   <li>{@code EXPLAIN PLAN FOR <user_sql>} (before — 인덱스 없음)</li>
 *   <li>각 인덱스: {@code CREATE INDEX <ctk_sim_xxx> ... INVISIBLE}</li>
 *   <li>{@code EXPLAIN PLAN FOR <user_sql>} (after — 인덱스들 적용)</li>
 *   <li>finally: {@code DROP INDEX <ctk_sim_xxx>} (모든 시뮬 인덱스)</li>
 *   <li>finally: {@code ALTER SESSION SET optimizer_use_invisible_indexes = FALSE}</li>
 * </ol>
 *
 * <p>INVISIBLE INDEX 는 *optimizer 후보로만* 동작 — 운영 SQL 의 실행계획에 영향 0.
 * 게다가 위 시퀀스가 *세션 단위* 로만 보이게 설정하므로 다른 사용자의 분석에도 영향 0.
 *
 * <p><b>안전장치 (이 클래스의 핵심):</b>
 * <ul>
 *   <li>최대 5개 인덱스 — 6개 이상 시 SecurityException</li>
 *   <li>인덱스 이름 prefix 강제 — 모든 시뮬 인덱스는 {@code CTK_SIM_xxx} 로 자동 rename</li>
 *   <li>INVISIBLE 옵션 강제 — 사용자가 보내는 DDL 에 자동 추가 (있으면 보존)</li>
 *   <li>statement timeout 60초 — 큰 테이블 시뮬레이션이 무한 대기 안 하도록</li>
 *   <li>try/finally — 어떤 실패에도 DROP + 세션 reset</li>
 * </ul>
 */
public class OracleIndexSimulator implements IndexSimulator {

    private static final Logger log = LoggerFactory.getLogger(OracleIndexSimulator.class);

    /** 시뮬레이션 인덱스 이름 prefix — 운영 인덱스와 명확히 구분 */
    public static final String SIM_INDEX_PREFIX = "CTK_SIM_";

    /** 한 번 시뮬레이션에 사용할 수 있는 최대 인덱스 수 */
    public static final int MAX_INDEXES_PER_SIMULATION = 5;

    /** 각 SQL (CREATE/DROP/EXPLAIN) statement timeout 초 */
    public static final int STATEMENT_TIMEOUT_SECONDS = 60;

    /** {@code CREATE INDEX <name> ON <table> (<cols>)} 패턴 — 사용자 DDL 검증/재작성용 */
    private static final Pattern CREATE_INDEX_PATTERN = Pattern.compile(
            "(?i)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+([A-Za-z0-9_.]+)\\s+ON\\s+([A-Za-z0-9_.]+)\\s*\\(([^)]+)\\)",
            Pattern.DOTALL);

    @Override
    public String getDbType() { return "oracle"; }

    @Override
    public IndexSimulationResult simulate(String userSql, List<String> indexDefs, DataSource dataSource) {
        IndexSimulationResult result = new IndexSimulationResult();
        result.setUserSql(userSql);

        // 1. SQL 안전 검증 — read-only 만 허용
        SqlOperation op = SqlClassifier.classify(userSql);
        if (!SqlClassifier.isReadOnly(op)) {
            throw new SecurityException(
                "IndexSimulator 는 read-only SQL 만 시뮬레이션 가능 — 받은 op=" + op);
        }
        // 2. 인덱스 수 상한
        if (indexDefs == null || indexDefs.isEmpty()) {
            throw new SecurityException("최소 1개 이상의 인덱스 정의가 필요합니다.");
        }
        if (indexDefs.size() > MAX_INDEXES_PER_SIMULATION) {
            throw new SecurityException(
                "한 번에 최대 " + MAX_INDEXES_PER_SIMULATION
                + "개 인덱스만 시뮬레이션 가능 — 요청: " + indexDefs.size());
        }

        // 3. 사용자 DDL → 시뮬 DDL (이름 rename + INVISIBLE 추가)
        List<SimIndex> simIndexes = new ArrayList<SimIndex>();
        for (int i = 0; i < indexDefs.size(); i++) {
            try {
                simIndexes.add(rewriteToSimIndex(indexDefs.get(i), i));
            } catch (Exception e) {
                throw new SecurityException(
                    "인덱스 정의 " + (i + 1) + "번 파싱 실패: " + e.getMessage());
            }
        }
        List<String> simulatedDdlList = new ArrayList<String>();
        for (SimIndex s : simIndexes) simulatedDdlList.add(s.simDdl);
        result.setSimulatedIndexes(simulatedDdlList);

        String trimmedSql = userSql.trim();
        if (trimmedSql.endsWith(";")) trimmedSql = trimmedSql.substring(0, trimmedSql.length() - 1);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);

        // ── 시뮬레이션 본체 — try/finally 로 cleanup 보장 ──
        boolean sessionConfigured = false;
        List<String> createdIndexes = new ArrayList<String>();
        try {
            // 1. invisible index 옵션 활성화
            jdbc.execute("ALTER SESSION SET optimizer_use_invisible_indexes = TRUE");
            sessionConfigured = true;

            // 2. before plan
            try {
                String[] beforePlan = explain(jdbc, trimmedSql, "ctk_before");
                result.setBeforePlanText(beforePlan[1]);
                result.setBeforeCost(parseLong(beforePlan[0]));
            } catch (Exception e) {
                result.addWarning("before EXPLAIN 실패: " + brief(e));
            }

            // 3. 시뮬 인덱스 생성
            for (SimIndex s : simIndexes) {
                try {
                    jdbc.execute(s.simDdl);
                    createdIndexes.add(s.simName);
                } catch (Exception e) {
                    result.addWarning("인덱스 " + s.simName + " 생성 실패 (스킵): " + brief(e));
                }
            }

            // 4. after plan — 위에서 만든 인덱스가 한 개라도 있으면 비교 의미 있음
            if (!createdIndexes.isEmpty()) {
                try {
                    String[] afterPlan = explain(jdbc, trimmedSql, "ctk_after");
                    result.setAfterPlanText(afterPlan[1]);
                    result.setAfterCost(parseLong(afterPlan[0]));
                } catch (Exception e) {
                    result.addWarning("after EXPLAIN 실패: " + brief(e));
                }
            }

        } catch (Exception e) {
            result.addWarning("시뮬레이션 중단: " + brief(e));
        } finally {
            // 5. 만든 인덱스 모두 DROP — 실패해도 다음 인덱스 진행
            for (String name : createdIndexes) {
                try {
                    jdbc.execute("DROP INDEX " + name);
                } catch (Exception e) {
                    log.warn("[IndexSimulator] DROP INDEX {} 실패 (수동 정리 필요): {}", name, e.getMessage());
                    result.addWarning("DROP INDEX " + name + " 실패 — DBA 가 수동으로 정리 필요: " + brief(e));
                }
            }
            // 6. 세션 옵션 reset
            if (sessionConfigured) {
                try {
                    jdbc.execute("ALTER SESSION SET optimizer_use_invisible_indexes = FALSE");
                } catch (Exception ignored) {}
            }
        }

        return result;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * 사용자 DDL 을 시뮬레이션용 DDL 로 재작성:
     *  - 인덱스 이름을 {@link #SIM_INDEX_PREFIX} + idx 로 강제
     *  - INVISIBLE 옵션 추가 (이미 있으면 그대로)
     *  - 그 외 부분은 보존 (테이블 이름 / 컬럼 / UNIQUE 등)
     */
    static SimIndex rewriteToSimIndex(String userDdl, int idx) {
        if (userDdl == null) throw new IllegalArgumentException("DDL is null");
        String trimmed = userDdl.trim();
        if (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        Matcher m = CREATE_INDEX_PATTERN.matcher(trimmed);
        if (!m.find()) {
            throw new IllegalArgumentException("CREATE INDEX 패턴 매칭 실패: " + previewSql(trimmed));
        }
        String table = m.group(2);
        String cols  = m.group(3);

        // 안전: 이름은 *항상* 우리가 부여 — 사용자 DDL 의 이름은 무시
        String simName = SIM_INDEX_PREFIX + System.nanoTime() + "_" + idx;
        // Oracle 은 30자 상한 (12c 이전), 12c+ 은 128자 — 안전하게 30자로 잘라냄
        if (simName.length() > 28) simName = simName.substring(0, 28);

        // UNIQUE 옵션 보존 (사용자가 UNIQUE INDEX 를 추천했다면)
        boolean unique = trimmed.toUpperCase().contains(" UNIQUE ")
                      || trimmed.toUpperCase().startsWith("CREATE UNIQUE");

        StringBuilder sb = new StringBuilder("CREATE ");
        if (unique) sb.append("UNIQUE ");
        sb.append("INDEX ").append(simName)
          .append(" ON ").append(table)
          .append(" (").append(cols.trim()).append(")")
          .append(" INVISIBLE");

        return new SimIndex(simName, sb.toString());
    }

    /**
     * EXPLAIN PLAN FOR <sql> + PLAN_TABLE 에서 *root operation cost* + 텍스트 plan 추출.
     *
     * @return [costString, planText] — costString 은 null 가능
     */
    private String[] explain(JdbcTemplate jdbc, String userSql, String stmtId) {
        // INSERT into PLAN_TABLE
        String suffix = stmtId + "_" + System.nanoTime();
        if (suffix.length() > 28) suffix = suffix.substring(0, 28);
        jdbc.execute("EXPLAIN PLAN SET STATEMENT_ID = '" + suffix + "' FOR " + userSql);

        // root cost (id=0)
        String cost = null;
        try {
            cost = jdbc.queryForObject(
                    "SELECT TO_CHAR(COST) FROM PLAN_TABLE " +
                    "WHERE STATEMENT_ID = ? AND ID = 0",
                    String.class, suffix);
        } catch (Exception ignored) { /* PLAN_TABLE COST 컬럼 없을 수 있음 (legacy) */ }

        // 텍스트 plan
        StringBuilder sb = new StringBuilder();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT PLAN_TABLE_OUTPUT FROM TABLE(" +
                    "  DBMS_XPLAN.DISPLAY('PLAN_TABLE', ?, 'TYPICAL'))",
                    suffix);
            for (Map<String, Object> r : rows) {
                Object line = r.get("PLAN_TABLE_OUTPUT");
                if (line != null) sb.append(line).append('\n');
            }
        } catch (Exception ignored) {}

        return new String[]{cost, sb.toString().trim()};
    }

    private static Long parseLong(String s) {
        if (s == null) return null;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return null; }
    }

    private static String brief(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return m.length() > 200 ? m.substring(0, 200) + "..." : m;
    }

    private static String previewSql(String s) {
        if (s == null) return "(null)";
        return s.length() > 80 ? s.substring(0, 77) + "..." : s;
    }

    /** 사용자 DDL 1개 → 시뮬 DDL 변환 결과 (이름 + 재작성된 DDL) */
    static final class SimIndex {
        final String simName;
        final String simDdl;
        SimIndex(String simName, String simDdl) {
            this.simName = simName;
            this.simDdl  = simDdl;
        }
    }
}
