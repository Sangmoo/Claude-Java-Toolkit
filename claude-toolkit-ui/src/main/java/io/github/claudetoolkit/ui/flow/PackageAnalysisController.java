package io.github.claudetoolkit.ui.flow;

import io.github.claudetoolkit.ui.api.ApiResponse;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.flow.indexer.JavaPackageIndexer;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisCallerIndex;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * v4.5 — 패키지 개요(Package Overview) REST API.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/package/settings}   — 현재 레벨/prefix + 레벨별 패키지 수 미리보기</li>
 *   <li>{@code POST /api/v1/package/settings}   — 레벨/prefix 저장</li>
 *   <li>{@code GET  /api/v1/package/overview}   — 전 패키지 요약 카드 목록</li>
 *   <li>{@code GET  /api/v1/package/detail}     — 패키지 하나 상세</li>
 *   <li>{@code POST /api/v1/package/refresh}    — 모든 인덱서 재빌드 (Java/MyBatis/Spring)</li>
 * </ul>
 */
@Tag(name = "Package Overview", description = "v4.5 — Java 패키지 단위 ERD · 흐름도 · 요약")
@RestController
@RequestMapping("/api/v1/package")
public class PackageAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(PackageAnalysisController.class);

    private final PackageAnalysisService service;
    private final JavaPackageIndexer     javaIndex;
    private final ToolkitSettings        settings;
    private final PackageErdBuilder      erdBuilder;
    private final PackageFlowBuilder     flowBuilder;
    private final PackageStoryService    storyService;
    private final MyBatisCallerIndex     callerIndex;

    public PackageAnalysisController(PackageAnalysisService service,
                                     JavaPackageIndexer javaIndex,
                                     ToolkitSettings settings,
                                     PackageErdBuilder erdBuilder,
                                     PackageFlowBuilder flowBuilder,
                                     PackageStoryService storyService,
                                     MyBatisCallerIndex callerIndex) {
        this.service      = service;
        this.javaIndex    = javaIndex;
        this.settings     = settings;
        this.erdBuilder   = erdBuilder;
        this.flowBuilder  = flowBuilder;
        this.storyService = storyService;
        this.callerIndex  = callerIndex;
    }

    @Operation(summary = "현재 레벨/prefix + 레벨별 패키지 수 미리보기")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> settings(
            @RequestParam(value = "prefix", required = false) String previewPrefix) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        int currentLevel  = service.currentLevel();
        String curPrefix  = service.currentPrefix();
        data.put("currentLevel", currentLevel);
        data.put("currentPrefix", curPrefix);
        // 미리보기용 prefix 는 요청 파라미터 우선, 없으면 저장된 값
        String previewTarget = previewPrefix != null ? previewPrefix : curPrefix;
        data.put("previewPrefix", previewTarget);
        data.put("previewLevels", service.previewLevels(previewTarget));
        data.put("totalClasses",  javaIndex.getTotalClasses());
        data.put("totalPackages", javaIndex.getTotalPackages());
        data.put("indexerReady",  javaIndex.isReady());
        data.put("lastScanMs",    javaIndex.getLastScanMs());
        // v4.5 — MyBatis Caller Index 상태 (ERD/풀흐름도의 테이블 매칭 품질 지표)
        data.put("callerIndexReady",       callerIndex.isReady());
        data.put("callerIndexFiles",       callerIndex.getFileCoverage());
        data.put("callerIndexStatements",  callerIndex.getStatementCoverage());
        data.put("callerIndexMatches",     callerIndex.getLastScanMatches());
        data.put("callerIndexLastScanMs",  callerIndex.getLastScanMs());
        // 샘플 패키지 3개
        Set<String> sample = javaIndex.distinctPackagesAtLevel(currentLevel, previewTarget);
        List<String> samples = new ArrayList<String>(sample);
        Collections.sort(samples);
        data.put("samplePackages", samples.size() > 10 ? samples.subList(0, 10) : samples);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(summary = "레벨/prefix 저장")
    @PostMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveSettings(
            @RequestBody Map<String, Object> body) {
        try {
            if (settings.getProject() == null) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("프로젝트 설정이 없습니다."));
            }
            if (body.get("level") instanceof Number) {
                int lv = ((Number) body.get("level")).intValue();
                if (lv < 2 || lv > 10) {
                    return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("level 은 2~10 사이여야 합니다."));
                }
                settings.getProject().setPackageLevel(lv);
            }
            if (body.get("prefix") instanceof String) {
                String pf = (String) body.get("prefix");
                if (pf.length() > 200) {
                    return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("prefix 가 너무 깁니다 (200자 제한)"));
                }
                settings.getProject().setPackagePrefix(pf);
            }
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("level", settings.getProject().getPackageLevel());
            out.put("prefix", settings.getProject().getPackagePrefix());
            log.info("[Package] settings saved: level={} prefix={}",
                    out.get("level"), out.get("prefix"));
            return ResponseEntity.ok(ApiResponse.ok(out));
        } catch (Exception e) {
            log.warn("[Package] settings 저장 실패", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("저장 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "전 패키지 요약 카드 목록")
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> overview(
            @RequestParam(value = "level",  required = false) Integer level,
            @RequestParam(value = "prefix", required = false) String   prefix) {
        int lv = level != null ? level : service.currentLevel();
        String pf = prefix != null ? prefix : service.currentPrefix();
        try {
            List<PackageAnalysisService.PackageSummary> summaries = service.listOverview(lv, pf);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("level", lv);
            data.put("prefix", pf);
            data.put("packageCount", summaries.size());
            data.put("packages", summaries);
            data.put("indexerReady", javaIndex.isReady());
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[Package] overview 실패", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("집계 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "패키지 하나 상세")
    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(
            @RequestParam("name") String packageName,
            @RequestParam(value = "level", required = false) Integer level) {
        int lv = level != null ? level : service.currentLevel();
        try {
            PackageAnalysisService.PackageDetail d = service.getDetail(packageName, lv);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("detail", d);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[Package] detail 실패 name={}", packageName, e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("상세 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "패키지 ERD — Oracle 메타데이터 기반 Mermaid 즉시 생성")
    @GetMapping("/erd")
    public ResponseEntity<ApiResponse<Map<String, Object>>> erd(
            @RequestParam("name") String packageName,
            @RequestParam(value = "level",          required = false) Integer level,
            @RequestParam(value = "columnDetail",   required = false, defaultValue = "false") boolean columnDetail,
            @RequestParam(value = "prefixGrouping", required = false, defaultValue = "true")  boolean prefixGrouping,
            @RequestParam(value = "heatmap",        required = false, defaultValue = "true")  boolean heatmap) {
        int lv = level != null ? level : service.currentLevel();
        try {
            PackageAnalysisService.PackageDetail detail = service.getDetail(packageName, lv);
            if (detail == null || detail.tables == null || detail.tables.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<String, Object>();
                empty.put("mermaid", "%% 연관 테이블이 없습니다\nerDiagram");
                empty.put("tableCount", 0);
                empty.put("foreignKeyCount", 0);
                empty.put("tables", java.util.Collections.<Object>emptyList());
                empty.put("hitCounts", java.util.Collections.<String, Integer>emptyMap());
                empty.put("warnings", java.util.Arrays.asList(
                        "이 패키지가 건드리는 테이블을 MyBatis 인덱스에서 찾지 못했습니다."));
                return ResponseEntity.ok(ApiResponse.ok(empty));
            }

            // MyBatis 히트카운트 집계
            Map<String, Integer> hitCounts = new LinkedHashMap<String, Integer>();
            if (detail.mybatisStatements != null) {
                for (io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer.MyBatisStatement st
                        : detail.mybatisStatements) {
                    if (st.tables == null) continue;
                    for (String t : st.tables) {
                        hitCounts.merge(t, 1, Integer::sum);
                    }
                }
            }

            PackageErdBuilder.ErdOptions opts = new PackageErdBuilder.ErdOptions();
            opts.columnDetail   = columnDetail;
            opts.prefixGrouping = prefixGrouping;
            opts.heatmap        = heatmap;

            PackageErdBuilder.ErdResult erd = erdBuilder.build(detail.tables, opts, hitCounts);

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("mermaid",         erd.mermaid);
            data.put("tableCount",      detail.tables.size());
            data.put("foreignKeyCount", erd.foreignKeyCount);
            data.put("tables",          erd.tables);
            data.put("hitCounts",       hitCounts);
            data.put("warnings",        erd.warnings);
            data.put("options", new LinkedHashMap<String, Object>() {{
                put("columnDetail",   columnDetail);
                put("prefixGrouping", prefixGrouping);
                put("heatmap",        heatmap);
            }});
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[Package] erd 실패 name={}", packageName, e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("ERD 생성 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "패키지 풀 흐름도 — 연관 테이블 전체에 대한 FlowAnalysis 병합")
    @GetMapping("/flow")
    public ResponseEntity<ApiResponse<Map<String, Object>>> flow(
            @RequestParam("name") String packageName,
            @RequestParam(value = "level", required = false) Integer level,
            @RequestParam(value = "fresh", required = false, defaultValue = "false") boolean fresh) {
        int lv = level != null ? level : service.currentLevel();
        try {
            PackageFlowBuilder.MergedResult merged = flowBuilder.build(packageName, lv, fresh);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("packageName",      merged.packageName);
            data.put("nodes",            merged.nodes);
            data.put("edges",            merged.edges);
            data.put("analyzedTables",   merged.analyzedTables);
            data.put("tablesTruncated",  merged.tablesTruncated);
            data.put("nodesByType",      merged.nodesByType);
            data.put("warnings",         merged.warnings);
            data.put("fromCache",        merged.fromCache);
            data.put("cacheAgeMs",       merged.cacheAgeMs);
            data.put("elapsedMs",        merged.elapsedMs);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[Package] flow 실패 name={}", packageName, e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("풀 흐름도 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "패키지 풀 흐름도 캐시 초기화")
    @PostMapping("/flow/clear-cache")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearFlowCache() {
        int cleared = flowBuilder.clearCache();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("cleared", cleared);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @Operation(summary = "패키지 스토리 — Claude 가 생성한 신입 친화 한국어 내러티브")
    @GetMapping("/story")
    public ResponseEntity<ApiResponse<Map<String, Object>>> story(
            @RequestParam("name") String packageName,
            @RequestParam(value = "level", required = false) Integer level,
            @RequestParam(value = "fresh", required = false, defaultValue = "false") boolean fresh) {
        int lv = level != null ? level : service.currentLevel();
        try {
            PackageStoryService.StoryResult r = storyService.generate(packageName, lv, fresh);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("packageName", r.packageName);
            data.put("markdown",    r.markdown);
            data.put("error",       r.error);
            data.put("fromCache",   r.fromCache);
            data.put("cacheAgeMs",  r.cacheAgeMs);
            data.put("elapsedMs",   r.elapsedMs);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[Package] story 실패 name={}", packageName, e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("스토리 생성 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "모든 인덱서 재빌드 (Java/MyBatis caller/Spring)")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(HttpServletRequest request) {
        if (!request.isUserInRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<Map<String, Object>>error("ADMIN 권한이 필요합니다."));
        }
        long start = System.currentTimeMillis();
        try {
            javaIndex.refresh();
            // v4.5 — MyBatis 호출자 역인덱스 재빌드 (ERD/풀흐름도의 테이블 매칭 정확도 향상)
            callerIndex.refresh();
            int overviewCleared = service.clearOverviewCache();
            int flowCleared     = flowBuilder.clearCache();
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("javaClasses",           javaIndex.getTotalClasses());
            out.put("javaPackages",          javaIndex.getTotalPackages());
            out.put("callerIndexFiles",      callerIndex.getFileCoverage());
            out.put("callerIndexStatements", callerIndex.getStatementCoverage());
            out.put("callerIndexMatches",    callerIndex.getLastScanMatches());
            out.put("overviewCacheCleared",  overviewCleared);
            out.put("flowCacheCleared",      flowCleared);
            out.put("elapsedMs",             System.currentTimeMillis() - start);
            return ResponseEntity.ok(ApiResponse.ok(out));
        } catch (Exception e) {
            log.warn("[Package] refresh 실패", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("재빌드 실패: " + e.getMessage()));
        }
    }
}
