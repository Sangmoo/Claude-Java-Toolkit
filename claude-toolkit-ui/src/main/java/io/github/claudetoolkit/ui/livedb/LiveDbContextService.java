package io.github.claudetoolkit.ui.livedb;

import io.github.claudetoolkit.ui.dbprofile.DbProfile;
import io.github.claudetoolkit.ui.dbprofile.DbProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * v4.7.x — #G3 Live DB Phase 1: 분석 페이지에서 호출하는 facade.
 *
 * <p>책임:
 * <ul>
 *   <li>주어진 dbProfileId 가 *Live 분석 가능* (readOnlyForLiveAnalysis=true) 한지 확인</li>
 *   <li>프로필별 DataSource 캐시 — 매 호출마다 connection pool 만드는 비용 회피</li>
 *   <li>DbProfile.dbType 으로 DBMS-별 Provider 라우팅 (현재는 Oracle 만)</li>
 *   <li>{@link LiveDbContext} 를 {@link LiveDbContextFormatter} 로 markdown 변환</li>
 * </ul>
 *
 * <p>Phase 2 에서 SseStreamController 가 이 facade 의 {@link #fetchAsMarkdown}
 * 한 번 호출로 전체 컨텍스트를 받아 Claude system prompt 에 prepend.
 */
@Service
public class LiveDbContextService {

    private static final Logger log = LoggerFactory.getLogger(LiveDbContextService.class);

    private final DbProfileService          profileService;
    private final LiveDbConfig              config;
    private final OracleLiveDbContextProvider   oracleProvider   = new OracleLiveDbContextProvider();
    private final PostgresLiveDbContextProvider postgresProvider = new PostgresLiveDbContextProvider();

    /** v4.7.x — Phase 5: 운영 가드 — 옵셔널 (테스트 환경에서 빈 미등록도 허용) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LiveDbRateLimiter rateLimiter;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LiveDbCircuitBreaker circuitBreaker;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LiveDbCallStats callStats;
    /** v4.7.x — #G3 보강 B2: 외부감사 추적용 audit_log 기록 — 옵셔널 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.claudetoolkit.ui.security.AuditLogService auditLogService;

    /** 프로필 ID → DataSource 캐시 — 매번 새 connection pool 만드는 비용 회피 */
    private final Map<Long, DataSource> dataSourceCache = new HashMap<Long, DataSource>();

    /** 프로필 ID → ReadOnlyJdbcTemplate 캐시 (DataSource 와 1:1) */
    private final Map<Long, ReadOnlyJdbcTemplate> jdbcCache = new HashMap<Long, ReadOnlyJdbcTemplate>();

    public LiveDbContextService(DbProfileService profileService, LiveDbConfig config) {
        this.profileService = profileService;
        this.config         = config;
    }

    /**
     * dbProfileId 와 사용자 SQL 로부터 라이브 컨텍스트를 markdown 으로 반환.
     * 이 메서드 한 번 호출이 SSE controller 의 단일 진입점.
     *
     * @return markdown 문자열. 비활성/실패/빈 컨텍스트면 빈 문자열 (Claude prompt 에 추가하지 말 것)
     */
    public String fetchAsMarkdown(String userSql, Long dbProfileId) {
        LiveDbContext ctx = fetch(userSql, dbProfileId);
        if (ctx == null || ctx.isEmpty()) return "";
        return LiveDbContextFormatter.format(ctx);
    }

    /**
     * 원본 컨텍스트를 반환 (테스트 / 어드민 디버깅 용도).
     * 비활성 / 실패 시 null 또는 빈 컨텍스트.
     */
    public LiveDbContext fetch(String userSql, Long dbProfileId) {
        if (!config.isEnabled()) {
            log.debug("[LiveDb] Disabled — skipping fetch");
            return null;
        }
        if (dbProfileId == null) return null;

        DbProfile profile = profileService.findById(dbProfileId);
        if (profile == null) {
            log.warn("[LiveDb] Profile {} not found", dbProfileId);
            return null;
        }
        if (!profile.isReadOnlyForLiveAnalysis()) {
            log.warn("[LiveDb] Profile '{}' is not enabled for live analysis", profile.getName());
            return null;
        }

        // v4.7.x — Phase 5: 운영 가드 — circuit breaker / rate limiter
        if (circuitBreaker != null && circuitBreaker.isOpen(profile.getId())) {
            log.info("[LiveDb] Circuit OPEN — profile '{}' temporarily disabled ({}s remaining)",
                     profile.getName(), circuitBreaker.secondsUntilHalfOpen(profile.getId()));
            LiveDbContext err = new LiveDbContext();
            err.addWarning("프로필 '" + profile.getName() + "' 일시 비활성 (회로차단기 작동 중) — "
                    + circuitBreaker.secondsUntilHalfOpen(profile.getId()) + "초 후 자동 복구");
            return err;
        }
        String username = currentUsername();
        if (rateLimiter != null && !rateLimiter.tryAcquire(username, profile.getId())) {
            log.info("[LiveDb] Rate limit exceeded — user={}, profile={}", username, profile.getName());
            LiveDbContext err = new LiveDbContext();
            err.addWarning("Live DB 호출 분당 한도 초과 (" + config.getMaxCallsPerMinute() + "/min) — 잠시 후 재시도");
            return err;
        }

        long t0 = System.currentTimeMillis();
        Integer status = 200;
        try {
            ReadOnlyJdbcTemplate ro = getOrCreateJdbc(profile);
            LiveDbContextProvider provider = pickProvider(profile);
            if (provider == null) {
                log.warn("[LiveDb] No provider for profile '{}' (only Oracle/Postgres supported)",
                         profile.getName());
                status = 501;
                return null;
            }
            // schema = profile 의 username (Oracle 관례 — username 이 schema)
            String schema = profile.getUsername();
            LiveDbContext ctx = provider.fetch(userSql, schema, ro);
            if (callStats != null) callStats.recordSuccess(profile.getId(), System.currentTimeMillis() - t0);
            return ctx;

        } catch (org.springframework.dao.QueryTimeoutException e) {
            // statement timeout — circuit breaker 의 trigger 신호
            if (callStats != null) callStats.recordTimeout(profile.getId(), System.currentTimeMillis() - t0);
            log.warn("[LiveDb] timeout for profile '{}': {}", profile.getName(), e.getMessage());
            status = 504;
            LiveDbContext err = new LiveDbContext();
            err.addWarning("Live DB 쿼리 timeout (" + config.getDefaultTimeoutSeconds() + "초): " + e.getMessage());
            return err;
        } catch (Exception e) {
            if (callStats != null) callStats.recordFailure(profile.getId(), System.currentTimeMillis() - t0);
            log.warn("[LiveDb] fetch failed for profile '{}': {}", profile.getName(), e.getMessage());
            status = 500;
            LiveDbContext err = new LiveDbContext();
            err.addWarning("Live DB 컨텍스트 수집 실패: " + e.getMessage());
            return err;
        } finally {
            // v4.7.x — #G3 보강 B2: 외부감사 추적 — 사용자 facing 호출당 1 row
            recordAudit("livedb.fetch", profile, username, status, System.currentTimeMillis() - t0);
        }
    }

    /**
     * v4.7.x — #G3 보강 B2: audit_log 에 facade 레벨 호출 기록.
     * 운영팀 / 외부감사인이 "사용자 X 가 언제 어떤 프로필로 livedb 호출했는지" 추적 가능.
     * 실패해도 silent — 호출 흐름에 영향 X.
     */
    private void recordAudit(String operation, DbProfile profile, String username,
                             Integer status, long durationMs) {
        if (auditLogService == null) return;
        try {
            String endpoint = "[livedb] " + operation + " profile=" + profile.getName();
            auditLogService.log(endpoint, "INTERNAL", null, null, status, false, username, durationMs);
        } catch (Exception ignored) {}
    }

    /**
     * v4.7.x — Phase 5: 백그라운드 스레드에서도 username capture 시도.
     * SecurityContext 가 비어 있으면 "(anon)" — RateLimiter 가 같은 키로 묶어 격리.
     */
    private String currentUsername() {
        try {
            org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return "(anon)";
    }

    /**
     * URL prefix 로 DBMS 자동 감지. DbProfile 별 dbType 컬럼은 추후 확장용 — 1차에선
     * URL 만으로 충분 (드라이버 매핑과 동일 분기).
     *
     * <ul>
     *   <li>{@code jdbc:oracle:} 또는 url 안 "oracle" → {@link OracleLiveDbContextProvider}</li>
     *   <li>{@code jdbc:postgresql:} → {@link PostgresLiveDbContextProvider}</li>
     *   <li>그 외 → null (Live DB 첨부 skip)</li>
     * </ul>
     */
    private LiveDbContextProvider pickProvider(DbProfile profile) {
        String url = profile.getUrl();
        if (url == null) return null;
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:oracle:") || lower.contains("oracle:thin:")) {
            return oracleProvider;
        }
        if (lower.startsWith("jdbc:postgresql:")) {
            return postgresProvider;
        }
        return null;
    }

    /**
     * DataSource + ReadOnlyJdbcTemplate 캐시. 한 프로필당 한 번만 생성.
     *
     * <p>주의: 프로필이 *수정* 되면 이 캐시는 stale 해질 수 있음 — Phase 5 에서
     * eviction hook 추가 (DbProfileService.update() 호출 시 invalidate).
     */
    private synchronized ReadOnlyJdbcTemplate getOrCreateJdbc(DbProfile profile) {
        Long id = profile.getId();
        ReadOnlyJdbcTemplate cached = jdbcCache.get(id);
        if (cached != null) return cached;

        DataSource ds = getOrCreateDataSource(profile);
        ReadOnlyJdbcTemplate ro = new ReadOnlyJdbcTemplate(ds, config, profile.getName());
        jdbcCache.put(id, ro);
        return ro;
    }

    private synchronized DataSource getOrCreateDataSource(DbProfile profile) {
        Long id = profile.getId();
        DataSource cached = dataSourceCache.get(id);
        if (cached != null) return cached;

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(profile.getUrl());
        // liveAnalysisUser 가 설정되어 있으면 그 user 사용 — 권한 분리 가능
        String user = profile.getLiveAnalysisUser();
        if (user == null || user.isEmpty()) user = profile.getUsername();
        ds.setUsername(user);
        ds.setPassword(profile.getPassword());

        // 드라이버 자동 감지 (URL prefix 기반)
        String url = profile.getUrl();
        if (url != null) {
            if (url.startsWith("jdbc:oracle:")) {
                ds.setDriverClassName("oracle.jdbc.OracleDriver");
            } else if (url.startsWith("jdbc:postgresql:")) {
                ds.setDriverClassName("org.postgresql.Driver");
            }
            // 다른 DBMS 추가 시 여기에:
            // else if (url.startsWith("jdbc:mysql:")) ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }

        dataSourceCache.put(id, ds);
        return ds;
    }

    /**
     * 캐시된 프로필을 무효화 — DbProfile 이 수정/삭제될 때 호출 (Phase 5 wiring).
     */
    public synchronized void invalidate(Long dbProfileId) {
        dataSourceCache.remove(dbProfileId);
        jdbcCache.remove(dbProfileId);
        log.debug("[LiveDb] invalidated cache for profile {}", dbProfileId);
    }

    /**
     * v4.7.x — #G3 보강 B1: DbProfileService 가 발행하는 변경 이벤트 listener.
     * UPDATE / DELETE / 활성화 토글 모두에서 캐시 invalidate — 변경 후 stale
     * connection / password 사용 사고 방지.
     */
    @org.springframework.context.event.EventListener
    public void onProfileChanged(io.github.claudetoolkit.ui.dbprofile.DbProfileChangedEvent ev) {
        invalidate(ev.getProfileId());
    }
}
