package io.github.claudetoolkit.ui.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TotpService 단위 테스트 — RFC 6238 TOTP 알고리즘 + Base32 codec 검증.
 */
class TotpServiceTest {

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    // ── generateSecret ────────────────────────────────────────────────────

    @Test
    @DisplayName("generateSecret — 비어있지 않은 Base32 문자열 반환")
    void generateSecret_returnsNonEmpty() {
        String secret = TotpService.generateSecret();
        assertNotNull(secret);
        assertFalse(secret.isEmpty());
    }

    @Test
    @DisplayName("generateSecret — 유효한 Base32 문자만 포함")
    void generateSecret_containsOnlyBase32Chars() {
        String secret = TotpService.generateSecret();
        for (char c : secret.toCharArray()) {
            assertTrue(BASE32_CHARS.indexOf(c) >= 0,
                    "유효하지 않은 Base32 문자: " + c);
        }
    }

    @RepeatedTest(5)
    @DisplayName("generateSecret — 반복 호출 시 서로 다른 값 생성")
    void generateSecret_isUnique() {
        Set<String> secrets = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            secrets.add(TotpService.generateSecret());
        }
        assertTrue(secrets.size() > 1, "10번 중 최소 2개는 달라야 함");
    }

    // ── base32 encode/decode round-trip ───────────────────────────────────

    @Test
    @DisplayName("base32Encode → base32Decode 왕복 변환")
    void base32_roundTrip() {
        byte[] original = "Hello, World! 안녕".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String encoded = TotpService.base32Encode(original);
        byte[] decoded = TotpService.base32Decode(encoded);
        assertArrayEquals(original, decoded, "Base32 인코딩 → 디코딩 결과가 원본과 달라야 함");
    }

    @Test
    @DisplayName("base32Decode — 소문자/공백 포함된 시크릿도 정상 처리")
    void base32Decode_toleratesLowerCaseAndSpaces() {
        String secret = TotpService.generateSecret();
        byte[] decodedUpper = TotpService.base32Decode(secret);
        byte[] decodedLower = TotpService.base32Decode(secret.toLowerCase());
        assertArrayEquals(decodedUpper, decodedLower, "대소문자 무관하게 동일한 결과");
    }

    // ── verifyCode ────────────────────────────────────────────────────────

    @Test
    @DisplayName("verifyCode — null 시크릿이면 false")
    void verifyCode_nullSecret_returnsFalse() {
        assertFalse(TotpService.verifyCode(null, "123456"));
    }

    @Test
    @DisplayName("verifyCode — null 코드이면 false")
    void verifyCode_nullCode_returnsFalse() {
        String secret = TotpService.generateSecret();
        assertFalse(TotpService.verifyCode(secret, null));
    }

    @Test
    @DisplayName("verifyCode — 숫자가 아닌 코드이면 false")
    void verifyCode_nonNumericCode_returnsFalse() {
        String secret = TotpService.generateSecret();
        assertFalse(TotpService.verifyCode(secret, "ABCDEF"));
        assertFalse(TotpService.verifyCode(secret, "12-456"));
        assertFalse(TotpService.verifyCode(secret, ""));
    }

    @Test
    @DisplayName("verifyCode — 잘못된 6자리 숫자 코드는 false (통계적으로 확실)")
    void verifyCode_wrongCode_returnsFalse() {
        String secret = TotpService.generateSecret();
        // 000000은 실제 코드일 확률이 1/1000000 — 테스트 통과에 충분히 낮음
        assertFalse(TotpService.verifyCode(secret, "000000"));
    }

    // ── buildOtpAuthUri ───────────────────────────────────────────────────

    @Test
    @DisplayName("buildOtpAuthUri — otpauth://totp/ 형식 URI 반환")
    void buildOtpAuthUri_format() {
        String secret = TotpService.generateSecret();
        String uri = TotpService.buildOtpAuthUri(secret, "testuser", "TestIssuer");

        assertTrue(uri.startsWith("otpauth://totp/"), "URI가 otpauth://totp/ 로 시작해야 함");
        assertTrue(uri.contains("secret=" + secret), "URI에 secret 파라미터 포함");
        assertTrue(uri.contains("issuer="), "URI에 issuer 파라미터 포함");
        assertTrue(uri.contains("digits=6"), "URI에 6자리 digits 설정");
    }
}
