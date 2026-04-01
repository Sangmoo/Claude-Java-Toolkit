package io.github.claudetoolkit.ui.config;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages user-defined system prompt overrides for each AI feature.
 *
 * <p>Prompts are stored in {@code ${user.home}/.claude-toolkit/prompt-templates.json}.
 * When a custom prompt is set for a feature, it replaces the hardcoded default.
 * If no custom prompt exists, the feature's default is used unchanged.
 *
 * <h3>Feature codes</h3>
 * <ul>
 *   <li>SQL_REVIEW      — SQL 성능·품질 리뷰</li>
 *   <li>SQL_SECURITY    — SQL 보안 취약점 검사</li>
 *   <li>SQL_EXPLAIN     — Oracle 실행계획 AI 분석</li>
 *   <li>CODE_REVIEW     — Java/Spring 코드 리뷰</li>
 *   <li>CODE_SECURITY   — Java/Spring 보안 감사</li>
 *   <li>DOC_GENERATE    — 소스코드 기술 문서 생성</li>
 *   <li>ERD_ANALYZE     — ERD 분석</li>
 * </ul>
 */
@Service
public class PromptTemplateService {

    private static final String DIR  = System.getProperty("user.home") + File.separator + ".claude-toolkit";
    private static final String FILE = DIR + File.separator + "prompt-templates.json";

    /** feature code → custom prompt text */
    private final Map<String, String> templates = new LinkedHashMap<>();

    public PromptTemplateService() {
        load();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns the effective system prompt for a feature.
     * Returns {@code defaultPrompt} when no custom prompt has been saved.
     */
    public String getPrompt(String featureCode, String defaultPrompt) {
        String custom = templates.get(featureCode);
        return (custom != null && !custom.trim().isEmpty()) ? custom : defaultPrompt;
    }

    /** Returns true when the user has saved a custom prompt for this feature. */
    public boolean hasCustomPrompt(String featureCode) {
        String v = templates.get(featureCode);
        return v != null && !v.trim().isEmpty();
    }

    /** Save or update a custom prompt. Pass blank/null to clear. */
    public void setPrompt(String featureCode, String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            templates.remove(featureCode);
        } else {
            templates.put(featureCode, prompt.trim());
        }
        save();
    }

    /** Remove the custom prompt and fall back to the default. */
    public void resetPrompt(String featureCode) {
        templates.remove(featureCode);
        save();
    }

    /** Read-only view of all saved custom prompts. */
    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(templates);
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    private void save() {
        try {
            File dir = new File(DIR);
            if (!dir.exists()) dir.mkdirs();
            StringBuilder sb = new StringBuilder("{\n");
            boolean first = true;
            for (Map.Entry<String, String> e : templates.entrySet()) {
                if (!first) sb.append(",\n");
                sb.append("  ").append(quoted(e.getKey()))
                  .append(": ").append(quoted(e.getValue()));
                first = false;
            }
            sb.append("\n}");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
                w.write(sb.toString());
            }
        } catch (Exception e) {
            System.err.println("[PromptTemplateService] save failed: " + e.getMessage());
        }
    }

    private void load() {
        File f = new File(FILE);
        if (!f.exists()) return;
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            String json = sb.toString().trim();
            // Simple key-value JSON parser (hand-written, no external dependency)
            int i = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (i < 0 || end < 0) return;
            String body = json.substring(i + 1, end);
            // Parse "key": "value" pairs
            int pos = 0;
            while (pos < body.length()) {
                int ks = body.indexOf('"', pos);
                if (ks < 0) break;
                int ke = body.indexOf('"', ks + 1);
                if (ke < 0) break;
                String key = body.substring(ks + 1, ke);
                int colon = body.indexOf(':', ke + 1);
                if (colon < 0) break;
                int vs = body.indexOf('"', colon + 1);
                if (vs < 0) break;
                // read escaped string value
                StringBuilder val = new StringBuilder();
                boolean escaped = false;
                int vi = vs + 1;
                while (vi < body.length()) {
                    char c = body.charAt(vi++);
                    if (escaped) {
                        if (c == '"' || c == '\\') val.append(c);
                        else if (c == 'n') val.append('\n');
                        else val.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        break;
                    } else {
                        val.append(c);
                    }
                }
                if (!key.isEmpty()) templates.put(key, val.toString());
                pos = vi;
            }
        } catch (Exception e) {
            System.err.println("[PromptTemplateService] load failed: " + e.getMessage());
        }
    }

    private static String quoted(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
