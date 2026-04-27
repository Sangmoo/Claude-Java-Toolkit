package io.github.claudetoolkit.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime-mutable settings shared across the toolkit UI.
 *
 * <p>Initial values are loaded from application.yml (prefix: toolkit).
 * The Settings page (/settings) can update these values in-memory at runtime.
 */
@Component
@ConfigurationProperties(prefix = "toolkit")
public class ToolkitSettings {

    private Db      db      = new Db();
    private Project project = new Project();
    private Email   email   = new Email();
    /** Optional project context memo — prepended to all Claude prompts when set. */
    private String  projectContext = "";
    /** Optional Claude model override — overrides application.yml default when non-blank. */
    private String  claudeModel    = ""; // 빈 문자열이면 application.yml 기본값 사용
    /** Optional accent color override (hex, e.g. "#f97316"). Empty means use default. */
    private String  accentColor    = "";
    /** Cron expression for automatic DB cache refresh (Spring cron, 6-field). Empty = disabled. */
    private String cacheRefreshCron = "";
    /** Slack Webhook URL */
    private String slackWebhookUrl = "";
    /** Teams Webhook URL */
    private String teamsWebhookUrl = "";
    /** Jira base URL */
    private String jiraBaseUrl = "";
    /** Jira project key */
    private String jiraProjectKey = "";
    /** Jira email */
    private String jiraEmail = "";
    /** Jira API token */
    private String jiraApiToken = "";

    public Db getDb()             { return db; }
    public void setDb(Db db)      { this.db = db; }

    public Email getEmail()           { return email; }
    public void setEmail(Email email) { this.email = email; }

    public boolean isEmailConfigured() {
        return email != null && isNotBlank(email.getHost()) && isNotBlank(email.getUsername());
    }

    public Project getProject()             { return project; }
    public void setProject(Project project) { this.project = project; }

    public String getProjectContext()               { return projectContext; }
    public void setProjectContext(String ctx)       { this.projectContext = ctx == null ? "" : ctx; }

    public String getClaudeModel()                  { return claudeModel != null ? claudeModel : ""; }
    public void setClaudeModel(String claudeModel)  { this.claudeModel = claudeModel; }
    public boolean isClaudeModelOverrideSet()       { return isNotBlank(claudeModel); }

    public String getAccentColor()                  { return accentColor != null ? accentColor : ""; }
    public void setAccentColor(String c)            { this.accentColor = c == null ? "" : c; }
    public boolean isAccentColorSet()               { return isNotBlank(accentColor); }

    public String getCacheRefreshCron()               { return cacheRefreshCron != null ? cacheRefreshCron : ""; }
    public void setCacheRefreshCron(String c)         { this.cacheRefreshCron = c == null ? "" : c; }
    public boolean isCacheRefreshCronSet()            { return isNotBlank(cacheRefreshCron); }

    public String getSlackWebhookUrl()               { return slackWebhookUrl != null ? slackWebhookUrl : ""; }
    public void setSlackWebhookUrl(String v)          { this.slackWebhookUrl = v == null ? "" : v; }

    public String getTeamsWebhookUrl()               { return teamsWebhookUrl != null ? teamsWebhookUrl : ""; }
    public void setTeamsWebhookUrl(String v)          { this.teamsWebhookUrl = v == null ? "" : v; }

    public String getJiraBaseUrl()                   { return jiraBaseUrl != null ? jiraBaseUrl : ""; }
    public void setJiraBaseUrl(String v)              { this.jiraBaseUrl = v == null ? "" : v; }

    public String getJiraProjectKey()                { return jiraProjectKey != null ? jiraProjectKey : ""; }
    public void setJiraProjectKey(String v)           { this.jiraProjectKey = v == null ? "" : v; }

    public String getJiraEmail()                     { return jiraEmail != null ? jiraEmail : ""; }
    public void setJiraEmail(String v)                { this.jiraEmail = v == null ? "" : v; }

    public String getJiraApiToken()                  { return jiraApiToken != null ? jiraApiToken : ""; }
    public void setJiraApiToken(String v)              { this.jiraApiToken = v == null ? "" : v; }

    /** Returns true only when all three DB fields are non-empty. */
    public boolean isDbConfigured() {
        return db != null
            && isNotBlank(db.getUrl())
            && isNotBlank(db.getUsername())
            && isNotBlank(db.getPassword());
    }

    /** Returns true when a project scan path has been set. */
    public boolean isProjectConfigured() {
        return project != null && isNotBlank(project.getScanPath());
    }

    /** Returns true when a project context memo has been written. */
    public boolean isProjectContextSet() {
        return isNotBlank(projectContext);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // ── inner classes ────────────────────────────────────────────────────────

    public static class Db {
        private String url      = "";
        private String username = "";
        private String password = "";

        public String getUrl()              { return url; }
        public void setUrl(String url)      { this.url = url; }

        public String getUsername()                 { return username; }
        public void setUsername(String username)    { this.username = username; }

        public String getPassword()                 { return password; }
        public void setPassword(String password)    { this.password = password; }
    }

    public static class Project {
        private String scanPath = "";

        /**
         * v4.4.x — Phase 5: Flow Analysis MiPlatform 인덱서가 사용하는 추가 URL 패턴 정규식.
         * <p>비어있으면 인덱서 기본값 (Transaction url=, transaction("...","svc::/...") 등) 만 사용.
         * <p>사이트별 화면 XML 컨벤션이 다르면 "url\\s*[:=]\\s*[\"']([^\"']+)[\"']" 같은
         * 그룹1 캡처 정규식을 콤마/줄바꿈 구분으로 등록.
         */
        private String miplatformPatterns = "";
        /**
         * v4.4.x — Flow Analysis MiPlatform 디렉토리 (자동 감지 외에 사용자 지정).
         * 비어있으면 인덱서가 src/main/webapp/miplatform 등 자동 탐색.
         */
        private String miplatformRoot = "";

        /**
         * v4.5 — Package Overview 페이지에서 "패키지" 로 간주할 Java 패키지 깊이.
         * <p>예: {@code io.github.claudetoolkit.ui.flow.indexer} 이면
         *     L3 → {@code io.github.claudetoolkit},
         *     L4 → {@code io.github.claudetoolkit.ui},
         *     L5 → {@code io.github.claudetoolkit.ui.flow} (기본값).
         * <p>ERP 프로젝트는 보통 L4~L5가 "업무 모듈" 단위.
         */
        private int packageLevel = 5;

        /**
         * v4.5 — 이 prefix 로 시작하는 패키지만 Package Overview 에 표시.
         * 비어있으면 전체 노출. (예: {@code com.mycompany.erp})
         */
        private String packagePrefix = "";

        public String getScanPath()         { return scanPath; }
        public void setScanPath(String p)   { this.scanPath = p; }

        public String getMiplatformPatterns()       { return miplatformPatterns == null ? "" : miplatformPatterns; }
        public void setMiplatformPatterns(String s) { this.miplatformPatterns = s == null ? "" : s; }

        public String getMiplatformRoot()           { return miplatformRoot == null ? "" : miplatformRoot; }
        public void setMiplatformRoot(String s)     { this.miplatformRoot = s == null ? "" : s; }

        public int  getPackageLevel()             { return packageLevel < 2 ? 5 : Math.min(packageLevel, 10); }
        public void setPackageLevel(int l)        { this.packageLevel = l; }

        public String getPackagePrefix()          { return packagePrefix == null ? "" : packagePrefix; }
        public void setPackagePrefix(String s)    { this.packagePrefix = s == null ? "" : s.trim(); }
    }

    public static class Email {
        private String host     = "";
        private int    port     = 587;
        private String username = "";
        private String password = "";
        private String from     = "";
        private boolean tls     = true;

        public String getHost()             { return host; }
        public void setHost(String h)       { this.host = h == null ? "" : h; }

        public int getPort()                { return port; }
        public void setPort(int p)          { this.port = p; }

        public String getUsername()             { return username; }
        public void setUsername(String u)       { this.username = u == null ? "" : u; }

        public String getPassword()             { return password; }
        public void setPassword(String p)       { this.password = p == null ? "" : p; }

        public String getFrom()                 { return from; }
        public void setFrom(String f)           { this.from = f == null ? "" : f; }

        public boolean isTls()                  { return tls; }
        public void setTls(boolean t)           { this.tls = t; }
    }
}
