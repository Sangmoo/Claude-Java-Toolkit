package io.github.claudetoolkit.ui.security;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * API 키 인증 및 Settings 비밀번호 잠금 설정.
 * ~/.claude-toolkit/security-settings.json 에 저장됩니다.
 *
 * <p>spring-security-crypto의 BCryptPasswordEncoder만 사용합니다.
 * spring-boot-starter-security는 사용하지 않습니다 (전체 엔드포인트 잠금 방지).
 */
public class SecuritySettings {

    private static final Path FILE = Paths.get(
            System.getProperty("user.home"), ".claude-toolkit", "security-settings.json");

    /** API 키 인증 활성화 여부 */
    private boolean apiKeyEnabled = false;

    /** BCrypt 해시된 API 키 (null = 미설정) */
    private String apiKeyHash = null;

    /** Settings 비밀번호 잠금 활성화 여부 */
    private boolean settingsLockEnabled = false;

    /** BCrypt 해시된 Settings 비밀번호 (null = 미설정) */
    private String settingsPasswordHash = null;

    /** 설치 마법사 완료 여부 */
    private boolean setupCompleted = false;

    // ── getters / setters ────────────────────────────────────────────────────

    public boolean isApiKeyEnabled()          { return apiKeyEnabled; }
    public void    setApiKeyEnabled(boolean v) { this.apiKeyEnabled = v; }

    public String  getApiKeyHash()            { return apiKeyHash; }
    public void    setApiKeyHash(String h)    { this.apiKeyHash = h; }

    public boolean isSettingsLockEnabled()          { return settingsLockEnabled; }
    public void    setSettingsLockEnabled(boolean v) { this.settingsLockEnabled = v; }

    public String  getSettingsPasswordHash()       { return settingsPasswordHash; }
    public void    setSettingsPasswordHash(String h){ this.settingsPasswordHash = h; }

    public boolean isSetupCompleted()              { return setupCompleted; }
    public void    setSetupCompleted(boolean v)     { this.setupCompleted = v; }

    // ── persistence ─────────────────────────────────────────────────────────

    public static SecuritySettings load() {
        SecuritySettings s = new SecuritySettings();
        if (!Files.exists(FILE)) return s;
        try {
            String json = new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8).trim();
            s.apiKeyEnabled        = getBool(json,   "apiKeyEnabled",        false);
            s.apiKeyHash           = getString(json, "apiKeyHash",           null);
            s.settingsLockEnabled  = getBool(json,   "settingsLockEnabled",  false);
            s.settingsPasswordHash = getString(json, "settingsPasswordHash", null);
            s.setupCompleted       = getBool(json,   "setupCompleted",       false);
        } catch (Exception e) {
            System.err.println("[SecuritySettings] load failed: " + e.getMessage());
        }
        return s;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            StringBuilder sb = new StringBuilder("{\n");
            sb.append("  \"apiKeyEnabled\": ").append(apiKeyEnabled).append(",\n");
            sb.append("  \"apiKeyHash\": ").append(jsonStr(apiKeyHash)).append(",\n");
            sb.append("  \"settingsLockEnabled\": ").append(settingsLockEnabled).append(",\n");
            sb.append("  \"settingsPasswordHash\": ").append(jsonStr(settingsPasswordHash)).append(",\n");
            sb.append("  \"setupCompleted\": ").append(setupCompleted).append("\n");
            sb.append("}");
            Files.write(FILE, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[SecuritySettings] save failed: " + e.getMessage());
        }
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────

    private static boolean getBool(String json, String key, boolean def) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0) return def;
        int c = json.indexOf(':', i);
        if (c < 0) return def;
        String rest = json.substring(c + 1).trim();
        if (rest.startsWith("true"))  return true;
        if (rest.startsWith("false")) return false;
        return def;
    }

    private static String getString(String json, String key, String def) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0) return def;
        int c = json.indexOf(':', i);
        if (c < 0) return def;
        String rest = json.substring(c + 1).trim();
        if (rest.startsWith("null")) return null;
        if (!rest.startsWith("\""))  return def;
        int s = rest.indexOf('"') + 1;
        int e = rest.indexOf('"', s);
        if (e < 0) return def;
        return rest.substring(s, e)
                   .replace("\\\"", "\"")
                   .replace("\\\\", "\\")
                   .replace("\\n", "\n");
    }

    private static String jsonStr(String v) {
        if (v == null) return "null";
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
