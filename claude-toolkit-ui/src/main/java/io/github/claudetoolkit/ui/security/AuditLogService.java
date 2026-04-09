package io.github.claudetoolkit.ui.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 감사 로그 저장 및 조회 서비스.
 *
 * <ul>
 *   <li>필터에서 호출 — 동기 저장 (H2 로컬 DB라 지연 무시)</li>
 *   <li>매일 새벽 3시에 90일 이전 로그 자동 삭제</li>
 * </ul>
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repo;

    public AuditLogService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void log(String endpoint, String method, String ip,
                    String userAgent, Integer statusCode, boolean apiKeyUsed) {
        log(endpoint, method, ip, userAgent, statusCode, apiKeyUsed, null);
    }

    @Transactional
    public void log(String endpoint, String method, String ip,
                    String userAgent, Integer statusCode, boolean apiKeyUsed, String username) {
        log(endpoint, method, ip, userAgent, statusCode, apiKeyUsed, username, null);
    }

    @Transactional
    public void log(String endpoint, String method, String ip,
                    String userAgent, Integer statusCode, boolean apiKeyUsed, String username, Long durationMs) {
        try {
            repo.save(new AuditLog(endpoint, method, ip, userAgent, statusCode, apiKeyUsed, username, durationMs));
        } catch (Exception e) {
            log.error("[AuditLog] save failed: " + e.getMessage());
        }
    }

    public List<AuditLog> findRecent() {
        return repo.findTop300ByOrderByCreatedAtDesc();
    }

    public org.springframework.data.domain.Page<AuditLog> findPaged(int page, int size) {
        return repo.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(page, size));
    }

    public org.springframework.data.domain.Page<AuditLog> findFiltered(
            String username, java.time.LocalDateTime since, int page, int size) {
        return repo.findFiltered(
                username != null && !username.isEmpty() ? username : null,
                since,
                org.springframework.data.domain.PageRequest.of(page, size));
    }

    public java.util.List<Object[]> getUserSummary() {
        return repo.countByUsername();
    }

    public long totalCount() {
        return repo.count();
    }

    public long countToday() {
        return repo.countByCreatedAtAfter(LocalDateTime.now().toLocalDate().atStartOfDay());
    }

    public long countThisHour() {
        return repo.countByCreatedAtAfter(LocalDateTime.now().minusHours(1));
    }

    /** 90일 이전 로그 자동 삭제 — 매일 새벽 3:00 */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        repo.deleteByCreatedAtBefore(cutoff);
    }
}
