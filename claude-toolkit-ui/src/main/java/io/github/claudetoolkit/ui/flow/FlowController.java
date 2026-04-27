package io.github.claudetoolkit.ui.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claudetoolkit.ui.api.ApiResponse;
import io.github.claudetoolkit.ui.flow.history.FlowHistory;
import io.github.claudetoolkit.ui.flow.history.FlowHistoryService;
import io.github.claudetoolkit.ui.flow.indexer.MiPlatformIndexer;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer;
import io.github.claudetoolkit.ui.flow.indexer.SpringUrlIndexer;
import io.github.claudetoolkit.ui.flow.model.FlowAnalysisRequest;
import io.github.claudetoolkit.ui.flow.model.FlowAnalysisResult;
import io.github.claudetoolkit.ui.share.SharedResult;
import io.github.claudetoolkit.ui.share.SharedResultRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 1 의 REST 진입점. POST 로 분석을 요청하면 동기 결과 반환 (LLM 미사용).
 *
 * <p>Phase 2 에서 SSE 스트림 + LLM 가 추가될 예정.
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>{@code POST /api/v1/flow/analyze} — 분석 실행 (JSON 본문)</li>
 *   <li>{@code GET  /api/v1/flow/status}  — 인덱서 상태</li>
 *   <li>{@code POST /api/v1/flow/reindex} — 모든 인덱스 강제 재빌드</li>
 * </ul>
 */
@Tag(name = "Flow Analysis", description = "데이터 흐름 추적 (Phase 1 — backend trace engine)")
@RestController
@RequestMapping("/api/v1/flow")
public class FlowController {

    private static final Logger log = LoggerFactory.getLogger(FlowController.class);

    private final FlowAnalysisService     service;
    private final MyBatisIndexer          mybatis;
    private final SpringUrlIndexer        spring;
    private final MiPlatformIndexer       miplatform;
    private final FlowHistoryService      history;
    private final SharedResultRepository  shareRepo;
    private final ObjectMapper            mapper;

    public FlowController(FlowAnalysisService service,
                          MyBatisIndexer mybatis,
                          SpringUrlIndexer spring,
                          MiPlatformIndexer miplatform,
                          FlowHistoryService history,
                          SharedResultRepository shareRepo,
                          ObjectMapper mapper) {
        this.service    = service;
        this.mybatis    = mybatis;
        this.spring     = spring;
        this.miplatform = miplatform;
        this.history    = history;
        this.shareRepo  = shareRepo;
        this.mapper     = mapper;
    }

    @Operation(summary = "데이터 흐름 분석 — 테이블 / SP / SQL_ID / MiPlatform 화면을 시작점으로 추적")
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<FlowAnalysisResult>> analyze(@RequestBody FlowAnalysisRequest req) {
        try {
            FlowAnalysisResult r = service.analyze(req);
            return ResponseEntity.ok(ApiResponse.ok(r));
        } catch (Exception e) {
            log.error("[Flow] analyze 실패", e);
            return ResponseEntity.ok(ApiResponse.<FlowAnalysisResult>error("분석 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "소스 파일의 특정 라인 주변 발췌 — Service/Controller 노드 드로어용")
    @GetMapping("/source")
    public ResponseEntity<ApiResponse<Map<String, Object>>> source(
            @RequestParam("file") String relPath,
            @RequestParam(value = "line", defaultValue = "1") int line,
            @RequestParam(value = "context", defaultValue = "12") int context) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        try {
            if (relPath == null || relPath.trim().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("file 파라미터가 필요합니다."));
            }
            // Path-traversal 방어 — scanPath 밖으로 빠져나가는 경로 거부
            String clean = relPath.replace('\\', '/').trim();
            if (clean.contains("../") || clean.startsWith("/") || clean.contains(":")) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("허용되지 않은 경로입니다."));
            }
            io.github.claudetoolkit.ui.config.ToolkitSettings settings = service.getSettings();
            if (settings.getProject() == null || settings.getProject().getScanPath() == null) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("scanPath 가 설정되지 않았습니다."));
            }
            java.nio.file.Path root = java.nio.file.Paths.get(
                    io.github.claudetoolkit.ui.config.HostPathTranslator.translate(
                            settings.getProject().getScanPath()));
            java.nio.file.Path target = root.resolve(clean).normalize();
            if (!target.startsWith(root)) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("scanPath 밖의 파일입니다."));
            }
            if (!java.nio.file.Files.isRegularFile(target)) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("파일을 찾을 수 없습니다: " + clean));
            }
            long size = java.nio.file.Files.size(target);
            if (size > 5_000_000) {
                return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("파일이 너무 큽니다 (" + size + "B)."));
            }
            int ctx = Math.max(3, Math.min(context, 60));
            int want = Math.max(1, line);
            java.util.List<String> all = java.nio.file.Files.readAllLines(target,
                    java.nio.charset.StandardCharsets.UTF_8);
            int from = Math.max(0, want - ctx - 1);
            int to   = Math.min(all.size(), want + ctx);
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < to; i++) {
                sb.append(String.format("%5d%s %s%n",
                        i + 1, (i + 1 == want ? ">" : " "), all.get(i)));
            }
            data.put("file",     clean);
            data.put("line",     want);
            data.put("fromLine", from + 1);
            data.put("toLine",   to);
            data.put("total",    all.size());
            data.put("snippet",  sb.toString());
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[Flow] source 발췌 실패 file={}: {}", relPath, e.getMessage());
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("발췌 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "인덱서 상태 조회 — MyBatis / Spring URL / MiPlatform")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Map<String, Object> mb = new LinkedHashMap<String, Object>();
        mb.put("ready",       mybatis.isReady());
        mb.put("statements",  mybatis.getStatementCount());
        mb.put("tables",      mybatis.getTableCount());
        mb.put("xmlFiles",    mybatis.getLastScanFiles());
        mb.put("lastScanMs",  mybatis.getLastScanMs());
        data.put("mybatis", mb);

        Map<String, Object> sp = new LinkedHashMap<String, Object>();
        sp.put("ready",       spring.isReady());
        sp.put("endpoints",   spring.getEndpointCount());
        sp.put("urls",        spring.getUrlCount());
        sp.put("javaFiles",   spring.getLastScanFiles());
        sp.put("lastScanMs",  spring.getLastScanMs());
        data.put("springUrl", sp);

        Map<String, Object> mi = new LinkedHashMap<String, Object>();
        mi.put("ready",        miplatform.isReady());
        mi.put("screens",      miplatform.getScreenCount());
        mi.put("urls",         miplatform.getUrlCount());
        mi.put("xmlFiles",     miplatform.getLastScanFiles());
        mi.put("detectedRoot", miplatform.getDetectedRoot());
        mi.put("lastScanMs",   miplatform.getLastScanMs());
        data.put("miplatform", mi);

        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── Phase 4: History ────────────────────────────────────────────────

    @Operation(summary = "내 분석 이력 목록 (최신순)")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> historyList(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>error("로그인이 필요합니다."));
        }
        List<FlowHistory> items = history.recent(principal.getName(), limit);
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (FlowHistory h : items) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id",          h.getId());
            m.put("query",       h.getQuery());
            m.put("targetType",  h.getTargetType());
            m.put("dmlFilters",  h.getDmlFilters());
            m.put("nodesCount",  h.getNodesCount());
            m.put("edgesCount",  h.getEdgesCount());
            m.put("elapsedMs",   h.getElapsedMs());
            m.put("createdAt",   h.getFormattedCreatedAt());
            out.add(m);
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @Operation(summary = "이력 상세 — 저장된 trace + narrative 복원")
    @GetMapping("/history/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> historyDetail(
            @PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("로그인이 필요합니다."));
        }
        Optional<FlowHistory> opt = history.findOwned(id, principal.getName());
        if (!opt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("이력이 없거나 접근 권한이 없습니다."));
        }
        FlowHistory h = opt.get();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id",         h.getId());
        m.put("query",      h.getQuery());
        m.put("targetType", h.getTargetType());
        m.put("dmlFilters", h.getDmlFilters());
        m.put("createdAt",  h.getFormattedCreatedAt());
        // trace_json 은 raw JSON 문자열 — 프론트에서 JSON.parse 해서 사용
        m.put("traceJson",  h.getTraceJson());
        m.put("narrative",  h.getNarrative());
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @Operation(summary = "이력 삭제 (본인 소유분만)")
    @DeleteMapping("/history/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> historyDelete(
            @PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("로그인이 필요합니다."));
        }
        boolean ok = history.delete(id, principal.getName());
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("deleted", ok);
        return ResponseEntity.ok(ApiResponse.ok(r));
    }

    @Operation(summary = "이력을 공유 링크로 변환 (7일 유효, 누구나 read-only)")
    @PostMapping("/history/{id}/share")
    public ResponseEntity<ApiResponse<Map<String, Object>>> historyShare(
            @PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("로그인이 필요합니다."));
        }
        Optional<FlowHistory> opt = history.findOwned(id, principal.getName());
        if (!opt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("이력이 없거나 접근 권한이 없습니다."));
        }
        FlowHistory h = opt.get();
        // 기존 SharedResult 인프라 재사용 — type="flow", inputContent=traceJson, outputContent=narrative
        SharedResult share = new SharedResult(h.getId(), "flow",
                truncate200("Flow: " + h.getQuery()),
                h.getTraceJson() == null ? "{}" : h.getTraceJson(),
                h.getNarrative()  == null ? ""   : h.getNarrative());
        shareRepo.save(share);

        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("token",     share.getToken());
        // 프론트는 /flow-analysis?share=token 으로 라우트해서 페이지 안에서 복원
        r.put("shareUrl",  "/flow-analysis?share=" + share.getToken());
        r.put("expiresAt", share.getExpiresAt().toString());
        r.put("remaining", share.getRemainingDaysText());
        return ResponseEntity.ok(ApiResponse.ok(r));
    }

    private static String truncate200(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    @Operation(summary = "모든 인덱스 강제 재빌드 (settings 변경 후 또는 운영자 요청)")
    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindex() {
        long start = System.currentTimeMillis();
        try {
            mybatis.refresh();
            spring.refresh();
            miplatform.refresh();
        } catch (Exception e) {
            log.error("[Flow] reindex 실패", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error("재인덱싱 실패: " + e.getMessage()));
        }
        long elapsed = System.currentTimeMillis() - start;
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("elapsedMs",         elapsed);
        data.put("mybatisStatements", mybatis.getStatementCount());
        data.put("springEndpoints",   spring.getEndpointCount());
        data.put("miplatformScreens", miplatform.getScreenCount());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
