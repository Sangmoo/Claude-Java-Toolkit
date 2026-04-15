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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String inputContent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String outputContent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * v4.2.7 — 소유자. DataRestController.favorites() 가 f.username = :u 로
     * 필터하는데 이 필드가 없어서 기존에 모든 즐겨찾기가 빈 목록으로 반환되던 버그가 있었다.
     */
    @Column(length = 100)
    private String username;

    /**
     * v4.2.7 — 이력에서 별표(star)로 저장된 경우의 원본 ReviewHistory ID.
     * 프론트엔드에서 이력 목록의 별 아이콘을 "즐겨찾기됨" 상태로 표시하기 위해 사용.
     * 직접 저장(/favorites/save) 경로에서는 null.
     */
    @Column(name = "history_id")
    private Long historyId;

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
        if ("EXPLAIN_PLAN".equals(type))    return "실행계획";
        if ("HARNESS_REVIEW".equals(type)) return "하네스 리뷰";
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
        if ("EXPLAIN_PLAN".equals(type))    return "#3b82f6";
        if ("HARNESS_REVIEW".equals(type)) return "#8b5cf6";
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

    public String getUsername()                { return username; }
    public void setUsername(String username)   { this.username = username; }

    public Long getHistoryId()                 { return historyId; }
    public void setHistoryId(Long historyId)   { this.historyId = historyId; }
}
