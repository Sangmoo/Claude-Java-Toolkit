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

    public AnalysisCacheService(AnalysisCacheRepository repository) {
        this.repository = repository;
    }

    /**
     * 캐시에서 결과를 조회합니다.
     * @return 캐시된 결과 또는 null (미스/만료)
     */
    @Transactional
    public String get(String feature, String input) {
        if (input == null) return null;
        String key = buildKey(feature, input);
        Optional<AnalysisCache> opt = repository.findByCacheKey(key);
        if (!opt.isPresent()) return null;

        AnalysisCache entry = opt.get();
        if (entry.isExpired()) {
            try { repository.delete(entry); } catch (Exception ignored) {}
            return null;
        }
        entry.incrementHitCount();
        try { repository.save(entry); } catch (Exception ignored) {}
        log.debug("[AnalysisCache] HIT: {} (hits={})", feature, entry.getHitCount());
        return entry.getResultText();
    }

    /**
     * 결과를 캐시에 저장합니다 (기존 키 있으면 덮어쓰기).
     */
    @Transactional
    public void put(String feature, String input, String result) {
        if (result == null || result.isEmpty() || input == null) return;
        String key = buildKey(feature, input);
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

    /**
     * 캐시 통계를 반환합니다.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<String, Object>();
        long total   = repository.count();
        long expired = repository.countByExpiresAtBefore(LocalDateTime.now());
        stats.put("size",        total);
        stats.put("activeSize",  total - expired);
        stats.put("expired",     expired);
        stats.put("ttlMinutes",  TTL_MINUTES);
        stats.put("storage",     "H2 DB");
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

    private String buildKey(String feature, String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(((feature == null ? "" : feature) + "|").getBytes(StandardCharsets.UTF_8));
            md.update(input.getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // fallback: 단순 해시 (SHA-256 실패는 거의 없음)
            return (feature == null ? "" : feature) + "_" + input.hashCode();
        }
    }
}
