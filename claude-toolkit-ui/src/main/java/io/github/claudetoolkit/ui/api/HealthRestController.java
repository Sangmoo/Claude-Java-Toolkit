package io.github.claudetoolkit.ui.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.HostPathTranslator;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.flow.indexer.MiPlatformIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REST API health check endpoint.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>GET /api/v1/health — 서버 상태 + 설정/DB/ERP 연결 가능성 확인</li>
 * </ul>
 *
 * <h3>응답 예시 (v4.4.x 확장)</h3>
 * <pre>
 * {
 *   "success": true,
 *   "data": {
 *     "status":         "UP",
 *     "version":        "1.0.0",
 *     "claudeModel":    "claude-sonnet-4-5",
 *     "apiKeySet":      true,
 *     "dbConfigured":   true,        ← Settings 에 외부 DB URL/계정 입력됨
 *     "dbReachable":    true,        ← 실제 외부 DB 연결 OK (60초 캐시)
 *     "dbInfo":         "jdbc:oracle:thin:@//host:1521/SVC",
 *     "erpConfigured":  true,        ← 프로젝트 스캔 경로 설정됨
 *     "erpReachable":   true,        ← 해당 디렉토리 실제 존재
 *     "erpInfo":        "/host/d/eclipse_indongfn/workspace/IND_ERP",
 *     "miConfigured":   true,        ← MiPlatform 디렉토리 (Settings 또는 자동감지) 있음
 *     "miReachable":    true,        ← MiPlatformIndexer 캐시에 화면 ≥1 존재
 *     "miInfo":         "src/main/webapp/miplatform (358 화면, 421 URL)"
 *   }
 * }
 * </pre>
 */
@Tag(name = "Health", description = "서버 상태 확인")
@RestController
@RequestMapping("/api/v1")
public class HealthRestController {

    private static final Logger log = LoggerFactory.getLogger(HealthRestController.class);
    private static final long DB_PROBE_TTL_MS = 60_000;  // 60초 캐시 — 헬스체크 폭증 방지

    private final ToolkitSettings    settings;
    private final ClaudeClient       claudeClient;
    private final MiPlatformIndexer  miplatformIndexer;

    /** v4.4.x — DB probe 결과 캐시 (URL+username 변경 시 자동 무효화) */
    private final AtomicReference<String> cachedDbKey   = new AtomicReference<>(null);
    private final AtomicReference<Boolean> cachedDbOk   = new AtomicReference<>(null);
    private final AtomicLong               cachedDbTime = new AtomicLong(0);

    public HealthRestController(ToolkitSettings settings, ClaudeClient claudeClient,
                                MiPlatformIndexer miplatformIndexer) {
        this.settings          = settings;
        this.claudeClient      = claudeClient;
        this.miplatformIndexer = miplatformIndexer;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        String apiKey = claudeClient.getApiKey();
        boolean apiKeySet = apiKey != null && !apiKey.trim().isEmpty();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status",       "UP");
        data.put("version",      "1.0.0");
        data.put("claudeModel",  claudeClient.getEffectiveModel());
        data.put("apiKeySet",    apiKeySet);

        // ── 외부 DB 검사 (Settings 입력 + 실제 reachable) ──────────────
        boolean dbConfigured = settings.isDbConfigured();
        boolean dbReachable  = false;
        String  dbInfo       = null;
        if (dbConfigured) {
            dbReachable = probeDb(settings.getDb().getUrl(),
                                  settings.getDb().getUsername(),
                                  settings.getDb().getPassword());
            dbInfo = maskUrl(settings.getDb().getUrl());
        }
        data.put("dbConfigured", dbConfigured);
        data.put("dbReachable",  dbReachable);
        data.put("dbInfo",       dbInfo);

        // ── 프로젝트 (ERP) 스캔 경로 검사 ──────────────────────────────
        boolean erpConfigured = false;
        boolean erpReachable  = false;
        String  erpInfo       = null;
        String  scanPath      = settings.getProject() != null ? settings.getProject().getScanPath() : null;
        if (scanPath != null && !scanPath.trim().isEmpty()) {
            erpConfigured = true;
            // v4.4.x — Linux 컨테이너에서 Windows 경로 자동 변환 (D:\ → /host/d/)
            String resolved = HostPathTranslator.translate(scanPath);
            erpInfo = HostPathTranslator.describe(scanPath);
            try {
                File f = new File(resolved);
                erpReachable = f.exists() && f.isDirectory() && f.canRead();
            } catch (Exception e) {
                log.debug("ERP path 검사 실패: {}", e.getMessage());
            }
        }
        data.put("erpConfigured", erpConfigured);
        data.put("erpReachable",  erpReachable);
        data.put("erpInfo",       erpInfo);

        // ── v4.4.x — MiPlatform 인덱서 상태 (Hero 위젯 추가 pill) ─────────
        boolean miConfigured = false;
        boolean miReachable  = false;
        String  miInfo       = null;
        if (miplatformIndexer != null) {
            String customRoot = settings.getProject() != null ? settings.getProject().getMiplatformRoot() : "";
            miConfigured = (customRoot != null && !customRoot.trim().isEmpty()) || erpConfigured;
            miReachable  = miplatformIndexer.isReady() && miplatformIndexer.getScreenCount() > 0;
            String detected = miplatformIndexer.getDetectedRoot();
            if (detected != null && !detected.isEmpty()) {
                miInfo = detected + "  (" + miplatformIndexer.getScreenCount() + " 화면, "
                       + miplatformIndexer.getUrlCount() + " URL)";
            } else if (customRoot != null && !customRoot.trim().isEmpty()) {
                miInfo = customRoot + "  (디렉토리 미감지 — 경로 확인 필요)";
            }
        }
        data.put("miConfigured", miConfigured);
        data.put("miReachable",  miReachable);
        data.put("miInfo",       miInfo);

        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── helpers ───────────────────────────────────────────────────────

    /**
     * DB 연결 가능성 검사 — 60초 캐시. URL/계정 변경 시 자동 무효화.
     */
    private boolean probeDb(String url, String user, String pass) {
        if (url == null || url.trim().isEmpty()) return false;
        String key = url + "|" + user;
        long now = System.currentTimeMillis();
        Boolean cached = cachedDbOk.get();
        if (cached != null && key.equals(cachedDbKey.get())
                && (now - cachedDbTime.get()) < DB_PROBE_TTL_MS) {
            return cached;
        }
        boolean ok = false;
        try {
            DriverManager.setLoginTimeout(5);  // 5초 안에 응답 없으면 false
            try (Connection c = DriverManager.getConnection(url, user, pass)) {
                ok = c.isValid(3);
            }
        } catch (Exception e) {
            log.debug("DB probe 실패: {}", e.getMessage());
        }
        cachedDbKey.set(key);
        cachedDbOk.set(ok);
        cachedDbTime.set(now);
        return ok;
    }

    private String maskUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("password=[^&;]*", "password=****")
                  .replaceAll(":[^:@/]+@", ":****@");
    }
}
