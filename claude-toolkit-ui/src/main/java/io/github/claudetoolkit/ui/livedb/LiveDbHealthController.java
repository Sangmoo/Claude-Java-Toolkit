package io.github.claudetoolkit.ui.livedb;

import io.github.claudetoolkit.ui.dbprofile.DbProfile;
import io.github.claudetoolkit.ui.dbprofile.DbProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.7.x — #G3 Live DB Phase 5: 헬스 / 운영 통계 API.
 *
 * <p><b>모두 ADMIN 전용</b> (SecurityConfig 가 /api/v1/admin/** 를 ADMIN 으로 제한).
 *
 * <ul>
 *   <li>{@code GET  /api/v1/admin/livedb/stats}      — 프로필별 통계 + breaker 상태</li>
 *   <li>{@code POST /api/v1/admin/livedb/breaker/{profileId}/close} — 회로 강제 복구</li>
 *   <li>{@code POST /api/v1/admin/livedb/stats/reset} — 통계 카운터 초기화 (테스트용)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/livedb")
public class LiveDbHealthController {

    private final LiveDbConfig          config;
    private final LiveDbCallStats       stats;
    private final LiveDbCircuitBreaker  breaker;
    private final DbProfileService      profileService;

    public LiveDbHealthController(LiveDbConfig config,
                                  LiveDbCallStats stats,
                                  LiveDbCircuitBreaker breaker,
                                  DbProfileService profileService) {
        this.config         = config;
        this.stats          = stats;
        this.breaker        = breaker;
        this.profileService = profileService;
    }

    /**
     * 종합 헬스 — 프로필별 통계 + 회로 상태 + 글로벌 설정.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        Map<String, Object> snap = stats.snapshot();

        // 프로필별 통계에 회로 상태 + 프로필 메타 병합
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byProfile = (List<Map<String, Object>>) snap.get("byProfile");
        List<Map<String, Object>> enriched = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> p : byProfile) {
            Map<String, Object> m = new LinkedHashMap<String, Object>(p);
            Long profileId = ((Number) p.get("profileId")).longValue();
            DbProfile profile = profileService.findById(profileId);
            m.put("profileName", profile != null ? profile.getName() : "(deleted)");
            m.put("circuitOpen", breaker.isOpen(profileId));
            m.put("circuitSecondsUntilHalfOpen", breaker.secondsUntilHalfOpen(profileId));
            enriched.add(m);
        }

        Map<String, Object> globalConfig = new LinkedHashMap<String, Object>();
        globalConfig.put("enabled",              config.isEnabled());
        globalConfig.put("defaultTimeoutSeconds", config.getDefaultTimeoutSeconds());
        globalConfig.put("maxRows",              config.getMaxRows());
        globalConfig.put("maxCallsPerMinute",    config.getMaxCallsPerMinute());

        resp.put("success",     true);
        resp.put("config",      globalConfig);
        resp.put("byProfile",   enriched);
        resp.put("total",       snap.get("total"));
        resp.put("snapshotAt",  System.currentTimeMillis());
        return ResponseEntity.ok(resp);
    }

    /** 회로차단 강제 복구 — DBA 가 즉시 재활성화. */
    @PostMapping("/breaker/{profileId}/close")
    public ResponseEntity<Map<String, Object>> forceCloseBreaker(
            @PathVariable Long profileId, HttpServletRequest request) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (!request.isUserInRole("ADMIN")) {
            resp.put("success", false);
            resp.put("error",   "ADMIN 권한이 필요합니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }
        breaker.forceClose(profileId);
        resp.put("success", true);
        resp.put("profileId", profileId);
        resp.put("message", "회로차단기 강제 복구 — 다음 호출부터 정상 동작");
        return ResponseEntity.ok(resp);
    }

    /** 통계 카운터 초기화 — JVM 라이프타임 누적이라 운영 중 reset 가능. */
    @PostMapping("/stats/reset")
    public ResponseEntity<Map<String, Object>> resetStats(HttpServletRequest request) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (!request.isUserInRole("ADMIN")) {
            resp.put("success", false);
            resp.put("error",   "ADMIN 권한이 필요합니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }
        stats.reset();
        breaker.reset();
        resp.put("success", true);
        resp.put("message", "Live DB 통계 + 회로차단 모두 초기화");
        return ResponseEntity.ok(resp);
    }
}
