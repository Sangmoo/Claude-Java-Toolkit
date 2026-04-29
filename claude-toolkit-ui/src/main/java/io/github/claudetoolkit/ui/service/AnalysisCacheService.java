package io.github.claudetoolkit.ui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 분석 결과 캐시 서비스 (v2.7.0 DB 영속화).
 *
 * <p>동일 입력에 대한 중복 Claude API 호출을 방지합니다.
 * H2 테이블 ({@code analysis_cache})에 저장되어 서버 재시작 후에도 유지됩니다.
 *
 * <ul>
 *   <li>SHA-256 해시 기반 캐시 키</li>
 *   <li>TTL: 1시간</li>
 *   <li>만료 캐시 정리: 매 시간 스케줄</li>
 * </ul>
 */
@Service
public class AnalysisCacheService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisCacheService.class);
    private static final long TTL_MINUTES = 60L;

    private final AnalysisCacheRepository repository;

    /** v4.4.0 — 캐시 히트/미스 메트릭 (옵셔널 — 없어도 정상 동작) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.claudetoolkit.ui.metrics.ToolkitMetrics metrics;

    /**
     * v4.7.x — JVM 인스턴스 라이프타임 동안의 hit/miss 카운터.
     * Prometheus 메트릭과 별개로, getStats() 에 *현재 hit rate* 를 즉시 포함시키기 위함.
     * 서버 재시작 시 0 으로 리셋 (영구 보관할 가치 없음 — 개발/모니터링 용도).
     */
    private final AtomicLong hitCount  = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public AnalysisCacheService(AnalysisCacheRepository repository) {
        this.repository = repository;
    }

    /**
     * 캐시에서 결과를 조회합니다 (단순 키, 기존 호출 호환).
     * @return 캐시된 결과 또는 null (미스/만료)
     */
    @Transactional
    public String get(String feature, String input) {
        return get(feature, input, null, null);
    }

    /**
     * v4.7.x — 분석 옵션까지 포함한 캐시 키로 조회.
     *
     * <p>같은 SQL 도 {@code reviewType=review} vs {@code security} 일 때 *다른 결과*
     * 를 받아야 하므로 캐시 키도 분리되어야 한다 (이전엔 충돌 버그 있었음).
     *
     * @param input2     보조 입력 (예: EXPLAIN PLAN 본문). null/empty 가능
     * @param sourceType 분석 옵션 (예: 'review' / 'security' / 'java' / 'sql'). null/empty 가능
     */
    @Transactional
    public String get(String feature, String input, String input2, String sourceType) {
        if (input == null) {
            recordMiss();
            return null;
        }
        String key = buildKey(feature, normalize(input), normalize(input2), normalize(sourceType));
        Optional<AnalysisCache> opt = repository.findByCacheKey(key);
        if (!opt.isPresent()) {
            recordMiss();
            return null;
        }

        AnalysisCache entry = opt.get();
        if (entry.isExpired()) {
            try { repository.delete(entry); } catch (Exception ignored) {}
            recordMiss();
            return null;
        }
        entry.incrementHitCount();
        try { repository.save(entry); } catch (Exception ignored) {}
        log.debug("[AnalysisCache] HIT: {} (hits={})", feature, entry.getHitCount());
        recordHit();
        return entry.getResultText();
    }

    /** 단순 키 저장 (기존 호환) */
    @Transactional
    public void put(String feature, String input, String result) {
        put(feature, input, null, null, result);
    }

    /**
     * v4.7.x — 분석 옵션까지 포함한 키로 저장.
     */
    @Transactional
    public void put(String feature, String input, String input2, String sourceType, String result) {
        if (result == null || result.isEmpty() || input == null) return;
        String key = buildKey(feature, normalize(input), normalize(input2), normalize(sourceType));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusMinutes(TTL_MINUTES);

        Optional<AnalysisCache> existing = repository.findByCacheKey(key);
        try {
            if (existing.isPresent()) {
                AnalysisCache c = existing.get();
                c.setResultText(result);
                c.setExpiresAt(expires);
                c.setHitCount(0);
                repository.save(c);
            } else {
                repository.save(new AnalysisCache(key, feature, result, now, expires));
            }
        } catch (Exception e) {
            log.warn("[AnalysisCache] save failed: {}", e.getMessage());
        }
    }

    private void recordHit() {
        hitCount.incrementAndGet();
        if (metrics != null) metrics.recordCacheHit("analysis");
    }

    private void recordMiss() {
        missCount.incrementAndGet();
        if (metrics != null) metrics.recordCacheMiss("analysis");
    }

    /**
     * 캐시 통계를 반환합니다.
     * v4.7.x — JVM 라이프타임 hit/miss 카운터 + hit rate (%) 포함.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<String, Object>();
        long total   = repository.count();
        long expired = repository.countByExpiresAtBefore(LocalDateTime.now());
        long hits    = hitCount.get();
        long misses  = missCount.get();
        long lookups = hits + misses;
        stats.put("size",        total);
        stats.put("activeSize",  total - expired);
        stats.put("expired",     expired);
        stats.put("ttlMinutes",  TTL_MINUTES);
        stats.put("storage",     "H2 DB");
        stats.put("hits",        hits);
        stats.put("misses",      misses);
        stats.put("hitRatePercent",
                lookups > 0 ? Math.round(100.0 * hits / lookups * 10) / 10.0 : 0.0);
        return stats;
    }

    /**
     * 캐시를 전체 초기화합니다.
     */
    @Transactional
    public void clear() {
        repository.deleteAll();
        log.info("[AnalysisCache] 캐시 전체 초기화");
    }

    /** 매 시간 정각에 만료된 캐시 엔트리 정리 */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void cleanupExpired() {
        try {
            int deleted = repository.deleteExpired(LocalDateTime.now());
            if (deleted > 0) {
                log.debug("[AnalysisCache] 만료 캐시 {}건 정리", deleted);
            }
        } catch (Exception e) {
            log.warn("[AnalysisCache] cleanup 실패: {}", e.getMessage());
        }
    }

    // ── private helpers ──

    /**
     * v4.7.x — 정규화: 같은 의미의 입력은 같은 캐시 키로 매핑.
     *  - UTF-8 BOM 제거
     *  - 양 끝 공백 trim
     *  - CRLF / CR → LF 통일
     *  - 라인 끝 trailing 공백 제거
     *  - 연속 빈 줄 (3개+) 은 2개로 축약
     * 의미가 보존되는 *cosmetic* 변경만 정규화하므로 캐시 의도된 동작에 영향 X.
     */
    /** UTF-8 BOM (U+FEFF) — 일부 에디터가 파일 앞에 추가. 정규화 시 제거. */
    private static final char BOM_CHAR = 0xFEFF;

    private static String normalize(String s) {
        if (s == null) return "";
        if (!s.isEmpty() && s.charAt(0) == BOM_CHAR) s = s.substring(1);
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        s = s.trim();
        // 라인 끝 trailing 공백 제거
        s = s.replaceAll("[ \\t]+\\n", "\n");
        // 연속 빈 줄 축약
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s;
    }

    /**
     * v4.7.x — 4개 컴포넌트 (feature + input + input2 + sourceType) 모두 SHA-256 으로 해싱.
     * 각 컴포넌트는 {@code |} (파이프) 로 분리되어 해시 입력에 들어가므로,
     * 단일 입력에 우연히 파이프 문자가 있어도 다른 컴포넌트와 충돌하지 않음.
     */
    private String buildKey(String feature, String input, String input2, String sourceType) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(((feature    == null ? "" : feature)    + "").getBytes(StandardCharsets.UTF_8));
            md.update(((input      == null ? "" : input)      + "").getBytes(StandardCharsets.UTF_8));
            md.update(((input2     == null ? "" : input2)     + "").getBytes(StandardCharsets.UTF_8));
            md.update(((sourceType == null ? "" : sourceType) + "").getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // fallback: 단순 해시 (SHA-256 실패는 거의 없음)
            return (feature == null ? "" : feature) + "_"
                    + (input == null ? "" : input).hashCode() + "_"
                    + (input2 == null ? "" : input2).hashCode() + "_"
                    + (sourceType == null ? "" : sourceType).hashCode();
        }
    }
}
