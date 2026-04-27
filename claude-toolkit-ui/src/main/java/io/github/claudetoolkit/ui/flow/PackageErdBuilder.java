package io.github.claudetoolkit.ui.flow;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

/**
 * v4.5 — 패키지 개요의 "🔗 ERD" 탭용 결정론적 Mermaid 빌더.
 *
 * <p>LLM 을 호출하지 않고 Oracle 메타데이터({@code ALL_TABLES/TAB_COLUMNS/CONSTRAINTS})
 * 를 직접 조회해서 Mermaid erDiagram 구문을 즉시 생성.
 *
 * <p>가독성 전략:
 * <ul>
 *   <li><b>Prefix 그룹핑</b>: 공통 prefix ({@code T_ORD_*}, {@code T_CUST_*}) 를 자동 탐지해
 *       Mermaid 주석으로 섹션 분리 + 렌더러가 시각적으로 그룹화</li>
 *   <li><b>컬럼 상세 on/off</b>: 간략 모드 = 테이블명 + 컬럼 수, 상세 = 전체 컬럼</li>
 *   <li><b>접근 빈도 히트맵</b>: MyBatis 호출수 기반 hotness 라벨 주입 (🔴 🟡 ⚪)</li>
 * </ul>
 *
 * <p>장점: 빠름(수 초), 결정론적, 클릭마다 LLM 토큰 낭비 없음.
 */
@Service
public class PackageErdBuilder {

    private static final Logger log = LoggerFactory.getLogger(PackageErdBuilder.class);

    /** Mermaid 렌더러 보호 — 너무 많은 테이블은 파싱 실패 / 무반응 유발 */
    private static final int MAX_TABLES = 50;

    private final ToolkitSettings settings;

    public PackageErdBuilder(ToolkitSettings settings) {
        this.settings = settings;
    }

    /**
     * 주어진 테이블 목록에 대한 Mermaid ERD + 메타 생성.
     *
     * @param tables     대상 테이블명 목록 (대문자)
     * @param opts       렌더 옵션
     * @param hitCounts  MyBatis 에서 각 테이블이 몇 번 참조됐는지 (heatmap용, null 가능)
     */
    public ErdResult build(Collection<String> tables, ErdOptions opts,
                           Map<String, Integer> hitCounts) {
        ErdResult result = new ErdResult();
        if (tables == null || tables.isEmpty()) {
            result.mermaid = "%% 연관 테이블이 없습니다\nerDiagram";
            return result;
        }
        if (opts == null) opts = new ErdOptions();
        if (hitCounts == null) hitCounts = Collections.emptyMap();

        if (!settings.isDbConfigured()) {
            result.mermaid = "%% DB 설정이 되어있지 않습니다\nerDiagram";
            result.warnings.add("Toolkit 설정에서 DB URL/username/password 를 지정해주세요.");
            return result;
        }

        Set<String> upperTables = new LinkedHashSet<String>();
        for (String t : tables) if (t != null) upperTables.add(t.trim().toUpperCase());

        // v4.5 — 테이블 상한. 너무 많으면 Mermaid 렌더 실패 / 브라우저 프리즈.
        // 빈도 히트맵 상위 N 개 우선, 나머지는 경고만 표시.
        if (upperTables.size() > MAX_TABLES) {
            final Map<String, Integer> hitRef = hitCounts;  // lambda capture 용 effectively-final
            List<String> sorted = new ArrayList<String>(upperTables);
            if (hitRef != null && !hitRef.isEmpty()) {
                sorted.sort((a, b) -> {
                    int ha = hitRef.getOrDefault(a, 0);
                    int hb = hitRef.getOrDefault(b, 0);
                    return Integer.compare(hb, ha);
                });
            }
            List<String> kept = sorted.subList(0, MAX_TABLES);
            result.warnings.add("테이블 " + upperTables.size() + "개 중 가독성 보호를 위해 상위 "
                    + MAX_TABLES + "개 (빈도순) 만 ERD 로 그립니다. 나머지는 [🔗 ERD] 탭 상단 설정에서 prefix 를 좁혀보세요.");
            upperTables = new LinkedHashSet<String>(kept);
        }

        Connection conn = null;
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            DriverManager.setLoginTimeout(5);
            conn = DriverManager.getConnection(
                    settings.getDb().getUrl(),
                    settings.getDb().getUsername(),
                    settings.getDb().getPassword());
            String owner = settings.getDb().getUsername().toUpperCase();

            List<TableInfo> tableInfos = fetchTables(conn, owner, upperTables);
            List<FkInfo>    fkInfos    = fetchForeignKeys(conn, owner, upperTables);

            result.tables = tableInfos;
            result.mermaid = renderMermaid(tableInfos, fkInfos, opts, hitCounts);
            result.foreignKeyCount = fkInfos.size();
        } catch (Exception e) {
            log.warn("[PackageErdBuilder] 메타데이터 조회 실패: {}", e.getMessage());
            result.warnings.add("메타데이터 조회 실패: " + e.getMessage());
            result.mermaid = "%% 메타데이터 조회 실패\nerDiagram";
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
        return result;
    }

    // ── 메타데이터 조회 ───────────────────────────────────────────────────

    private List<TableInfo> fetchTables(Connection conn, String owner, Set<String> names) throws SQLException {
        List<TableInfo> out = new ArrayList<TableInfo>();
        if (names.isEmpty()) return out;

        // 1) TABLE + COMMENT
        Map<String, TableInfo> byName = new LinkedHashMap<String, TableInfo>();
        StringBuilder sql = new StringBuilder(
                "SELECT t.TABLE_NAME, tc.COMMENTS FROM ALL_TABLES t "
              + "LEFT JOIN ALL_TAB_COMMENTS tc ON t.OWNER = tc.OWNER AND t.TABLE_NAME = tc.TABLE_NAME "
              + "WHERE t.OWNER = ? AND t.TABLE_NAME IN (");
        for (int i = 0; i < names.size(); i++) { if (i > 0) sql.append(','); sql.append('?'); }
        sql.append(")");
        PreparedStatement ps = conn.prepareStatement(sql.toString());
        ps.setString(1, owner);
        int idx = 2;
        for (String n : names) ps.setString(idx++, n);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            TableInfo ti = new TableInfo();
            ti.name    = rs.getString("TABLE_NAME");
            ti.comment = rs.getString("COMMENTS");
            byName.put(ti.name, ti);
            out.add(ti);
        }
        rs.close(); ps.close();

        // 2) PK 집합 (테이블별 컬럼셋)
        Map<String, Set<String>> pkCols = fetchPrimaryKeys(conn, owner, names);

        // 3) 컬럼
        StringBuilder cSql = new StringBuilder(
                "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, NULLABLE, DATA_LENGTH "
              + "FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME IN (");
        for (int i = 0; i < names.size(); i++) { if (i > 0) cSql.append(','); cSql.append('?'); }
        cSql.append(") ORDER BY TABLE_NAME, COLUMN_ID");
        PreparedStatement cps = conn.prepareStatement(cSql.toString());
        cps.setString(1, owner);
        idx = 2;
        for (String n : names) cps.setString(idx++, n);
        ResultSet crs = cps.executeQuery();
        while (crs.next()) {
            String tn = crs.getString("TABLE_NAME");
            TableInfo ti = byName.get(tn);
            if (ti == null) continue;
            ColumnInfo ci = new ColumnInfo();
            ci.name     = crs.getString("COLUMN_NAME");
            ci.dataType = crs.getString("DATA_TYPE");
            ci.nullable = "Y".equals(crs.getString("NULLABLE"));
            ci.pk       = pkCols.getOrDefault(tn, Collections.<String>emptySet()).contains(ci.name);
            ti.columns.add(ci);
        }
        crs.close(); cps.close();

        return out;
    }

    private Map<String, Set<String>> fetchPrimaryKeys(Connection conn, String owner,
                                                      Set<String> names) throws SQLException {
        Map<String, Set<String>> out = new HashMap<String, Set<String>>();
        if (names.isEmpty()) return out;
        StringBuilder sql = new StringBuilder(
                "SELECT cc.TABLE_NAME, cc.COLUMN_NAME FROM ALL_CONSTRAINTS c "
              + "JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME "
              + "WHERE c.OWNER = ? AND c.CONSTRAINT_TYPE = 'P' AND cc.TABLE_NAME IN (");
        for (int i = 0; i < names.size(); i++) { if (i > 0) sql.append(','); sql.append('?'); }
        sql.append(")");
        PreparedStatement ps = conn.prepareStatement(sql.toString());
        ps.setString(1, owner);
        int idx = 2;
        for (String n : names) ps.setString(idx++, n);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String tn = rs.getString("TABLE_NAME");
            Set<String> s = out.get(tn);
            if (s == null) { s = new HashSet<String>(); out.put(tn, s); }
            s.add(rs.getString("COLUMN_NAME"));
        }
        rs.close(); ps.close();
        return out;
    }

    private List<FkInfo> fetchForeignKeys(Connection conn, String owner,
                                          Set<String> names) throws SQLException {
        List<FkInfo> out = new ArrayList<FkInfo>();
        if (names.isEmpty()) return out;
        StringBuilder sql = new StringBuilder(
                "SELECT cc.TABLE_NAME AS FROM_T, cc.COLUMN_NAME AS FROM_C, "
              + "       (SELECT MIN(c2.TABLE_NAME) FROM ALL_CONSTRAINTS c2 "
              + "        WHERE c2.CONSTRAINT_NAME = c.R_CONSTRAINT_NAME "
              + "          AND c2.OWNER = c.R_OWNER) AS TO_T "
              + "FROM ALL_CONSTRAINTS c JOIN ALL_CONS_COLUMNS cc "
              + "  ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME "
              + "WHERE c.OWNER = ? AND c.CONSTRAINT_TYPE = 'R' AND cc.TABLE_NAME IN (");
        for (int i = 0; i < names.size(); i++) { if (i > 0) sql.append(','); sql.append('?'); }
        sql.append(")");
        PreparedStatement ps = conn.prepareStatement(sql.toString());
        ps.setString(1, owner);
        int idx = 2;
        for (String n : names) ps.setString(idx++, n);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String toT = rs.getString("TO_T");
            if (toT == null) continue;
            // FK 가 대상 테이블도 이 패키지 목록 안에 있을 때만 포함 (가독성)
            if (!names.contains(toT)) continue;
            FkInfo fk = new FkInfo();
            fk.fromTable  = rs.getString("FROM_T");
            fk.fromColumn = rs.getString("FROM_C");
            fk.toTable    = toT;
            out.add(fk);
        }
        rs.close(); ps.close();
        return out;
    }

    // ── Mermaid 렌더 ─────────────────────────────────────────────────────

    private String renderMermaid(List<TableInfo> tables, List<FkInfo> fks,
                                 ErdOptions opts, Map<String, Integer> hitCounts) {
        StringBuilder mm = new StringBuilder();
        mm.append("erDiagram\n");

        // Prefix 그룹핑 — 테이블명 앞 토큰 (T_ORD_... → ORD) 기준
        Map<String, List<TableInfo>> groups;
        if (opts.prefixGrouping) {
            groups = groupByPrefix(tables);
        } else {
            groups = new LinkedHashMap<String, List<TableInfo>>();
            groups.put("", tables);
        }

        int maxHit = 0;
        for (Integer v : hitCounts.values()) if (v != null && v > maxHit) maxHit = v;

        for (Map.Entry<String, List<TableInfo>> e : groups.entrySet()) {
            String groupKey = e.getKey();
            if (!groupKey.isEmpty()) {
                mm.append("\n%% ── 그룹: ").append(groupKey)
                  .append(" (").append(e.getValue().size()).append("개) ──\n");
            }
            for (TableInfo ti : e.getValue()) {
                String label = ti.name;
                if (opts.heatmap && maxHit > 0) {
                    Integer hc = hitCounts.get(ti.name);
                    int h = hc == null ? 0 : hc;
                    label = heatPrefix(h, maxHit) + " " + label;
                }
                mm.append("    ").append(sanitizeId(ti.name));
                if (opts.columnDetail && !ti.columns.isEmpty()) {
                    // 상세 모드 — 실제 컬럼 attribute 블록
                    // Mermaid 문법: <type> <name> [PK|FK|UK] ["comment"]
                    mm.append(" {\n");
                    for (ColumnInfo ci : ti.columns) {
                        String typeTok = safeType(ci.dataType);
                        String nameTok = sanitizeId(ci.name);
                        if (typeTok.isEmpty()) typeTok = "string";
                        if (nameTok.isEmpty()) nameTok = "col";
                        mm.append("        ").append(typeTok).append(' ').append(nameTok);
                        if (ci.pk)        mm.append(" PK");
                        if (!ci.nullable) mm.append(" \"NOT NULL\"");
                        mm.append('\n');
                    }
                    mm.append("    }\n");
                } else {
                    // 간략 모드 또는 컬럼 정보 없음 → 빈 엔티티 (박스 + 이름만).
                    // 이전 "integer \"(N cols)\"" 는 Mermaid 문법 위반 (ATTRIBUTE_WORD 누락).
                    // 컬럼 수는 화면 하단 "테이블 요약" 리스트에 이미 표시됨.
                    mm.append('\n');
                }
                // 코멘트/히트 주석
                StringBuilder note = new StringBuilder();
                if (ti.comment != null && !ti.comment.isEmpty()) {
                    note.append(ti.comment);
                }
                if (opts.heatmap && hitCounts.get(ti.name) != null) {
                    if (note.length() > 0) note.append(" · ");
                    note.append("hits=").append(hitCounts.get(ti.name));
                }
                if (note.length() > 0) {
                    mm.append("    %% ").append(ti.name).append(": ").append(note).append("\n");
                }
            }
        }

        // FK 관계
        if (!fks.isEmpty()) {
            mm.append("\n%% ── 관계 ──\n");
            for (FkInfo fk : fks) {
                mm.append("    ").append(sanitizeId(fk.fromTable))
                  .append(" }o--|| ").append(sanitizeId(fk.toTable))
                  .append(" : \"").append(fk.fromColumn).append("\"\n");
            }
        }
        return mm.toString();
    }

    private static Map<String, List<TableInfo>> groupByPrefix(List<TableInfo> tables) {
        Map<String, List<TableInfo>> groups = new LinkedHashMap<String, List<TableInfo>>();
        for (TableInfo ti : tables) {
            String key = extractGroupKey(ti.name);
            List<TableInfo> list = groups.get(key);
            if (list == null) { list = new ArrayList<TableInfo>(); groups.put(key, list); }
            list.add(ti);
        }
        // 그룹이 1개 멤버뿐이면 "기타" 로 합치기
        Map<String, List<TableInfo>> merged = new LinkedHashMap<String, List<TableInfo>>();
        List<TableInfo> misc = new ArrayList<TableInfo>();
        for (Map.Entry<String, List<TableInfo>> e : groups.entrySet()) {
            if (e.getValue().size() <= 1) misc.addAll(e.getValue());
            else merged.put(e.getKey(), e.getValue());
        }
        if (!misc.isEmpty()) merged.put("기타", misc);
        return merged;
    }

    private static String extractGroupKey(String tableName) {
        if (tableName == null) return "기타";
        // T_ORD_H → ORD, TB_CUST_M → CUST, PRD_MST → PRD
        String[] parts = tableName.split("_");
        if (parts.length >= 2) {
            if (parts[0].length() <= 2 && parts.length >= 3) return parts[1]; // T_X_...
            return parts[0];
        }
        return "기타";
    }

    private static String heatPrefix(int hit, int maxHit) {
        double r = maxHit > 0 ? (double) hit / (double) maxHit : 0;
        if (hit == 0)   return "";       // 미접근 - 라벨 없음
        if (r >= 0.66)  return "🔴";     // Hot
        if (r >= 0.33)  return "🟡";     // Warm
        return "⚪";                     // Cold
    }

    private static String sanitizeId(String s) {
        if (s == null) return "_";
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String safeType(String t) {
        if (t == null) return "string";
        String lower = t.toLowerCase();
        // Mermaid 는 . 포함 타입 이름을 싫어함
        lower = lower.replaceAll("[^a-z0-9]", "");
        return lower.isEmpty() ? "string" : lower;
    }

    // ── DTO ─────────────────────────────────────────────────────────────

    public static class ErdOptions {
        public boolean columnDetail   = false;
        public boolean prefixGrouping = true;
        public boolean heatmap        = true;
    }

    public static class ErdResult {
        public String mermaid;
        public int    foreignKeyCount;
        public List<TableInfo> tables = new ArrayList<TableInfo>();
        public List<String> warnings  = new ArrayList<String>();

        public String getMermaid()              { return mermaid; }
        public int    getForeignKeyCount()      { return foreignKeyCount; }
        public List<TableInfo> getTables()      { return tables; }
        public List<String> getWarnings()       { return warnings; }
    }

    public static class TableInfo {
        public String name;
        public String comment;
        public List<ColumnInfo> columns = new ArrayList<ColumnInfo>();

        public String getName()              { return name; }
        public String getComment()            { return comment; }
        public List<ColumnInfo> getColumns() { return columns; }
    }

    public static class ColumnInfo {
        public String name;
        public String dataType;
        public boolean nullable;
        public boolean pk;

        public String  getName()     { return name; }
        public String  getDataType() { return dataType; }
        public boolean isNullable()  { return nullable; }
        public boolean isPk()        { return pk; }
    }

    public static class FkInfo {
        public String fromTable;
        public String fromColumn;
        public String toTable;

        public String getFromTable()  { return fromTable; }
        public String getFromColumn() { return fromColumn; }
        public String getToTable()    { return toTable; }
    }
}
