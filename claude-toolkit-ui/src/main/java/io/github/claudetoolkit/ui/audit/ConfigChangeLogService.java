package io.github.claudetoolkit.ui.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * v4.7.x — Settings/권한/DB프로필/보안 변경을 감사 로그에 기록.
 *
 * <p>{@link ConfigChangeLog} 엔티티 영속화 + *민감 값 자동 마스킹*. 호출자는
 * {@link #recordIfChanged(String, String, String, String, String, boolean)}
 * 한 줄로 변경 이력을 남길 수 있다 (값이 같으면 자동 무시).
 *
 * <p>현재 사용자 / IP 는 SecurityContext + RequestContextHolder 에서 자동 추출.
 * 백그라운드 스레드에서 호출 시 둘 다 null → "(system)" 으로 기록.
 */
@Service
@Transactional
public class ConfigChangeLogService {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeLogService.class);

    public static final String CATEGORY_SETTINGS    = "SETTINGS";
    public static final String CATEGORY_PERMISSION  = "PERMISSION";
    public static final String CATEGORY_DB_PROFILE  = "DB_PROFILE";
    public static final String CATEGORY_SECURITY    = "SECURITY";
    public static final String CATEGORY_ROI         = "ROI";

    private final ConfigChangeLogRepository repo;

    public ConfigChangeLogService(ConfigChangeLogRepository repo) {
        this.repo = repo;
    }

    /**
     * 값이 변경된 경우에만 로그 기록. 값이 같으면 noop.
     *
     * @param sensitive true 면 oldValue/newValue 자동 마스킹
     */
    public void recordIfChanged(String configKey, String configLabel, String category,
                                String oldValue, String newValue, boolean sensitive) {
        // null 안전 비교 — 둘 다 null 또는 빈문자열이면 변경 없음으로 간주
        String oldNorm = oldValue == null ? "" : oldValue;
        String newNorm = newValue == null ? "" : newValue;
        if (Objects.equals(oldNorm, newNorm)) return;

        try {
            String maskedOld = sensitive ? maskValue(oldValue) : truncate(oldValue, 4000);
            String maskedNew = sensitive ? maskValue(newValue) : truncate(newValue, 4000);
            ConfigChangeLog rec = new ConfigChangeLog(
                    configKey, configLabel, category,
                    maskedOld, maskedNew, sensitive,
                    currentUsername(), currentIp());
            repo.save(rec);
        } catch (Exception e) {
            // 감사 로그 실패가 *원래 작업* 을 방해하면 안 됨 — 워닝만 남기고 계속
            log.warn("[ConfigChangeLog] 기록 실패 key={} : {}", configKey, e.getMessage());
        }
    }

    /**
     * 단순 이벤트 기록 (변경 비교 없이 무조건 기록). 권한 부여/회수 같은
     * "이벤트 자체" 가 의미 있는 경우.
     */
    public void recordEvent(String configKey, String configLabel, String category,
                             String description) {
        try {
            ConfigChangeLog rec = new ConfigChangeLog(
                    configKey, configLabel, category,
                    null, truncate(description, 4000), false,
                    currentUsername(), currentIp());
            repo.save(rec);
        } catch (Exception e) {
            log.warn("[ConfigChangeLog] 이벤트 기록 실패 key={} : {}", configKey, e.getMessage());
        }
    }

    // ── 마스킹 ─────────────────────────────────────────────────────────────

    /**
     * 민감 값 마스킹 — 길이 정보는 살리되 내용은 가려야 함.
     * 8자 미만: 전체 ****
     * 8자 이상: 앞 4자 + ... + 뒤 2자
     * URL 패턴: query 파라미터의 token 류만 마스킹, host/path 는 노출
     */
    public static String maskValue(String s) {
        if (s == null || s.isEmpty()) return s;
        // URL 류 (?token=xxx, /hooks/xxx) 우선 마스킹
        if (s.startsWith("http")) return maskUrl(s);
        if (s.length() < 8) return "****";
        return s.substring(0, 4) + "..." + s.substring(s.length() - 2)
                + " (총 " + s.length() + "자)";
    }

    private static String maskUrl(String url) {
        // /hooks/T01/B02/{token} 류 — 마지막 segment 마스킹
        try {
            int q = url.indexOf('?');
            String base = q >= 0 ? url.substring(0, q) : url;
            int last = base.lastIndexOf('/');
            if (last > 0 && last < base.length() - 4) {
                return base.substring(0, last + 1) + "****";
            }
            return base;
        } catch (Exception e) {
            return "****";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + " ...(잘림)";
    }

    // ── 호출자 컨텍스트 ────────────────────────────────────────────────────

    private static String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return "(system)";
    }

    private static String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (Exception ignored) {
            return null;
        }
    }
}
