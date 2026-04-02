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
    /** Optional project context memo — prepended to all Claude prompts when set. */
    private String  projectContext = "";
    /** Optional Claude model override — overrides application.yml default when non-blank. */
    private String  claudeModel    = ""; // 빈 문자열이면 application.yml 기본값 사용
    /** Optional accent color override (hex, e.g. "#f97316"). Empty means use default. */
    private String  accentColor    = "";

    public Db getDb()             { return db; }
    public void setDb(Db db)      { this.db = db; }

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
}
