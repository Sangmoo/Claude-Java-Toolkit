package io.github.claudetoolkit.ui.config;

import io.github.claudetoolkit.starter.properties.ClaudeProperties;
import io.github.claudetoolkit.ui.security.CryptoUtils;
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
               "  \"dbPassword\": "     + quotedEncrypted(s.getDb().getPassword()) + ",\n" +
               "  \"scanPath\": "       + quoted(s.getProject().getScanPath())  + ",\n" +
               "  \"projectContext\": " + quoted(s.getProjectContext())         + ",\n" +
               "  \"claudeModel\": "    + quoted(s.getClaudeModel())            + ",\n" +
               "  \"claudeApiKey\": "   + quotedEncrypted(claudeProperties.getApiKey()) + ",\n" +
               "  \"accentColor\": "    + quoted(s.getAccentColor())            + ",\n" +
               "  \"emailHost\": "      + quoted(s.getEmail().getHost())        + ",\n" +
               "  \"emailPort\": "      + s.getEmail().getPort()               + ",\n" +
               "  \"emailUsername\": "  + quoted(s.getEmail().getUsername())    + ",\n" +
               "  \"emailPassword\": "  + quotedEncrypted(s.getEmail().getPassword()) + ",\n" +
               "  \"emailFrom\": "      + quoted(s.getEmail().getFrom())        + ",\n" +
               "  \"emailTls\": "            + s.getEmail().isTls()                 + ",\n" +
               "  \"cacheRefreshCron\": "    + quoted(s.getCacheRefreshCron())       + ",\n" +
               "  \"slackWebhookUrl\": "    + quoted(s.getSlackWebhookUrl())       + ",\n" +
               "  \"teamsWebhookUrl\": "    + quoted(s.getTeamsWebhookUrl())       + ",\n" +
               "  \"jiraBaseUrl\": "        + quoted(s.getJiraBaseUrl())           + ",\n" +
               "  \"jiraProjectKey\": "     + quoted(s.getJiraProjectKey())        + ",\n" +
               "  \"jiraEmail\": "          + quoted(s.getJiraEmail())             + ",\n" +
               "  \"jiraApiToken\": "       + quotedEncrypted(s.getJiraApiToken()) + "\n" +
               "}";
    }

    private String quoted(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n").replace("\r", "") + "\"";
    }

    /** 민감 필드: AES 암호화 후 quoted 처리 */
    private String quotedEncrypted(String val) {
        if (val == null || val.isEmpty()) return "\"\"";
        return quoted(CryptoUtils.ensureEncrypted(val));
    }

    /** 민감 필드: JSON에서 추출 후 AES 복호화 */
    private String extractDecrypted(String json, String key) {
        String raw = extractField(json, key);
        if (raw == null || raw.isEmpty()) return raw;
        return CryptoUtils.decrypt(raw);
    }

    private void applyJson(String json, ToolkitSettings s) {
        s.getDb().setUrl(extractField(json, "dbUrl"));
        s.getDb().setUsername(extractField(json, "dbUsername"));
        s.getDb().setPassword(extractDecrypted(json, "dbPassword"));
        s.getProject().setScanPath(extractField(json, "scanPath"));
        s.setProjectContext(extractField(json, "projectContext"));
        String claudeModel = extractField(json, "claudeModel");
        if (claudeModel != null) s.setClaudeModel(claudeModel);
        String savedApiKey = extractDecrypted(json, "claudeApiKey");
        if (savedApiKey != null && !savedApiKey.isEmpty()) {
            claudeProperties.setApiKey(savedApiKey);
        }
        String accentColor = extractField(json, "accentColor");
        if (accentColor != null) s.setAccentColor(accentColor);
        String emailHost = extractField(json, "emailHost");
        if (emailHost != null) s.getEmail().setHost(emailHost);
        String emailPortStr = extractRawField(json, "emailPort");
        if (emailPortStr != null && !emailPortStr.isEmpty()) {
            try { s.getEmail().setPort(Integer.parseInt(emailPortStr.trim())); } catch (NumberFormatException e2) { /* keep default */ }
        }
        String emailUsername = extractField(json, "emailUsername");
        if (emailUsername != null) s.getEmail().setUsername(emailUsername);
        String emailPassword = extractDecrypted(json, "emailPassword");
        if (emailPassword != null) s.getEmail().setPassword(emailPassword);
        String emailFrom = extractField(json, "emailFrom");
        if (emailFrom != null) s.getEmail().setFrom(emailFrom);
        String emailTls = extractRawField(json, "emailTls");
        if (emailTls != null) s.getEmail().setTls(!"false".equals(emailTls.trim()));
        String cacheRefreshCron = extractField(json, "cacheRefreshCron");
        if (cacheRefreshCron != null) s.setCacheRefreshCron(cacheRefreshCron);
        String slackWebhookUrl = extractField(json, "slackWebhookUrl");
        if (slackWebhookUrl != null) s.setSlackWebhookUrl(slackWebhookUrl);
        String teamsWebhookUrl = extractField(json, "teamsWebhookUrl");
        if (teamsWebhookUrl != null) s.setTeamsWebhookUrl(teamsWebhookUrl);
        String jiraBaseUrl = extractField(json, "jiraBaseUrl");
        if (jiraBaseUrl != null) s.setJiraBaseUrl(jiraBaseUrl);
        String jiraProjectKey = extractField(json, "jiraProjectKey");
        if (jiraProjectKey != null) s.setJiraProjectKey(jiraProjectKey);
        String jiraEmail = extractField(json, "jiraEmail");
        if (jiraEmail != null) s.setJiraEmail(jiraEmail);
        String jiraApiToken = extractDecrypted(json, "jiraApiToken");
        if (jiraApiToken != null) s.setJiraApiToken(jiraApiToken);
    }

    /** Extract a raw (unquoted) JSON field value — used for numbers and booleans. */
    private String extractRawField(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return null;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '\n' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
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
