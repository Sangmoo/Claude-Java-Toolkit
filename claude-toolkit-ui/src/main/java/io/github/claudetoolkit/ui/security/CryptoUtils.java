package io.github.claudetoolkit.ui.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-CBC 기반 암호화/복호화 유틸리티.
 *
 * <p>암호화 키는 {@code ~/.claude-toolkit/.encryption-key} 파일에 저장되며
 * 최초 실행 시 자동 생성됩니다. JDK 1.8 호환.
 *
 * <p>암호문 형식: {@code ENC(Base64(iv + ciphertext))}
 */
public final class CryptoUtils {

    private static final String ALGORITHM   = "AES/CBC/PKCS5Padding";
    private static final String PREFIX      = "ENC(";
    private static final String SUFFIX      = ")";
    private static final int    KEY_SIZE    = 128;   // JDK 1.8 기본 정책에서 128비트 보장
    private static final int    IV_SIZE     = 16;

    private static final String KEY_DIR  =
            System.getProperty("user.home") + File.separator + ".claude-toolkit";
    private static final String KEY_FILE = KEY_DIR + File.separator + ".encryption-key";

    private static volatile byte[] cachedKey;

    private CryptoUtils() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * 평문을 AES 암호화하여 {@code ENC(Base64...)} 형식으로 반환합니다.
     * null이거나 빈 문자열이면 그대로 반환합니다.
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        try {
            byte[] key = getOrCreateKey();
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // iv + encrypted → Base64
            byte[] combined = new byte[IV_SIZE + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_SIZE);
            System.arraycopy(encrypted, 0, combined, IV_SIZE, encrypted.length);

            return PREFIX + base64Encode(combined) + SUFFIX;
        } catch (Exception e) {
            throw new RuntimeException("암호화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * {@code ENC(Base64...)} 형식의 암호문을 복호화합니다.
     * ENC() 형식이 아니면 평문으로 간주하여 그대로 반환합니다.
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return cipherText;
        if (!isEncrypted(cipherText)) return cipherText;
        try {
            String base64 = cipherText.substring(PREFIX.length(), cipherText.length() - SUFFIX.length());
            byte[] combined = base64Decode(base64);

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_SIZE);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_SIZE, combined.length);

            byte[] key = getOrCreateKey();
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 복호화 실패 → 평문으로 간주 (기존 데이터 마이그레이션 호환)
            return cipherText;
        }
    }

    /**
     * ENC() 형식인지 확인합니다.
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }

    /**
     * 평문이면 암호화하고, 이미 암호화된 값이면 그대로 반환합니다.
     * 기존 평문 → 암호문 자동 마이그레이션에 사용합니다.
     */
    public static String ensureEncrypted(String value) {
        if (value == null || value.isEmpty()) return value;
        if (isEncrypted(value)) return value;
        return encrypt(value);
    }

    // ── Key Management ────────────────────────────────────────────────────────

    private static byte[] getOrCreateKey() throws Exception {
        if (cachedKey != null) return cachedKey;
        synchronized (CryptoUtils.class) {
            if (cachedKey != null) return cachedKey;

            File keyFile = new File(KEY_FILE);
            if (keyFile.exists()) {
                cachedKey = readKeyFile(keyFile);
            } else {
                cachedKey = generateAndSaveKey(keyFile);
            }
            return cachedKey;
        }
    }

    private static byte[] generateAndSaveKey(File keyFile) throws Exception {
        File dir = new File(KEY_DIR);
        if (!dir.exists()) dir.mkdirs();

        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(KEY_SIZE, new SecureRandom());
        SecretKey secretKey = keygen.generateKey();
        byte[] keyBytes = secretKey.getEncoded();

        // Base64로 파일 저장
        FileOutputStream fos = new FileOutputStream(keyFile);
        try {
            fos.write(base64Encode(keyBytes).getBytes(StandardCharsets.UTF_8));
        } finally {
            fos.close();
        }
        return keyBytes;
    }

    private static byte[] readKeyFile(File keyFile) throws Exception {
        FileInputStream fis = new FileInputStream(keyFile);
        try {
            byte[] buf = new byte[(int) keyFile.length()];
            int read = fis.read(buf);
            String base64 = new String(buf, 0, read, StandardCharsets.UTF_8).trim();
            return base64Decode(base64);
        } finally {
            fis.close();
        }
    }

    // ── Base64 (JDK 1.8 호환) ─────────────────────────────────────────────────

    private static final char[] BASE64_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    static String base64Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < data.length) {
            int b0 = data[i++] & 0xff;
            if (i == data.length) {
                sb.append(BASE64_CHARS[b0 >> 2]);
                sb.append(BASE64_CHARS[(b0 & 0x3) << 4]);
                sb.append("==");
            } else {
                int b1 = data[i++] & 0xff;
                if (i == data.length) {
                    sb.append(BASE64_CHARS[b0 >> 2]);
                    sb.append(BASE64_CHARS[((b0 & 0x3) << 4) | (b1 >> 4)]);
                    sb.append(BASE64_CHARS[(b1 & 0xf) << 2]);
                    sb.append('=');
                } else {
                    int b2 = data[i++] & 0xff;
                    sb.append(BASE64_CHARS[b0 >> 2]);
                    sb.append(BASE64_CHARS[((b0 & 0x3) << 4) | (b1 >> 4)]);
                    sb.append(BASE64_CHARS[((b1 & 0xf) << 2) | (b2 >> 6)]);
                    sb.append(BASE64_CHARS[b2 & 0x3f]);
                }
            }
        }
        return sb.toString();
    }

    private static final int[] BASE64_DECODE = new int[128];
    static {
        Arrays.fill(BASE64_DECODE, -1);
        for (int i = 0; i < BASE64_CHARS.length; i++) BASE64_DECODE[BASE64_CHARS[i]] = i;
        BASE64_DECODE['='] = 0;
    }

    static byte[] base64Decode(String s) {
        s = s.replaceAll("\\s", "");
        int len = s.length();
        int outLen = len * 3 / 4;
        if (s.endsWith("==")) outLen -= 2;
        else if (s.endsWith("=")) outLen -= 1;
        byte[] out = new byte[outLen];
        int j = 0;
        for (int i = 0; i < len; i += 4) {
            int c0 = BASE64_DECODE[s.charAt(i)];
            int c1 = BASE64_DECODE[s.charAt(i + 1)];
            int c2 = (i + 2 < len) ? BASE64_DECODE[s.charAt(i + 2)] : 0;
            int c3 = (i + 3 < len) ? BASE64_DECODE[s.charAt(i + 3)] : 0;
            if (j < outLen) out[j++] = (byte) ((c0 << 2) | (c1 >> 4));
            if (j < outLen) out[j++] = (byte) ((c1 << 4) | (c2 >> 2));
            if (j < outLen) out[j++] = (byte) ((c2 << 6) | c3);
        }
        return out;
    }
}
