package io.github.claudetoolkit.ui.livedb;

import io.github.claudetoolkit.ui.dbprofile.DbProfile;
import io.github.claudetoolkit.ui.dbprofile.DbProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

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

    @Autowired(required = false)
    private LiveDbQueryLogRepository queryLogRepo;

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

    /**
     * 최근 Live DB 쿼리 실행 이력 — ADMIN 감사·성능 트래킹.
     * @param limit 최대 행 수 (기본 100, 최대 500)
     * @param profileId 특정 프로필만 조회 (생략하면 전체)
     */
    @GetMapping("/query-log")
    public ResponseEntity<Map<String, Object>> queryLog(
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestParam(value = "profileId", required = false) Long profileId) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (queryLogRepo == null) {
            resp.put("success", false);
            resp.put("error", "LiveDbQueryLogRepository 미등록 — Spring JPA 설정 확인");
            return ResponseEntity.ok(resp);
        }
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<LiveDbQueryLog> logs = profileId != null
                ? queryLogRepo.findRecentByProfile(profileId, PageRequest.of(0, safeLimit))
                : queryLogRepo.findRecent(PageRequest.of(0, safeLimit));

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (LiveDbQueryLog l : logs) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id",           l.getId());
            m.put("profileId",    l.getProfileId());
            m.put("executedAt",   l.getExecutedAt() != null ? l.getExecutedAt().toString() : null);
            m.put("username",     l.getUsername());
            m.put("sqlText",      l.getSqlText());
            m.put("durationMs",   l.getDurationMs());
            m.put("rowCount",     l.getRowCount());
            m.put("status",       l.getStatus());
            m.put("errorMessage", l.getErrorMessage());
            rows.add(m);
        }
        resp.put("success", true);
        resp.put("count",   rows.size());
        resp.put("logs",    rows);
        return ResponseEntity.ok(resp);
    }

    /**
     * v4.7.x — #G3 보강: 런타임 글로벌 ON/OFF 토글 (ADMIN 전용).
     *
     * <p><b>주의</b>: 이 토글은 *현재 JVM 인스턴스* 에만 적용. application 재시작 후엔
     * {@code application.yml} 또는 환경변수 {@code TOOLKIT_LIVEDB_ENABLED} 가 우선.
     * 영구 활성화하려면 yml/env 도 함께 수정 필요.
     */
    @PostMapping("/enabled")
    public ResponseEntity<Map<String, Object>> setEnabled(
            @org.springframework.web.bind.annotation.RequestParam("value") boolean value,
            HttpServletRequest request) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (!request.isUserInRole("ADMIN")) {
            resp.put("success", false);
            resp.put("error",   "ADMIN 권한이 필요합니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }
        config.setEnabled(value);
        resp.put("success",  true);
        resp.put("enabled",  value);
        resp.put("message",  value
            ? "✅ Live DB 글로벌 활성화됨 (현재 인스턴스만 — 영구하려면 yml/환경변수 변경)"
            : "⏸ Live DB 글로벌 비활성화");
        resp.put("warning", value
            ? "재시작 후엔 yml/env 가 우선합니다. 영구 ON 을 원하면 application.yml 의 toolkit.livedb.enabled=true 추가"
            : null);
        return ResponseEntity.ok(resp);
    }
}
