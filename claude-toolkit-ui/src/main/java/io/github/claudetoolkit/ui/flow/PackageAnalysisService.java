package io.github.claudetoolkit.ui.flow;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.flow.indexer.JavaPackageIndexer;
import io.github.claudetoolkit.ui.flow.indexer.JavaPackageIndexer.JavaClassInfo;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisCallerIndex;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer.MyBatisStatement;
import io.github.claudetoolkit.ui.flow.indexer.SpringUrlIndexer;
import io.github.claudetoolkit.ui.flow.indexer.SpringUrlIndexer.ControllerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * v4.5 — "패키지 개요(Package Overview)" 페이지용 집계 서비스.
 *
 * <p>기존 {@link JavaPackageIndexer}, {@link MyBatisIndexer}, {@link SpringUrlIndexer} 를
 * 패키지 단위로 조합·집계해서 프론트가 카드·리스트로 렌더할 수 있는 DTO 를 제공한다.
 *
 * <p>핵심 개념:
 * <ul>
 *   <li>패키지 = 사용자가 설정한 레벨로 자른 Java 패키지 prefix (예: L5 → {@code com.erp.sales.order})</li>
 *   <li>MyBatis statement 는 {@code fullId = namespace.id} 의 namespace 가 "대응 Java 패키지" 와
 *       같거나 prefix 가 겹치면 해당 패키지로 귀속</li>
 * </ul>
 */
@Service
public class PackageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(PackageAnalysisService.class);

    private final ToolkitSettings     settings;
    private final JavaPackageIndexer  javaIndex;
    private final MyBatisIndexer      mybatis;
    private final SpringUrlIndexer    spring;
    private final MyBatisCallerIndex  callerIndex;

    /** v4.5 — overview 결과 캐시. key = "level@prefix" */
    private final java.util.concurrent.ConcurrentHashMap<String, OverviewCache> overviewCache =
            new java.util.concurrent.ConcurrentHashMap<String, OverviewCache>();

    private static class OverviewCache {
        final List<PackageSummary> list;
        final int indexerVersion;
        OverviewCache(List<PackageSummary> l, int v) { this.list = l; this.indexerVersion = v; }
    }

    public PackageAnalysisService(ToolkitSettings settings,
                                  JavaPackageIndexer javaIndex,
                                  MyBatisIndexer mybatis,
                                  SpringUrlIndexer spring,
                                  MyBatisCallerIndex callerIndex) {
        this.settings    = settings;
        this.javaIndex   = javaIndex;
        this.mybatis     = mybatis;
        this.spring      = spring;
        this.callerIndex = callerIndex;
    }

    // ── 설정값 ─────────────────────────────────────────────────────────

    public int    currentLevel()  { return settings.getProject() != null ? settings.getProject().getPackageLevel()  : 5; }
    public String currentPrefix() { return settings.getProject() != null ? settings.getProject().getPackagePrefix() : ""; }

    /** 슬라이더 미리보기 — 각 레벨(2~9)별 패키지 수 */
    public Map<Integer, Integer> previewLevels(String prefix) {
        Map<Integer, Integer> out = new LinkedHashMap<Integer, Integer>();
        for (int lv = 2; lv <= 9; lv++) out.put(lv, javaIndex.distinctPackagesAtLevel(lv, prefix).size());
        return out;
    }

    // ── Overview (전 패키지 요약 카드 목록) ──────────────────────────────

    /**
     * v4.5 — 최적화: 1회 패스로 전 패키지 집계.
     * 이전: O(pkgs × (classes + mb + endpoints × classes)) ≈ O(N³)
     * 개선: O(classes + mb + endpoints) — 전부 linear scan 1회씩.
     * 결과는 (level, prefix) 키로 캐시, 인덱서 재빌드 시 자동 무효화 (버전 카운터 비교).
     */
    public List<PackageSummary> listOverview(int level, String prefix) {
        String pf = prefix == null ? "" : prefix;
        String cacheKey = level + "@" + pf;
        OverviewCache cached = overviewCache.get(cacheKey);
        int indexerVersion = javaIndex.getTotalClasses();  // 간단 버전 키 (클래스 수 변화 감지)
        if (cached != null && cached.indexerVersion == indexerVersion) {
            return cached.list;
        }

        // 1) classes by truncated package
        Map<String, List<JavaClassInfo>> classesByPkg = new LinkedHashMap<String, List<JavaClassInfo>>();
        for (JavaClassInfo info : javaIndex.getAllClasses()) {
            String pkg = JavaPackageIndexer.truncate(info.packageName, level);
            if (!pf.isEmpty() && !pkg.startsWith(pf)) continue;
            List<JavaClassInfo> list = classesByPkg.get(pkg);
            if (list == null) { list = new ArrayList<JavaClassInfo>(); classesByPkg.put(pkg, list); }
            list.add(info);
        }

        // 2) MyBatis — 3층 매칭
        //   Layer 1: MyBatisCallerIndex (Java file .shortId( grep) → 패키지 (정확)
        //   Layer 2: namespace + .mapper/.dao/.repository strip → 패키지 (fallback)
        //   Layer 3: 원본 namespace truncate → 패키지 (legacy, 가장 loose)
        Map<String, Set<MyBatisStatement>> mbByPkgSet = new HashMap<String, Set<MyBatisStatement>>();
        Map<String, Set<String>> tablesByPkg         = new HashMap<String, Set<String>>();
        Map<String, MyBatisStatement> mbByFullId     = new HashMap<String, MyBatisStatement>();
        for (MyBatisStatement st : mybatis.allStatements()) {
            if (st.fullId != null) mbByFullId.put(st.fullId, st);
        }

        // Layer 1 — Java 파일 기반 호출자 역인덱스
        for (JavaClassInfo info : javaIndex.getAllClasses()) {
            String pkg = JavaPackageIndexer.truncate(info.packageName, level);
            if (!pf.isEmpty() && !pkg.startsWith(pf)) continue;
            Set<String> called = callerIndex.statementsCalledByFile(info.relPath);
            if (called.isEmpty()) continue;
            for (String fullId : called) {
                MyBatisStatement st = mbByFullId.get(fullId);
                if (st == null) continue;
                Set<MyBatisStatement> set = mbByPkgSet.get(pkg);
                if (set == null) { set = new LinkedHashSet<MyBatisStatement>(); mbByPkgSet.put(pkg, set); }
                set.add(st);
                Set<String> ts = tablesByPkg.get(pkg);
                if (ts == null) { ts = new HashSet<String>(); tablesByPkg.put(pkg, ts); }
                if (st.tables != null) ts.addAll(st.tables);
            }
        }

        // Layer 2 & 3 — 아직 커버되지 않은 statement 는 namespace 기반 추론으로 연결
        Set<String> covered = new HashSet<String>();
        for (Set<MyBatisStatement> set : mbByPkgSet.values()) {
            for (MyBatisStatement st : set) covered.add(st.fullId);
        }
        for (MyBatisStatement st : mybatis.allStatements()) {
            if (st.fullId == null || covered.contains(st.fullId)) continue;
            for (String candidatePkg : nsPackageCandidates(st.fullId, level)) {
                if (!pf.isEmpty() && !candidatePkg.startsWith(pf)) continue;
                Set<MyBatisStatement> set = mbByPkgSet.get(candidatePkg);
                if (set == null) { set = new LinkedHashSet<MyBatisStatement>(); mbByPkgSet.put(candidatePkg, set); }
                set.add(st);
                Set<String> ts = tablesByPkg.get(candidatePkg);
                if (ts == null) { ts = new HashSet<String>(); tablesByPkg.put(candidatePkg, ts); }
                if (st.tables != null) ts.addAll(st.tables);
            }
        }

        Map<String, List<MyBatisStatement>> mbByPkg = new HashMap<String, List<MyBatisStatement>>();
        for (Map.Entry<String, Set<MyBatisStatement>> e : mbByPkgSet.entrySet()) {
            mbByPkg.put(e.getKey(), new ArrayList<MyBatisStatement>(e.getValue()));
        }

        // 3) Spring endpoints by (relPath → class → truncated package) — O(1) lookup per endpoint
        Map<String, Integer> epCountByPkg = new HashMap<String, Integer>();
        for (ControllerEndpoint ep : spring.allEndpoints()) {
            JavaClassInfo match = javaIndex.findByRelPath(ep.file);
            if (match == null) continue;
            String pkg = JavaPackageIndexer.truncate(match.packageName, level);
            if (!pf.isEmpty() && !pkg.startsWith(pf)) continue;
            epCountByPkg.merge(pkg, 1, Integer::sum);
        }

        // 4) 요약 조립
        List<PackageSummary> out = new ArrayList<PackageSummary>();
        for (Map.Entry<String, List<JavaClassInfo>> e : classesByPkg.entrySet()) {
            String pkg = e.getKey();
            List<JavaClassInfo> classes = e.getValue();
            PackageSummary s = new PackageSummary();
            s.packageName = pkg;
            s.classTotal = classes.size();
            s.controllerCount = countByType(classes, "controller");
            s.serviceCount    = countByType(classes, "service");
            s.daoCount        = countByType(classes, "dao");
            s.modelCount      = countByType(classes, "model");
            s.otherCount      = s.classTotal - s.controllerCount - s.serviceCount
                              - s.daoCount  - s.modelCount;
            List<MyBatisStatement> mb = mbByPkg.get(pkg);
            s.mybatisCount = mb == null ? 0 : mb.size();
            Set<String> tb = tablesByPkg.get(pkg);
            s.tableCount   = tb == null ? 0 : tb.size();
            s.endpointCount = epCountByPkg.getOrDefault(pkg, 0);
            out.add(s);
        }
        Collections.sort(out, new Comparator<PackageSummary>() {
            public int compare(PackageSummary a, PackageSummary b) {
                int sa = a.mybatisCount + a.endpointCount * 2 + a.classTotal;
                int sb = b.mybatisCount + b.endpointCount * 2 + b.classTotal;
                return Integer.compare(sb, sa);
            }
        });
        overviewCache.put(cacheKey, new OverviewCache(out, indexerVersion));
        log.info("[Package] overview built: level={} prefix='{}' packages={} classes={} mb={} eps={}",
                level, pf, out.size(), javaIndex.getTotalClasses(),
                mybatis.allStatements().size(), spring.allEndpoints().size());
        return out;
    }

    /** 캐시 수동 초기화 (refresh 후 호출 권장) */
    public int clearOverviewCache() {
        int n = overviewCache.size();
        overviewCache.clear();
        return n;
    }

    /** v4.5 — 어드민 캐시 통계 엔드포인트용. */
    public int overviewCacheSize() {
        return overviewCache.size();
    }

    // ── 패키지 의존성 그래프 ───────────────────────────────────────────

    /**
     * 현재 레벨의 전 패키지에 대해 Spring endpoint callee 기반으로 패키지 간 의존성을 계산.
     * 반환 형식: {@code { nodes: [...], edges: [...], level: N }}
     */
    public Map<String, Object> buildDependencyGraph(int level, String prefix) {
        List<PackageSummary> summaries = listOverview(level, prefix);
        Set<String> pkgNames = new LinkedHashSet<String>();
        for (PackageSummary s : summaries) pkgNames.add(s.packageName);

        List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> edges = new ArrayList<Map<String, Object>>();
        Set<String> edgeSet = new HashSet<String>();

        for (PackageSummary s : summaries) {
            Map<String, Object> node = new LinkedHashMap<String, Object>();
            node.put("id",              s.packageName);
            node.put("packageName",     s.packageName);
            node.put("classTotal",      s.classTotal);
            node.put("controllerCount", s.controllerCount);
            node.put("serviceCount",    s.serviceCount);
            node.put("daoCount",        s.daoCount);
            nodes.add(node);

            List<ControllerEndpoint> eps = springEndpointsMatching(s.packageName, level);
            List<String> deps = inferExternalDeps(eps, s.packageName, level);
            for (String dep : deps) {
                if (!pkgNames.contains(dep) || dep.equals(s.packageName)) continue;
                String key = s.packageName + "->" + dep;
                if (edgeSet.add(key)) {
                    Map<String, Object> edge = new LinkedHashMap<String, Object>();
                    edge.put("id",     key);
                    edge.put("source", s.packageName);
                    edge.put("target", dep);
                    edges.add(edge);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("level", level);
        result.put("packageCount", nodes.size());
        result.put("edgeCount",    edges.size());
        return result;
    }

    // ── Detail (패키지 하나 상세) ──────────────────────────────────────

    public PackageDetail getDetail(String packageName, int level) {
        PackageDetail d = new PackageDetail();
        d.packageName = packageName;
        d.level = level;

        List<JavaClassInfo> classes = javaIndex.classesInPackage(packageName, level);
        d.classes = classes;
        d.classTotal = classes.size();
        d.controllerCount = countByType(classes, "controller");
        d.serviceCount    = countByType(classes, "service");
        d.daoCount        = countByType(classes, "dao");
        d.modelCount      = countByType(classes, "model");

        // MyBatis
        List<MyBatisStatement> mbStmts = mybatisMatching(packageName);
        d.mybatisStatements = mbStmts;
        d.mybatisCount = mbStmts.size();
        d.tables = new ArrayList<String>(distinctTables(mbStmts));
        Collections.sort(d.tables);
        d.tableCount = d.tables.size();

        // Spring endpoints (파일 경로 기반)
        List<ControllerEndpoint> eps = springEndpointsMatching(packageName, level);
        d.endpoints = eps;
        d.endpointCount = eps.size();

        // 외부 패키지 의존 — classes 의 import 는 파싱 안 함. MyBatis 가 다른 패키지에 있거나
        // 엔드포인트 callees 에 다른 패키지 메서드가 있으면 외부 의존. 단순 추정.
        d.externalDependencies = inferExternalDeps(eps, packageName, level);

        return d;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static int countByType(List<JavaClassInfo> list, String type) {
        int c = 0;
        for (JavaClassInfo info : list) if (type.equals(info.type)) c++;
        return c;
    }

    /**
     * 이 패키지에 "속하는" MyBatis statement 판정 (3층).
     * Layer 1 — 이 패키지의 Java 파일 중 MyBatisCallerIndex 에 등재된 것들의 statement 수집
     * Layer 2 — namespace 에서 .mapper/.dao/.repository 등 서브패키지 strip 후 매칭
     * Layer 3 — 원본 namespace 기반 truncate 매칭 (legacy)
     */
    private List<MyBatisStatement> mybatisMatching(String truncatedPkg) {
        int level = currentLevel();
        Set<MyBatisStatement> out = new LinkedHashSet<MyBatisStatement>();
        Map<String, MyBatisStatement> byFullId = new HashMap<String, MyBatisStatement>();
        for (MyBatisStatement st : mybatis.allStatements()) {
            if (st.fullId != null) byFullId.put(st.fullId, st);
        }

        // Layer 1 — caller index
        for (JavaClassInfo info : javaIndex.getAllClasses()) {
            String pkg = JavaPackageIndexer.truncate(info.packageName, level);
            if (!pkg.equals(truncatedPkg)) continue;
            for (String fullId : callerIndex.statementsCalledByFile(info.relPath)) {
                MyBatisStatement st = byFullId.get(fullId);
                if (st != null) out.add(st);
            }
        }

        // Layer 2 & 3 — namespace candidates
        for (MyBatisStatement st : mybatis.allStatements()) {
            if (st.fullId == null || out.contains(st)) continue;
            for (String candidatePkg : nsPackageCandidates(st.fullId, level)) {
                if (candidatePkg.equals(truncatedPkg)) { out.add(st); break; }
            }
        }

        return new ArrayList<MyBatisStatement>(out);
    }

    /**
     * v4.5 — namespace 에서 **가능한 Java 패키지 후보** 여러 개 생성.
     * 실제 ERP 프로젝트의 Mapper 배치 관용구를 커버:
     * <ul>
     *   <li>{@code com.x.sales.order.mapper.OrderMapper.id} → 후보: {@code com.x.sales.order.mapper}, {@code com.x.sales.order}</li>
     *   <li>{@code com.x.sales.order.dao.OrderDao.id} → 후보: {@code com.x.sales.order.dao}, {@code com.x.sales.order}</li>
     * </ul>
     */
    private static Set<String> nsPackageCandidates(String fullId, int level) {
        Set<String> out = new LinkedHashSet<String>();
        if (fullId == null) return out;
        int dot = fullId.lastIndexOf('.');
        if (dot <= 0) return out;
        String namespace = fullId.substring(0, dot);
        int nsDot = namespace.lastIndexOf('.');
        String nsPkg = nsDot > 0 ? namespace.substring(0, nsDot) : namespace;

        // 후보 1 — 원본
        out.add(JavaPackageIndexer.truncate(nsPkg, level));

        // 후보 2 — .mapper/.dao/.repository/.mappers/.daos 접미사 strip
        String stripped = nsPkg;
        for (String suffix : new String[]{
                ".mapper", ".mappers",
                ".dao", ".daos",
                ".repository", ".repositories",
                ".persistence"}) {
            if (stripped.toLowerCase().endsWith(suffix)) {
                stripped = stripped.substring(0, stripped.length() - suffix.length());
                out.add(JavaPackageIndexer.truncate(stripped, level));
                break;
            }
        }
        return out;
    }

    private List<ControllerEndpoint> springEndpointsMatching(String truncatedPkg, int level) {
        List<ControllerEndpoint> out = new ArrayList<ControllerEndpoint>();
        for (ControllerEndpoint ep : spring.allEndpoints()) {
            // v4.5 — JavaPackageIndexer.findByRelPath 의 O(1) 인덱스 이용
            JavaClassInfo match = javaIndex.findByRelPath(ep.file);
            if (match == null) continue;
            if (JavaPackageIndexer.truncate(match.packageName, level).equals(truncatedPkg)) {
                out.add(ep);
            }
        }
        return out;
    }

    private static Set<String> distinctTables(List<MyBatisStatement> stmts) {
        Set<String> t = new TreeSet<String>();
        for (MyBatisStatement s : stmts) {
            if (s.tables != null) t.addAll(s.tables);
        }
        return t;
    }

    private List<String> inferExternalDeps(List<ControllerEndpoint> eps,
                                           String selfPkg, int level) {
        Set<String> out = new TreeSet<String>();
        for (ControllerEndpoint ep : eps) {
            if (ep.callees == null) continue;
            // callees 는 메서드 단순명 리스트. 어떤 패키지에 해당 메서드가 있는지 역탐색.
            for (String callee : ep.callees) {
                if (callee == null || callee.length() < 3) continue;
                // 간단 휴리스틱 — 이 부분은 정확도 낮으니 표면적만. 추후 개선.
                for (JavaClassInfo info : javaIndex.getAllClasses()) {
                    String t = JavaPackageIndexer.truncate(info.packageName, level);
                    if (t.equals(selfPkg)) continue;
                    // 해당 클래스 이름이 callee 를 힌트로 포함하면 의존
                    if (info.className != null && info.className.toLowerCase().contains(callee.toLowerCase())) {
                        out.add(t);
                        break;
                    }
                }
            }
        }
        return new ArrayList<String>(out);
    }

    // ── DTO ─────────────────────────────────────────────────────────────

    public static class PackageSummary {
        public String packageName;
        public int classTotal;
        public int controllerCount;
        public int serviceCount;
        public int daoCount;
        public int modelCount;
        public int otherCount;
        public int mybatisCount;
        public int tableCount;
        public int endpointCount;

        public String getPackageName()   { return packageName; }
        public int    getClassTotal()    { return classTotal; }
        public int    getControllerCount() { return controllerCount; }
        public int    getServiceCount()  { return serviceCount; }
        public int    getDaoCount()      { return daoCount; }
        public int    getModelCount()    { return modelCount; }
        public int    getOtherCount()    { return otherCount; }
        public int    getMybatisCount()  { return mybatisCount; }
        public int    getTableCount()    { return tableCount; }
        public int    getEndpointCount() { return endpointCount; }
    }

    public static class PackageDetail {
        public String packageName;
        public int level;
        public int classTotal;
        public int controllerCount;
        public int serviceCount;
        public int daoCount;
        public int modelCount;
        public int mybatisCount;
        public int tableCount;
        public int endpointCount;
        public List<JavaClassInfo> classes;
        public List<MyBatisStatement> mybatisStatements;
        public List<ControllerEndpoint> endpoints;
        public List<String> tables;
        public List<String> externalDependencies;

        public String getPackageName()    { return packageName; }
        public int    getLevel()          { return level; }
        public int    getClassTotal()     { return classTotal; }
        public int    getControllerCount(){ return controllerCount; }
        public int    getServiceCount()   { return serviceCount; }
        public int    getDaoCount()       { return daoCount; }
        public int    getModelCount()     { return modelCount; }
        public int    getMybatisCount()   { return mybatisCount; }
        public int    getTableCount()     { return tableCount; }
        public int    getEndpointCount()  { return endpointCount; }
        public List<JavaClassInfo>    getClasses()          { return classes; }
        public List<MyBatisStatement> getMybatisStatements(){ return mybatisStatements; }
        public List<ControllerEndpoint> getEndpoints()      { return endpoints; }
        public List<String>           getTables()           { return tables; }
        public List<String>           getExternalDependencies() { return externalDependencies; }
    }
}
