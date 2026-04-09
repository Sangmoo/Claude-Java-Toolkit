package io.github.claudetoolkit.ui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 분석 결과 인메모리 캐시 서비스.
 *
 * <p>동일 입력에 대한 중복 API 호출을 방지합니다.
 * SHA-256 해시 기반 캐시 키, LRU 방식 최대 200개, TTL 1시간.
 */
@Service
public class AnalysisCacheService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisCacheService.class);
    private static final int MAX_ENTRIES = 200;
    private static final long TTL_MS = 3600_000L; // 1 hour

    // LRU LinkedHashMap (accessOrder=true)
    private final Map<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /**
     * 캐시에서 결과를 조회합니다.
     * @param feature 분석 유형 키
     * @param input   입력 텍스트
     * @return 캐시된 결과 또는 null (미스)
     */
    public synchronized String get(String feature, String input) {
        String key = buildKey(feature, input);
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.createdAt > TTL_MS) {
            cache.remove(key);
            return null;
        }
        entry.hitCount++;
        log.debug("[AnalysisCache] HIT: {} (hits={})", feature, entry.hitCount);
        return entry.result;
    }

    /**
     * 결과를 캐시에 저장합니다.
     */
    public synchronized void put(String feature, String input, String result) {
        if (result == null || result.isEmpty()) return;
        String key = buildKey(feature, input);
        cache.put(key, new CacheEntry(result));
    }

    /**
     * 캐시 통계를 반환합니다.
     */
    public synchronized Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<String, Object>();
        stats.put("size", cache.size());
        stats.put("maxEntries", MAX_ENTRIES);
        stats.put("ttlMinutes", TTL_MS / 60000);
        // 만료된 항목 정리
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        int expired = 0;
        while (it.hasNext()) {
            if (now - it.next().getValue().createdAt > TTL_MS) {
                it.remove();
                expired++;
            }
        }
        stats.put("expired", expired);
        stats.put("activeSize", cache.size());
        return stats;
    }

    /**
     * 캐시를 전체 초기화합니다.
     */
    public synchronized void clear() {
        cache.clear();
        log.info("[AnalysisCache] 캐시 전체 초기화");
    }

    // ── private helpers ──

    private String buildKey(String feature, String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((feature + "|").getBytes(StandardCharsets.UTF_8));
            md.update(input.getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // fallback: simple hash
            return feature + "_" + input.hashCode();
        }
    }

    private static class CacheEntry {
        final String result;
        final long createdAt;
        int hitCount;

        CacheEntry(String result) {
            this.result    = result;
            this.createdAt = System.currentTimeMillis();
            this.hitCount  = 0;
        }
    }
}
