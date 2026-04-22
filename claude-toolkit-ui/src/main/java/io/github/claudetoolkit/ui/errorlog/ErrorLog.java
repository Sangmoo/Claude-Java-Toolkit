package io.github.claudetoolkit.ui.errorlog;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import java.time.LocalDateTime;

/**
 * v4.4.0 — 자체 구축 에러 모니터링 엔티티 (Sentry-style).
 *
 * <p>{@code GlobalExceptionHandler.handleGeneral()} 에서 발생한 모든 예외를
 * 영속화. 같은 메시지+클래스 조합은 dedupe 되며 발생 횟수만 증가.
 *
 * <p>관리자가 {@code /admin/error-log} 페이지에서:
 * <ul>
 *   <li>최근 오류 목록 조회 (필터: 미해결만 / 기간 / 레벨)</li>
 *   <li>스택트레이스 전체 보기</li>
 *   <li>"해결됨" 마킹 — 같은 dedupeKey 발생 시 자동 unresolved 로 복귀</li>
 * </ul>
 */
@Entity
@Table(name = "error_log",
       indexes = {
           @Index(name = "idx_errlog_created", columnList = "createdAt DESC"),
           @Index(name = "idx_errlog_dedupe",  columnList = "dedupeKey"),
           @Index(name = "idx_errlog_resolved", columnList = "resolved"),
       })
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ERROR / WARN / INFO — 향후 확장 (현재는 ERROR 만 발행) */
    @Column(nullable = false, length = 10)
    private String level = "ERROR";

    /** 예외 클래스 단축명 (예: "NullPointerException") */
    @Column(nullable = false, length = 200)
    private String exceptionClass;

    /** 오류 메시지 — 최대 500자 (DB 제약) */
    @Column(nullable = false, length = 500)
    private String message;

    /** 스택트레이스 전문 (최대 10KB 절단) */
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    /** dedupe 키 — exceptionClass + message 정규화 SHA-256 (같은 오류 그룹화) */
    @Column(nullable = false, length = 64)
    private String dedupeKey;

    /** 같은 dedupeKey 의 발생 횟수 */
    @Column(nullable = false)
    private long occurrenceCount = 1;

    /** 처음 발생 시각 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 마지막 발생 시각 (occurrenceCount 증가 시 갱신) */
    @Column(nullable = false)
    private LocalDateTime lastOccurredAt;

    @Column(length = 200)
    private String requestPath;

    @Column(length = 10)
    private String requestMethod;

    @Column(length = 50)
    private String username;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 50)
    private String clientIp;

    /** ADMIN 이 해결로 마킹한 경우 true — 같은 dedupeKey 가 다시 발생하면 자동 false */
    @Column(nullable = false)
    private boolean resolved = false;

    @Column(length = 50)
    private String resolvedBy;

    @Column
    private LocalDateTime resolvedAt;

    public ErrorLog() {}

    // ── getters / setters ─────────────────────────────────────────────

    public Long getId() { return id; }
    public String getLevel() { return level; }
    public String getExceptionClass() { return exceptionClass; }
    public String getMessage() { return message; }
    public String getStackTrace() { return stackTrace; }
    public String getDedupeKey() { return dedupeKey; }
    public long getOccurrenceCount() { return occurrenceCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastOccurredAt() { return lastOccurredAt; }
    public String getRequestPath() { return requestPath; }
    public String getRequestMethod() { return requestMethod; }
    public String getUsername() { return username; }
    public String getUserAgent() { return userAgent; }
    public String getClientIp() { return clientIp; }
    public boolean isResolved() { return resolved; }
    public String getResolvedBy() { return resolvedBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }

    public void setLevel(String s) { this.level = s; }
    public void setExceptionClass(String s) { this.exceptionClass = s; }
    public void setMessage(String s) { this.message = s; }
    public void setStackTrace(String s) { this.stackTrace = s; }
    public void setDedupeKey(String s) { this.dedupeKey = s; }
    public void setOccurrenceCount(long n) { this.occurrenceCount = n; }
    public void incrementOccurrence() { this.occurrenceCount++; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public void setLastOccurredAt(LocalDateTime t) { this.lastOccurredAt = t; }
    public void setRequestPath(String s) { this.requestPath = s; }
    public void setRequestMethod(String s) { this.requestMethod = s; }
    public void setUsername(String s) { this.username = s; }
    public void setUserAgent(String s) { this.userAgent = s; }
    public void setClientIp(String s) { this.clientIp = s; }
    public void setResolved(boolean b) { this.resolved = b; }
    public void setResolvedBy(String s) { this.resolvedBy = s; }
    public void setResolvedAt(LocalDateTime t) { this.resolvedAt = t; }
}
