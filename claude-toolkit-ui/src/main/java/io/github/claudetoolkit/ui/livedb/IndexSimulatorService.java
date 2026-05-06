package io.github.claudetoolkit.ui.livedb;

import io.github.claudetoolkit.ui.dbprofile.DbProfile;
import io.github.claudetoolkit.ui.dbprofile.DbProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.7.x — #G3 Live DB Phase 4: 인덱스 시뮬레이션 facade.
 *
 * <p>{@link LiveDbContextService} 와 동일한 패턴 — DbProfile 검증 + DataSource 캐시
 * + DBMS-별 simulator 라우팅. 단 *한 가지 큰 차이*: 시뮬레이터는 DDL (CREATE/DROP INDEX)
 * 을 실행해야 하므로 {@link ReadOnlyJdbcTemplate} 가 *아닌* 일반 {@link DataSource} 사용.
 *
 * <p>안전성 보장은 {@link OracleIndexSimulator} 내부의 다음 정책에 위임:
 * <ul>
 *   <li>인덱스 이름 prefix 강제 (CTK_SIM_*)</li>
 *   <li>INVISIBLE 옵션 강제</li>
 *   <li>최대 5개 인덱스</li>
 *   <li>try/finally cleanup</li>
 * </ul>
 */
@Service
public class IndexSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(IndexSimulatorService.class);

    private final DbProfileService     profileService;
    private final LiveDbConfig         config;
    private final OracleIndexSimulator oracleSimulator = new OracleIndexSimulator();

    /** v4.7.x — Phase 5: 운영 가드 — 옵셔널 (테스트 빈 미등록 허용) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LiveDbRateLimiter rateLimiter;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LiveDbCircuitBreaker circuitBreaker;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LiveDbCallStats callStats;
    /** v4.7.x — #G3 보강 B2: 외부감사 추적용 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.claudetoolkit.ui.security.AuditLogService auditLogService;

    /** 프로필 ID → DataSource 캐시 — Live DB connection pool 과 분리 (DDL 가능) */
    private final Map<Long, DataSource> dataSourceCache = new HashMap<Long, DataSource>();

    public IndexSimulatorService(DbProfileService profileService, LiveDbConfig config) {
        this.profileService = profileService;
        this.config         = config;
    }

    /**
     * 인덱스 시뮬레이션 — feature flag + 프로필 검증 후 DBMS-별 simulator 호출.
     *
     * @return 시뮬 결과. 비활성/실패 시 null
     */
    public IndexSimulationResult simulate(String userSql, List<String> indexDefs, Long dbProfileId) {
        if (!config.isEnabled()) {
            log.debug("[IndexSimulator] Disabled — skipping");
            return null;
        }
        if (dbProfileId == null) return null;

        DbProfile profile = profileService.findById(dbProfileId);
        if (profile == null) {
            log.warn("[IndexSimulator] Profile {} not found", dbProfileId);
            return null;
        }
        if (!profile.isReadOnlyForLiveAnalysis()) {
            log.warn("[IndexSimulator] Profile '{}' is not enabled for live analysis", profile.getName());
            return null;
        }

        IndexSimulator simulator = pickSimulator(profile);
        if (simulator == null) {
            log.warn("[IndexSimulator] No simulator for profile '{}' (Oracle 만 지원)", profile.getName());
            IndexSimulationResult err = new IndexSimulationResult();
            err.setUserSql(userSql);
            err.addWarning("이 DBMS 는 인덱스 시뮬레이션 미지원 — Oracle 만 가능");
            return err;
        }

        // v4.7.x — Phase 5: 운영 가드. 시뮬레이션은 Live DB 보다 비용이 더 크므로
        // (DDL 실행) 동일 가드를 적용하되 별도 quota 가 아닌 같은 풀을 사용.
        if (circuitBreaker != null && circuitBreaker.isOpen(profile.getId())) {
            IndexSimulationResult err = new IndexSimulationResult();
            err.setUserSql(userSql);
            err.addWarning("프로필 일시 비활성 (회로차단기 작동 중) — "
                    + circuitBreaker.secondsUntilHalfOpen(profile.getId()) + "초 후 자동 복구");
            return err;
        }
        String username = currentUsername();
        if (rateLimiter != null && !rateLimiter.tryAcquire(username, profile.getId())) {
            IndexSimulationResult err = new IndexSimulationResult();
            err.setUserSql(userSql);
            err.addWarning("Live DB 호출 분당 한도 초과 — 잠시 후 재시도");
            return err;
        }

        DataSource ds = getOrCreateDataSource(profile);
        long t0 = System.currentTimeMillis();
        Integer status = 200;
        try {
            IndexSimulationResult result = simulator.simulate(userSql, indexDefs, ds);
            if (callStats != null) callStats.recordSuccess(profile.getId(), System.currentTimeMillis() - t0);
            return result;
        } catch (org.springframework.dao.QueryTimeoutException e) {
            if (callStats != null) callStats.recordTimeout(profile.getId(), System.currentTimeMillis() - t0);
            status = 504;
            throw e;
        } catch (RuntimeException e) {
            if (callStats != null) callStats.recordFailure(profile.getId(), System.currentTimeMillis() - t0);
            status = 500;
            throw e;
        } finally {
            // v4.7.x — #G3 보강 B2: facade 레벨 audit
            recordAudit("livedb.simulate-index", profile, username, status,
                        System.currentTimeMillis() - t0, indexDefs.size());
        }
    }

    private void recordAudit(String operation, DbProfile profile, String username,
                             Integer status, long durationMs, int indexCount) {
        if (auditLogService == null) return;
        try {
            String endpoint = "[livedb] " + operation + " profile=" + profile.getName()
                    + " indexes=" + indexCount;
            auditLogService.log(endpoint, "INTERNAL", null, null, status, false, username, durationMs);
        } catch (Exception ignored) {}
    }

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

    private IndexSimulator pickSimulator(DbProfile profile) {
        String url = profile.getUrl();
        if (url == null) return null;
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:oracle:") || lower.contains("oracle:thin:")) {
            return oracleSimulator;
        }
        // PostgreSQL 의 HypoPG 는 별도 extension 필요 — 1차 출시 미지원
        return null;
    }

    private synchronized DataSource getOrCreateDataSource(DbProfile profile) {
        Long id = profile.getId();
        DataSource cached = dataSourceCache.get(id);
        if (cached != null) return cached;

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(profile.getUrl());
        // liveAnalysisUser 가 있어도 시뮬레이션은 *DDL 가능* user 가 필요 — 기본 username 사용
        ds.setUsername(profile.getUsername());
        ds.setPassword(profile.getPassword());
        if (profile.getUrl() != null && profile.getUrl().startsWith("jdbc:oracle:")) {
            ds.setDriverClassName("oracle.jdbc.OracleDriver");
        }
        dataSourceCache.put(id, ds);
        return ds;
    }

    public synchronized void invalidate(Long dbProfileId) {
        dataSourceCache.remove(dbProfileId);
        log.debug("[IndexSimulator] invalidated cache for profile {}", dbProfileId);
    }

    /**
     * v4.7.x — #G3 보강 B1: DbProfileService 변경 이벤트 listener.
     */
    @org.springframework.context.event.EventListener
    public void onProfileChanged(io.github.claudetoolkit.ui.dbprofile.DbProfileChangedEvent ev) {
        invalidate(ev.getProfileId());
    }
}
