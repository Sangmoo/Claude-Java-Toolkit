package io.github.claudetoolkit.ui.favorites;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * JPA entity representing a saved favorite item (starred review result).
 * Persisted to H2 file database at ~/.claude-toolkit/history-db.
 */
@Entity
@Table(name = "favorites")
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String type;       // e.g. "SQL_REVIEW", "CODE_REVIEW", etc.

    @Column(length = 500)
    private String tag;        // optional tag / category

    @Lob
    @Column(nullable = false)
    private String inputContent;

    @Lob
    @Column(nullable = false)
    private String outputContent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Required by JPA — do not use directly */
    protected Favorite() {}

    public Favorite(String type, String title, String tag,
                    String inputContent, String outputContent) {
        this.type          = type;
        this.title         = title;
        this.tag           = (tag != null && !tag.trim().isEmpty()) ? tag.trim() : "";
        this.inputContent  = inputContent;
        this.outputContent = outputContent;
        this.createdAt     = LocalDateTime.now();
    }

    // ── Derived helpers ───────────────────────────────────────────────────────

    public String getTypeLabel() {
        if ("SQL_REVIEW".equals(type))     return "SQL 리뷰";
        if ("SQL_SECURITY".equals(type))   return "SQL 보안";
        if ("CODE_REVIEW".equals(type))    return "코드 리뷰";
        if ("CODE_REVIEW_SEC".equals(type))return "코드 보안";
        if ("DOC_GEN".equals(type))        return "기술 문서";
        if ("CODE_CONVERT".equals(type))   return "코드 변환";
        if ("MOCK_DATA".equals(type))      return "Mock 데이터";
        if ("COMPLEXITY".equals(type))     return "복잡도 분석";
        if ("MIGRATION".equals(type))      return "마이그레이션";
        return type;
    }

    public String getTypeBadgeColor() {
        if ("SQL_REVIEW".equals(type))     return "#f97316";
        if ("SQL_SECURITY".equals(type))   return "#ef4444";
        if ("CODE_REVIEW".equals(type))    return "#3b82f6";
        if ("CODE_REVIEW_SEC".equals(type))return "#ef4444";
        if ("DOC_GEN".equals(type))        return "#22c55e";
        if ("CODE_CONVERT".equals(type))   return "#a855f7";
        if ("MOCK_DATA".equals(type))      return "#f59e0b";
        if ("COMPLEXITY".equals(type))     return "#06b6d4";
        if ("MIGRATION".equals(type))      return "#ec4899";
        return "#94a3b8";
    }

    public String getOutputPreview() {
        if (outputContent == null || outputContent.isEmpty()) return "(내용 없음)";
        String clean = outputContent.replaceAll("#+ ", "").replaceAll("\\*\\*", "").replaceAll("[`\\[\\]]", "");
        return clean.length() > 120 ? clean.substring(0, 120) + "…" : clean;
    }

    public String getFormattedDate() {
        return createdAt != null
                ? createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : "-";
    }

    public List<String> getTagList() {
        if (tag == null || tag.trim().isEmpty()) return Arrays.asList();
        return Arrays.asList(tag.split("[,;\\s]+"));
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public long getId()                        { return id; }
    public void setId(long id)                 { this.id = id; }

    public String getType()                    { return type; }
    public void setType(String type)           { this.type = type; }

    public String getTitle()                   { return title; }
    public void setTitle(String title)         { this.title = title; }

    public String getTag()                     { return tag; }
    public void setTag(String tag)             { this.tag = tag; }

    public String getInputContent()            { return inputContent; }
    public void setInputContent(String v)      { this.inputContent = v; }

    public String getOutputContent()           { return outputContent; }
    public void setOutputContent(String v)     { this.outputContent = v; }

    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
}
