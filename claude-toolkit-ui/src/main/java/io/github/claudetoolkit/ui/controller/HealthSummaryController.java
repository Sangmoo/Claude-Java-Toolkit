package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.api.ApiResponse;
import io.github.claudetoolkit.ui.errorlog.ErrorLogRepository;
import io.github.claudetoolkit.ui.flow.indexer.JavaPackageIndexer;
import io.github.claudetoolkit.ui.flow.indexer.MiPlatformIndexer;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisCallerIndex;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer;
import io.github.claudetoolkit.ui.flow.indexer.SpringUrlIndexer;
import io.github.claudetoolkit.ui.harness.HarnessCacheService;
import io.github.claudetoolkit.ui.security.AuditLogRepository;
import io.github.claudetoolkit.ui.service.AnalysisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.6.x — 시스템 헬스 대시보드용 어그리게이션 엔드포인트.
 *
 * <p>{@code GET /api/v1/admin/health/summary} 가 인덱서 / 캐시 / Claude API /
 * 감사 로그 / 오류 로그 6개 영역의 상태를 한 번에 반환한다. 기존 {@code /data}
 * 엔드포인트(JVM/DB) 와는 영역이 다르고 독립적으로 운용 가능하도록 분리.
 *
 * <p>모든 데이터는 인-메모리 또는 인덱스된 카운트만 읽으므로 30초 주기 폴링에도
 * 부하가 거의 없음. ADMIN 전용 (URL 패턴 기반 보안 정책 자동 적용).
 */
@RestController
@RequestMapping("/api/v1/admin/health")
public class HealthSummaryController {

    private static final Logger log = LoggerFactory.getLogger(HealthSummaryController.class);

    private final MyBatisIndexer        mybatisIndexer;
    private final SpringUrlIndexer      springUrlIndexer;
    private final MiPlatformIndexer     miplatformIndexer;
    private final JavaPackageIndexer    javaPackageIndexer;
    private final MyBatisCallerIndex    callerIndex;
    private final HarnessCacheService   harnessCache;
    private final AnalysisCacheService  analysisCache;
    private final ClaudeClient          claudeClient;
    private final AuditLogRepository    auditLogRepo;
    private final ErrorLogRepository    errorLogRepo;

    public HealthSummaryController(MyBatisIndexer mybatisIndexer,
                                   SpringUrlIndexer springUrlIndexer,
                                   MiPlatformIndexer miplatformIndexer,
                                   JavaPackageIndexer javaPackageIndexer,
                                   MyBatisCallerIndex callerIndex,
                                   HarnessCacheService harnessCache,
                                   AnalysisCacheService analysisCache,
                                   ClaudeClient claudeClient,
                                   AuditLogRepository auditLogRepo,
                                   ErrorLogRepository errorLogRepo) {
        this.mybatisIndexer     = mybatisIndexer;
        this.springUrlIndexer   = springUrlIndexer;
        this.miplatformIndexer  = miplatformIndexer;
        this.javaPackageIndexer = javaPackageIndexer;
        this.callerIndex        = callerIndex;
        this.harnessCache       = harnessCache;
        this.analysisCache      = analysisCache;
        this.claudeClient       = claudeClient;
        this.auditLogRepo       = auditLogRepo;
        this.errorLogRepo       = errorLogRepo;
    }

    /**
     * 시스템 전체 헬스 한 번에 조회.
     * 응답 키: {@code indexers} · {@code claudeApi} · {@code cache} · {@code audit} · {@code errorLog}.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("indexers",  buildIndexers());
            data.put("claudeApi", buildClaudeApi());
            data.put("cache",     buildCache());
            data.put("audit",     buildAudit());
            data.put("errorLog",  buildErrorLog());
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            log.warn("[HealthSummary] 조회 실패", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(
                    "헬스 조회 실패: " + e.getMessage()));
        }
    }

    // ── 인덱서 5종 ──────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildIndexers() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(indexerEntry("MyBatis SQL",
                mybatisIndexer.isReady(),
                mybatisIndexer.getStatementCount(), "구문",
                mybatisIndexer.getLastScanMs(),
                mybatisIndexer.getLastScanFiles()));
        list.add(indexerEntry("Spring URL",
                springUrlIndexer.isReady(),
                springUrlIndexer.getEndpointCount(), "엔드포인트",
                springUrlIndexer.getLastScanMs(),
                springUrlIndexer.getLastScanFiles()));
        list.add(indexerEntry("MiPlatform 화면",
                miplatformIndexer.isReady(),
                miplatformIndexer.getScreenCount(), "화면",
                miplatformIndexer.getLastScanMs(),
                miplatformIndexer.getLastScanFiles()));
        list.add(indexerEntry("Java 패키지",
                javaPackageIndexer.isReady(),
                javaPackageIndexer.getTotalClasses(), "클래스",
                javaPackageIndexer.getLastScanMs(),
                javaPackageIndexer.getLastScanFiles()));
        list.add(indexerEntry("MyBatis Caller",
                callerIndex.isReady(),
                callerIndex.getStatementCoverage(), "매핑",
                callerIndex.getLastScanMs(),
                callerIndex.getLastScanFiles()));
        return list;
    }

    private Map<String, Object> indexerEntry(String name, boolean ready, int count, String unit,
                                              long lastScanMs, int lastScanFiles) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name",          name);
        m.put("ready",         ready);
        m.put("count",         count);
        m.put("unit",          unit);
        m.put("lastScanMs",    lastScanMs);
        m.put("lastScanFiles", lastScanFiles);
        return m;
    }

    // ── Claude API ──────────────────────────────────────────────────────────

    private Map<String, Object> buildClaudeApi() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            String model = claudeClient.getEffectiveModel();
            String key   = claudeClient.getEffectiveApiKey();
            m.put("model",          model != null ? model : "(미설정)");
            m.put("keyConfigured",  key != null && !key.trim().isEmpty());
            // 마지막 호출의 토큰량 — 별도 호출 통계가 없어 ClaudeClient 가 보유한 가장 최근 값 노출
            m.put("lastInputTokens",  claudeClient.getLastInputTokens());
            m.put("lastOutputTokens", claudeClient.getLastOutputTokens());
        } catch (Exception e) {
            m.put("error", e.getMessage());
        }
        return m;
    }

    // ── 캐시 3종 ────────────────────────────────────────────────────────────

    private Map<String, Object> buildCache() {
        Map<String, Object> m = new LinkedHashMap<>();
        // 분석 캐시 (response cache)
        try {
            m.put("analysis", analysisCache.getStats());
        } catch (Exception e) {
            m.put("analysis", errorMap(e));
        }
        // 하네스 — Java 파일 캐시
        try {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("loaded",       harnessCache.isFileCacheLoaded());
            file.put("refreshing",   harnessCache.isFileRefreshing());
            file.put("count",        harnessCache.getCachedFiles().size());
            file.put("lastRefresh",  harnessCache.getLastFileRefresh());
            m.put("harnessFiles", file);
        } catch (Exception e) {
            m.put("harnessFiles", errorMap(e));
        }
        // 하네스 — DB 객체 캐시
        try {
            Map<String, Object> db = new LinkedHashMap<>();
            db.put("loaded",      harnessCache.isDbCacheLoaded());
            db.put("refreshing",  harnessCache.isDbRefreshing());
            db.put("count",       harnessCache.getCachedDbObjects().size());
            db.put("configured",  harnessCache.isDbConfiguredAtLastRefresh());
            db.put("lastRefresh", harnessCache.getLastDbRefresh());
            db.put("error",       harnessCache.getLastDbError());
            m.put("harnessDb", db);
        } catch (Exception e) {
            m.put("harnessDb", errorMap(e));
        }
        return m;
    }

    // ── 감사 로그 ───────────────────────────────────────────────────────────

    private Map<String, Object> buildAudit() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            LocalDateTime since24 = LocalDateTime.now().minusHours(24);
            LocalDateTime since1h = LocalDateTime.now().minusHours(1);
            m.put("totalCount",  auditLogRepo.count());
            m.put("last24h",     auditLogRepo.countByCreatedAtAfter(since24));
            m.put("last1h",      auditLogRepo.countByCreatedAtAfter(since1h));
        } catch (Exception e) {
            return errorMap(e);
        }
        return m;
    }

    // ── 오류 로그 (Sentry-style) ────────────────────────────────────────────

    private Map<String, Object> buildErrorLog() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            m.put("totalCount",       errorLogRepo.count());
            m.put("unresolvedCount",  errorLogRepo.countByResolvedFalse());
        } catch (Exception e) {
            return errorMap(e);
        }
        return m;
    }

    private Map<String, Object> errorMap(Throwable t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        return m;
    }
}
