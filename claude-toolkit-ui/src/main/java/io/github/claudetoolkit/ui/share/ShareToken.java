package io.github.claudetoolkit.ui.share;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 분석 결과 공유 토큰 엔티티.
 * 7일 만료, 로그인 불필요 독립 뷰 제공.
 */
@Entity
@Table(name = "share_token")
public class ShareToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String token;

    @Column(nullable = false)
    private Long historyId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, length = 50)
    private String createdBy;

    @Column(nullable = false)
    private int viewCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ShareToken() {}

    public ShareToken(String token, Long historyId, String createdBy) {
        this.token     = token;
        this.historyId = historyId;
        this.createdBy = createdBy;
        this.expiresAt = LocalDateTime.now().plusDays(7);
        this.viewCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId()                    { return id; }
    public String getToken()               { return token; }
    public Long getHistoryId()             { return historyId; }
    public LocalDateTime getExpiresAt()    { return expiresAt; }
    public String getCreatedBy()           { return createdBy; }
    public int getViewCount()              { return viewCount; }
    public LocalDateTime getCreatedAt()    { return createdAt; }

    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
