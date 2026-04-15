package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import io.github.claudetoolkit.ui.share.SharedResult;
import io.github.claudetoolkit.ui.share.SharedResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);

    private final ReviewHistoryService   historyService;
    private final SharedResultRepository shareRepository;

    public ShareController(ReviewHistoryService historyService, SharedResultRepository shareRepository) {
        this.historyService  = historyService;
        this.shareRepository = shareRepository;
    }

    /**
     * Create a share link for a history entry.
     *
     * <p>v4.2.8 — JSON 응답으로 전환 + shortUrl 반환 (기존 plain text 토큰 호환 필드도 포함).
     * 링크는 생성 후 7일간 유효, 로그인 없이 누구나 read-only 로 접근 가능 (`/share/{token}`).
     */
    @PostMapping("/history/{id}/share")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createShare(@PathVariable long id, Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        ReviewHistory h = historyService.findById(id);
        if (h == null) {
            resp.put("success", false);
            resp.put("error",   "이력을 찾을 수 없습니다.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }

        SharedResult share = new SharedResult(h.getId(), h.getType(), h.getTitle(),
                h.getInputContent(), h.getOutputContent());
        shareRepository.save(share);

        resp.put("success",    true);
        resp.put("token",      share.getToken());
        resp.put("shareUrl",   "/share/" + share.getToken());
        resp.put("expiresAt",  share.getExpiresAt().toString());
        resp.put("remaining",  share.getRemainingDaysText());
        return ResponseEntity.ok(resp);
    }

    /**
     * v4.2.8 — GET /share/{token} — 공유된 이력을 JSON 으로 조회.
     *
     * <p>produces="application/json" 으로 제한하여 <b>브라우저 navigation</b>
     * (Accept: text/html) 은 이 핸들러를 타지 않고 SPA 로 떨어지게 한다.
     * ShareViewPage 가 fetch() 로 호출할 때만 이 엔드포인트에 도달한다.
     *
     * <p>로그인 없이 접근 가능 (SecurityConfig 에서 `/share/**` permitAll).
     * 만료된 링크는 410 Gone.
     */
    @GetMapping(value = "/share/{token}", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> viewShare(@PathVariable String token) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        SharedResult share = shareRepository.findByToken(token).orElse(null);
        if (share == null) {
            resp.put("success", false);
            resp.put("error",   "공유 링크가 존재하지 않습니다.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
        }
        if (share.isExpired()) {
            resp.put("success", false);
            resp.put("error",   "공유 링크가 만료되었습니다. (생성 후 7일간 유효)");
            return ResponseEntity.status(HttpStatus.GONE).body(resp);
        }
        // ShareViewPage 가 기대하는 필드명과 호환 — menuName/inputText/resultText 를 매핑
        resp.put("success",       true);
        resp.put("token",         share.getToken());
        resp.put("menuName",      share.getType());
        resp.put("title",         share.getTitle());
        resp.put("inputText",     share.getInputContent());
        resp.put("resultText",    share.getOutputContent());
        resp.put("sharedAt",      share.getFormattedCreatedAt());
        resp.put("remaining",     share.getRemainingDaysText());
        resp.put("expiresAt",     share.getExpiresAt().toString());
        return ResponseEntity.ok(resp);
    }

    /** Cleanup expired shares daily at 3AM. */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpired() {
        List<SharedResult> expired = shareRepository.findExpired(LocalDateTime.now());
        if (!expired.isEmpty()) {
            shareRepository.deleteAll(expired);
            log.info("[Share] Cleaned up " + expired.size() + " expired share links.");
        }
    }
}
