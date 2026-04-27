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
 * v4.5 — Package Overview 페이지를 위한 Java 파일 인덱서.
 *
 * <p>scanPath 의 모든 {@code .java} 를 1회 스캔하여:
 * <ul>
 *   <li>파일 경로 → 선언된 패키지명 ({@code package xxx.yyy.zzz;})</li>
 *   <li>클래스명 + 타입 휴리스틱 (controller / service / dao / mapper / model / other)</li>
 * </ul>
 *
 * <p>목적: PackageAnalysisService 가 패키지 단위로 집계·그룹핑할 수 있도록 기반 제공.
 *
 * <p>성능: {@link MyBatisIndexer}/{@link SpringUrlIndexer} 와 동급 스캔 비용.
 * 결과는 메모리 보관, {@code refresh()} 호출 전까지 유효.
 */
@Service
@DependsOn("settingsPersistenceService")
public class JavaPackageIndexer {

    private static final Logger log = LoggerFactory.getLogger(JavaPackageIndexer.class);

    private static final Pattern PACKAGE_DECL = Pattern.compile(
            "(?m)^\\s*package\\s+([a-zA-Z_][\\w.]*)\\s*;");

    private static final Pattern TYPE_DECL = Pattern.compile(
            "(?m)^\\s*(?:public\\s+|abstract\\s+|final\\s+)*" +
            "(class|interface|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private final ToolkitSettings settings;
    private final IndexerConfig   indexerConfig;

    private final Map<String, List<JavaClassInfo>> byPackage =
            new ConcurrentHashMap<String, List<JavaClassInfo>>();
    /** v4.5 — relPath (normalized with '/') → class 매핑. findByRelPath O(1) 용. */
    private final Map<String, JavaClassInfo> byRelPath =
            new ConcurrentHashMap<String, JavaClassInfo>();
    private final List<JavaClassInfo> all = new ArrayList<JavaClassInfo>();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile long lastScanMs;
    private volatile int  lastScanFiles;

    public JavaPackageIndexer(ToolkitSettings settings, IndexerConfig indexerConfig) {
        this.settings = settings;
        this.indexerConfig = indexerConfig;
    }

    @PostConstruct
    public void initOnStartup() {
        try { refresh(); }
        catch (Exception e) { log.warn("[JavaPackageIndexer] 초기 인덱스 실패: {}", e.getMessage()); }
    }

    public synchronized void refresh() {
        long start = System.currentTimeMillis();
        byPackage.clear();
        byRelPath.clear();
        synchronized (all) { all.clear(); }

        if (settings.getProject() == null
                || settings.getProject().getScanPath() == null
                || settings.getProject().getScanPath().trim().isEmpty()) {
            ready.set(true);
            log.info("[JavaPackageIndexer] scanPath 없음 — 인덱스 비움");
            return;
        }

        String resolved = HostPathTranslator.translate(settings.getProject().getScanPath());
        final Path root = Paths.get(resolved);
        if (!Files.isDirectory(root)) {
            ready.set(true);
            log.warn("[JavaPackageIndexer] scanPath 디렉토리 아님: {}", resolved);
            return;
        }

        final int[] scanned = { 0 };
        final List<JavaClassInfo> collected = new ArrayList<JavaClassInfo>();

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 12, new SimpleFileVisitor<Path>() {
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
                    String fname = file.getFileName().toString();
                    if (!fname.endsWith(".java")) return FileVisitResult.CONTINUE;
                    try {
                        if (Files.size(file) > indexerConfig.getMaxFileSize()) return FileVisitResult.CONTINUE;
                        // 앞부분 8KB 만 읽어도 package + class 선언은 충분히 잡힘 (성능 최적화)
                        byte[] head = readHead(file, 8192);
                        String content = new String(head, StandardCharsets.UTF_8);
                        Matcher pm = PACKAGE_DECL.matcher(content);
                        String pkg = pm.find() ? pm.group(1) : "(default)";
                        Matcher cm = TYPE_DECL.matcher(content);
                        String className;
                        if (cm.find()) {
                            className = cm.group(2);
                        } else {
                            className = fname.endsWith(".java")
                                    ? fname.substring(0, fname.length() - 5) : fname;
                        }
                        JavaClassInfo info = new JavaClassInfo();
                        info.packageName = pkg;
                        info.className   = className;
                        info.relPath     = root.relativize(file).toString().replace('\\', '/');
                        info.type        = guessType(info.className, info.relPath);
                        collected.add(info);
                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("[JavaPackageIndexer] walk 실패: {}", e.getMessage());
        }

        for (JavaClassInfo info : collected) {
            List<JavaClassInfo> list = byPackage.get(info.packageName);
            if (list == null) {
                list = new ArrayList<JavaClassInfo>();
                byPackage.put(info.packageName, list);
            }
            list.add(info);
            if (info.relPath != null) byRelPath.put(info.relPath, info);
        }
        synchronized (all) { all.addAll(collected); }

        lastScanMs = System.currentTimeMillis() - start;
        lastScanFiles = scanned[0];
        ready.set(true);
        log.info("[JavaPackageIndexer] 인덱스 완료: files={} classes={} packages={} elapsed={}ms",
                scanned[0], collected.size(), byPackage.size(), lastScanMs);
    }

    private static byte[] readHead(Path file, int maxBytes) throws java.io.IOException {
        try (java.io.InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[maxBytes];
            int total = 0;
            while (total < maxBytes) {
                int n = in.read(buf, total, maxBytes - total);
                if (n < 0) break;
                total += n;
            }
            if (total == maxBytes) return buf;
            byte[] out = new byte[total];
            System.arraycopy(buf, 0, out, 0, total);
            return out;
        }
    }

    /** 클래스명/경로 휴리스틱으로 레이어 타입 추정 */
    private static String guessType(String className, String relPath) {
        String n = className == null ? "" : className.toLowerCase();
        String p = relPath == null ? "" : relPath.toLowerCase();
        if (n.endsWith("controller") || n.endsWith("restcontroller")
                || p.contains("/controller/")) return "controller";
        if (n.endsWith("serviceimpl") || n.endsWith("service")
                || n.endsWith("manager") || p.contains("/service/")) return "service";
        if (n.endsWith("dao") || n.endsWith("daoimpl")
                || n.endsWith("mapper") || n.endsWith("repository")
                || p.contains("/dao/") || p.contains("/mapper/")) return "dao";
        if (n.endsWith("dto") || n.endsWith("vo") || n.endsWith("entity")
                || n.endsWith("model") || n.endsWith("bean")
                || p.contains("/dto/") || p.contains("/vo/")
                || p.contains("/model/") || p.contains("/entity/")) return "model";
        if (n.endsWith("util") || n.endsWith("utils") || n.endsWith("helper")
                || p.contains("/util/") || p.contains("/common/")) return "util";
        if (n.endsWith("config") || n.endsWith("configuration")
                || p.contains("/config/")) return "config";
        if (n.endsWith("exception") || n.endsWith("error")) return "exception";
        return "other";
    }

    // ── public read API ──────────────────────────────────────────────────

    /** 주어진 레벨로 패키지명을 자름: L5 이면 앞 5개 세그먼트만. */
    public static String truncate(String pkg, int level) {
        if (pkg == null || pkg.isEmpty() || level < 1) return pkg;
        String[] parts = pkg.split("\\.");
        if (parts.length <= level) return pkg;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** 전체 Java 클래스 정보 (읽기 전용 스냅샷) */
    public List<JavaClassInfo> getAllClasses() {
        synchronized (all) { return new ArrayList<JavaClassInfo>(all); }
    }

    /** 주어진 level 로 자른 패키지 이름들의 고유 집합. prefix 로 필터링 가능 (null/빈 문자열이면 미적용) */
    public Set<String> distinctPackagesAtLevel(int level, String prefix) {
        Set<String> out = new TreeSet<String>();
        for (JavaClassInfo info : getAllClasses()) {
            String truncated = truncate(info.packageName, level);
            if (prefix != null && !prefix.isEmpty() && !truncated.startsWith(prefix)) continue;
            out.add(truncated);
        }
        return out;
    }

    /** 주어진 level 로 자른 패키지에 속하는 클래스들. */
    public List<JavaClassInfo> classesInPackage(String truncatedPkg, int level) {
        List<JavaClassInfo> out = new ArrayList<JavaClassInfo>();
        if (truncatedPkg == null) return out;
        for (JavaClassInfo info : getAllClasses()) {
            if (truncate(info.packageName, level).equals(truncatedPkg)) out.add(info);
        }
        return out;
    }

    /**
     * v4.5 — relPath → JavaClassInfo O(1) lookup (PackageAnalysisService 성능).
     * null/미발견이면 null. 입력 경로는 '/' 또는 '\\' 섞여있을 수 있으므로 정규화.
     */
    public JavaClassInfo findByRelPath(String relPath) {
        if (relPath == null) return null;
        String norm = relPath.replace('\\', '/');
        JavaClassInfo hit = byRelPath.get(norm);
        if (hit != null) return hit;
        // fallback — 뒤에서부터 접미사 매칭 (경로가 약간 다를 때)
        for (Map.Entry<String, JavaClassInfo> e : byRelPath.entrySet()) {
            if (e.getKey().endsWith(norm) || norm.endsWith(e.getKey())) return e.getValue();
        }
        return null;
    }

    public boolean isReady()              { return ready.get(); }
    public long    getLastScanMs()        { return lastScanMs; }
    public int     getLastScanFiles()     { return lastScanFiles; }
    public int     getTotalClasses()      { return all.size(); }
    public int     getTotalPackages()     { return byPackage.size(); }

    // ── DTO ─────────────────────────────────────────────────────────────

    public static class JavaClassInfo {
        public String packageName;  // 선언된 전체 패키지
        public String className;
        public String relPath;
        public String type;          // controller / service / dao / model / util / config / exception / other

        public String getPackageName() { return packageName; }
        public String getClassName()   { return className; }
        public String getRelPath()     { return relPath; }
        public String getType()        { return type; }
    }
}
