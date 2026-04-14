package io.github.claudetoolkit.ui.security;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자별 일일 API 호출 카운터 (v4.2.1).
 *
 * <p>서버 재시작 후에도 일일/월간 한도를 유지하기 위한 영속 엔티티.
 * RateLimitService가 이 테이블을 읽고 씁니다.</p>
 *
 * <p>분/시간 단위 짧은 윈도우는 여전히 메모리에서 관리합니다 (재시작 시 리셋 허용).</p>
 */
@Entity
@Table(name = "user_api_usage",
       indexes = {
           @Index(name = "idx_usage_user_date", columnList = "username,usageDate", unique = true),
           @Index(name = "idx_usage_date", columnList = "usageDate"),
       })
public class UserApiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false)
    private int requestCount;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public UserApiUsage() {}

    public UserApiUsage(String username, LocalDate usageDate) {
        this.username = username;
        this.usageDate = usageDate;
        this.requestCount = 0;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDate getUsageDate() { return usageDate; }
    public void setUsageDate(LocalDate usageDate) { this.usageDate = usageDate; }

    public int getRequestCount() { return requestCount; }
    public void setRequestCount(int requestCount) { this.requestCount = requestCount; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int increment() {
        this.requestCount++;
        this.updatedAt = LocalDateTime.now();
        return this.requestCount;
    }
}
