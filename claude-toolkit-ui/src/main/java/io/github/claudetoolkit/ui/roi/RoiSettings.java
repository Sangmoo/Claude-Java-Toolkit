package io.github.claudetoolkit.ui.roi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ROI 리포트 설정 — {@code ~/.claude-toolkit/roi-settings.json} 에 영속화됩니다.
 *
 * <p>SettingsPersistenceService의 hand-written JSON과 분리하여 Map 직렬화 복잡도를 피합니다.
 */
public class RoiSettings {

    private static final String SETTINGS_FILE =
            System.getProperty("user.home") + File.separator + ".claude-toolkit"
          + File.separator + "roi-settings.json";

    // ── 설정 필드 ──────────────────────────────────────────────────────────────

    /** 시간당 인건비 (원) */
    private int hourlyRateWon = 40_000;

    /** 기능 유형별 절감 시간 (분) */
    private Map<String, Integer> timeSavingByType = defaultTimeSavings();

    /** Claude API 입력 토큰 단가 (달러/100만 토큰) */
    private double inputCostPerMillion = 3.0;

    /** Claude API 출력 토큰 단가 (달러/100만 토큰) */
    private double outputCostPerMillion = 15.0;

    /** USD → KRW 환율 */
    private int usdToKrw = 1380;

    /** 월 예산 한도 (USD, 0이면 알림 비활성화) */
    private double monthlyBudgetUsd = 0.0;

    /** 예산 초과 알림 이메일 */
    private String budgetAlertEmail = "";

    // ── 기본값 ──────────────────────────────────────────────────────────────

    private static Map<String, Integer> defaultTimeSavings() {
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        m.put("SQL_REVIEW",      20);
        m.put("SQL_SECURITY",    20);
        m.put("SQL_TRANSLATE",   20);
        m.put("SQL_BATCH",       25);
        m.put("CODE_REVIEW",     30);
        m.put("CODE_REVIEW_SEC", 30);
        m.put("HARNESS_REVIEW",  45);
        m.put("TEST_GEN",        40);
        m.put("JAVADOC",         25);
        m.put("REFACTORING",     35);
        m.put("DOC_GEN",         30);
        m.put("DEP_CHECK",       15);
        m.put("EXPLAIN_PLAN",    20);
        m.put("DATA_MASKING",    15);
        m.put("SPRING_MIGRATE",  20);
        m.put("DEFAULT",         10);
        return m;
    }

    // ── 영속화 ────────────────────────────────────────────────────────────────

    public void save() {
        try {
            File file = new File(SETTINGS_FILE);
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
            try { w.write(toJson()); } finally { w.close(); }
        } catch (Exception e) {
            System.err.println("[RoiSettings] save failed: " + e.getMessage());
        }
    }

    public static RoiSettings load() {
        RoiSettings s = new RoiSettings();
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) return s;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            } finally { r.close(); }
            s.applyJson(sb.toString());
        } catch (Exception e) {
            System.err.println("[RoiSettings] load failed: " + e.getMessage());
        }
        return s;
    }

    // ── JSON 직렬화 (hand-written) ────────────────────────────────────────────

    private String toJson() {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("  \"hourlyRateWon\": ").append(hourlyRateWon).append(",\n");
        sb.append("  \"inputCostPerMillion\": ").append(inputCostPerMillion).append(",\n");
        sb.append("  \"outputCostPerMillion\": ").append(outputCostPerMillion).append(",\n");
        sb.append("  \"usdToKrw\": ").append(usdToKrw).append(",\n");
        sb.append("  \"monthlyBudgetUsd\": ").append(monthlyBudgetUsd).append(",\n");
        sb.append("  \"budgetAlertEmail\": ").append(q(budgetAlertEmail)).append(",\n");
        sb.append("  \"timeSavingByType\": {\n");
        int i = 0;
        for (Map.Entry<String, Integer> e : timeSavingByType.entrySet()) {
            sb.append("    ").append(q(e.getKey())).append(": ").append(e.getValue());
            if (++i < timeSavingByType.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }\n}");
        return sb.toString();
    }

    private void applyJson(String json) {
        String hr = raw(json, "hourlyRateWon");
        if (hr != null) try { hourlyRateWon = Integer.parseInt(hr.trim()); } catch (NumberFormatException ignored) {}

        String ic = raw(json, "inputCostPerMillion");
        if (ic != null) try { inputCostPerMillion = Double.parseDouble(ic.trim()); } catch (NumberFormatException ignored) {}

        String oc = raw(json, "outputCostPerMillion");
        if (oc != null) try { outputCostPerMillion = Double.parseDouble(oc.trim()); } catch (NumberFormatException ignored) {}

        String fx = raw(json, "usdToKrw");
        if (fx != null) try { usdToKrw = Integer.parseInt(fx.trim()); } catch (NumberFormatException ignored) {}

        String mb = raw(json, "monthlyBudgetUsd");
        if (mb != null) try { monthlyBudgetUsd = Double.parseDouble(mb.trim()); } catch (NumberFormatException ignored) {}

        String email = str(json, "budgetAlertEmail");
        if (email != null) budgetAlertEmail = email;

        // timeSavingByType object 파싱
        int objStart = json.indexOf("\"timeSavingByType\"");
        if (objStart >= 0) {
            int braceOpen = json.indexOf('{', objStart);
            int braceClose = json.indexOf('}', braceOpen + 1);
            if (braceOpen >= 0 && braceClose > braceOpen) {
                String inner = json.substring(braceOpen + 1, braceClose);
                Map<String, Integer> parsed = parseIntMap(inner);
                if (!parsed.isEmpty()) timeSavingByType = parsed;
            }
        }
    }

    private Map<String, Integer> parseIntMap(String inner) {
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        String[] pairs = inner.split(",");
        for (String pair : pairs) {
            pair = pair.trim();
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String k = pair.substring(0, colon).trim().replace("\"", "");
            String v = pair.substring(colon + 1).trim();
            try { m.put(k, Integer.parseInt(v)); } catch (NumberFormatException ignored) {}
        }
        return m;
    }

    private String raw(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0) return null;
        int ci = json.indexOf(':', ki + search.length());
        if (ci < 0) return null;
        int s = ci + 1;
        while (s < json.length() && Character.isWhitespace(json.charAt(s))) s++;
        int e = s;
        while (e < json.length() && json.charAt(e) != ',' && json.charAt(e) != '\n' && json.charAt(e) != '}') e++;
        return json.substring(s, e).trim();
    }

    private String str(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0) return null;
        int ci = json.indexOf(':', ki + search.length());
        if (ci < 0) return null;
        int oq = json.indexOf('"', ci + 1);
        if (oq < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = oq + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') { i++; if (i < json.length()) { char n = json.charAt(i); if (n == 'n') sb.append('\n'); else sb.append(n); } }
            else if (c == '"') break;
            else sb.append(c);
        }
        return sb.toString();
    }

    private String q(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int getHourlyRateWon()                        { return hourlyRateWon; }
    public void setHourlyRateWon(int v)                  { this.hourlyRateWon = v; }
    public Map<String, Integer> getTimeSavingByType()    { return timeSavingByType; }
    public void setTimeSavingByType(Map<String,Integer> m){ this.timeSavingByType = m; }
    public double getInputCostPerMillion()               { return inputCostPerMillion; }
    public void setInputCostPerMillion(double v)         { this.inputCostPerMillion = v; }
    public double getOutputCostPerMillion()              { return outputCostPerMillion; }
    public void setOutputCostPerMillion(double v)        { this.outputCostPerMillion = v; }
    public int getUsdToKrw()                             { return usdToKrw; }
    public void setUsdToKrw(int v)                       { this.usdToKrw = v; }
    public double getMonthlyBudgetUsd()                  { return monthlyBudgetUsd; }
    public void setMonthlyBudgetUsd(double v)            { this.monthlyBudgetUsd = v; }
    public String getBudgetAlertEmail()                  { return budgetAlertEmail != null ? budgetAlertEmail : ""; }
    public void setBudgetAlertEmail(String v)            { this.budgetAlertEmail = v; }

    public int getTimeSaving(String type) {
        if (timeSavingByType.containsKey(type)) return timeSavingByType.get(type);
        return timeSavingByType.containsKey("DEFAULT") ? timeSavingByType.get("DEFAULT") : 10;
    }
}
