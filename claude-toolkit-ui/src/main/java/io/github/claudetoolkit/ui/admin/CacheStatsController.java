package io.github.claudetoolkit.ui.admin;

import io.github.claudetoolkit.ui.api.ApiResponse;
import io.github.claudetoolkit.ui.flow.PackageAnalysisService;
import io.github.claudetoolkit.ui.flow.PackageFlowBuilder;
import io.github.claudetoolkit.ui.flow.PackageStoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v4.5 — 어드민 전용 캐시 통계.
 *
 * <p>경로 {@code /api/v1/admin/**} 는 {@code SecurityConfig} 에서
 * {@code hasRole("ADMIN")} 으로 보호 — 별도 가드 불필요.
 */
@Tag(name = "Admin Caches", description = "어드민 전용 — 인메모리 캐시 사이즈 모니터링")
@RestController
@RequestMapping("/api/v1/admin/caches")
public class CacheStatsController {

    private final PackageStoryService    storyService;
    private final PackageFlowBuilder     flowBuilder;
    private final PackageAnalysisService packageService;

    public CacheStatsController(PackageStoryService storyService,
                                PackageFlowBuilder flowBuilder,
                                PackageAnalysisService packageService) {
        this.storyService   = storyService;
        this.flowBuilder    = flowBuilder;
        this.packageService = packageService;
    }

    @Operation(summary = "Package Analysis 인메모리 캐시 사이즈")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();

        Map<String, Object> story = new LinkedHashMap<String, Object>();
        story.put("size",  storyService.cacheSize());
        story.put("ttlMs", 30L * 60 * 1000);
        data.put("packageStory", story);

        Map<String, Object> flow = new LinkedHashMap<String, Object>();
        flow.put("size",  flowBuilder.cacheSize());
        flow.put("ttlMs", 30L * 60 * 1000);
        data.put("packageFlow", flow);

        Map<String, Object> overview = new LinkedHashMap<String, Object>();
        overview.put("size", packageService.overviewCacheSize());
        data.put("packageOverview", overview);

        data.put("collectedAt", System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
