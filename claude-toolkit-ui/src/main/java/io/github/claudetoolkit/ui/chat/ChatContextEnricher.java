package io.github.claudetoolkit.ui.chat;

import io.github.claudetoolkit.sql.db.OracleMetaService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v4.4.x — AI 채팅 자동 컨텍스트 엔리처.
 *
 * <p>사용자 질문을 분석하여 자동으로 다음을 system prompt 에 추가:
 * <ul>
 *   <li><b>DB 스키마</b>: 메시지에 언급된 테이블명 (T_* / 대문자 식별자) 을
 *       Settings 의 DB 에서 조회해 컬럼/PK/인덱스 정보 주입</li>
 *   <li><b>코드 스니펫</b>: Settings 프로젝트 스캔 경로의 파일을 grep 하여
 *       해당 테이블/컬럼/함수명을 참조하는 SQL/Java/MyBatis 코드 발췌</li>
 * </ul>
 *
 * <p>예시 사용자 입력:
 * <pre>"T_SHOP_INVT_RANK 테이블에서 FINAL_RANK 컬럼이 언제 UPDATE 되는지 확인해줘"</pre>
 *
 * <p>자동 추가되는 컨텍스트:
 * <ul>
 *   <li>Oracle 에서 T_SHOP_INVT_RANK 테이블 메타정보 (컬럼/타입/PK/인덱스)</li>
 *   <li>프로젝트 안에서 'T_SHOP_INVT_RANK' 또는 'FINAL_RANK' 를 포함하는 .java/.xml/.sql 파일
 *       (UPDATE/MERGE 구문 우선 — 어떤 SP/Mapper 가 갱신하는지 즉시 확인 가능)</li>
 * </ul>
 */
@Service
public class ChatContextEnricher {

    private static final Logger log = LoggerFactory.getLogger(ChatContextEnricher.class);

    /** Oracle/DB 테이블명 패턴 — T_* / 대문자 8자 이상 / 밑줄 포함 식별자 */
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "\\b(T_[A-Z_0-9]{2,}|[A-Z][A-Z_0-9]{6,})\\b");

    /** 컬럼명 패턴 — 대문자 + 밑줄 (테이블보다 짧을 수 있음) */
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Z_0-9]{2,})\\b");

    /** 한 번에 스캔할 최대 파일 수 (성능 보호) — ERP 급 모노레포 (수만 파일) 대응 */
    private static final int MAX_SCANNED_FILES = 20_000;
    /** 매칭된 코드 발췌 최대 개수 — 점수순 상위 N */
    private static final int MAX_SNIPPETS = 8;
    /** 발췌 1개당 최대 글자 수 */
    private static final int SNIPPET_MAX_LEN = 800;
    /** 컨텍스트 전체 최대 글자 수 (토큰 폭주 방지) */
    private static final int CONTEXT_MAX_TOTAL = 12_000;

    /** 일반적 영어 단어로 false positive 가 많은 식별자 — 필터 */
    private static final Set<String> SQL_KEYWORDS = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "UPDATE", "DELETE", "INSERT", "VALUES",
            "JOIN", "INNER", "OUTER", "LEFT", "RIGHT", "FULL", "CROSS",
            "AND", "OR", "NOT", "NULL", "IS", "IN", "LIKE", "BETWEEN",
            "GROUP", "ORDER", "BY", "HAVING", "ASC", "DESC", "DISTINCT",
            "CASE", "WHEN", "THEN", "ELSE", "END", "AS", "WITH", "ON",
            "CREATE", "TABLE", "INDEX", "VIEW", "DROP", "ALTER", "COLUMN",
            "PRIMARY", "FOREIGN", "KEY", "REFERENCES", "CONSTRAINT", "DEFAULT",
            "TODO", "FIXME", "NOTE", "HACK", "BUG"
    ));

    private final ToolkitSettings settings;
    private final OracleMetaService oracleMetaService;

    public ChatContextEnricher(ToolkitSettings settings, OracleMetaService oracleMetaService) {
        this.settings = settings;
        this.oracleMetaService = oracleMetaService;
    }

    /**
     * 사용자 메시지 분석 → DB 스키마 + 코드 컨텍스트를 markdown 으로 반환.
     * 아무것도 못찾으면 빈 문자열 반환 (system prompt 에 추가하지 않음).
     */
    public String enrich(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        // ── 1. 테이블 메타조회 ─────────────────────────────────────
        Set<String> tables = extractCandidates(TABLE_PATTERN, userMessage);
        if (!tables.isEmpty() && settings.isDbConfigured()) {
            String dbCtx = fetchTableMetadata(tables);
            if (!dbCtx.isEmpty()) {
                sb.append("\n\n## 🗄️ 자동 감지된 DB 스키마\n\n").append(dbCtx);
            }
        }

        // ── 2. 코드 grep ──────────────────────────────────────────
        Set<String> keywords = new LinkedHashSet<>(tables);
        for (String c : extractCandidates(COLUMN_PATTERN, userMessage)) {
            if (!SQL_KEYWORDS.contains(c) && c.length() >= 3) keywords.add(c);
        }
        if (!keywords.isEmpty()) {
            String scanPath = settings.getProject() != null ? settings.getProject().getScanPath() : null;
            if (scanPath != null && !scanPath.trim().isEmpty()) {
                String codeCtx = grepProjectFiles(scanPath, keywords);
                if (codeCtx != null && !codeCtx.isEmpty()) {
                    // codeCtx 는 매칭이 0 건이어도 "검색했지만 못 찾음" 메시지를 반환 →
                    // AI 가 "프로젝트에 코드가 없다" 고 단정하지 않고 "검색은 됐지만 못 찾음 →
                    // 다른 키워드 / DB 트리거 / 외부 시스템 가능성" 으로 답하게 됨.
                    sb.append("\n\n## 📂 자동 감지된 프로젝트 코드 참조\n\n").append(codeCtx);
                }
            }
        }

        // ── 3. DB 오브젝트 (PROCEDURE/FUNCTION/PACKAGE/TRIGGER) 소스 grep ──
        // ALL_SOURCE 를 검색해 SP/FUNC/PKG 안에서 테이블/컬럼명을 참조하는 곳을 찾는다.
        // 사용자 시나리오: "GIFT_MALL_ORDER 가 언제 INSERT 되나?" 에 Java 코드는 없어도
        // SP_WMS_DELV_SALE 같은 SP 안의 INSERT 문이 답인 경우가 많다.
        if (!tables.isEmpty() && settings.isDbConfigured()) {
            String dbObjCtx = grepDbObjectSources(tables);
            if (dbObjCtx != null && !dbObjCtx.isEmpty()) {
                sb.append("\n\n## 🗃️ 자동 감지된 DB 오브젝트 (SP/Func/Pkg/Trigger) 참조\n\n")
                  .append(dbObjCtx);
            }
        }

        String result = sb.toString();
        if (result.length() > CONTEXT_MAX_TOTAL) {
            result = result.substring(0, CONTEXT_MAX_TOTAL)
                  + "\n\n_(컨텍스트가 너무 길어 절단됨 — 토큰 절약)_";
        }
        return result;
    }

    // ── 1. 테이블 메타 ─────────────────────────────────────────────────

    private String fetchTableMetadata(Set<String> tables) {
        StringBuilder sb = new StringBuilder();
        String url = settings.getDb().getUrl();
        String user = settings.getDb().getUsername();
        String pass = settings.getDb().getPassword();

        // 추출된 테이블을 SQL 형식으로 조립해서 OracleMetaService.buildTableContext 가
        // 자기 식으로 메타조회 가능하도록 처리. (메서드는 SQL 안에 등장한 테이블만 본다)
        StringBuilder fakeSql = new StringBuilder("SELECT 1 FROM ");
        boolean first = true;
        int count = 0;
        for (String t : tables) {
            if (count++ >= 10) break;  // 너무 많은 테이블 조회 방지
            if (!first) fakeSql.append(", ");
            fakeSql.append(t);
            first = false;
        }
        try {
            String ctx = oracleMetaService.buildTableContext(url, user, pass, fakeSql.toString());
            if (ctx != null && !ctx.trim().isEmpty()) sb.append(ctx);
        } catch (Exception e) {
            log.debug("DB 메타조회 실패 (silent): {}", e.getMessage());
        }
        return sb.toString();
    }

    // ── 2. 코드 grep ───────────────────────────────────────────────────

    /**
     * 프로젝트 전체를 스캔하면서 모든 매칭 파일을 점수화 → 상위 {@link #MAX_SNIPPETS} 개만
     * 반환. 이전에는 매칭 8개를 채우면 walk 를 즉시 종료해, 알파벳 순으로 먼저 만난
     * 무관한 파일이 슬롯을 차지하고 정작 핵심 SQLMAP 파일은 방문조차 안 되는 버그가
     * 있었다.
     *
     * <p><b>점수 체계</b> (높을수록 우선):
     * <ul>
     *   <li>+15 / 키워드 — 스니펫 안에 포함된 distinct 키워드 수 (SHOP_INVT_RANK
     *       와 FINAL_RANK 둘 다 들어있으면 +30)</li>
     *   <li>+10 UPDATE / +8 MERGE / +5 INSERT / +3 DELETE — DML 보너스</li>
     *   <li>+20 if 파일명에 sqlmap/mapper/dao/service 포함 — 보통 사용자가 찾는 곳</li>
     * </ul>
     *
     * <p>0 건이어도 빈 문자열이 아닌 "검색했지만 매칭 없음" 진단 메시지를 반환 →
     * AI 가 "프로젝트에 코드가 없다" 로 단정하는 것을 방지.
     */
    private String grepProjectFiles(String rootPath, Set<String> keywords) {
        // v4.4.x — Linux 컨테이너에서 Windows 경로 자동 변환 (D:\ → /host/d/)
        String resolved = io.github.claudetoolkit.ui.config.HostPathTranslator.translate(rootPath);
        Path root = Paths.get(resolved);
        if (!Files.isDirectory(root)) {
            log.warn("[Enricher] 스캔 경로가 디렉토리가 아님: input='{}', resolved='{}'",
                    rootPath, resolved);
            return String.format("_⚠ 스캔 경로(`%s`)에 접근할 수 없어 코드 검색을 건너뜀._\n", rootPath);
        }

        final List<Snippet> all = new ArrayList<>();
        final int[] scanned = { 0 };
        final int[] matched = { 0 };
        long start = System.currentTimeMillis();

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 12, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (scanned[0]++ > MAX_SCANNED_FILES) return FileVisitResult.TERMINATE;
                    String name = file.getFileName().toString().toLowerCase();
                    if (!name.endsWith(".java") && !name.endsWith(".xml")
                            && !name.endsWith(".sql") && !name.endsWith(".pls")
                            && !name.endsWith(".pck")) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        if (Files.size(file) > 1_500_000) return FileVisitResult.CONTINUE;
                        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        // ── 이 파일에서 가장 좋은 매칭 위치 1개 추출 (모든 키워드 합산 점수) ──
                        int bestIdx = -1;
                        String bestKw = null;
                        for (String kw : keywords) {
                            int idx = content.indexOf(kw);
                            if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                                bestIdx = idx; bestKw = kw;
                            }
                        }
                        if (bestIdx >= 0) {
                            matched[0]++;
                            all.add(extractSnippet(file, content, bestIdx, bestKw, root, keywords));
                        }
                    } catch (Exception ignored) { /* unreadable file — skip */ }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String n = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (n.equals("target") || n.equals("build") || n.equals("node_modules")
                            || n.equals(".git") || n.equals(".idea") || n.equals("out")
                            || n.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("[Enricher] 프로젝트 스캔 실패: {}", e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[Enricher] grep 완료: root='{}' keywords={} scanned={} matched={} elapsed={}ms",
                root, keywords, scanned[0], matched[0], elapsed);

        if (all.isEmpty()) {
            // 0 건이어도 명시적 메시지 반환 — AI 가 "정보 없음" 으로 단정 못 하게 차단
            return String.format(
                    "_프로젝트 스캔 결과: `%s` 키워드를 포함하는 파일이 **0 건** "
                            + "(총 %d 파일 검사, %d ms). 키워드 변형(예: 동의어, 약어), DB 트리거, "
                            + "외부 배치/ETL, 또는 별도 모듈 가능성을 점검 필요._\n",
                    String.join(", ", keywords), scanned[0], elapsed);
        }

        // 점수 내림차순 정렬 후 상위 N
        all.sort((a, b) -> Integer.compare(b.priority, a.priority));
        List<Snippet> top = all.size() > MAX_SNIPPETS ? all.subList(0, MAX_SNIPPETS) : all;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "프로젝트 스캔 결과 — 키워드 `%s` 매칭 **총 %d 파일** (스캔 %d, %d ms). "
                        + "관련도 상위 %d 건:\n\n",
                String.join(", ", keywords), all.size(), scanned[0], elapsed, top.size()));
        for (Snippet s : top) {
            sb.append("### `").append(s.relativePath)
              .append("` (line ~").append(s.lineNumber)
              .append(", score=").append(s.priority).append(")\n\n");
            String langTag = s.relativePath.toLowerCase().endsWith(".java") ? "java"
                    : (s.relativePath.toLowerCase().endsWith(".xml") ? "xml" : "sql");
            sb.append("```").append(langTag).append("\n").append(s.code).append("\n```\n\n");
        }
        return sb.toString();
    }

    private Snippet extractSnippet(Path file, String content, int matchIdx, String keyword,
                                   Path root, Set<String> allKeywords) {
        // 매칭 위치 주변 ±500 자 발췌 (라인 단위로 확장)
        int start = Math.max(0, matchIdx - 500);
        int end = Math.min(content.length(), matchIdx + 500);
        while (start > 0 && content.charAt(start - 1) != '\n') start--;
        while (end < content.length() && content.charAt(end) != '\n') end++;
        String snippet = content.substring(start, end);
        if (snippet.length() > SNIPPET_MAX_LEN) {
            snippet = snippet.substring(0, SNIPPET_MAX_LEN) + "\n... (생략)";
        }
        int lineNum = 1;
        for (int i = 0; i < matchIdx; i++) if (content.charAt(i) == '\n') lineNum++;

        // ── 점수 산정 ──────────────────────────────────────────────────
        int priority = 0;
        String upper = snippet.toUpperCase();
        // (1) 스니펫 내 distinct 키워드 — 같은 파일에 테이블+컬럼 둘 다 있으면 강력한 신호
        for (String kw : allKeywords) {
            if (snippet.contains(kw)) priority += 15;
        }
        // (2) DML 보너스
        if (upper.contains("UPDATE")) priority += 10;
        if (upper.contains("MERGE"))  priority += 8;
        if (upper.contains("INSERT")) priority += 5;
        if (upper.contains("DELETE")) priority += 3;
        // (3) 파일 위치/이름 — sqlmap/mapper/dao/service 는 보통 비즈니스 로직
        String pathLower = file.toString().toLowerCase();
        if (pathLower.contains("sqlmap") || pathLower.contains("mapper")) priority += 20;
        if (pathLower.contains("/dao/") || pathLower.contains("/service/")) priority += 10;

        Snippet s = new Snippet();
        s.relativePath = root.relativize(file).toString().replace('\\', '/');
        s.code = snippet;
        s.lineNumber = lineNum;
        s.priority = priority;
        return s;
    }

    // ── 3. DB 오브젝트 (ALL_SOURCE) grep ───────────────────────────────

    /** ALL_SOURCE 한 라인 매칭당 최대 후보 오브젝트 수 (DB 부하 보호) */
    private static final int MAX_DB_OBJECTS_PER_KW = 25;
    /** 최종 컨텍스트에 포함할 오브젝트 수 */
    private static final int MAX_DB_OBJECT_SNIPPETS = 6;
    /** 한 오브젝트당 보여줄 최대 줄 수 (앞뒤 컨텍스트 포함) */
    private static final int DB_OBJECT_SNIPPET_LINES = 25;

    /**
     * Settings 의 외부 Oracle DB 에 접속해서 ALL_SOURCE 를 검색.
     * 사용자 메시지에 등장한 테이블명이 SP/FUNC/PKG/TRIGGER 의 본문에서 발견되면
     * 그 위치를 중심으로 ±N 줄을 발췌하여 컨텍스트에 추가한다.
     *
     * <p>왜 필요한가: ERP 시스템에서 INSERT/UPDATE 로직이 Java 가 아니라 Oracle SP
     * 안에 있는 경우가 흔하다. 이전에는 Java 코드만 grep 해서 "코드를 못 찾음" 으로
     * 답하던 한계가 있었다.
     *
     * <p>점수: distinct 키워드 hit ×15, INSERT/MERGE/UPDATE 의 등장 횟수 ×3 가산,
     * TRIGGER 타입 ×2 (트리거는 자동 호출이라 INSERT 시점 답으로 매우 유의미).
     */
    private String grepDbObjectSources(Set<String> tables) {
        // 너무 짧은 키워드 제거 (오탑 방지) — 6자 미만이면 패스
        List<String> kws = new ArrayList<>();
        for (String t : tables) if (t.length() >= 6) kws.add(t);
        if (kws.isEmpty()) return "";

        String url  = settings.getDb().getUrl();
        String user = settings.getDb().getUsername();
        String pass = settings.getDb().getPassword();
        long start  = System.currentTimeMillis();

        // (OWNER, NAME, TYPE) → 매칭 정보
        Map<String, DbObjMatch> aggregate = new LinkedHashMap<>();

        java.sql.Connection conn = null;
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            java.sql.DriverManager.setLoginTimeout(5);
            conn = java.sql.DriverManager.getConnection(url, user, pass);

            // 1) 어떤 오브젝트가 매칭되는지 — 키워드별 hits / DML 점수 집계
            //    Oracle 11g 호환을 위해 ROWNUM 래핑 (FETCH FIRST 사용 X)
            String findSql =
                "SELECT * FROM ("
              + "  SELECT OWNER, NAME, TYPE, "
              + "         COUNT(*) AS HITS, "
              + "         SUM(CASE WHEN UPPER(TEXT) LIKE '%INSERT%' THEN 1 ELSE 0 END) AS INS_CNT, "
              + "         SUM(CASE WHEN UPPER(TEXT) LIKE '%MERGE%'  THEN 1 ELSE 0 END) AS MRG_CNT, "
              + "         SUM(CASE WHEN UPPER(TEXT) LIKE '%UPDATE%' THEN 1 ELSE 0 END) AS UPD_CNT "
              + "  FROM ALL_SOURCE "
              + "  WHERE UPPER(TEXT) LIKE ? "
              + "    AND TYPE IN ('PROCEDURE','FUNCTION','PACKAGE','PACKAGE BODY','TRIGGER') "
              + "    AND OWNER NOT IN ('SYS','SYSTEM','OUTLN','DBSNMP','MDSYS','CTXSYS','XDB',"
              + "                      'APEX_030200','APEX_040000','FLOWS_FILES') "
              + "  GROUP BY OWNER, NAME, TYPE "
              + "  ORDER BY HITS DESC"
              + ") WHERE ROWNUM <= " + MAX_DB_OBJECTS_PER_KW;

            for (String kw : kws) {
                java.sql.PreparedStatement ps = conn.prepareStatement(findSql);
                try {
                    ps.setString(1, "%" + kw.toUpperCase() + "%");
                    java.sql.ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        String owner = rs.getString("OWNER");
                        String name  = rs.getString("NAME");
                        String type  = rs.getString("TYPE");
                        int hits     = rs.getInt("HITS");
                        int ins      = rs.getInt("INS_CNT");
                        int mrg      = rs.getInt("MRG_CNT");
                        int upd      = rs.getInt("UPD_CNT");
                        String key   = owner + "." + name + "." + type;
                        DbObjMatch m = aggregate.get(key);
                        if (m == null) {
                            m = new DbObjMatch();
                            m.owner = owner; m.name = name; m.type = type;
                            aggregate.put(key, m);
                        }
                        m.hitKeywords.add(kw);
                        m.totalHits += hits;
                        m.insCount = Math.max(m.insCount, ins);
                        m.mrgCount = Math.max(m.mrgCount, mrg);
                        m.updCount = Math.max(m.updCount, upd);
                    }
                    rs.close();
                } finally {
                    ps.close();
                }
            }

            if (aggregate.isEmpty()) {
                long el = System.currentTimeMillis() - start;
                log.info("[Enricher] DB 오브젝트 grep: 키워드={} 매칭 0건 ({} ms)", kws, el);
                return String.format(
                        "_DB 오브젝트 스캔: `%s` 를 참조하는 SP/FUNC/PKG/TRIGGER **0 건** (%d ms). "
                                + "오브젝트 명을 직접 검색해도 발견되지 않으면 동적 SQL/EXECUTE IMMEDIATE 또는 "
                                + "외부 ETL 가능성 있음._\n",
                        String.join(", ", kws), el);
            }

            // 2) 점수 매기고 상위 N 만 골라 본문 가져오기
            List<DbObjMatch> ranked = new ArrayList<>(aggregate.values());
            for (DbObjMatch m : ranked) {
                m.priority = m.hitKeywords.size() * 15
                           + m.insCount * 3 + m.mrgCount * 3 + m.updCount * 3
                           + ("TRIGGER".equals(m.type) ? 30 : 0);
            }
            ranked.sort((a, b) -> Integer.compare(b.priority, a.priority));
            if (ranked.size() > MAX_DB_OBJECT_SNIPPETS) {
                ranked = ranked.subList(0, MAX_DB_OBJECT_SNIPPETS);
            }

            // 3) 각 오브젝트 본문에서 매칭 라인 ±컨텍스트 발췌
            String fetchSrc =
                "SELECT LINE, TEXT FROM ALL_SOURCE "
              + "WHERE OWNER = ? AND NAME = ? AND TYPE = ? "
              + "ORDER BY LINE";
            for (DbObjMatch m : ranked) {
                java.sql.PreparedStatement ps = conn.prepareStatement(fetchSrc);
                try {
                    ps.setString(1, m.owner);
                    ps.setString(2, m.name);
                    ps.setString(3, m.type);
                    java.sql.ResultSet rs = ps.executeQuery();
                    List<int[]> lines = new ArrayList<>(); // [line, hash]
                    List<String> texts = new ArrayList<>();
                    int firstHitLine = -1;
                    while (rs.next()) {
                        int ln = rs.getInt("LINE");
                        String t = rs.getString("TEXT");
                        if (t == null) t = "";
                        // 매칭된 키워드가 등장하는 첫 라인을 찾는다 (가능하면 INSERT 가 함께 있는 라인)
                        if (firstHitLine < 0) {
                            String tu = t.toUpperCase();
                            for (String kw : m.hitKeywords) {
                                if (tu.contains(kw.toUpperCase())) {
                                    firstHitLine = ln;
                                    break;
                                }
                            }
                        }
                        lines.add(new int[]{ ln });
                        texts.add(t);
                    }
                    rs.close();
                    if (texts.isEmpty()) continue;
                    int hitIdx = 0;
                    if (firstHitLine > 0) {
                        for (int i = 0; i < lines.size(); i++) {
                            if (lines.get(i)[0] == firstHitLine) { hitIdx = i; break; }
                        }
                    }
                    int from = Math.max(0, hitIdx - DB_OBJECT_SNIPPET_LINES / 3);
                    int to   = Math.min(texts.size(), hitIdx + (DB_OBJECT_SNIPPET_LINES * 2 / 3));
                    StringBuilder code = new StringBuilder();
                    for (int i = from; i < to; i++) {
                        // 줄 끝 개행이 이미 텍스트 안에 들어있는 경우가 있음 — rtrim
                        String t = texts.get(i);
                        while (t.endsWith("\n") || t.endsWith("\r")) {
                            t = t.substring(0, t.length() - 1);
                        }
                        code.append(t).append('\n');
                    }
                    m.snippet = code.toString();
                    m.snippetStartLine = from + 1; // 1-based
                } finally {
                    ps.close();
                }
            }

            long el = System.currentTimeMillis() - start;
            log.info("[Enricher] DB 오브젝트 grep: 키워드={} 후보={} 선택={} ({} ms)",
                    kws, aggregate.size(), ranked.size(), el);

            // 4) 마크다운 조립
            StringBuilder out = new StringBuilder();
            out.append(String.format(
                    "DB 오브젝트 스캔 결과 — `%s` 매칭 **%d 개 오브젝트** (%d ms). 관련도 상위 %d:\n\n",
                    String.join(", ", kws), aggregate.size(), el, ranked.size()));
            for (DbObjMatch m : ranked) {
                out.append("### `").append(m.owner).append('.').append(m.name)
                   .append("` (").append(m.type).append(", ")
                   .append("hits=").append(m.totalHits)
                   .append(", INS=").append(m.insCount)
                   .append(", MRG=").append(m.mrgCount)
                   .append(", UPD=").append(m.updCount)
                   .append(", score=").append(m.priority)
                   .append(")\n\n");
                if (m.snippet != null && !m.snippet.isEmpty()) {
                    out.append("```sql\n-- line ~").append(m.snippetStartLine).append("\n")
                       .append(m.snippet).append("```\n\n");
                }
            }
            return out.toString();

        } catch (Exception e) {
            log.warn("[Enricher] DB 오브젝트 grep 실패: {}", e.getMessage());
            return String.format("_⚠ DB 오브젝트 스캔 실패: %s_\n", e.getMessage());
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
    }

    private static class DbObjMatch {
        String owner, name, type;
        Set<String> hitKeywords = new LinkedHashSet<>();
        int totalHits;
        int insCount, mrgCount, updCount;
        int priority;
        String snippet;
        int snippetStartLine;
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────

    private Set<String> extractCandidates(Pattern p, String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            String cand = m.group(1);
            if (!SQL_KEYWORDS.contains(cand)) out.add(cand);
        }
        return out;
    }

    private static class Snippet {
        String relativePath;
        String code;
        int lineNumber;
        int priority;
    }
}
