package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import io.github.claudetoolkit.ui.share.SharedResult;
import io.github.claudetoolkit.ui.share.SharedResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);

    private final ReviewHistoryService   historyService;
    private final SharedResultRepository shareRepository;

    public ShareController(ReviewHistoryService historyService, SharedResultRepository shareRepository) {
        this.historyService  = historyService;
        this.shareRepository = shareRepository;
    }

    /** Create a share link for a history entry. Returns token as plain text. */
    @PostMapping("/history/{id}/share")
    @ResponseBody
    public ResponseEntity<String> createShare(@PathVariable long id, Principal principal) {
        ReviewHistory h = historyService.findById(id);
        if (h == null) return ResponseEntity.notFound().build();

        SharedResult share = new SharedResult(h.getId(), h.getType(), h.getTitle(),
                h.getInputContent(), h.getOutputContent());
        shareRepository.save(share);
        return ResponseEntity.ok(share.getToken());
    }

    // Shared result page rendering removed — SpaViewResolver handles routing.
    // React fetches shared result data via API calls.

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
