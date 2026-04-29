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
    // v4.4.x — Flow Analysis 인덱서 자동 재빌드 (Settings 저장 직후)
    private final io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer    flowMybatisIndexer;
    private final io.github.claudetoolkit.ui.flow.indexer.SpringUrlIndexer  flowSpringIndexer;
    private final io.github.claudetoolkit.ui.flow.indexer.MiPlatformIndexer flowMiplatformIndexer;
    // v4.7.x — Settings 변경 감사 로그
    private final io.github.claudetoolkit.ui.audit.ConfigChangeLogService   configChangeLog;

    public SettingsController(ToolkitSettings settings,
                              OracleMetaService oracleMetaService,
                              ProjectScannerService projectScannerService,
                              SettingsPersistenceService persistenceService,
                              ClaudeClient claudeClient,
                              ClaudeProperties claudeProperties,
                              io.github.claudetoolkit.ui.harness.HarnessCacheService harnessCacheService,
                              io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer    flowMybatisIndexer,
                              io.github.claudetoolkit.ui.flow.indexer.SpringUrlIndexer  flowSpringIndexer,
                              io.github.claudetoolkit.ui.flow.indexer.MiPlatformIndexer flowMiplatformIndexer,
                              io.github.claudetoolkit.ui.audit.ConfigChangeLogService configChangeLog) {
        this.settings              = settings;
        this.oracleMetaService     = oracleMetaService;
        this.projectScannerService = projectScannerService;
        this.persistenceService    = persistenceService;
        this.claudeClient          = claudeClient;
        this.claudeProperties      = claudeProperties;
        this.harnessCacheService   = harnessCacheService;
        this.flowMybatisIndexer    = flowMybatisIndexer;
        this.flowSpringIndexer     = flowSpringIndexer;
        this.flowMiplatformIndexer = flowMiplatformIndexer;
        this.configChangeLog       = configChangeLog;
    }

    @GetMapping
    public String showSettings(HttpSession session) {
        // Settings 비밀번호 잠금 확인
        SecuritySettings sec = SecuritySettings.load();
        if (sec.isSettingsLockEnabled() && sec.getSettingsPasswordHash() != null) {
            Boolean unlocked = (Boolean) session.getAttribute("settingsUnlocked");
            if (!Boolean.TRUE.equals(unlocked)) {
                return "redirect:/security/settings-unlock?redirect=/settings";
            }
        }
        return "redirect:/";
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
            // v4.4.x — Flow Analysis MiPlatform
            @RequestParam(required = false, defaultValue = "") String miplatformRoot,
            @RequestParam(required = false, defaultValue = "") String miplatformPatterns,
            HttpSession saveSession) {

        // 입력값 유효성 검증
        if (!dbUrl.trim().isEmpty() && !dbUrl.trim().startsWith("jdbc:")) {
            return "redirect:/settings?error=invalid_db_url";
        }

        // v4.7.x — 변경 전 값 스냅샷 (감사 로그 비교용)
        SettingsSnapshot before = SettingsSnapshot.capture(settings, claudeProperties);

        settings.getDb().setUrl(dbUrl.trim());
        settings.getDb().setUsername(dbUsername.trim());
        settings.getDb().setPassword(dbPassword.trim());
        settings.getProject().setScanPath(scanPath.trim());
        settings.getProject().setMiplatformRoot(miplatformRoot.trim());
        settings.getProject().setMiplatformPatterns(miplatformPatterns);
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

        // v4.7.x — 감사 로그 기록 (변경된 항목만 자동 필터)
        SettingsSnapshot after = SettingsSnapshot.capture(settings, claudeProperties);
        recordSettingsChanges(before, after);

        // Settings 저장 후 캐시 자동 갱신 (백그라운드)
        Thread cacheThread = new Thread(new Runnable() {
            public void run() {
                harnessCacheService.refreshFileCache();
                harnessCacheService.refreshDbCache();
                // v4.4.x — Flow Analysis 인덱서도 같이 갱신
                // (scanPath / miplatformRoot / miplatformPatterns 변경 즉시 반영)
                try { flowMybatisIndexer.refresh(); }    catch (Exception ignored) {}
                try { flowSpringIndexer.refresh(); }     catch (Exception ignored) {}
                try { flowMiplatformIndexer.refresh(); } catch (Exception ignored) {}
            }
        });
        cacheThread.setDaemon(true);
        cacheThread.setName("settings-cache-refresh");
        cacheThread.start();

        claudeClient.setModelOverride(claudeModel);

        return "redirect:/settings?saved=true";
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
            // Docker 환경에서 Windows 경로 자동 변환
            String resolvedPath = io.github.claudetoolkit.ui.harness.HarnessCacheService
                    .resolveHostPath(settings.getProject().getScanPath());
            List<ScannedFile> files = projectScannerService.scanProject(resolvedPath);
            return "ok:" + projectScannerService.getScanSummary(files);
        } catch (IOException e) {
            return "error:" + e.getMessage();
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 8) return key == null || key.isEmpty() ? "(미설정)" : "****";
        return key.substring(0, 10) + "..." + key.substring(key.length() - 4);
    }

    /**
     * v4.7.x — 변경 전/후 비교 후 감사 로그 기록.
     * 같은 값이면 자동으로 noop (recordIfChanged 의 동작).
     */
    private void recordSettingsChanges(SettingsSnapshot before, SettingsSnapshot after) {
        String C = io.github.claudetoolkit.ui.audit.ConfigChangeLogService.CATEGORY_SETTINGS;
        // 비민감 — 그대로 기록
        configChangeLog.recordIfChanged("settings.db.url",         "Oracle DB URL",      C, before.dbUrl,         after.dbUrl,         false);
        configChangeLog.recordIfChanged("settings.db.username",    "Oracle DB 계정명",   C, before.dbUsername,    after.dbUsername,    false);
        configChangeLog.recordIfChanged("settings.project.scanPath", "프로젝트 스캔 경로", C, before.scanPath,      after.scanPath,      false);
        configChangeLog.recordIfChanged("settings.project.miplatformRoot",     "MiPlatform 루트",   C, before.miplatformRoot,     after.miplatformRoot,     false);
        configChangeLog.recordIfChanged("settings.project.miplatformPatterns", "MiPlatform 패턴",   C, before.miplatformPatterns, after.miplatformPatterns, false);
        configChangeLog.recordIfChanged("settings.projectContext", "프로젝트 메모",       C, before.projectContext, after.projectContext, false);
        configChangeLog.recordIfChanged("settings.claude.model",   "Claude 모델",         C, before.claudeModel,   after.claudeModel,   false);
        configChangeLog.recordIfChanged("settings.accentColor",    "팔레트 색상",         C, before.accentColor,   after.accentColor,   false);
        configChangeLog.recordIfChanged("settings.email.host",     "Email SMTP 호스트",   C, before.emailHost,     after.emailHost,     false);
        configChangeLog.recordIfChanged("settings.email.port",     "Email SMTP 포트",     C, before.emailPort,     after.emailPort,     false);
        configChangeLog.recordIfChanged("settings.email.username", "Email 발송 계정",     C, before.emailUsername, after.emailUsername, false);
        configChangeLog.recordIfChanged("settings.email.from",     "Email 발신자",        C, before.emailFrom,     after.emailFrom,     false);
        configChangeLog.recordIfChanged("settings.email.tls",      "Email TLS",          C, before.emailTls,      after.emailTls,      false);
        configChangeLog.recordIfChanged("settings.cacheRefreshCron", "캐시 갱신 cron",   C, before.cacheRefreshCron, after.cacheRefreshCron, false);
        configChangeLog.recordIfChanged("settings.jira.baseUrl",   "Jira 베이스 URL",     C, before.jiraBaseUrl,   after.jiraBaseUrl,   false);
        configChangeLog.recordIfChanged("settings.jira.projectKey","Jira 프로젝트 Key",   C, before.jiraProjectKey, after.jiraProjectKey, false);
        configChangeLog.recordIfChanged("settings.jira.email",     "Jira 사용자 Email",   C, before.jiraEmail,     after.jiraEmail,     false);
        // 민감 — 마스킹 처리
        configChangeLog.recordIfChanged("settings.db.password",    "Oracle DB 비밀번호",  C, before.dbPassword,    after.dbPassword,    true);
        configChangeLog.recordIfChanged("settings.claude.apiKey",  "Claude API Key",     C, before.claudeApiKey,  after.claudeApiKey,  true);
        configChangeLog.recordIfChanged("settings.email.password", "Email SMTP 비밀번호", C, before.emailPassword, after.emailPassword, true);
        configChangeLog.recordIfChanged("settings.slackWebhookUrl","Slack Webhook URL",  C, before.slackWebhookUrl, after.slackWebhookUrl, true);
        configChangeLog.recordIfChanged("settings.teamsWebhookUrl","Teams Webhook URL",  C, before.teamsWebhookUrl, after.teamsWebhookUrl, true);
        configChangeLog.recordIfChanged("settings.jira.apiToken",  "Jira API Token",     C, before.jiraApiToken,  after.jiraApiToken,  true);
    }

    /**
     * v4.7.x — 비교 가능한 단일 시점의 Settings 스냅샷.
     * 변경 전 / 후 두 번 capture 해서 차이만 감사 로그에 기록한다.
     */
    private static final class SettingsSnapshot {
        String dbUrl, dbUsername, dbPassword;
        String scanPath, miplatformRoot, miplatformPatterns;
        String projectContext;
        String claudeModel, claudeApiKey;
        String accentColor;
        String emailHost, emailPort, emailUsername, emailPassword, emailFrom, emailTls;
        String cacheRefreshCron;
        String slackWebhookUrl, teamsWebhookUrl;
        String jiraBaseUrl, jiraProjectKey, jiraEmail, jiraApiToken;

        static SettingsSnapshot capture(ToolkitSettings s, ClaudeProperties cp) {
            SettingsSnapshot ss = new SettingsSnapshot();
            ss.dbUrl              = nz(s.getDb() != null ? s.getDb().getUrl() : null);
            ss.dbUsername         = nz(s.getDb() != null ? s.getDb().getUsername() : null);
            ss.dbPassword         = nz(s.getDb() != null ? s.getDb().getPassword() : null);
            ss.scanPath           = nz(s.getProject() != null ? s.getProject().getScanPath() : null);
            ss.miplatformRoot     = nz(s.getProject() != null ? s.getProject().getMiplatformRoot() : null);
            ss.miplatformPatterns = nz(s.getProject() != null ? s.getProject().getMiplatformPatterns() : null);
            ss.projectContext     = nz(s.getProjectContext());
            ss.claudeModel        = nz(s.getClaudeModel());
            ss.claudeApiKey       = nz(cp != null ? cp.getApiKey() : null);
            ss.accentColor        = nz(s.getAccentColor());
            ss.emailHost          = nz(s.getEmail() != null ? s.getEmail().getHost() : null);
            ss.emailPort          = s.getEmail() != null ? String.valueOf(s.getEmail().getPort()) : "";
            ss.emailUsername      = nz(s.getEmail() != null ? s.getEmail().getUsername() : null);
            ss.emailPassword      = nz(s.getEmail() != null ? s.getEmail().getPassword() : null);
            ss.emailFrom          = nz(s.getEmail() != null ? s.getEmail().getFrom() : null);
            ss.emailTls           = s.getEmail() != null ? String.valueOf(s.getEmail().isTls()) : "true";
            ss.cacheRefreshCron   = nz(s.getCacheRefreshCron());
            ss.slackWebhookUrl    = nz(s.getSlackWebhookUrl());
            ss.teamsWebhookUrl    = nz(s.getTeamsWebhookUrl());
            ss.jiraBaseUrl        = nz(s.getJiraBaseUrl());
            ss.jiraProjectKey     = nz(s.getJiraProjectKey());
            ss.jiraEmail          = nz(s.getJiraEmail());
            ss.jiraApiToken       = nz(s.getJiraApiToken());
            return ss;
        }

        private static String nz(String s) { return s == null ? "" : s; }
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
