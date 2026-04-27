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
 * MiPlatform / Tobesoft Nexacro 기반 ERP 화면 XML 인덱서.
 *
 * <p>스캔 대상: {@code /src/main/webapp/miplatform/app/**} 아래의 모든 .xml.
 * 사용자가 입력한 자유 패턴이 있으면 거기서 우선 검색.
 *
 * <p>아래 패턴들로 URL/Service ID 를 추출 — 사이트마다 표기가 달라 가능한 한 모두 잡는다:
 * <ul>
 *   <li>{@code <Transaction url="/api/foo" />} — XML 정의</li>
 *   <li>{@code transaction("...", "svc::/api/foo", ...)} — 인라인 스크립트</li>
 *   <li>{@code transaction("...", "/api/foo", ...)}</li>
 *   <li>{@code url="/api/foo"} 또는 {@code url:"/api/foo"} 단순 속성</li>
 *   <li>{@code "svc::/api/foo"} 형태 문자열 리터럴 (Tobesoft 컨벤션)</li>
 * </ul>
 *
 * <p>인덱스: URL → 화면 XML 파일 목록.
 *
 * <p>주의: 사이트별 컨벤션이 정말 다양해서 정규식만으론 100% 커버 불가.
 * Phase 5 에서 사용자 정의 패턴 등록 기능 추가 예정.
 */
@Service
@DependsOn("settingsPersistenceService")
public class MiPlatformIndexer {

    private static final Logger log = LoggerFactory.getLogger(MiPlatformIndexer.class);

    /** 슬래시로 시작하는 URL 같은 문자열 — /api/... , /service/... 등 */
    private static final Pattern URL_LIKE = Pattern.compile(
            "[\"']((?:svc::)?/[a-zA-Z][a-zA-Z0-9_/\\-\\.]{2,})[\"']");

    /** url= or url: */
    private static final Pattern URL_ATTR = Pattern.compile(
            "url\\s*[=:]\\s*[\"']((?:svc::)?/[a-zA-Z][a-zA-Z0-9_/\\-\\.]{2,})[\"']",
            Pattern.CASE_INSENSITIVE);

    /** MiPlatform 디렉토리 후보 — 사용자가 명시 안하면 이 패턴들로 자동 탐지 */
    private static final List<String> MIPLATFORM_HINTS = Arrays.asList(
            "src/main/webapp/miplatform",
            "webapp/miplatform",
            "src/main/webapp/nexacro",
            "webapp/nexacro"
    );

    private final ToolkitSettings settings;

    /** URL (svc:: prefix 제거 / 정규화) → 그 URL 을 호출하는 화면 XML 파일 목록 */
    private final Map<String, List<MiPlatformScreen>> byUrl = new ConcurrentHashMap<String, List<MiPlatformScreen>>();
    private final List<MiPlatformScreen> all = new ArrayList<MiPlatformScreen>();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile long lastScanMs;
    private volatile int  lastScanFiles;
    private volatile String detectedRoot;  // 자동 감지된 miplatform 루트 (디버그용)

    public MiPlatformIndexer(ToolkitSettings settings) { this.settings = settings; }

    @PostConstruct
    public void initOnStartup() {
        try { refresh(); }
        catch (Exception e) { log.warn("[MiPlatformIndexer] 초기 인덱스 실패: {}", e.getMessage()); }
    }

    public synchronized void refresh() {
        long start = System.currentTimeMillis();
        if (settings.getProject() == null
                || settings.getProject().getScanPath() == null
                || settings.getProject().getScanPath().trim().isEmpty()) {
            byUrl.clear(); all.clear();
            ready.set(true);
            return;
        }
        String resolved = HostPathTranslator.translate(settings.getProject().getScanPath());
        Path projectRoot = Paths.get(resolved);
        if (!Files.isDirectory(projectRoot)) { ready.set(true); return; }

        // v4.4.x Phase 5 — Settings 의 miplatformRoot 가 우선 (있으면 사용자 지정)
        Path miRoot = null;
        String customRoot = settings.getProject().getMiplatformRoot();
        if (customRoot != null && !customRoot.trim().isEmpty()) {
            Path candidate = customRoot.startsWith("/") || (customRoot.length() > 1 && customRoot.charAt(1) == ':')
                    ? Paths.get(HostPathTranslator.translate(customRoot))
                    : projectRoot.resolve(customRoot);
            if (Files.isDirectory(candidate)) miRoot = candidate;
            else log.warn("[MiPlatformIndexer] Settings 의 miplatformRoot 디렉토리 없음: {}", candidate);
        }
        // 자동 감지 (사용자 지정 없거나 무효)
        if (miRoot == null) {
            for (String hint : MIPLATFORM_HINTS) {
                Path candidate = projectRoot.resolve(hint);
                if (Files.isDirectory(candidate)) { miRoot = candidate; break; }
            }
        }
        if (miRoot == null) {
            log.info("[MiPlatformIndexer] miplatform 디렉토리 미감지 — 인덱스 비움");
            byUrl.clear(); all.clear();
            ready.set(true);
            return;
        }
        detectedRoot = projectRoot.relativize(miRoot).toString().replace('\\', '/');

        // 사용자 정의 추가 패턴 (콤마/줄바꿈 구분, 그룹1 캡처 정규식)
        final List<Pattern> customPatterns = compileCustomPatterns(settings.getProject().getMiplatformPatterns());

        final Map<String, List<MiPlatformScreen>> nByUrl = new HashMap<String, List<MiPlatformScreen>>();
        final List<MiPlatformScreen>              nAll   = new ArrayList<MiPlatformScreen>();
        final int[] xmlSeen = { 0 };
        final Path  finalProjectRoot = projectRoot;

        try {
            Files.walkFileTree(miRoot, EnumSet.noneOf(FileVisitOption.class), 12, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String n = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (n.startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fn = file.getFileName().toString().toLowerCase();
                    if (!fn.endsWith(".xml")) return FileVisitResult.CONTINUE;
                    xmlSeen[0]++;
                    try {
                        if (Files.size(file) > 3_000_000) return FileVisitResult.CONTINUE;
                        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        Set<String> urls = extractUrls(content);
                        // 사용자 정의 패턴 추가 적용
                        for (Pattern p : customPatterns) {
                            Matcher m = p.matcher(content);
                            while (m.find()) {
                                String u = m.groupCount() >= 1 ? m.group(1) : m.group();
                                if (u != null && u.length() < 200) urls.add(u);
                            }
                        }
                        if (urls.isEmpty()) return FileVisitResult.CONTINUE;
                        String relPath = finalProjectRoot.relativize(file).toString().replace('\\', '/');
                        MiPlatformScreen sc = new MiPlatformScreen();
                        sc.file  = relPath;
                        sc.urls  = urls;
                        sc.title = guessTitle(content, file);
                        nAll.add(sc);
                        for (String u : urls) {
                            String norm = normalizeUrl(u);
                            List<MiPlatformScreen> list = nByUrl.get(norm);
                            if (list == null) { list = new ArrayList<MiPlatformScreen>(); nByUrl.put(norm, list); }
                            list.add(sc);
                        }
                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("[MiPlatformIndexer] walk 실패: {}", e.getMessage());
        }

        byUrl.clear(); byUrl.putAll(nByUrl);
        synchronized (all) { all.clear(); all.addAll(nAll); }
        long elapsed = System.currentTimeMillis() - start;
        lastScanMs = elapsed;
        lastScanFiles = xmlSeen[0];
        ready.set(true);
        log.info("[MiPlatformIndexer] 인덱스 완료: root={} xml={} screens={} urls={} elapsed={}ms",
                detectedRoot, xmlSeen[0], nAll.size(), nByUrl.size(), elapsed);
    }

    private static List<Pattern> compileCustomPatterns(String raw) {
        List<Pattern> out = new ArrayList<Pattern>();
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String line : raw.split("[\\n,]")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            try { out.add(Pattern.compile(t, Pattern.CASE_INSENSITIVE)); }
            catch (Exception e) {
                LoggerFactory.getLogger(MiPlatformIndexer.class)
                        .warn("[MiPlatformIndexer] 잘못된 사용자 정의 패턴 '{}': {}", t, e.getMessage());
            }
        }
        return out;
    }

    private static Set<String> extractUrls(String content) {
        Set<String> out = new LinkedHashSet<String>();
        Matcher m1 = URL_ATTR.matcher(content);
        while (m1.find()) out.add(m1.group(1));
        Matcher m2 = URL_LIKE.matcher(content);
        while (m2.find()) {
            String u = m2.group(1);
            // 너무 긴 (로깅 메시지 등) / 화이트리스트에 없는 확장자 등 노이즈 제거
            if (u.length() > 200) continue;
            if (u.endsWith(".js") || u.endsWith(".css") || u.endsWith(".gif")
                    || u.endsWith(".png") || u.endsWith(".jpg") || u.endsWith(".html")
                    || u.endsWith(".xml") || u.endsWith(".jsp")) continue;
            out.add(u);
        }
        return out;
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String s = url.trim();
        if (s.startsWith("svc::")) s = s.substring(5);
        if (!s.startsWith("/")) s = "/" + s;
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String guessTitle(String content, Path file) {
        // 화면 제목 추정 — <FormString>, title 속성 등에서
        Matcher t = Pattern.compile("title\\s*[=:]\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(content);
        if (t.find()) return t.group(1).trim();
        return file.getFileName().toString();
    }

    /** 파일명 또는 타이틀에 keyword 가 포함되는 화면 목록 (대소문자 무시, 최대 20건) */
    public List<MiPlatformScreen> findByQuery(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return Collections.emptyList();
        String kw = keyword.trim().toUpperCase();
        List<MiPlatformScreen> out = new ArrayList<MiPlatformScreen>();
        for (MiPlatformScreen sc : all) {
            boolean matchFile  = sc.file  != null && sc.file.toUpperCase().contains(kw);
            boolean matchTitle = sc.title != null && sc.title.toUpperCase().contains(kw);
            if (matchFile || matchTitle) {
                out.add(sc);
                if (out.size() >= 20) break;
            }
        }
        return out;
    }

    /** URL 로 화면 lookup (정확 매칭) */
    public List<MiPlatformScreen> findByUrl(String url) {
        if (url == null) return Collections.emptyList();
        List<MiPlatformScreen> r = byUrl.get(normalizeUrl(url));
        return r != null ? new ArrayList<MiPlatformScreen>(r) : Collections.<MiPlatformScreen>emptyList();
    }

    /** URL 부분 매칭 — endpoint URL 의 마지막 segment 로 검색하는 fallback */
    public List<MiPlatformScreen> findByUrlPartial(String url) {
        if (url == null || url.isEmpty()) return Collections.emptyList();
        String norm = normalizeUrl(url);
        Set<MiPlatformScreen> seen = new LinkedHashSet<MiPlatformScreen>();
        for (Map.Entry<String, List<MiPlatformScreen>> e : byUrl.entrySet()) {
            String key = e.getKey();
            if (key.equals(norm) || key.endsWith(norm) || norm.endsWith(key)) {
                seen.addAll(e.getValue());
            }
        }
        return new ArrayList<MiPlatformScreen>(seen);
    }

    public int     getScreenCount()    { return all.size(); }
    public int     getUrlCount()       { return byUrl.size(); }
    public boolean isReady()           { return ready.get(); }
    public long    getLastScanMs()     { return lastScanMs; }
    public int     getLastScanFiles()  { return lastScanFiles; }
    public String  getDetectedRoot()   { return detectedRoot; }

    // ── 결과 데이터 객체 ───────────────────────────────────────────────

    public static class MiPlatformScreen {
        public String file;
        public String title;
        public Set<String> urls;

        public String getFile()  { return file; }
        public String getTitle() { return title; }
        public Set<String> getUrls() { return urls; }
    }
}
