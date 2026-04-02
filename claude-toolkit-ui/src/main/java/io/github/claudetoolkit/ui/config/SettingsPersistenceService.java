package io.github.claudetoolkit.ui.config;

import io.github.claudetoolkit.starter.properties.ClaudeProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Persists {@link ToolkitSettings} to a JSON file so settings survive server restarts.
 *
 * <p>File location: {@code ${user.home}/.claude-toolkit/settings.json}
 *
 * <p>Hand-written JSON — no external library dependency (JDK 1.8 compatible).
 */
@Service
public class SettingsPersistenceService {

    private static final String SETTINGS_DIR  =
            System.getProperty("user.home") + File.separator + ".claude-toolkit";
    private static final String SETTINGS_FILE = SETTINGS_DIR + File.separator + "settings.json";

    private final ToolkitSettings  settings;
    private final ClaudeProperties claudeProperties;

    public SettingsPersistenceService(ToolkitSettings settings, ClaudeProperties claudeProperties) {
        this.settings         = settings;
        this.claudeProperties = claudeProperties;
        load();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void save() {
        try {
            File dir = new File(SETTINGS_DIR);
            if (!dir.exists()) dir.mkdirs();

            String json = toJson(settings);
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(SETTINGS_FILE), StandardCharsets.UTF_8));
            try {
                writer.write(json);
            } finally {
                writer.close();
            }
        } catch (Exception e) {
            System.err.println("[SettingsPersistence] save failed: " + e.getMessage());
        }
    }

    public void load() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) return;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            } finally {
                reader.close();
            }
            applyJson(sb.toString(), settings);
        } catch (Exception e) {
            System.err.println("[SettingsPersistence] load failed: " + e.getMessage());
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private String toJson(ToolkitSettings s) {
        return "{\n" +
               "  \"dbUrl\": "          + quoted(s.getDb().getUrl())            + ",\n" +
               "  \"dbUsername\": "     + quoted(s.getDb().getUsername())       + ",\n" +
               "  \"dbPassword\": "     + quoted(s.getDb().getPassword())       + ",\n" +
               "  \"scanPath\": "       + quoted(s.getProject().getScanPath())  + ",\n" +
               "  \"projectContext\": " + quoted(s.getProjectContext())         + ",\n" +
               "  \"claudeModel\": "    + quoted(s.getClaudeModel())            + ",\n" +
               "  \"claudeApiKey\": "   + quoted(claudeProperties.getApiKey()) + ",\n" +
               "  \"accentColor\": "    + quoted(s.getAccentColor())            + "\n" +
               "}";
    }

    private String quoted(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n").replace("\r", "") + "\"";
    }

    private void applyJson(String json, ToolkitSettings s) {
        s.getDb().setUrl(extractField(json, "dbUrl"));
        s.getDb().setUsername(extractField(json, "dbUsername"));
        s.getDb().setPassword(extractField(json, "dbPassword"));
        s.getProject().setScanPath(extractField(json, "scanPath"));
        s.setProjectContext(extractField(json, "projectContext"));
        String claudeModel = extractField(json, "claudeModel");
        if (claudeModel != null) s.setClaudeModel(claudeModel);
        String savedApiKey = extractField(json, "claudeApiKey");
        if (savedApiKey != null && !savedApiKey.isEmpty()) {
            claudeProperties.setApiKey(savedApiKey);
        }
        String accentColor = extractField(json, "accentColor");
        if (accentColor != null) s.setAccentColor(accentColor);
    }

    private String extractField(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return "";
        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return "";
        int openQuote = json.indexOf('"', colonIdx + 1);
        if (openQuote < 0) return "";
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = openQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == '"' || c == '\\') sb.append(c);
                else if (c == 'n')         sb.append('\n');
                else if (c == 'r')         { /* skip */ }
                else                       sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
