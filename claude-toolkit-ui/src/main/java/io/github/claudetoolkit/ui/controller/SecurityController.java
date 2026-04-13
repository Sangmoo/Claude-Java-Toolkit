package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.security.AuditLog;
import io.github.claudetoolkit.ui.security.AuditLogService;
import io.github.claudetoolkit.ui.security.SecuritySettings;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.*;

/**
 * 보안 설정 컨트롤러 (/security)
 *
 * <ul>
 *   <li>GET  /security             — 보안 설정 페이지</li>
 *   <li>GET  /security/audit-log   — 감사 로그 JSON</li>
 *   <li>POST /security/api-key     — API 키 설정/활성화/비활성화</li>
 *   <li>POST /security/settings-password — Settings 비밀번호 설정</li>
 *   <li>POST /security/settings-lock    — 설정 잠금 활성화/비활성화</li>
 * </ul>
 */
@Controller
@RequestMapping("/security")
public class SecurityController {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom RNG = new SecureRandom();
    private static final String CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private final AuditLogService auditLogService;

    public SecurityController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // ── 감사 로그 JSON ────────────────────────────────────────────────────────

    @GetMapping("/audit-log")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> auditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String period) {

        // 기간 필터 (KST 기준)
        java.time.ZoneId KST = java.time.ZoneId.of("Asia/Seoul");
        java.time.LocalDateTime since = null;
        if ("1h".equals(period)) since = java.time.LocalDateTime.now(KST).minusHours(1);
        else if ("today".equals(period)) since = java.time.LocalDate.now(KST).atStartOfDay();
        else if ("7d".equals(period)) since = java.time.LocalDateTime.now(KST).minusDays(7);
        else if ("30d".equals(period)) since = java.time.LocalDateTime.now(KST).minusDays(30);

        org.springframework.data.domain.Page<AuditLog> pageResult;
        if ((user != null && !user.isEmpty()) || since != null) {
            pageResult = auditLogService.findFiltered(user, since, page, size);
        } else {
            pageResult = auditLogService.findPaged(page, size);
        }

        List<AuditLog> logs = pageResult.getContent();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (AuditLog l : logs) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id",          l.getId());
            m.put("endpoint",    l.getEndpoint());
            m.put("method",      l.getMethod());
            m.put("ip",          l.getIp());
            m.put("statusCode",  l.getStatusCode());
            m.put("apiKeyUsed",  l.isApiKeyUsed());
            m.put("username",    l.getUsername());
            m.put("actionType",  l.getActionType());
            m.put("menuName",    l.getMenuName());
            m.put("durationMs",  l.getDurationMs());
            m.put("formattedDate", l.getFormattedDate());
            m.put("statusColor", l.getStatusBadgeColor());
            items.add(m);
        }

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("content", items);
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", pageResult.getTotalPages());
        response.put("totalElements", pageResult.getTotalElements());
        return ResponseEntity.ok(response);
    }

    /** 감사 로그 CSV 내보내기 */
    @GetMapping("/audit-log/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportAuditLogCsv() {
        List<AuditLog> logs = auditLogService.findRecent();
        StringBuilder csv = new StringBuilder();
        csv.append("시간,사용자,기능 메뉴,액션,메서드,엔드포인트,IP,상태,응답시간(ms)\n");
        for (AuditLog l : logs) {
            csv.append(l.getFormattedDate()).append(',');
            csv.append(l.getUsername() != null ? l.getUsername() : "-").append(',');
            csv.append(l.getMenuName()).append(',');
            csv.append(l.getActionType()).append(',');
            csv.append(l.getMethod()).append(',');
            csv.append('"').append((l.getEndpoint() != null ? l.getEndpoint() : "").replace("\"","\"\"")).append("\",");
            csv.append(l.getIp() != null ? l.getIp() : "-").append(',');
            csv.append(l.getStatusCode() != null ? l.getStatusCode() : "-").append(',');
            csv.append(l.getDurationMs() != null ? l.getDurationMs() : "-").append('\n');
        }
        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // BOM for Excel 한국어 호환
        byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
        byte[] result2 = new byte[bom.length + bytes.length];
        System.arraycopy(bom, 0, result2, 0, bom.length);
        System.arraycopy(bytes, 0, result2, bom.length, bytes.length);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"audit-log.csv\"")
                .header("Content-Type", "text/csv;charset=UTF-8")
                .body(result2);
    }

    // ── API 키 관리 ──────────────────────────────────────────────────────────

    /**
     * 새 API 키 생성. 평문 키를 응답에 포함 (한 번만 표시).
     * 해시는 SecuritySettings에 저장.
     */
    @PostMapping("/api-key/generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateApiKey() {
        Map<String, Object> resp = new HashMap<String, Object>();
        try {
            String rawKey = generateKey(40);
            String hash   = ENCODER.encode(rawKey);

            SecuritySettings settings = SecuritySettings.load();
            settings.setApiKeyHash(hash);
            settings.setApiKeyEnabled(true);
            settings.save();

            resp.put("success", true);
            resp.put("rawKey",  rawKey);
            resp.put("message", "API 키가 생성되었습니다. 이 키는 다시 확인할 수 없습니다.");
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** API 키 인증 활성화/비활성화 토글 */
    @PostMapping("/api-key/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleApiKey(
            @RequestParam("enabled") boolean enabled) {
        Map<String, Object> resp = new HashMap<String, Object>();
        SecuritySettings settings = SecuritySettings.load();
        if (enabled && settings.getApiKeyHash() == null) {
            resp.put("success", false);
            resp.put("error", "API 키를 먼저 생성해주세요.");
            return ResponseEntity.ok(resp);
        }
        settings.setApiKeyEnabled(enabled);
        settings.save();
        resp.put("success", true);
        resp.put("enabled", enabled);
        return ResponseEntity.ok(resp);
    }

    /** API 키 삭제 */
    @PostMapping("/api-key/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteApiKey() {
        Map<String, Object> resp = new HashMap<String, Object>();
        SecuritySettings settings = SecuritySettings.load();
        settings.setApiKeyHash(null);
        settings.setApiKeyEnabled(false);
        settings.save();
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    // ── Settings 비밀번호 잠금 ───────────────────────────────────────────────

    /** Settings 비밀번호 설정/변경 */
    @PostMapping("/settings-password/set")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setSettingsPassword(
            @RequestParam("password") String password) {
        Map<String, Object> resp = new HashMap<String, Object>();
        if (password == null || password.length() < 4) {
            resp.put("success", false);
            resp.put("error", "비밀번호는 4자 이상이어야 합니다.");
            return ResponseEntity.ok(resp);
        }
        String hash = ENCODER.encode(password);
        SecuritySettings settings = SecuritySettings.load();
        settings.setSettingsPasswordHash(hash);
        settings.setSettingsLockEnabled(true);
        settings.save();
        resp.put("success", true);
        resp.put("message", "비밀번호가 설정되었습니다. Settings 잠금이 활성화됩니다.");
        return ResponseEntity.ok(resp);
    }

    /** Settings 잠금 활성화/비활성화 */
    @PostMapping("/settings-password/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleSettingsLock(
            @RequestParam("enabled") boolean enabled) {
        Map<String, Object> resp = new HashMap<String, Object>();
        SecuritySettings settings = SecuritySettings.load();
        if (enabled && settings.getSettingsPasswordHash() == null) {
            resp.put("success", false);
            resp.put("error", "비밀번호를 먼저 설정해주세요.");
            return ResponseEntity.ok(resp);
        }
        settings.setSettingsLockEnabled(enabled);
        settings.save();
        resp.put("success", true);
        resp.put("enabled", enabled);
        return ResponseEntity.ok(resp);
    }

    /** Settings 비밀번호 삭제 */
    @PostMapping("/settings-password/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteSettingsPassword() {
        Map<String, Object> resp = new HashMap<String, Object>();
        SecuritySettings settings = SecuritySettings.load();
        settings.setSettingsPasswordHash(null);
        settings.setSettingsLockEnabled(false);
        settings.save();
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    // ── Settings 잠금 해제 ───────────────────────────────────────────────────

    /** Settings 잠금 해제 처리 */
    @PostMapping("/settings-unlock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> settingsUnlock(
            @RequestParam("password") String password,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<String, Object>();
        SecuritySettings settings = SecuritySettings.load();
        if (!settings.isSettingsLockEnabled() || settings.getSettingsPasswordHash() == null) {
            session.setAttribute("settingsUnlocked", Boolean.TRUE);
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        }
        try {
            if (ENCODER.matches(password, settings.getSettingsPasswordHash())) {
                session.setAttribute("settingsUnlocked", Boolean.TRUE);
                resp.put("success", true);
            } else {
                resp.put("success", false);
                resp.put("error", "비밀번호가 올바르지 않습니다.");
            }
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", "검증 중 오류가 발생했습니다.");
        }
        return ResponseEntity.ok(resp);
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────────

    private String generateKey(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
