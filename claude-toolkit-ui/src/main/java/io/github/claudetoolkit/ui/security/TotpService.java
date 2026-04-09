package io.github.claudetoolkit.ui.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * TOTP (Time-based One-Time Password) 서비스.
 * Google Authenticator 호환 (RFC 6238).
 */
public class TotpService {

    private static final int SECRET_LENGTH = 20;
    private static final int CODE_DIGITS   = 6;
    private static final int TIME_STEP     = 30; // seconds
    private static final int WINDOW        = 1;  // ±1 time step tolerance

    /** 새 시크릿 키 생성 (Base32 인코딩) */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** Google Authenticator용 otpauth:// URI 생성 */
    public static String buildOtpAuthUri(String secret, String username, String issuer) {
        return "otpauth://totp/" + urlEncode(issuer) + ":" + urlEncode(username)
                + "?secret=" + secret + "&issuer=" + urlEncode(issuer) + "&digits=" + CODE_DIGITS;
    }

    /** TOTP 코드 검증 (±1 time step 허용) */
    public static boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) return false;
        try {
            int inputCode = Integer.parseInt(code.trim());
            byte[] key = base32Decode(secret);
            long currentTime = System.currentTimeMillis() / 1000 / TIME_STEP;

            for (int i = -WINDOW; i <= WINDOW; i++) {
                int expected = generateCode(key, currentTime + i);
                if (expected == inputCode) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static int generateCode(byte[] key, long timeCounter) throws Exception {
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (timeCounter & 0xFF);
            timeCounter >>= 8;
        }
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                   | ((hash[offset + 1] & 0xFF) << 16)
                   | ((hash[offset + 2] & 0xFF) << 8)
                   | (hash[offset + 3] & 0xFF);
        return binary % (int) Math.pow(10, CODE_DIGITS);
    }

    // ── Base32 encode/decode (RFC 4648) ──

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_CHARS.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String encoded) {
        encoded = encoded.toUpperCase().replaceAll("[^A-Z2-7]", "");
        byte[] result = new byte[encoded.length() * 5 / 8];
        int buffer = 0, bitsLeft = 0, idx = 0;
        for (char c : encoded.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[idx++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return java.util.Arrays.copyOf(result, idx);
    }

    private static String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }
}
