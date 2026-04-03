package io.github.claudetoolkit.ui.dbprofile;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Saved Oracle DB connection profile.
 * Multiple profiles can be stored and switched into the active settings at runtime.
 */
@Entity
@Table(name = "db_profile")
public class DbProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false, length = 200)
    private String username;

    @Column(nullable = false, length = 500)
    private String password;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected DbProfile() {}

    public DbProfile(String name, String url, String username, String password, String description) {
        this.name        = name;
        this.url         = url;
        this.username    = username;
        this.password    = password;
        this.description = description != null ? description : "";
        this.createdAt   = LocalDateTime.now();
    }

    public String getFormattedDate() {
        return createdAt != null
                ? createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : "-";
    }

    public String getMaskedUrl() {
        if (url == null) return "";
        // Show host:port/service but hide credentials embedded in URL
        return url.length() > 80 ? url.substring(0, 77) + "..." : url;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId()                         { return id; }
    public void setId(Long id)                  { this.id = id; }

    public String getName()                     { return name; }
    public void setName(String name)            { this.name = name; }

    public String getUrl()                      { return url; }
    public void setUrl(String url)              { this.url = url; }

    public String getUsername()                 { return username; }
    public void setUsername(String username)    { this.username = username; }

    public String getPassword()                 { return password; }
    public void setPassword(String password)    { this.password = password; }

    public String getDescription()              { return description; }
    public void setDescription(String d)        { this.description = d; }

    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void setCreatedAt(LocalDateTime v)   { this.createdAt = v; }
}
