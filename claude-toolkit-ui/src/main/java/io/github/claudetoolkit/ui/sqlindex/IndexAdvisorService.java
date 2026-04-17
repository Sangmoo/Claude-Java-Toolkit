package io.github.claudetoolkit.ui.sqlindex;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v4.3.0 — SQL 인덱스 임팩트 시뮬레이션 서비스.
 *
 * <p>입력 SQL 을 정적 파싱하여:
 * <ol>
 *   <li>참조 테이블 추출 (FROM / JOIN 절)</li>
 *   <li>WHERE / JOIN 조건의 컬럼 추출</li>
 *   <li>대상 DB 의 메타데이터에서 기존 인덱스 조회 (MySQL/PostgreSQL/Oracle/H2 자동 감지)</li>
 *   <li>기존 인덱스로 커버되는 컬럼은 "활용 가능" 으로 분류</li>
 *   <li>커버되지 않는 컬럼은 "신규 인덱스 추천" 으로 DDL 까지 자동 생성</li>
 *   <li>(선택) Claude API 로 추가 분석 코멘트 — 향후 확장</li>
 * </ol>
 *
 * <p>현재 단계는 정적 분석 + 메타데이터 조회만으로 충분히 의미 있는 추천을 생성한다.
 * Claude 호출은 향후 옵션으로 추가 가능.
 */
@Service
public class IndexAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(IndexAdvisorService.class);

    /** FROM/JOIN 뒤의 식별자(스키마 가능) 추출 */
    private static final Pattern TABLE_REF = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN)\\s+([A-Za-z_][\\w$]*(?:\\.[A-Za-z_][\\w$]*)?)" );

    /** WHERE / ON 조건절에서 col=... col<>... col IN(...) 패턴 추출 (스키마.테이블.컬럼 또는 컬럼) */
    private static final Pattern PREDICATE = Pattern.compile(
            "(?i)([A-Za-z_][\\w$]*(?:\\.[A-Za-z_][\\w$]*){0,2})\\s*(?:=|<>|!=|<|>|<=|>=|LIKE|IN|BETWEEN)" );

    /** WHERE / ON 절 범위 추출 (간단 휴리스틱 — GROUP/ORDER 까지) */
    private static final Pattern WHERE_BLOCK = Pattern.compile(
            "(?is)\\bWHERE\\b(.*?)(?:\\bGROUP\\s+BY\\b|\\bORDER\\s+BY\\b|\\bHAVING\\b|\\bLIMIT\\b|\\bFETCH\\b|;|\\z)" );
    private static final Pattern ON_BLOCK = Pattern.compile(
            "(?is)\\bON\\b(.*?)(?:\\bWHERE\\b|\\bJOIN\\b|\\bGROUP\\s+BY\\b|\\bORDER\\s+BY\\b|;|\\z)" );

    private final ToolkitSettings settings;
    private final javax.sql.DataSource dataSource;
    private final ClaudeClient claudeClient;

    public IndexAdvisorService(ToolkitSettings settings,
                               javax.sql.DataSource dataSource,
                               ClaudeClient claudeClient) {
        this.settings    = settings;
        this.dataSource  = dataSource;
        this.claudeClient = claudeClient;
    }

    /**
     * 현재 Settings 에 설정된 외부 DB 연결 정보를 마스킹된 형태로 반환.
     * 프론트가 분석 전 "어떤 DB로 조회될지" 표시하는 용도.
     */
    public Map<String, Object> describeTargetDb() {
        Map<String, Object> info = new LinkedHashMap<String, Object>();
        boolean hasExternal = settings != null
                && settings.getDb() != null
                && settings.getDb().getUrl() != null
                && !settings.getDb().getUrl().trim().isEmpty();
        info.put("hasExternal", hasExternal);
        if (hasExternal) {
            String url = settings.getDb().getUrl();
            info.put("url",      maskJdbcUrl(url));
            info.put("username", settings.getDb().getUsername());
            info.put("dbType",   detectDbTypeFromUrl(url));
            info.put("source",   "settings");
        } else {
            // 외부 DB 미설정 — 시스템 기본 DataSource (보통 H2 — 앱 운영 DB)
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData md = conn.getMetaData();
                info.put("url",      maskJdbcUrl(md.getURL()));
                info.put("username", md.getUserName());
                info.put("dbType",   detectDbType(md.getDatabaseProductName()));
                info.put("source",   "default-datasource");
                info.put("warning",  "Settings 에 외부 DB 가 설정되지 않아 앱 내부 DB(H2 등)로 폴백됩니다. "
                        + "운영 DB 인덱스를 보려면 Settings → Oracle DB 설정 을 입력하세요.");
            } catch (Exception e) {
                info.put("error", e.getMessage());
            }
        }
        return info;
    }

    /**
     * 인덱스 시뮬레이션 수행.
     *
     * @param sql       대상 SQL
     * @param dbProfile "settings" (기본) / "default" — settings 면 ToolkitSettings.db 사용,
     *                  default 면 시스템 DataSource (H2) 사용
     */
    public Map<String, Object> analyze(String sql, String dbProfile) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("sql 은 필수입니다.");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("sql",       sql.trim());
        result.put("dbProfile", dbProfile != null ? dbProfile : "settings");

        // 1. 정적 파싱
        List<String> tables  = extractTables(sql);
        List<String> columns = extractPredicateColumns(sql);

        Map<String, Set<String>> byTable = groupColumnsByTable(tables, columns);
        result.put("tables",        tables);
        result.put("predicateColumns", columns);

        // 2. 기존 인덱스 메타데이터 조회 + 매칭
        // v4.3.x: Settings 에 외부 DB 가 설정되어 있으면 그쪽으로 연결
        List<Map<String, Object>> tableReports = new ArrayList<Map<String, Object>>();
        Connection conn = null;
        try {
            conn = openTargetConnection(dbProfile, result);
            DatabaseMetaData md = conn.getMetaData();
            String dbType = detectDbType(md.getDatabaseProductName());
            result.put("detectedDbType", dbType);
            result.put("dbProduct",      md.getDatabaseProductName());
            result.put("dbUrl",          maskJdbcUrl(md.getURL()));
            result.put("dbUsername",     md.getUserName());

            for (Map.Entry<String, Set<String>> e : byTable.entrySet()) {
                String fullTable = e.getKey();
                Set<String> predCols = e.getValue();
                Map<String, Object> tableReport = analyzeTable(conn, md, dbType, fullTable, predCols);
                tableReports.add(tableReport);
            }
        } catch (Exception ex) {
            log.warn("DB 메타데이터 조회 실패 — DDL 추천만 제공: {}", ex.getMessage());
            result.put("dbConnectionError", ex.getMessage());
            for (Map.Entry<String, Set<String>> e : byTable.entrySet()) {
                Map<String, Object> tableReport = new LinkedHashMap<String, Object>();
                tableReport.put("table", e.getKey());
                tableReport.put("existingIndexes", new ArrayList<Map<String, Object>>());
                List<Map<String, Object>> recs = new ArrayList<Map<String, Object>>();
                if (!e.getValue().isEmpty()) {
                    recs.add(buildRecommendation(e.getKey(), e.getValue(),
                            "DB 메타조회 실패 — 단순 컬럼 기반 추천"));
                }
                tableReport.put("recommendations", recs);
                tableReports.add(tableReport);
            }
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ig) { /* ignore */ }
        }
        result.put("tableReports", tableReports);

        // 요약 카운트
        int existingHits = 0, newRecs = 0;
        for (Map<String, Object> tr : tableReports) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> exi = (List<Map<String, Object>>) tr.get("existingIndexes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rec = (List<Map<String, Object>>) tr.get("recommendations");
            existingHits += exi != null ? exi.size() : 0;
            newRecs      += rec != null ? rec.size() : 0;
        }
        result.put("summaryExistingIndexCount", existingHits);
        result.put("summaryNewRecommendCount",  newRecs);
        return result;
    }

    // ── 정적 파싱 헬퍼 ─────────────────────────────────────────────────────

    private List<String> extractTables(String sql) {
        List<String> tables = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        Matcher m = TABLE_REF.matcher(stripStrings(sql));
        while (m.find()) {
            String t = m.group(1);
            if (seen.add(t.toUpperCase(Locale.ROOT))) tables.add(t);
        }
        return tables;
    }

    private List<String> extractPredicateColumns(String sql) {
        StringBuilder block = new StringBuilder();
        String stripped = stripStrings(sql);
        Matcher mw = WHERE_BLOCK.matcher(stripped);
        while (mw.find()) block.append(' ').append(mw.group(1));
        Matcher mo = ON_BLOCK.matcher(stripped);
        while (mo.find()) block.append(' ').append(mo.group(1));

        List<String> cols = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        Matcher mp = PREDICATE.matcher(block.toString());
        while (mp.find()) {
            String c = mp.group(1);
            // SQL 키워드/함수 제외
            String upper = c.toUpperCase(Locale.ROOT);
            if (isReservedWord(upper)) continue;
            if (seen.add(upper)) cols.add(c);
        }
        return cols;
    }

    private Map<String, Set<String>> groupColumnsByTable(List<String> tables, List<String> columns) {
        Map<String, Set<String>> out = new LinkedHashMap<String, Set<String>>();
        for (String t : tables) out.put(t, new LinkedHashSet<String>());

        for (String fullCol : columns) {
            String[] parts = fullCol.split("\\.");
            if (parts.length >= 2) {
                String tableQual = parts.length == 3 ? parts[0] + "." + parts[1] : parts[0];
                String columnName = parts[parts.length - 1];
                Set<String> bucket = findMatchingBucket(out, tableQual);
                if (bucket != null) bucket.add(columnName);
                else if (tables.size() == 1) {
                    out.get(tables.get(0)).add(columnName);
                }
            } else {
                // 테이블 prefix 없는 컬럼 — 단일 테이블 SQL 이면 그 테이블에 귀속
                if (tables.size() == 1) {
                    out.get(tables.get(0)).add(fullCol);
                }
            }
        }
        // 빈 테이블 제거
        out.entrySet().removeIf(e -> e.getValue().isEmpty());
        return out;
    }

    private Set<String> findMatchingBucket(Map<String, Set<String>> buckets, String tableQual) {
        String upper = tableQual.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, Set<String>> e : buckets.entrySet()) {
            if (e.getKey().toUpperCase(Locale.ROOT).endsWith(upper) ||
                upper.endsWith(e.getKey().toUpperCase(Locale.ROOT))) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 'string literals' 와 라인 코멘트 제거 — 패턴 매칭 정확도 향상 */
    private String stripStrings(String sql) {
        return sql.replaceAll("'[^']*'", "''")
                  .replaceAll("--.*?(\\r?\\n|$)", " ")
                  .replaceAll("(?s)/\\*.*?\\*/", " ");
    }

    private boolean isReservedWord(String upper) {
        switch (upper) {
            case "AND": case "OR": case "NOT": case "NULL": case "TRUE": case "FALSE":
            case "BETWEEN": case "IN": case "LIKE": case "IS": case "ON": case "AS":
            case "SELECT": case "FROM": case "WHERE": case "GROUP": case "ORDER":
            case "BY": case "JOIN": case "INNER": case "OUTER": case "LEFT": case "RIGHT":
                return true;
            default: return false;
        }
    }

    // ── DB 메타데이터 조회 ─────────────────────────────────────────────────

    private Map<String, Object> analyzeTable(Connection conn, DatabaseMetaData md, String dbType,
                                             String fullTable, Set<String> predCols) {
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("table", fullTable);

        String schema = null, tableName = fullTable;
        if (fullTable.contains(".")) {
            String[] parts = fullTable.split("\\.");
            schema    = parts[0];
            tableName = parts[1];
        }

        List<Map<String, Object>> existing = new ArrayList<Map<String, Object>>();
        Set<String> coveredCols = new LinkedHashSet<String>();
        try {
            // JDBC 표준 — 모든 DB 에서 동일하게 동작
            ResultSet idxRs = md.getIndexInfo(null, schema, tableName, false, true);
            Map<String, Map<String, Object>> idxMap = new LinkedHashMap<String, Map<String, Object>>();
            while (idxRs.next()) {
                String idxName = idxRs.getString("INDEX_NAME");
                if (idxName == null) continue;
                String colName = idxRs.getString("COLUMN_NAME");
                short ordinal  = idxRs.getShort("ORDINAL_POSITION");
                Map<String, Object> idx = idxMap.get(idxName);
                if (idx == null) {
                    idx = new LinkedHashMap<String, Object>();
                    idx.put("name",    idxName);
                    idx.put("columns", new ArrayList<String>());
                    idx.put("nonUnique", idxRs.getBoolean("NON_UNIQUE"));
                    idxMap.put(idxName, idx);
                }
                @SuppressWarnings("unchecked")
                List<String> cols = (List<String>) idx.get("columns");
                while (cols.size() < ordinal) cols.add(null);
                if (ordinal >= 1 && cols.size() >= ordinal) cols.set(ordinal - 1, colName);
            }
            idxRs.close();

            // 인덱스의 leading column 이 predCols 에 포함되는지 확인 → 활용 가능
            for (Map<String, Object> idx : idxMap.values()) {
                @SuppressWarnings("unchecked")
                List<String> cols = (List<String>) idx.get("columns");
                cols.removeIf(java.util.Objects::isNull);
                String leading = cols.isEmpty() ? null : cols.get(0);
                boolean usable = false;
                if (leading != null) {
                    for (String pc : predCols) {
                        if (pc.equalsIgnoreCase(leading)) { usable = true; break; }
                    }
                }
                idx.put("usableForQuery", usable);
                if (usable) {
                    coveredCols.add(leading.toUpperCase(Locale.ROOT));
                    idx.put("recommendation", "✅ 기존 인덱스 활용 가능 — leading column "
                            + leading + " 가 WHERE 조건과 일치");
                } else {
                    idx.put("recommendation", "ℹ️ 이 쿼리에는 미활용 — leading column 이 다름");
                }
                existing.add(idx);
            }
        } catch (Exception e) {
            log.debug("인덱스 메타조회 실패: table={}, err={}", fullTable, e.getMessage());
        }
        report.put("existingIndexes", existing);

        // 미커버 컬럼 → 신규 인덱스 추천
        Set<String> uncovered = new LinkedHashSet<String>();
        for (String pc : predCols) {
            if (!coveredCols.contains(pc.toUpperCase(Locale.ROOT))) uncovered.add(pc);
        }
        List<Map<String, Object>> recs = new ArrayList<Map<String, Object>>();
        if (!uncovered.isEmpty()) {
            recs.add(buildRecommendation(fullTable, uncovered,
                    "WHERE 조건의 미커버 컬럼 — 신규 인덱스로 풀스캔 회피 가능"));
        }
        report.put("recommendations", recs);
        return report;
    }

    private Map<String, Object> buildRecommendation(String fullTable, Set<String> cols, String rationale) {
        Map<String, Object> rec = new LinkedHashMap<String, Object>();
        String tableShort = fullTable.contains(".") ? fullTable.substring(fullTable.indexOf('.') + 1) : fullTable;
        StringBuilder colList = new StringBuilder();
        StringBuilder colSnake = new StringBuilder();
        for (String c : cols) {
            if (colList.length() > 0) { colList.append(", "); colSnake.append("_"); }
            colList.append(c);
            colSnake.append(c.replaceAll("[^A-Za-z0-9]", ""));
        }
        String idxName = ("IDX_" + tableShort + "_" + colSnake).toUpperCase(Locale.ROOT);
        if (idxName.length() > 30) idxName = idxName.substring(0, 30); // Oracle 식별자 제한
        String ddl = "CREATE INDEX " + idxName + " ON " + fullTable + " (" + colList + ");";

        rec.put("indexName",  idxName);
        rec.put("table",      fullTable);
        rec.put("columns",    new ArrayList<String>(cols));
        rec.put("ddl",        ddl);
        rec.put("rationale",  rationale);
        rec.put("priority",   cols.size() == 1 ? "MEDIUM" : "HIGH");
        return rec;
    }

    private String detectDbType(String product) {
        if (product == null) return "unknown";
        String l = product.toLowerCase(Locale.ROOT);
        if (l.contains("h2"))         return "h2";
        if (l.contains("mysql"))      return "mysql";
        if (l.contains("postgresql")) return "postgresql";
        if (l.contains("postgres"))   return "postgresql";
        if (l.contains("oracle"))     return "oracle";
        return "unknown";
    }

    private String detectDbTypeFromUrl(String url) {
        if (url == null) return "unknown";
        String l = url.toLowerCase(Locale.ROOT);
        if (l.contains(":h2:"))         return "h2";
        if (l.contains(":mysql:"))      return "mysql";
        if (l.contains(":postgresql:")) return "postgresql";
        if (l.contains(":oracle:"))     return "oracle";
        if (l.contains(":sqlserver:"))  return "mssql";
        return "unknown";
    }

    private String maskJdbcUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("password=([^&;]*)", "password=****")
                  .replaceAll(":[^:@/]+@", ":****@");
    }

    /**
     * 분석 대상 DB 연결 — settings 우선, 미설정 시 기본 DataSource (H2) 폴백.
     *
     * @param dbProfile  "settings" / "default" — null 또는 다른 값은 settings 로 간주
     * @param result     호출자 응답 맵에 source/warning 정보 주입
     */
    private Connection openTargetConnection(String dbProfile, Map<String, Object> result) throws Exception {
        boolean wantsDefault = "default".equalsIgnoreCase(dbProfile)
                            || "h2".equalsIgnoreCase(dbProfile);

        if (!wantsDefault && settings != null && settings.getDb() != null
                && settings.getDb().getUrl() != null
                && !settings.getDb().getUrl().trim().isEmpty()) {
            // Settings 의 외부 DB 사용
            String url = settings.getDb().getUrl();
            String user = settings.getDb().getUsername();
            String pass = settings.getDb().getPassword();
            try {
                // 드라이버 명시적 로드 — Spring Boot 의 lazy 로딩에 의존하지 않음
                loadDriverFor(url);
                Connection c = DriverManager.getConnection(url, user, pass);
                result.put("dbSource", "settings");
                return c;
            } catch (Exception e) {
                log.warn("Settings DB 연결 실패 — 기본 DataSource 로 폴백: {}", e.getMessage());
                result.put("dbSource", "default-fallback");
                result.put("settingsDbError", e.getMessage());
                return dataSource.getConnection();
            }
        }
        result.put("dbSource", "default");
        return dataSource.getConnection();
    }

    private void loadDriverFor(String url) {
        if (url == null) return;
        String l = url.toLowerCase(Locale.ROOT);
        try {
            if (l.contains(":oracle:"))      Class.forName("oracle.jdbc.OracleDriver");
            else if (l.contains(":mysql:"))  Class.forName("com.mysql.cj.jdbc.Driver");
            else if (l.contains(":postgresql:")) Class.forName("org.postgresql.Driver");
            else if (l.contains(":h2:"))     Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            log.debug("JDBC 드라이버 로드 실패 (DriverManager 가 자동 시도): {}", e.getMessage());
        }
    }
}
