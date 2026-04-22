package io.github.claudetoolkit.ui.flow;

import io.github.claudetoolkit.ui.api.ApiResponse;
import io.github.claudetoolkit.ui.flow.indexer.MiPlatformIndexer;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer;
import io.github.claudetoolkit.ui.flow.indexer.SpringUrlIndexer;
import io.github.claudetoolkit.ui.flow.model.FlowAnalysisRequest;
import io.github.claudetoolkit.ui.flow.model.FlowAnalysisResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

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

    private final FlowAnalysisService service;
    private final MyBatisIndexer      mybatis;
    private final SpringUrlIndexer    spring;
    private final MiPlatformIndexer   miplatform;

    public FlowController(FlowAnalysisService service,
                          MyBatisIndexer mybatis,
                          SpringUrlIndexer spring,
                          MiPlatformIndexer miplatform) {
        this.service    = service;
        this.mybatis    = mybatis;
        this.spring     = spring;
        this.miplatform = miplatform;
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
