package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.scanner.ProjectScannerService;
import io.github.claudetoolkit.docgen.scanner.ScannedFile;
import io.github.claudetoolkit.sql.db.OracleMetaService;
import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.starter.properties.ClaudeProperties;
import io.github.claudetoolkit.ui.config.SettingsPersistenceService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * Manages the Settings page (/settings).
 */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final ToolkitSettings            settings;
    private final OracleMetaService          oracleMetaService;
    private final ProjectScannerService      projectScannerService;
    private final SettingsPersistenceService persistenceService;
    private final ClaudeClient               claudeClient;
    private final ClaudeProperties           claudeProperties;

    public SettingsController(ToolkitSettings settings,
                              OracleMetaService oracleMetaService,
                              ProjectScannerService projectScannerService,
                              SettingsPersistenceService persistenceService,
                              ClaudeClient claudeClient,
                              ClaudeProperties claudeProperties) {
        this.settings              = settings;
        this.oracleMetaService     = oracleMetaService;
        this.projectScannerService = projectScannerService;
        this.persistenceService    = persistenceService;
        this.claudeClient          = claudeClient;
        this.claudeProperties      = claudeProperties;
    }

    @GetMapping
    public String showSettings(Model model) {
        model.addAttribute("settings", settings);
        model.addAttribute("currentApiKeyMasked", maskApiKey(claudeProperties.getApiKey()));
        java.util.List<String> availableModels = new java.util.ArrayList<String>();
        availableModels.add("claude-opus-4-5");
        availableModels.add("claude-sonnet-4-5");
        availableModels.add("claude-sonnet-4-20250514");
        availableModels.add("claude-haiku-4-5");
        availableModels.add("claude-haiku-3-5");
        model.addAttribute("availableModels", availableModels);
        model.addAttribute("currentModel", claudeClient.getEffectiveModel());
        return "settings/index";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam(value = "dbUrl",          defaultValue = "") String dbUrl,
            @RequestParam(value = "dbUsername",     defaultValue = "") String dbUsername,
            @RequestParam(value = "dbPassword",     defaultValue = "") String dbPassword,
            @RequestParam(value = "scanPath",       defaultValue = "") String scanPath,
            @RequestParam(value = "projectContext", defaultValue = "") String projectContext,
            @RequestParam(required = false, defaultValue = "") String claudeModel,
            @RequestParam(required = false, defaultValue = "") String claudeApiKey,
            @RequestParam(required = false, defaultValue = "") String accentColor,
            @RequestParam(required = false, defaultValue = "") String emailHost,
            @RequestParam(required = false, defaultValue = "587") String emailPort,
            @RequestParam(required = false, defaultValue = "") String emailUsername,
            @RequestParam(required = false, defaultValue = "") String emailPassword,
            @RequestParam(required = false, defaultValue = "") String emailFrom,
            @RequestParam(required = false, defaultValue = "true") String emailTls,
            @RequestParam(required = false, defaultValue = "") String cacheRefreshCron,
            Model model) {

        settings.getDb().setUrl(dbUrl.trim());
        settings.getDb().setUsername(dbUsername.trim());
        settings.getDb().setPassword(dbPassword.trim());
        settings.getProject().setScanPath(scanPath.trim());
        settings.setProjectContext(projectContext.trim());
        settings.setClaudeModel(claudeModel);
        settings.setAccentColor(accentColor);
        settings.getEmail().setHost(emailHost.trim());
        try { settings.getEmail().setPort(Integer.parseInt(emailPort.trim())); } catch (NumberFormatException ex) { settings.getEmail().setPort(587); }
        settings.getEmail().setUsername(emailUsername.trim());
        if (!emailPassword.isEmpty()) settings.getEmail().setPassword(emailPassword.trim());
        settings.getEmail().setFrom(emailFrom.trim());
        settings.getEmail().setTls(!"false".equals(emailTls));
        settings.setCacheRefreshCron(cacheRefreshCron);

        // API 키가 입력된 경우 즉시 적용
        if (claudeApiKey != null && !claudeApiKey.trim().isEmpty()) {
            claudeProperties.setApiKey(claudeApiKey.trim());
        }

        persistenceService.save();

        claudeClient.setModelOverride(claudeModel);

        model.addAttribute("settings",     settings);
        model.addAttribute("saveSuccess",  true);
        model.addAttribute("currentApiKeyMasked", maskApiKey(claudeProperties.getApiKey()));

        java.util.List<String> availableModels = new java.util.ArrayList<String>();
        availableModels.add("claude-opus-4-5");
        availableModels.add("claude-sonnet-4-5");
        availableModels.add("claude-sonnet-4-20250514");
        availableModels.add("claude-haiku-4-5");
        availableModels.add("claude-haiku-3-5");
        model.addAttribute("availableModels", availableModels);
        model.addAttribute("currentModel", claudeClient.getEffectiveModel());
        return "settings/index";
    }

    /** AJAX: tests DB connectivity. */
    @PostMapping("/test-db")
    @ResponseBody
    public String testDb() {
        if (!settings.isDbConfigured()) {
            return "error:DB \uc811\uc18d \uc815\ubcf4\uac00 \uc124\uc815\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4.";
        }
        boolean ok = oracleMetaService.testConnection(
                settings.getDb().getUrl(),
                settings.getDb().getUsername(),
                settings.getDb().getPassword());
        return ok ? "ok:DB \uc5f0\uacb0 \uc131\uacf5!"
                  : "error:DB \uc5f0\uacb0 \uc2e4\ud328. URL/\uacc4\uc815 \uc815\ubcf4\ub97c \ud655\uc778\ud558\uc138\uc694.";
    }

    /** AJAX: scans project and returns file count summary. */
    @PostMapping("/scan-preview")
    @ResponseBody
    public String scanPreview() {
        if (!settings.isProjectConfigured()) {
            return "error:\ud504\ub85c\uc81d\ud2b8 \uacbd\ub85c\uac00 \uc124\uc815\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4.";
        }
        try {
            List<ScannedFile> files = projectScannerService.scanProject(settings.getProject().getScanPath());
            return "ok:" + projectScannerService.getScanSummary(files);
        } catch (IOException e) {
            return "error:" + e.getMessage();
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 8) return key == null || key.isEmpty() ? "(미설정)" : "****";
        return key.substring(0, 10) + "..." + key.substring(key.length() - 4);
    }

    /** AJAX: validates Claude API key by making a minimal test request. */
    @PostMapping("/validate-api")
    @ResponseBody
    public String validateApi() {
        try {
            String result = claudeClient.chat(
                    "Respond with the single word: ok",
                    "ok");
            if (result != null && !result.trim().isEmpty()) {
                return "ok:Claude API \uc5f0\uacb0 \uc131\uacf5! (\ubaa8\ub378: " + claudeClient.getModel() + ")";
            }
            return "error:\uc751\ub2f5\uc774 \ube44\uc5b4 \uc788\uc2b5\ub2c8\ub2e4.";
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("401")) {
                return "error:API \ud0a4\uac00 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. (401 Unauthorized)";
            }
            return "error:API \uc5f0\uacb0 \uc2e4\ud328: " + (msg != null ? msg.substring(0, Math.min(msg.length(), 100)) : "unknown");
        }
    }
}
