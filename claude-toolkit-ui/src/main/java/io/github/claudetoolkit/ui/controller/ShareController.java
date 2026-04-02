package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import io.github.claudetoolkit.ui.share.SharedResult;
import io.github.claudetoolkit.ui.share.SharedResultRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class ShareController {

    private final ReviewHistoryService   historyService;
    private final SharedResultRepository shareRepository;

    public ShareController(ReviewHistoryService historyService, SharedResultRepository shareRepository) {
        this.historyService  = historyService;
        this.shareRepository = shareRepository;
    }

    /** Create a share link for a history entry. Returns token as plain text. */
    @PostMapping("/history/{id}/share")
    @ResponseBody
    public ResponseEntity<String> createShare(@PathVariable long id) {
        ReviewHistory h = historyService.findById(id);
        if (h == null) return ResponseEntity.notFound().build();

        SharedResult share = new SharedResult(h.getId(), h.getType(), h.getTitle(),
                h.getInputContent(), h.getOutputContent());
        shareRepository.save(share);
        return ResponseEntity.ok(share.getToken());
    }

    /** Display a shared result page. */
    @GetMapping("/share/{token}")
    public String viewShare(@PathVariable String token, Model model) {
        SharedResult share = shareRepository.findByToken(token).orElse(null);
        if (share == null) {
            model.addAttribute("error", "공유 링크를 찾을 수 없습니다.");
            return "share/not-found";
        }
        if (share.isExpired()) {
            model.addAttribute("error", "공유 링크가 만료되었습니다. (7일 경과)");
            return "share/not-found";
        }
        model.addAttribute("share", share);
        return "share/view";
    }

    /** Cleanup expired shares daily at 3AM. */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpired() {
        List<SharedResult> expired = shareRepository.findExpired(LocalDateTime.now());
        if (!expired.isEmpty()) {
            shareRepository.deleteAll(expired);
            System.out.println("[Share] Cleaned up " + expired.size() + " expired share links.");
        }
    }
}
