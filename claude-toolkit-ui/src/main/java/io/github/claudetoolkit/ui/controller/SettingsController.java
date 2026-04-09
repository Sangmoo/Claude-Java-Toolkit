package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.scanner.ProjectScannerService;
import io.github.claudetoolkit.docgen.scanner.ScannedFile;
import io.github.claudetoolkit.sql.db.OracleMetaService;
import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.starter.properties.ClaudeProperties;
import io.github.claudetoolkit.ui.config.SettingsPersistenceService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.security.SecuritySettings;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
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
    private final io.github.claudetoolkit.ui.harness.HarnessCacheService harnessCacheService;

    public SettingsController(ToolkitSettings settings,
                              OracleMetaService oracleMetaService,
                              ProjectScannerService projectScannerService,
                              SettingsPersistenceService persistenceService,
                              ClaudeClient claudeClient,
                              ClaudeProperties claudeProperties,
                              io.github.claudetoolkit.ui.harness.HarnessCacheService harnessCacheService) {
        this.settings              = settings;
        this.oracleMetaService     = oracleMetaService;
        this.projectScannerService = projectScannerService;
        this.persistenceService    = persistenceService;
        this.claudeClient          = claudeClient;
        this.claudeProperties      = claudeProperties;
        this.harnessCacheService   = harnessCacheService;
    }

    @GetMapping
    public String showSettings(Model model, HttpSession session) {
        // Settings 비밀번호 잠금 확인
        SecuritySettings sec = SecuritySettings.load();
        if (sec.isSettingsLockEnabled() && sec.getSettingsPasswordHash() != null) {
            Boolean unlocked = (Boolean) session.getAttribute("settingsUnlocked");
            if (!Boolean.TRUE.equals(unlocked)) {
                return "redirect:/security/settings-unlock?redirect=/settings";
            }
        }
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
            @RequestParam(required = false, defaultValue = "") String slackWebhookUrl,
            @RequestParam(required = false, defaultValue = "") String teamsWebhookUrl,
            @RequestParam(required = false, defaultValue = "") String jiraBaseUrl,
            @RequestParam(required = false, defaultValue = "") String jiraProjectKey,
            @RequestParam(required = false, defaultValue = "") String jiraEmail,
            @RequestParam(required = false, defaultValue = "") String jiraApiToken,
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
        settings.setSlackWebhookUrl(slackWebhookUrl);
        settings.setTeamsWebhookUrl(teamsWebhookUrl);
        settings.setJiraBaseUrl(jiraBaseUrl);
        settings.setJiraProjectKey(jiraProjectKey);
        settings.setJiraEmail(jiraEmail);
        if (!jiraApiToken.isEmpty()) settings.setJiraApiToken(jiraApiToken);

        // API 키가 입력된 경우 즉시 적용
        if (claudeApiKey != null && !claudeApiKey.trim().isEmpty()) {
            claudeProperties.setApiKey(claudeApiKey.trim());
        }

        persistenceService.save();

        // Settings 저장 후 캐시 자동 갱신 (백그라운드)
        Thread cacheThread = new Thread(new Runnable() {
            public void run() {
                harnessCacheService.refreshFileCache();
                harnessCacheService.refreshDbCache();
            }
        });
        cacheThread.setDaemon(true);
        cacheThread.setName("settings-cache-refresh");
        cacheThread.start();

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

    /** Slack/Teams 웹훅 테스트 */
    @PostMapping("/test-webhook")
    @ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> testWebhook(
            @RequestParam String type,
            @RequestParam String webhookUrl) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        try {
            io.github.claudetoolkit.ui.integration.NotificationService ns =
                    org.springframework.web.context.support.WebApplicationContextUtils
                            .getRequiredWebApplicationContext(((org.springframework.web.context.request.ServletRequestAttributes)
                                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes())
                                    .getRequest().getServletContext())
                            .getBean(io.github.claudetoolkit.ui.integration.NotificationService.class);
            boolean ok;
            if ("slack".equals(type)) {
                ok = ns.sendSlack(webhookUrl, "Claude Toolkit 테스트", "Settings에서 테스트 메시지를 전송했습니다.", "#36a64f");
            } else {
                ok = ns.sendTeams(webhookUrl, "Claude Toolkit 테스트", "Settings에서 테스트 메시지를 전송했습니다.");
            }
            resp.put("success", ok);
            if (!ok) resp.put("error", "웹훅 전송 실패 (URL을 확인하세요)");
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return org.springframework.http.ResponseEntity.ok(resp);
    }

    /** Jira 연결 테스트 */
    @PostMapping("/test-jira")
    @ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> testJira(
            @RequestParam String baseUrl,
            @RequestParam String email,
            @RequestParam String apiToken) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        try {
            io.github.claudetoolkit.ui.integration.JiraService js =
                    org.springframework.web.context.support.WebApplicationContextUtils
                            .getRequiredWebApplicationContext(((org.springframework.web.context.request.ServletRequestAttributes)
                                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes())
                                    .getRequest().getServletContext())
                            .getBean(io.github.claudetoolkit.ui.integration.JiraService.class);
            boolean ok = js.testConnection(baseUrl, email, apiToken);
            resp.put("success", ok);
            if (!ok) resp.put("error", "Jira 연결 실패 (URL/인증 정보를 확인하세요)");
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return org.springframework.http.ResponseEntity.ok(resp);
    }
}
