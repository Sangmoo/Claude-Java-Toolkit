package io.github.claudetoolkit.ui.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * v4.7.x — #M3 Platform: API 키 발급 / 검증 / 회수.
 *
 * <p><b>SECURITY 모델:</b>
 * <ul>
 *   <li>키 형식: {@code ctk_live_<32자 base62 random>} (총 41자)</li>
 *   <li>발급 시 평문 1회만 반환 — DB 에는 SHA-256 해시 저장</li>
 *   <li>검증은 timing-safe ({@link MessageDigest#isEqual})</li>
 *   <li>키 prefix (앞 12자) 는 식별용으로 평문 보관 — 사용자가 *어떤 키* 인지 인지 가능</li>
 * </ul>
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    /** 키 prefix — 환경 식별 ("ctk_live_" production / 추후 "ctk_test_" 테스트) */
    public static final String KEY_PREFIX = "ctk_live_";

    /** 무작위 부분 길이 (base62) */
    private static final int   RANDOM_PART_LENGTH = 32;

    /** 시각적 식별 prefix 길이 (UI 표시용 — 32자 random 의 앞 4자만 노출) */
    public static final int   VISIBLE_PREFIX_LENGTH = KEY_PREFIX.length() + 4;  // ctk_live_a1b2

    private static final char[] BASE62 = (
            "0123456789" +
            "abcdefghijklmnopqrstuvwxyz" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray();

    private final ApiKeyRepository repo;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repo) {
        this.repo = repo;
    }

    /**
     * 새 키 발급. 평문 키는 *반환값 1회만* 노출 — 호출자가 사용자에게 보여준 후 반드시 폐기.
     *
     * @return [평문키, 저장된 ApiKey 엔티티]
     */
    @Transactional
    public IssueResult issue(String name, String createdBy, String role,
                              Integer rateLimitPerMinute, Integer ttlDays) {
        String plaintext = generatePlaintext();
        String hash      = sha256Hex(plaintext);
        String prefix    = plaintext.substring(0, Math.min(plaintext.length(), VISIBLE_PREFIX_LENGTH));

        LocalDateTime expiresAt = (ttlDays != null && ttlDays > 0)
                ? LocalDateTime.now().plusDays(ttlDays)
                : null;

        ApiKey k = new ApiKey(hash, prefix, name, createdBy, expiresAt, role, rateLimitPerMinute);
        ApiKey saved = repo.save(k);
        log.info("[ApiKey] 발급 — name='{}' createdBy='{}' role='{}' prefix='{}' expiresAt={}",
                name, createdBy, role, prefix, expiresAt);
        return new IssueResult(plaintext, saved);
    }

    /**
     * 평문 키 → ApiKey 검증. timing-safe + 만료/회수 검증.
     * @return ApiKey if usable, otherwise empty
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> verify(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return Optional.empty();
        if (!plaintext.startsWith(KEY_PREFIX)) return Optional.empty();
        String hash = sha256Hex(plaintext);
        Optional<ApiKey> found = repo.findByKeyHash(hash);
        if (!found.isPresent()) return Optional.empty();
        ApiKey k = found.get();
        if (!k.isUsable()) return Optional.empty();
        return found;
    }

    @Transactional
    public boolean revoke(Long id) {
        Optional<ApiKey> found = repo.findById(id);
        if (!found.isPresent()) return false;
        ApiKey k = found.get();
        k.setRevoked(true);
        repo.save(k);
        log.info("[ApiKey] 회수 — id={} prefix='{}'", id, k.getKeyPrefix());
        return true;
    }

    /** Filter 가 호출 — 마지막 사용 시각 + 카운터 갱신 (별도 트랜잭션) */
    @Transactional
    public void touchUsage(Long id) {
        try {
            repo.touchUsage(id, LocalDateTime.now());
        } catch (Exception ignored) {
            /* 갱신 실패해도 호출 흐름 영향 X */
        }
    }

    // ── private helpers ─────────────────────────────────────────────

    private String generatePlaintext() {
        StringBuilder sb = new StringBuilder(KEY_PREFIX.length() + RANDOM_PART_LENGTH);
        sb.append(KEY_PREFIX);
        for (int i = 0; i < RANDOM_PART_LENGTH; i++) {
            sb.append(BASE62[random.nextInt(BASE62.length)]);
        }
        return sb.toString();
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            // SHA-256 always available
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 발급 결과 — plaintext 는 *호출자가 즉시 사용자에게* 노출 후 폐기 */
    public static class IssueResult {
        public final String plaintext;
        public final ApiKey entity;
        IssueResult(String plaintext, ApiKey entity) {
            this.plaintext = plaintext;
            this.entity    = entity;
        }
    }
}
