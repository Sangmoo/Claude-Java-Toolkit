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

        public String getScanPath()         { return scanPath; }
        public void setScanPath(String p)   { this.scanPath = p; }
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
