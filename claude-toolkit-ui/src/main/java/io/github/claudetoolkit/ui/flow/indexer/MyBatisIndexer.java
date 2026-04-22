package io.github.claudetoolkit.ui.flow.indexer;

import io.github.claudetoolkit.ui.config.HostPathTranslator;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프로젝트 안의 MyBatis / iBATIS XML 매퍼 인덱스.
 *
 * <p>스캔 대상:
 * <ul>
 *   <li>{@code SQLMAP-*.xml} (iBATIS 2 / 사내 컨벤션)</li>
 *   <li>{@code *Mapper.xml}, {@code mapper-*.xml} (MyBatis 3)</li>
 *   <li>임의 XML 인데 안에 {@code <sqlMap namespace="..">} 또는 {@code <mapper namespace="..">} 가 있는 경우</li>
 * </ul>
 *
 * <p>각 파일에서 추출:
 * <ul>
 *   <li>namespace</li>
 *   <li>{@code <insert/update/merge/delete/select id="...">...</...>}</li>
 *   <li>SQL 본문 — 어떤 테이블을 어떤 DML 로 만지는지 판정</li>
 * </ul>
 *
 * <p>인덱스를 두 가지로 만든다:
 * <ol>
 *   <li>{@code byId}    — {@code namespace.id} → Statement (정확한 lookup)</li>
 *   <li>{@code byTable} — TABLE_NAME → 그 테이블을 DML 하는 Statement 목록 (역방향)</li>
 * </ol>
 *
 * <p>Phase 1 의 핵심 컴포넌트 — TABLE 시작점에서 흐름을 거꾸로 거슬러 올라가는 출발점.
 */
@Service
@DependsOn("settingsPersistenceService")
public class MyBatisIndexer {

    private static final Logger log = LoggerFactory.getLogger(MyBatisIndexer.class);

    /** &lt;sqlMap namespace="X"&gt; 또는 &lt;mapper namespace="X"&gt; */
    private static final Pattern NAMESPACE = Pattern.compile(
            "<\\s*(?:sqlMap|mapper)\\b[^>]*\\bnamespace\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    /** &lt;insert id="Y" ...&gt; ... &lt;/insert&gt; (insert/update/merge/delete/select 다 매칭) */
    private static final Pattern STMT = Pattern.compile(
            "<\\s*(insert|update|merge|delete|select)\\b[^>]*\\bid\\s*=\\s*\"([^\"]+)\"[^>]*>"
                    + "([\\s\\S]*?)"
                    + "<\\s*/\\s*\\1\\s*>",
            Pattern.CASE_INSENSITIVE);

    /** SQL 본문에서 테이블 추출 — INSERT INTO X / UPDATE X / MERGE INTO X / DELETE FROM X / FROM X / JOIN X */
    private static final Pattern TABLE_REF = Pattern.compile(
            "(?:INSERT\\s+INTO|UPDATE|MERGE\\s+INTO|DELETE\\s+FROM|FROM|JOIN)\\s+([A-Z][A-Z0-9_]{2,})",
            Pattern.CASE_INSENSITIVE);

    private final ToolkitSettings settings;

    /** namespace.id → Statement */
    private final Map<String, MyBatisStatement> byId = new ConcurrentHashMap<String, MyBatisStatement>();
    /** TABLE_NAME (대문자) → 그 테이블을 DML 하는 statement 목록 */
    private final Map<String, List<MyBatisStatement>> byTable = new ConcurrentHashMap<String, List<MyBatisStatement>>();

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile long lastScanMs = 0;
    private volatile int  lastScanFiles = 0;

    public MyBatisIndexer(ToolkitSettings settings) {
        this.settings = settings;
    }

    /**
     * 컨텍스트 시작 시 인덱스 빌드. settings.json 이 먼저 로드돼 있어야 하므로
     * {@code @DependsOn("settingsPersistenceService")} 로 순서 보장.
     */
    @PostConstruct
    public void initOnStartup() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("[MyBatisIndexer] 시작시 인덱스 빌드 실패: {}", e.getMessage());
        }
    }

    /** 수동 / API 트리거 재인덱싱. 스레드 안전 (CHM swap). */
    public synchronized void refresh() {
        long start = System.currentTimeMillis();
        if (settings.getProject() == null
                || settings.getProject().getScanPath() == null
                || settings.getProject().getScanPath().trim().isEmpty()) {
            byId.clear(); byTable.clear();
            ready.set(true);
            log.info("[MyBatisIndexer] scanPath 미설정 — 인덱스 비움");
            return;
        }
        String resolved = HostPathTranslator.translate(settings.getProject().getScanPath());
        Path root = Paths.get(resolved);
        if (!Files.isDirectory(root)) {
            log.warn("[MyBatisIndexer] scanPath 디렉토리 없음: {}", resolved);
            ready.set(true);
            return;
        }

        final Map<String, MyBatisStatement> newById       = new HashMap<String, MyBatisStatement>();
        final Map<String, List<MyBatisStatement>> newByTable = new HashMap<String, List<MyBatisStatement>>();
        final int[] scanned = { 0 };
        final int[] xmlSeen = { 0 };

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 12, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String n = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (n.equals("target") || n.equals("build") || n.equals("node_modules")
                            || n.equals(".git") || n.equals(".idea") || n.equals("out")
                            || n.startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    scanned[0]++;
                    String fname = file.getFileName().toString().toLowerCase();
                    if (!fname.endsWith(".xml")) return FileVisitResult.CONTINUE;
                    xmlSeen[0]++;
                    try {
                        if (Files.size(file) > 5_000_000) return FileVisitResult.CONTINUE; // 5MB 초과 스킵
                        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        parseFile(file, content, root, newById, newByTable);
                    } catch (Exception e) {
                        // 파싱 실패는 조용히 무시 (XML 깨졌거나 인코딩 이슈)
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("[MyBatisIndexer] walk 실패: {}", e.getMessage());
        }

        // CHM swap
        byId.clear();    byId.putAll(newById);
        byTable.clear(); byTable.putAll(newByTable);
        long elapsed = System.currentTimeMillis() - start;
        lastScanMs = elapsed;
        lastScanFiles = xmlSeen[0];
        ready.set(true);
        log.info("[MyBatisIndexer] 인덱스 완료: scanned={} xml={} statements={} tables={} elapsed={}ms",
                scanned[0], xmlSeen[0], byId.size(), byTable.size(), elapsed);
    }

    private void parseFile(Path file, String content, Path root,
                           Map<String, MyBatisStatement> outById,
                           Map<String, List<MyBatisStatement>> outByTable) {
        Matcher nm = NAMESPACE.matcher(content);
        if (!nm.find()) return;
        String namespace = nm.group(1).trim();
        String relPath = root.relativize(file).toString().replace('\\', '/');

        Matcher sm = STMT.matcher(content);
        while (sm.find()) {
            String dml  = sm.group(1).toUpperCase();
            String id   = sm.group(2);
            String body = sm.group(3);
            int    line = lineOf(content, sm.start());

            // 본문에서 테이블 참조 추출 (DML 종류 무관 — UPDATE/MERGE/INSERT 다 본다)
            Set<String> tables = new LinkedHashSet<String>();
            Matcher tm = TABLE_REF.matcher(body);
            while (tm.find()) {
                String t = tm.group(1).toUpperCase();
                // 너무 짧은 토큰 / 흔한 키워드 제거
                if (t.length() >= 4 && !RESERVED.contains(t)) tables.add(t);
            }

            MyBatisStatement st = new MyBatisStatement();
            st.namespace = namespace;
            st.id        = id;
            st.fullId    = namespace + "." + id;
            st.dml       = dml;
            st.file      = relPath;
            st.line      = line;
            st.tables    = tables;
            st.snippet   = compactSnippet(body);

            outById.put(st.fullId, st);

            // v4.4.x — SELECT 포함 모든 statement 를 byTable 에 등록.
            // 검색 시 dmlFilter 로 골라낸다 ("어떻게 조회되는지" 도 흐름의 일부).
            for (String t : tables) {
                List<MyBatisStatement> list = outByTable.get(t);
                if (list == null) {
                    list = new ArrayList<MyBatisStatement>();
                    outByTable.put(t, list);
                }
                list.add(st);
            }
        }
    }

    /** 검색 API. table 대문자 정규화 후 byTable lookup. dmlFilter null 이면 전체. */
    public List<MyBatisStatement> findStatementsForTable(String table, String dmlFilter) {
        if (dmlFilter == null || dmlFilter.equalsIgnoreCase("ALL")) {
            return findStatementsForTable(table, (Set<String>) null);
        }
        return findStatementsForTable(table, Collections.singleton(dmlFilter.toUpperCase()));
    }

    /**
     * v4.4.x — 다중 DML 필터 지원.
     * @param dmlSet 활성 DML 집합 (예: ["INSERT","UPDATE","SELECT"]). null/empty 면 전체.
     */
    public List<MyBatisStatement> findStatementsForTable(String table, Set<String> dmlSet) {
        if (table == null) return Collections.emptyList();
        List<MyBatisStatement> all = byTable.get(table.trim().toUpperCase());
        if (all == null) return Collections.emptyList();
        if (dmlSet == null || dmlSet.isEmpty()) return new ArrayList<MyBatisStatement>(all);
        // 대소문자 안전화
        Set<String> upper = new HashSet<String>();
        for (String s : dmlSet) if (s != null) upper.add(s.toUpperCase());
        List<MyBatisStatement> out = new ArrayList<MyBatisStatement>();
        for (MyBatisStatement s : all) {
            if (upper.contains(s.dml)) out.add(s);
        }
        return out;
    }

    /** namespace.id 정확 lookup */
    public MyBatisStatement findById(String fullId) {
        if (fullId == null) return null;
        return byId.get(fullId.trim());
    }

    public int     getStatementCount() { return byId.size(); }
    public int     getTableCount()     { return byTable.size(); }
    public boolean isReady()           { return ready.get(); }
    public long    getLastScanMs()     { return lastScanMs; }
    public int     getLastScanFiles()  { return lastScanFiles; }
    public Collection<MyBatisStatement> allStatements() {
        return Collections.unmodifiableCollection(byId.values());
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────

    private static int lineOf(String content, int idx) {
        int ln = 1;
        for (int i = 0; i < idx && i < content.length(); i++) {
            if (content.charAt(i) == '\n') ln++;
        }
        return ln;
    }

    /** XML 본문의 공백 / 주석 압축해서 보기 좋은 한 덩어리로 (최대 ~600자) */
    private static String compactSnippet(String body) {
        String s = body.replaceAll("(?s)<!--.*?-->", " ")  // XML 주석
                       .replaceAll("\\s+", " ")
                       .trim();
        if (s.length() > 600) s = s.substring(0, 600) + " ...";
        return s;
    }

    /** TABLE_REF 패턴 후 화이트리스트 제외 */
    private static final Set<String> RESERVED = new HashSet<String>(Arrays.asList(
            "DUAL", "SELECT", "WHERE", "ORDER", "GROUP", "WHEN", "THEN", "CASE",
            "INNER", "OUTER", "LEFT", "RIGHT", "CROSS", "FULL"
    ));

    // ── 결과 데이터 객체 ───────────────────────────────────────────────

    public static class MyBatisStatement {
        public String namespace;
        public String id;
        public String fullId;     // namespace + "." + id  ← 자바 코드가 호출할 때 쓰는 키
        public String dml;        // INSERT / UPDATE / MERGE / DELETE / SELECT
        public String file;       // 상대경로
        public int    line;
        public Set<String> tables;
        public String snippet;    // 압축된 SQL 본문

        public String getNamespace() { return namespace; }
        public String getId()        { return id; }
        public String getFullId()    { return fullId; }
        public String getDml()       { return dml; }
        public String getFile()      { return file; }
        public int    getLine()      { return line; }
        public Set<String> getTables(){ return tables; }
        public String getSnippet()   { return snippet; }
    }
}
