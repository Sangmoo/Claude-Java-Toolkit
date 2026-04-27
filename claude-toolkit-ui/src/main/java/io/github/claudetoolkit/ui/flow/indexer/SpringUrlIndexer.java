package io.github.claudetoolkit.ui.flow.indexer;

import io.github.claudetoolkit.ui.config.HostPathTranslator;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프로젝트 안의 Spring Controller URL 인덱서.
 *
 * <p>각 .java 파일에서 다음을 추출:
 * <ul>
 *   <li>클래스 레벨 {@code @RequestMapping("/api/foo")} (있으면 prefix)</li>
 *   <li>메서드 레벨 {@code @RequestMapping/Get/Post/Put/Delete/Patch Mapping("/bar")}</li>
 *   <li>그 메서드가 호출하는 Service/Manager 메서드 이름들 (단순 grep — {@code .someMethod(} 패턴)</li>
 * </ul>
 *
 * <p>두 인덱스:
 * <ol>
 *   <li>{@code byUrl}            — full URL → endpoints (URL → Controller 역방향 lookup)</li>
 *   <li>{@code byCalleeMethod}   — 호출되는 메서드 단순명 → endpoints (Service 메서드 → 호출 Controller 찾기)</li>
 * </ol>
 *
 * <p>참고: 정규식 기반이라 100% 정확하지 않음 (특히 어노테이션 분할 작성/메타 어노테이션 등).
 * 정확도가 부족하면 사용자에게 "AI 추정" 라벨로 표시.
 */
@Service
@DependsOn("settingsPersistenceService")
public class SpringUrlIndexer {

    private static final Logger log = LoggerFactory.getLogger(SpringUrlIndexer.class);

    /** 클래스 선언 줄 위에 붙는 @RequestMapping (single line 가정) */
    private static final Pattern CLASS_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\(([^)]*)\\)\\s*(?:@[A-Za-z]+(?:\\([^)]*\\))?\\s*)*"
                    + "public\\s+(?:abstract\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*)");

    /** 메서드 어노테이션 — Get/Post/Put/Delete/Patch/Request Mapping. 메서드 이름까지 한꺼번에. */
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(Request|Get|Post|Put|Delete|Patch)Mapping\\s*\\(([^)]*)\\)"
                    + "(?:[\\s\\S]{0,500}?)"   // 어노테이션과 메서드 사이 다른 어노테이션 허용
                    + "public\\s+[\\w<>\\[\\],?\\s\\.]+?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(",
            Pattern.MULTILINE);

    /** 어노테이션 안에서 URL 추출 — value="..." 또는 첫 string literal */
    private static final Pattern URL_VALUE = Pattern.compile(
            "(?:value\\s*=\\s*)?\\{?\\s*\"([^\"]+)\"");

    /** 메서드 본문에서 .someMethod( 패턴 — 의심스러운 호출 추출 (정확도 X, 후보 후보) */
    private static final Pattern METHOD_CALL = Pattern.compile(
            "\\.([a-z][A-Za-z0-9_]{2,})\\s*\\(");

    private final ToolkitSettings settings;

    private final Map<String, List<ControllerEndpoint>> byUrl          = new ConcurrentHashMap<String, List<ControllerEndpoint>>();
    private final Map<String, List<ControllerEndpoint>> byCalleeMethod = new ConcurrentHashMap<String, List<ControllerEndpoint>>();
    private final List<ControllerEndpoint>              all            = new ArrayList<ControllerEndpoint>();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile long lastScanMs;
    private volatile int  lastScanFiles;

    public SpringUrlIndexer(ToolkitSettings settings) { this.settings = settings; }

    @PostConstruct
    public void initOnStartup() {
        try { refresh(); }
        catch (Exception e) { log.warn("[SpringUrlIndexer] 초기 인덱스 실패: {}", e.getMessage()); }
    }

    public synchronized void refresh() {
        long start = System.currentTimeMillis();
        if (settings.getProject() == null
                || settings.getProject().getScanPath() == null
                || settings.getProject().getScanPath().trim().isEmpty()) {
            byUrl.clear(); byCalleeMethod.clear(); all.clear();
            ready.set(true);
            return;
        }
        String resolved = HostPathTranslator.translate(settings.getProject().getScanPath());
        Path root = Paths.get(resolved);
        if (!Files.isDirectory(root)) { ready.set(true); return; }

        final Map<String, List<ControllerEndpoint>> nUrl    = new HashMap<String, List<ControllerEndpoint>>();
        final Map<String, List<ControllerEndpoint>> nCallee = new HashMap<String, List<ControllerEndpoint>>();
        final List<ControllerEndpoint>              nAll    = new ArrayList<ControllerEndpoint>();
        final int[] scanned = { 0 };
        final int[] javaSeen = { 0 };

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 12, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String n = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (n.equals("target") || n.equals("build") || n.equals("node_modules")
                            || n.equals(".git") || n.equals(".idea") || n.equals("out")
                            || n.equals("test") || n.startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    scanned[0]++;
                    if (!file.getFileName().toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                    javaSeen[0]++;
                    try {
                        if (Files.size(file) > 2_000_000) return FileVisitResult.CONTINUE;
                        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        // Controller 만 보는 게 효율적 (모든 .java 안의 RequestMapping 까지 보면 느림)
                        if (!content.contains("@RequestMapping") && !content.contains("Mapping(")) {
                            return FileVisitResult.CONTINUE;
                        }
                        parseFile(file, content, root, nUrl, nCallee, nAll);
                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("[SpringUrlIndexer] walk 실패: {}", e.getMessage());
        }

        byUrl.clear();          byUrl.putAll(nUrl);
        byCalleeMethod.clear(); byCalleeMethod.putAll(nCallee);
        synchronized (all) { all.clear(); all.addAll(nAll); }
        long elapsed = System.currentTimeMillis() - start;
        lastScanMs = elapsed;
        lastScanFiles = javaSeen[0];
        ready.set(true);
        log.info("[SpringUrlIndexer] 인덱스 완료: scanned={} java={} endpoints={} urls={} elapsed={}ms",
                scanned[0], javaSeen[0], nAll.size(), nUrl.size(), elapsed);
    }

    private void parseFile(Path file, String content, Path root,
                           Map<String, List<ControllerEndpoint>> outByUrl,
                           Map<String, List<ControllerEndpoint>> outByCallee,
                           List<ControllerEndpoint> outAll) {
        // 클래스 prefix
        String classPrefix = "";
        String className   = null;
        Matcher cm = CLASS_MAPPING.matcher(content);
        if (cm.find()) {
            String url = extractUrl(cm.group(1));
            if (url != null) classPrefix = normalize(url);
            className = cm.group(2);
        } else {
            // 클래스 prefix 없으면 className 만이라도 추출
            Matcher c2 = Pattern.compile("public\\s+(?:abstract\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*)")
                    .matcher(content);
            if (c2.find()) className = c2.group(1);
        }

        String relPath = root.relativize(file).toString().replace('\\', '/');

        Matcher mm = METHOD_MAPPING.matcher(content);
        while (mm.find()) {
            String httpMethod = mm.group(1).toUpperCase();
            if ("REQUEST".equals(httpMethod)) httpMethod = "GET/POST";  // RequestMapping 은 다중
            String urlPart    = extractUrl(mm.group(2));
            String methodName = mm.group(3);
            String fullUrl    = joinUrl(classPrefix, urlPart);
            int    line       = lineOf(content, mm.start());

            ControllerEndpoint ep = new ControllerEndpoint();
            ep.url        = fullUrl;
            ep.httpMethod = httpMethod;
            ep.className  = className;
            ep.methodName = methodName;
            ep.file       = relPath;
            ep.line       = line;
            ep.callees    = extractMethodBodyCallees(content, mm.end());

            // 인덱스 등록
            outAll.add(ep);
            putList(outByUrl, normalize(fullUrl), ep);
            for (String callee : ep.callees) putList(outByCallee, callee, ep);
        }
    }

    private static Set<String> extractMethodBodyCallees(String content, int methodHeaderEnd) {
        // 단순 — 메서드 헤더 끝 이후 첫 ~3000자 안에서 .xxx( 호출 추출
        int from = methodHeaderEnd;
        int to   = Math.min(content.length(), from + 3000);
        String body = content.substring(from, to);
        Set<String> out = new LinkedHashSet<String>();
        Matcher m = METHOD_CALL.matcher(body);
        while (m.find()) {
            String name = m.group(1);
            // 너무 흔한 노이즈 제거
            if (NOISE_CALLS.contains(name)) continue;
            out.add(name);
        }
        return out;
    }

    public List<ControllerEndpoint> findByUrl(String url) {
        if (url == null) return Collections.emptyList();
        List<ControllerEndpoint> r = byUrl.get(normalize(url));
        return r != null ? new ArrayList<ControllerEndpoint>(r) : Collections.<ControllerEndpoint>emptyList();
    }

    public List<ControllerEndpoint> findByCallee(String simpleMethodName) {
        if (simpleMethodName == null) return Collections.emptyList();
        List<ControllerEndpoint> r = byCalleeMethod.get(simpleMethodName);
        return r != null ? new ArrayList<ControllerEndpoint>(r) : Collections.<ControllerEndpoint>emptyList();
    }

    public int     getEndpointCount()  { return all.size(); }
    public int     getUrlCount()       { return byUrl.size(); }

    /** v4.5 — Package Overview 용: 전체 endpoint 목록 스냅샷. */
    public List<ControllerEndpoint> allEndpoints() {
        synchronized (all) { return new ArrayList<ControllerEndpoint>(all); }
    }
    public boolean isReady()           { return ready.get(); }
    public long    getLastScanMs()     { return lastScanMs; }
    public int     getLastScanFiles()  { return lastScanFiles; }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private static String extractUrl(String annotationArgs) {
        Matcher m = URL_VALUE.matcher(annotationArgs);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String normalize(String url) {
        if (url == null) return "";
        String s = url.trim();
        if (!s.startsWith("/")) s = "/" + s;
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String joinUrl(String prefix, String suffix) {
        String p = normalize(prefix);
        String s = suffix == null ? "" : normalize(suffix);
        if (p.isEmpty()) return s;
        if (s.isEmpty() || "/".equals(s)) return p;
        return p + s;
    }

    private static int lineOf(String content, int idx) {
        int ln = 1;
        for (int i = 0; i < idx && i < content.length(); i++) if (content.charAt(i) == '\n') ln++;
        return ln;
    }

    private static <K, V> void putList(Map<K, List<V>> map, K key, V val) {
        List<V> list = map.get(key);
        if (list == null) { list = new ArrayList<V>(); map.put(key, list); }
        list.add(val);
    }

    private static final Set<String> NOISE_CALLS = new HashSet<String>(Arrays.asList(
            "toString", "equals", "hashCode", "getClass", "wait", "notify", "notifyAll",
            "isEmpty", "size", "length", "trim", "valueOf", "format", "info", "debug",
            "warn", "error", "log", "println", "print", "add", "put", "get", "set",
            "ok", "build", "body", "status", "of", "list", "stream", "collect"
    ));

    // ── 결과 데이터 객체 ───────────────────────────────────────────────

    public static class ControllerEndpoint {
        public String url;
        public String httpMethod;
        public String className;
        public String methodName;
        public String file;
        public int    line;
        public Set<String> callees;

        public String getUrl()        { return url; }
        public String getHttpMethod() { return httpMethod; }
        public String getClassName()  { return className; }
        public String getMethodName() { return methodName; }
        public String getFile()       { return file; }
        public int    getLine()       { return line; }
        public Set<String> getCallees() { return callees; }
    }
}
