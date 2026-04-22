package io.github.claudetoolkit.ui.errorlog;

import io.github.claudetoolkit.ui.metrics.ToolkitMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * v4.4.0 — 자체 에러 모니터링 서비스 (Sentry-style).
 *
 * <p>{@code GlobalExceptionHandler.handleGeneral()} 에서 호출되어 모든 미처리
 * 예외를 영속화. 같은 (예외클래스 + 정규화된 메시지) 조합은 dedupe 되어
 * 같은 ErrorLog 행에 occurrenceCount 만 증가.
 *
 * <p>안전 동작:
 * <ul>
 *   <li>저장 실패 시 절대 원본 요청에 영향을 주지 않음 (예외 삼킴)</li>
 *   <li>스택트레이스 10KB / 메시지 500자 자동 절단</li>
 *   <li>메시지 정규화: 숫자/UUID/타임스탬프 → 플레이스홀더 (dedupe 정확도 향상)</li>
 * </ul>
 */
@Service
public class ErrorLogService {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogService.class);
    private static final int MAX_STACKTRACE_BYTES = 10_000;
    private static final int MAX_MESSAGE_LENGTH   = 500;

    private final ErrorLogRepository repo;

    /** v4.4.0 — 에러 발생률을 Prometheus 메트릭으로도 발행 (Grafana 알람 트리거) */
    @Autowired(required = false)
    private ToolkitMetrics metrics;

    public ErrorLogService(ErrorLogRepository repo) {
        this.repo = repo;
    }

    /**
     * 예외 발생 시 호출. 저장 실패는 silent (운영 영향 0).
     */
    @Transactional
    public void record(Throwable ex, HttpServletRequest req) {
        try {
            String exceptionClass = ex.getClass().getSimpleName();
            String rawMsg = ex.getMessage() != null ? ex.getMessage() : "(메시지 없음)";
            String message = truncate(rawMsg, MAX_MESSAGE_LENGTH);
            String dedupeKey = computeDedupeKey(exceptionClass, normalizeForDedupe(rawMsg));

            // v4.4.0: Prometheus 메트릭 — Grafana 알람 트리거 (저장 실패와 별도)
            if (metrics != null) {
                metrics.recordError(exceptionClass,
                        req != null ? truncate(req.getRequestURI(), 100) : "internal");
            }

            Optional<ErrorLog> existing = repo.findByDedupeKey(dedupeKey);
            if (existing.isPresent()) {
                ErrorLog e = existing.get();
                e.incrementOccurrence();
                e.setLastOccurredAt(LocalDateTime.now());
                // 같은 오류가 다시 발생하면 자동으로 unresolved 로 복귀
                if (e.isResolved()) {
                    e.setResolved(false);
                    e.setResolvedBy(null);
                    e.setResolvedAt(null);
                }
                repo.save(e);
                return;
            }

            ErrorLog e = new ErrorLog();
            e.setLevel("ERROR");
            e.setExceptionClass(exceptionClass);
            e.setMessage(message);
            e.setStackTrace(captureStackTrace(ex));
            e.setDedupeKey(dedupeKey);
            e.setOccurrenceCount(1);
            LocalDateTime now = LocalDateTime.now();
            e.setCreatedAt(now);
            e.setLastOccurredAt(now);
            if (req != null) {
                e.setRequestPath(truncate(req.getRequestURI(), 200));
                e.setRequestMethod(req.getMethod());
                e.setUserAgent(truncate(req.getHeader("User-Agent"), 500));
                e.setClientIp(extractClientIp(req));
            }
            e.setUsername(currentUsername());
            repo.save(e);
        } catch (Exception logFailure) {
            // 절대 원본 요청을 깨뜨리지 않음
            log.warn("ErrorLog 저장 실패 (silent): {}", logFailure.getMessage());
        }
    }

    @Transactional
    public boolean markResolved(long id, String resolvedBy) {
        Optional<ErrorLog> opt = repo.findById(id);
        if (!opt.isPresent()) return false;
        ErrorLog e = opt.get();
        e.setResolved(true);
        e.setResolvedBy(resolvedBy);
        e.setResolvedAt(LocalDateTime.now());
        repo.save(e);
        return true;
    }

    @Transactional
    public boolean markUnresolved(long id) {
        Optional<ErrorLog> opt = repo.findById(id);
        if (!opt.isPresent()) return false;
        ErrorLog e = opt.get();
        e.setResolved(false);
        e.setResolvedBy(null);
        e.setResolvedAt(null);
        repo.save(e);
        return true;
    }

    @Transactional
    public int purgeResolvedOlderThan(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return repo.deleteResolvedOlderThan(cutoff);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private String captureStackTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            ex.printStackTrace(pw);
        }
        String full = sw.toString();
        if (full.length() <= MAX_STACKTRACE_BYTES) return full;
        return full.substring(0, MAX_STACKTRACE_BYTES) + "\n... (절단됨, 전체 " + full.length() + " bytes)";
    }

    /**
     * dedupe 정확도 향상을 위한 메시지 정규화:
     * - 숫자 → "{n}"
     * - UUID → "{uuid}"
     * - 16진수 hash → "{hash}"
     * - 타임스탬프 패턴 → "{ts}"
     *
     * <p>예: "Connection failed at 2026-04-22T14:30:00 to user 12345"
     *       → "Connection failed at {ts} to user {n}"
     */
    private String normalizeForDedupe(String msg) {
        if (msg == null) return "";
        return msg
                // ISO timestamp
                .replaceAll("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?", "{ts}")
                // UUID
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "{uuid}")
                // hex hash (8자 이상)
                .replaceAll("\\b[0-9a-fA-F]{8,}\\b", "{hash}")
                // 일반 숫자
                .replaceAll("\\d+", "{n}");
    }

    private String computeDedupeKey(String exceptionClass, String normalizedMsg) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((exceptionClass + "|" + normalizedMsg).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 미지원 환경 fallback (사실상 발생 안 함)
            return exceptionClass + "|" + (normalizedMsg.length() > 50 ? normalizedMsg.substring(0, 50) : normalizedMsg);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    private String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            // 첫 번째 IP (클라이언트 원본)
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return req.getRemoteAddr();
    }
}
