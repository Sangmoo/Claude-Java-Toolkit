package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import io.github.claudetoolkit.ui.notification.Notification;
import io.github.claudetoolkit.ui.notification.NotificationPublisher;
import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 리뷰 이력 승인/거절 컨트롤러 (v4.2.x).
 *
 * <p>REVIEWER/ADMIN 권한의 사용자가 리뷰 이력을 Accept(승인) / Reject(거절) 할 수 있고,
 * 결과는 이력 작성자에게 알림으로 전달된다.
 */
@Controller
@RequestMapping("/history")
public class ReviewStatusController {

    public static final String STATUS_PENDING  = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";

    private final ReviewHistoryRepository historyRepository;
    private final AppUserRepository       userRepository;
    private final NotificationPublisher   notificationPublisher;

    public ReviewStatusController(ReviewHistoryRepository historyRepository,
                                  AppUserRepository userRepository,
                                  NotificationPublisher notificationPublisher) {
        this.historyRepository     = historyRepository;
        this.userRepository        = userRepository;
        this.notificationPublisher = notificationPublisher;
    }

    /**
     * 리뷰 이력 승인/거절.
     * <ul>
     *   <li>REVIEWER/ADMIN 만 호출 가능 (VIEWER 는 403)</li>
     *   <li>status 파라미터: ACCEPTED 또는 REJECTED</li>
     *   <li>이력 작성자에게 알림 발송 (자기 자신의 이력엔 발송 안 함)</li>
     * </ul>
     */
    @PostMapping("/{historyId}/review-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateReviewStatus(
            @PathVariable long historyId,
            @RequestParam String status,
            @RequestParam(required = false, defaultValue = "") String note,
            Principal principal) {

        Map<String, Object> resp = new LinkedHashMap<String, Object>();

        if (principal == null) {
            resp.put("success", false);
            resp.put("error",   "로그인이 필요합니다.");
            return ResponseEntity.ok(resp);
        }
        String reviewer = principal.getName();

        // 권한 체크 — REVIEWER 또는 ADMIN 만 허용
        AppUser user = userRepository.findByUsername(reviewer).orElse(null);
        if (user == null) {
            resp.put("success", false);
            resp.put("error",   "사용자를 찾을 수 없습니다.");
            return ResponseEntity.ok(resp);
        }
        String role = user.getRole();
        if (!"REVIEWER".equals(role) && !"ADMIN".equals(role)) {
            resp.put("success", false);
            resp.put("error",   "리뷰 승인/거절 권한이 없습니다. (REVIEWER 또는 ADMIN 필요)");
            return ResponseEntity.ok(resp);
        }

        // 상태 검증
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!STATUS_ACCEPTED.equals(normalized) && !STATUS_REJECTED.equals(normalized) && !STATUS_PENDING.equals(normalized)) {
            resp.put("success", false);
            resp.put("error",   "잘못된 상태값입니다. (ACCEPTED / REJECTED / PENDING)");
            return ResponseEntity.ok(resp);
        }

        ReviewHistory history = historyRepository.findById(historyId).orElse(null);
        if (history == null) {
            resp.put("success", false);
            resp.put("error",   "리뷰 이력을 찾을 수 없습니다.");
            return ResponseEntity.ok(resp);
        }

        history.setReviewStatus(normalized);
        history.setReviewedBy(reviewer);
        history.setReviewedAt(LocalDateTime.now());
        history.setReviewNote(note != null ? note.trim() : null);
        historyRepository.save(history);

        // 알림 발송 — 이력 작성자가 리뷰어 본인이 아닐 때만
        try {
            String targetUser = history.getUsername();
            if (targetUser != null && !targetUser.isEmpty() && !reviewer.equals(targetUser)) {
                String actionLabel = STATUS_ACCEPTED.equals(normalized) ? "승인" : STATUS_REJECTED.equals(normalized) ? "거절" : "대기";
                String type        = STATUS_ACCEPTED.equals(normalized) ? "REVIEW_ACCEPTED" : "REVIEW_REJECTED";
                String title       = reviewer + "님이 리뷰를 " + actionLabel + "했습니다";
                String body        = (history.getTitle() != null ? history.getTitle() : "(제목 없음)")
                        + (note != null && !note.isEmpty() ? " — " + note : "");
                Notification noti  = new Notification(targetUser, type, title, body, "/history");
                notificationPublisher.publish(noti);
            }
        } catch (Exception ignored) {}

        resp.put("success",      true);
        resp.put("reviewStatus", normalized);
        resp.put("reviewedBy",   reviewer);
        return ResponseEntity.ok(resp);
    }
}
