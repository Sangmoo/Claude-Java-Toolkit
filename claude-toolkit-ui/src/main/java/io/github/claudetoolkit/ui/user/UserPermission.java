package io.github.claudetoolkit.ui.user;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 사용자별 프로그램 기능 권한 엔티티.
 *
 * <p>RBAC 역할(ADMIN/REVIEWER/VIEWER)과 별도로, 개별 사용자에게
 * 특정 기능 메뉴의 접근을 허용/차단합니다.
 *
 * <p>featureKey 예시: "workspace", "advisor", "sql-translate", "harness", "settings" 등
 */
@Entity
@Table(name = "user_permission", indexes = {
    @Index(name = "idx_perm_user", columnList = "userId")
})
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 기능 식별 키 (URL 경로 기반, 예: "workspace", "advisor", "harness") */
    @Column(nullable = false, length = 50)
    private String featureKey;

    /** 허용 여부 (true=허용, false=차단) */
    @Column(nullable = false)
    private boolean allowed = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected UserPermission() {}

    public UserPermission(Long userId, String featureKey, boolean allowed) {
        this.userId     = userId;
        this.featureKey = featureKey;
        this.allowed    = allowed;
        this.createdAt  = LocalDateTime.now();
    }

    public Long getId()            { return id; }
    public Long getUserId()        { return userId; }
    public String getFeatureKey()  { return featureKey; }
    public boolean isAllowed()     { return allowed; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setAllowed(boolean allowed) { this.allowed = allowed; }
}
