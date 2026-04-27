package io.github.claudetoolkit.ui.flow;

import io.github.claudetoolkit.ui.flow.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * v4.5 — 패키지 개요의 "🌊 풀 흐름도" 탭을 위한 벌크 Flow 집계 빌더.
 *
 * <p>패키지가 터치하는 모든 테이블에 대해 {@link FlowAnalysisService}를 병렬 호출한 뒤,
 * 노드/엣지를 중복 제거하여 통합 그래프를 만든다.
 *
 * <p>성능: 큰 패키지(50+ 테이블)는 분석이 오래 걸릴 수 있으므로
 * <ul>
 *   <li>결과는 (packageName, level) 키로 <b>메모리 캐시</b> (기본 30분 TTL)</li>
 *   <li>동시 최대 4개 테이블 분석 (ForkJoinPool common) — 큰 병렬화는 DB 풀/메모리에 부담</li>
 *   <li>한 패키지당 분석 테이블 수는 {@link #MAX_TABLES_PER_PACKAGE} 로 상한</li>
 * </ul>
 */
@Service
public class PackageFlowBuilder {

    private static final Logger log = LoggerFactory.getLogger(PackageFlowBuilder.class);

    /** 한 번에 분석할 최대 테이블 수 (너무 크면 너무 오래 걸림) */
    private static final int MAX_TABLES_PER_PACKAGE = 30;
    /** 병합 후 노드 수 상한 — ReactFlow 렌더 안정성 보호 */
    private static final int MAX_MERGED_NODES       = 1500;
    /** 병합 후 엣지 수 상한 — 동일 */
    private static final int MAX_MERGED_EDGES       = 3000;
    /** 캐시 TTL (ms) — 30 분 */
    private static final long CACHE_TTL_MS          = 30L * 60 * 1000;
    /** 병렬 worker 수 */
    private static final int PARALLELISM            = 4;

    private final PackageAnalysisService packageService;
    private final FlowAnalysisService    flowService;

    /** 단순 TTL 캐시 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<String, CacheEntry>();

    public PackageFlowBuilder(PackageAnalysisService packageService,
                              FlowAnalysisService    flowService) {
        this.packageService = packageService;
        this.flowService    = flowService;
    }

    /**
     * 패키지의 풀 흐름도 생성 또는 캐시 반환.
     *
     * @param fresh true 면 캐시 무시하고 재분석
     */
    public MergedResult build(String packageName, int level, boolean fresh) {
        String cacheKey = packageName + "@L" + level;
        long now = System.currentTimeMillis();

        if (!fresh) {
            CacheEntry e = cache.get(cacheKey);
            if (e != null && (now - e.createdAt) < CACHE_TTL_MS) {
                MergedResult cached = e.result;
                cached.fromCache = true;
                cached.cacheAgeMs = now - e.createdAt;
                return cached;
            }
        }

        long t0 = System.currentTimeMillis();
        PackageAnalysisService.PackageDetail detail = packageService.getDetail(packageName, level);
        MergedResult out = new MergedResult();
        out.packageName = packageName;

        if (detail == null || detail.tables == null || detail.tables.isEmpty()) {
            out.warnings.add("이 패키지가 참조하는 테이블을 찾지 못했습니다.");
            return out;
        }

        List<String> targetTables = detail.tables;
        boolean truncated = false;
        if (targetTables.size() > MAX_TABLES_PER_PACKAGE) {
            out.warnings.add("테이블 " + targetTables.size() + "개 중 상위 "
                    + MAX_TABLES_PER_PACKAGE + "개만 분석합니다. (가독성 보호)");
            targetTables = new ArrayList<String>(targetTables).subList(0, MAX_TABLES_PER_PACKAGE);
            truncated = true;
        }
        out.analyzedTables = new ArrayList<String>(targetTables);
        out.tablesTruncated = truncated;

        // 병렬 분석
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(PARALLELISM, targetTables.size()));
        List<Future<FlowAnalysisResult>> futures = new ArrayList<Future<FlowAnalysisResult>>();
        for (final String table : targetTables) {
            futures.add(pool.submit(new Callable<FlowAnalysisResult>() {
                public FlowAnalysisResult call() {
                    try {
                        FlowAnalysisRequest req = new FlowAnalysisRequest();
                        req.setQuery(table);
                        req.setTargetType(FlowAnalysisRequest.TargetType.TABLE);
                        req.setMaxBranches(3);
                        req.setIncludeDb(true);
                        req.setIncludeUi(true);
                        return flowService.analyze(req);
                    } catch (Exception ex) {
                        log.warn("[PackageFlow] 테이블 '{}' 분석 실패: {}", table, ex.getMessage());
                        FlowAnalysisResult empty = new FlowAnalysisResult();
                        empty.warnings.add("테이블 '" + table + "' 분석 실패: " + ex.getMessage());
                        return empty;
                    }
                }
            }));
        }
        pool.shutdown();

        List<FlowAnalysisResult> results = new ArrayList<FlowAnalysisResult>();
        for (Future<FlowAnalysisResult> f : futures) {
            try {
                results.add(f.get(60, TimeUnit.SECONDS));
            } catch (Exception ex) {
                log.warn("[PackageFlow] 병렬 작업 실패: {}", ex.getMessage());
            }
        }
        try { pool.awaitTermination(1, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        mergeResults(results, out);
        out.elapsedMs = System.currentTimeMillis() - t0;
        cache.put(cacheKey, new CacheEntry(out, System.currentTimeMillis()));
        log.info("[PackageFlow] pkg={} tables={} nodes={} edges={} {}ms",
                packageName, targetTables.size(), out.nodes.size(), out.edges.size(), out.elapsedMs);
        return out;
    }

    // ── 병합 로직 ────────────────────────────────────────────────────────

    /**
     * 여러 FlowAnalysisResult 의 노드/엣지를 type+label 키로 중복제거하며 병합.
     * ID 는 병합 후 재할당 (원본 ID 충돌 방지).
     */
    private void mergeResults(List<FlowAnalysisResult> results, MergedResult out) {
        Map<String, FlowNode> byKey = new LinkedHashMap<String, FlowNode>();  // 키 = type::label
        Set<String> edgeSeen = new LinkedHashSet<String>();
        List<FlowEdge> mergedEdges = new ArrayList<FlowEdge>();
        Set<String> warnings = new LinkedHashSet<String>();

        int seq = 0;
        for (FlowAnalysisResult r : results) {
            if (r == null) continue;
            if (r.warnings != null) warnings.addAll(r.warnings);

            Map<String, String> oldIdToKey = new HashMap<String, String>();
            if (r.nodes != null) {
                for (FlowNode n : r.nodes) {
                    String key = nodeKey(n);
                    oldIdToKey.put(n.id, key);
                    FlowNode existing = byKey.get(key);
                    if (existing == null) {
                        FlowNode copy = new FlowNode("m" + (seq++), n.type, n.label);
                        copy.file = n.file;
                        copy.line = n.line;
                        if (n.meta != null) copy.meta.putAll(n.meta);
                        byKey.put(key, copy);
                    } else if (n.meta != null && !n.meta.isEmpty()) {
                        // meta 보강 (누락 키만 추가 — 기존 값 덮어쓰지 않음)
                        for (Map.Entry<String, String> e : n.meta.entrySet()) {
                            if (!existing.meta.containsKey(e.getKey())) {
                                existing.meta.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                }
            }
            if (r.edges != null) {
                for (FlowEdge e : r.edges) {
                    String fromKey = oldIdToKey.get(e.from);
                    String toKey   = oldIdToKey.get(e.to);
                    if (fromKey == null || toKey == null) continue;
                    FlowNode fromN = byKey.get(fromKey);
                    FlowNode toN   = byKey.get(toKey);
                    if (fromN == null || toN == null) continue;
                    String label = e.label == null ? "" : e.label;
                    String edgeKey = fromN.id + "->" + toN.id + "::" + label;
                    if (edgeSeen.add(edgeKey)) {
                        mergedEdges.add(new FlowEdge(fromN.id, toN.id, e.label));
                    }
                }
            }
        }

        List<FlowNode> allNodes = new ArrayList<FlowNode>(byKey.values());
        List<FlowEdge> allEdges = mergedEdges;

        // v4.5 — 노드/엣지 상한 (ReactFlow 렌더 안정성)
        if (allNodes.size() > MAX_MERGED_NODES) {
            warnings.add("병합 후 노드 " + allNodes.size() + "개 중 상위 " + MAX_MERGED_NODES
                    + "개만 표시합니다. 타입 필터로 레이어를 좁혀보세요.");
            // 타입 우선순위로 트림 (table → mybatis → dao → service → controller → ui → sp 순 중요도)
            // 간단히 그냥 앞쪽 N 개 유지
            allNodes = new ArrayList<FlowNode>(allNodes.subList(0, MAX_MERGED_NODES));
            Set<String> keepIds = new HashSet<String>();
            for (FlowNode n : allNodes) keepIds.add(n.id);
            List<FlowEdge> trimmed = new ArrayList<FlowEdge>();
            for (FlowEdge e : allEdges) {
                if (keepIds.contains(e.from) && keepIds.contains(e.to)) trimmed.add(e);
            }
            allEdges = trimmed;
        }
        if (allEdges.size() > MAX_MERGED_EDGES) {
            warnings.add("엣지 " + allEdges.size() + "개 중 상위 " + MAX_MERGED_EDGES + "개만 표시합니다.");
            allEdges = new ArrayList<FlowEdge>(allEdges.subList(0, MAX_MERGED_EDGES));
        }

        out.nodes = allNodes;
        out.edges = allEdges;
        out.warnings.addAll(warnings);

        // 노드 타입별 분포
        Map<String, Integer> byType = new LinkedHashMap<String, Integer>();
        for (FlowNode n : out.nodes) {
            byType.merge(n.type == null ? "other" : n.type, 1, Integer::sum);
        }
        out.nodesByType = byType;
    }

    private static String nodeKey(FlowNode n) {
        String t = n.type  == null ? "other" : n.type;
        String l = n.label == null ? ""      : n.label;
        return t + "::" + l;
    }

    // ── 캐시 ────────────────────────────────────────────────────────────

    public int clearCache() {
        int n = cache.size();
        cache.clear();
        return n;
    }

    private static class CacheEntry {
        final MergedResult result;
        final long createdAt;
        CacheEntry(MergedResult r, long t) { this.result = r; this.createdAt = t; }
    }

    // ── 결과 DTO ────────────────────────────────────────────────────────

    public static class MergedResult {
        public String       packageName;
        public List<FlowNode> nodes = new ArrayList<FlowNode>();
        public List<FlowEdge> edges = new ArrayList<FlowEdge>();
        public List<String>   analyzedTables = new ArrayList<String>();
        public Map<String, Integer> nodesByType = new LinkedHashMap<String, Integer>();
        public List<String>   warnings = new ArrayList<String>();
        public boolean        tablesTruncated;
        public boolean        fromCache;
        public long           cacheAgeMs;
        public long           elapsedMs;

        public String         getPackageName()     { return packageName; }
        public List<FlowNode> getNodes()           { return nodes; }
        public List<FlowEdge> getEdges()           { return edges; }
        public List<String>   getAnalyzedTables()  { return analyzedTables; }
        public Map<String, Integer> getNodesByType() { return nodesByType; }
        public List<String>   getWarnings()        { return warnings; }
        public boolean        isTablesTruncated()  { return tablesTruncated; }
        public boolean        isFromCache()        { return fromCache; }
        public long           getCacheAgeMs()      { return cacheAgeMs; }
        public long           getElapsedMs()       { return elapsedMs; }
    }
}
