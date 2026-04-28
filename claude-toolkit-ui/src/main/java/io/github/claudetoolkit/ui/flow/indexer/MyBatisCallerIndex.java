package io.github.claudetoolkit.ui.flow.indexer;

import io.github.claudetoolkit.ui.config.HostPathTranslator;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer.MyBatisStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v4.5 — MyBatis 문 ↔ Java 호출자 파일 역인덱스.
 *
 * <p>기존 PackageAnalysisService 는 MyBatis namespace 를 Java 패키지로 추측했는데,
 * 실제 ERP 프로젝트에서는 보통
 * <pre>
 *   [업무 패키지] com.company.sales.order        (Controller/Service/DTO)
 *   [매퍼 패키지] com.company.sales.order.mapper (MyBatis 인터페이스)
 * </pre>
 * 로 분리되어 단순 namespace 매칭이 실패한다. 이 인덱스는 **Java 파일 안에서
 * {@code .shortId(} 호출 패턴을 grep** 하여 "어떤 Java 파일이 어떤 MyBatis 문을
 * 호출하는지" 를 역으로 기록한다.
 *
 * <p>스캔 1회 비용: 수 초 (scanPath 의 .java 전체). 이후 조회는 O(1) 맵 lookup.
 *
 * <p>주의 — 정확도 100% 는 아님. 같은 이름의 메서드가 MyBatis 와 무관하게
 * 존재할 수 있음. 이 경우 소량의 false positive 발생. 실전에서는
 * ({@code shortId.length >= 3}) + 코드베이스 컨벤션 기반으로 노이즈 적은 편.
 */
/**
 * v4.5 — Bean 생성 순서:
 * <pre>
 *   settingsPersistenceService → myBatisIndexer (@PostConstruct refresh 완료)
 *   → javaPackageIndexer (@PostConstruct refresh 완료)
 *   → MyBatisCallerIndex (@PostConstruct refresh) ← 여기
 * </pre>
 * {@code @DependsOn} 덕분에 이 클래스의 @PostConstruct 가 호출될 때
 * MyBatisIndexer 의 data 가 이미 완료된 상태 → 동기 스캔 가능.
 */
@Service
@DependsOn({"settingsPersistenceService", "myBatisIndexer", "javaPackageIndexer"})
public class MyBatisCallerIndex {

    private static final Logger log = LoggerFactory.getLogger(MyBatisCallerIndex.class);

    private final ToolkitSettings    settings;
    private final MyBatisIndexer     mybatis;
    private final IndexerConfig      indexerConfig;

    /** file relPath → Set of MyBatis statement fullIds called in that file */
    private final Map<String, Set<String>> fileToStatements = new ConcurrentHashMap<String, Set<String>>();
    /** statement fullId → Set of file relPaths that call it */
    private final Map<String, Set<String>> statementToFiles = new ConcurrentHashMap<String, Set<String>>();

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile long lastScanMs;
    private volatile int  lastScanFiles;
    private volatile int  lastScanMatches;

    public MyBatisCallerIndex(ToolkitSettings settings, MyBatisIndexer mybatis, IndexerConfig indexerConfig) {
        this.settings = settings;
        this.mybatis  = mybatis;
        this.indexerConfig = indexerConfig;
    }

    @PostConstruct
    public void initOnStartup() {
        // v4.5 — @DependsOn 으로 MyBatisIndexer 가 이미 준비된 상태이므로 동기 스캔.
        // WAS 기동 시점에 Java 파일 scan + caller 역인덱스가 모두 완료되어야
        // 메뉴 진입 시 재스캔 없이 즉시 캐시 사용 가능.
        try { refresh(); }
        catch (Exception e) { log.warn("[MyBatisCallerIndex] 초기 인덱스 실패: {}", e.getMessage()); }
    }

    public synchronized void refresh() {
        long start = System.currentTimeMillis();
        fileToStatements.clear();
        statementToFiles.clear();

        if (settings.getProject() == null
                || settings.getProject().getScanPath() == null
                || settings.getProject().getScanPath().trim().isEmpty()) {
            ready.set(true);
            return;
        }

        // 1) shortId → List<fullId> 맵 구성 (동일 shortId 가 여러 Mapper 에 존재할 수 있음)
        // + fullId → mapperClass 매핑 — 같은 shortId 의 false positive 를 막기 위함
        // (예: OrderMapper.insert 와 UserMapper.insert 가 같은 shortId 면, Java 파일에
        //  ".insert(" 만 매치해서는 둘 다 잡히므로 mapperClass 가 파일 안에 있는지를
        //  추가 검증한다 → import / @Autowired / 변수타입 등으로 클래스명이 거의 항상 노출됨)
        Map<String, List<String>> shortIdToFullIds = new HashMap<String, List<String>>();
        Map<String, String>       mapperClassByFullId = new HashMap<String, String>();
        for (MyBatisStatement st : mybatis.allStatements()) {
            if (st.fullId == null) continue;
            int dot = st.fullId.lastIndexOf('.');
            if (dot <= 0) continue;
            String shortId = st.fullId.substring(dot + 1);
            if (shortId.length() < 3) continue;
            // 너무 흔한 이름 제외 (false positive 심함)
            if (isTooCommonName(shortId)) continue;
            List<String> list = shortIdToFullIds.get(shortId);
            if (list == null) { list = new ArrayList<String>(); shortIdToFullIds.put(shortId, list); }
            list.add(st.fullId);

            // mapperClass = fullId 의 namespace 마지막 segment.
            // namespace 가 com.foo.bar.OrderMapper 면 "OrderMapper",
            // 그냥 "OrderMapper" 면 그대로 "OrderMapper".
            int dot2 = st.fullId.lastIndexOf('.', dot - 1);
            String mapperClass = (dot2 >= 0)
                    ? st.fullId.substring(dot2 + 1, dot)
                    : st.fullId.substring(0, dot);
            mapperClassByFullId.put(st.fullId, mapperClass);
        }

        if (shortIdToFullIds.isEmpty()) {
            ready.set(true);
            log.info("[MyBatisCallerIndex] MyBatis shortIds 없음 — 스캔 스킵");
            return;
        }

        String resolved = HostPathTranslator.translate(settings.getProject().getScanPath());
        final Path root = Paths.get(resolved);
        if (!Files.isDirectory(root)) {
            ready.set(true);
            return;
        }

        final Map<String, List<String>> finalShortIds       = shortIdToFullIds;
        final Map<String, String>       finalMapperClassMap = mapperClassByFullId;
        final int[] scanned = { 0 };
        final int[] matched = { 0 };

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 12,
                    new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String n = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (n.equals("target") || n.equals("build") || n.equals("node_modules")
                            || n.equals(".git") || n.equals(".idea") || n.equals("test")
                            || n.startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    int maxScan = indexerConfig.getMaxJavaScan();
                    if (maxScan > 0 && scanned[0]++ > maxScan) return FileVisitResult.TERMINATE;
                    if (!file.getFileName().toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                    try {
                        if (Files.size(file) > indexerConfig.getMaxFileSize()) return FileVisitResult.CONTINUE;
                        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        String relPath = root.relativize(file).toString().replace('\\', '/');

                        Set<String> callsInFile = new LinkedHashSet<String>();
                        // mapperClass 별 word-boundary 검증 결과를 파일 단위로 캐시 (반복 regex 방지)
                        Map<String, Boolean> classPresentCache = new HashMap<String, Boolean>();

                        for (Map.Entry<String, List<String>> e : finalShortIds.entrySet()) {
                            String shortId = e.getKey();
                            if (!content.contains("." + shortId + "(")) continue;
                            // ".shortId(" 만으로는 어떤 Mapper 인지 모름 →
                            // 같은 shortId 를 가진 fullId 들 중, "그 mapperClass 가 이 파일에
                            // 노출되어 있는" 것만 호출자로 인정한다.
                            for (String fullId : e.getValue()) {
                                String mapperClass = finalMapperClassMap.get(fullId);
                                if (mapperClass == null) continue;
                                Boolean cached = classPresentCache.get(mapperClass);
                                if (cached == null) {
                                    cached = containsWord(content, mapperClass);
                                    classPresentCache.put(mapperClass, cached);
                                }
                                if (cached) callsInFile.add(fullId);
                            }
                        }
                        if (!callsInFile.isEmpty()) {
                            fileToStatements.put(relPath, callsInFile);
                            for (String fullId : callsInFile) {
                                Set<String> files = statementToFiles.get(fullId);
                                if (files == null) {
                                    files = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
                                    statementToFiles.put(fullId, files);
                                }
                                files.add(relPath);
                                matched[0]++;
                            }
                        }
                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("[MyBatisCallerIndex] walk 실패: {}", e.getMessage());
        }

        lastScanMs      = System.currentTimeMillis() - start;
        lastScanFiles   = scanned[0];
        lastScanMatches = matched[0];
        ready.set(true);
        log.info("[MyBatisCallerIndex] files={} matches={} statementsWithCallers={} shortIds={} elapsed={}ms",
                scanned[0], matched[0], statementToFiles.size(),
                shortIdToFullIds.size(), lastScanMs);
    }

    // ── 조회 API ─────────────────────────────────────────────────────────

    public Set<String> statementsCalledByFile(String relPath) {
        if (relPath == null) return Collections.emptySet();
        Set<String> s = fileToStatements.get(relPath);
        return s != null ? s : Collections.<String>emptySet();
    }

    public Set<String> filesCallingStatement(String fullId) {
        if (fullId == null) return Collections.emptySet();
        Set<String> s = statementToFiles.get(fullId);
        return s != null ? s : Collections.<String>emptySet();
    }

    public int     getStatementCoverage() { return statementToFiles.size(); }
    public int     getFileCoverage()      { return fileToStatements.size(); }
    public int     getLastScanFiles()     { return lastScanFiles; }
    public int     getLastScanMatches()   { return lastScanMatches; }
    public long    getLastScanMs()        { return lastScanMs; }
    public boolean isReady()              { return ready.get(); }

    // ── 너무 흔한 이름 필터 (MyBatis 아닐 가능성 큼) ──────────────────────

    private static boolean isTooCommonName(String s) {
        if (s == null) return true;
        String lower = s.toLowerCase();
        return lower.equals("tostring") || lower.equals("hashcode") || lower.equals("equals")
                || lower.equals("init")    || lower.equals("run")      || lower.equals("close")
                || lower.equals("get")     || lower.equals("set")      || lower.equals("put")
                || lower.equals("add")     || lower.equals("size")     || lower.equals("isempty")
                || lower.equals("clone")   || lower.equals("clear");
    }

    /**
     * 파일 내용에서 식별자 단위로 단어가 등장하는지 검사 (\bWORD\b).
     * Java 식별자는 [A-Za-z0-9_$] 가 식별자 문자라, 단순 substring contains 면
     * "OrderMapper" 이 "OrderMapperImpl" 안에서도 매치되어 false positive 가 또
     * 발생할 수 있다. 그래서 앞·뒤 글자가 식별자 문자가 아닌지 직접 확인.
     */
    private static boolean containsWord(String content, String word) {
        if (content == null || word == null || word.isEmpty()) return false;
        int from = 0;
        while (true) {
            int idx = content.indexOf(word, from);
            if (idx < 0) return false;
            char before = idx == 0 ? ' ' : content.charAt(idx - 1);
            int  endIdx = idx + word.length();
            char after  = endIdx >= content.length() ? ' ' : content.charAt(endIdx);
            if (!isJavaIdentChar(before) && !isJavaIdentChar(after)) return true;
            from = idx + 1;
        }
    }

    private static boolean isJavaIdentChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '$';
    }
}
