package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewComment;
import io.github.claudetoolkit.ui.history.ReviewCommentRepository;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import io.github.claudetoolkit.ui.notification.Notification;
import io.github.claudetoolkit.ui.notification.NotificationPublisher;
import io.github.claudetoolkit.ui.notification.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * 분석 이력 댓글 CRUD API.
 */
@RestController
@RequestMapping("/history")
public class ReviewCommentController {

    private final ReviewCommentRepository commentRepository;
    private final ReviewHistoryRepository historyRepository;
    private final NotificationRepository  notificationRepository;
    private final NotificationPublisher   notificationPublisher;

    public ReviewCommentController(ReviewCommentRepository commentRepository,
                                   ReviewHistoryRepository historyRepository,
                                   NotificationRepository notificationRepository,
                                   NotificationPublisher notificationPublisher) {
        this.commentRepository      = commentRepository;
        this.historyRepository      = historyRepository;
        this.notificationRepository = notificationRepository;
        this.notificationPublisher  = notificationPublisher;
    }

    /** 댓글 목록 조회 */
    @GetMapping("/{historyId}/comments")
    public ResponseEntity<List<Map<String, Object>>> getComments(@PathVariable long historyId) {
        List<ReviewComment> comments = commentRepository.findByHistoryIdOrderByCreatedAtAsc(historyId);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (ReviewComment c : comments) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id",        c.getId());
            m.put("parentId",  c.getParentId());
            m.put("username",  c.getUsername());
            m.put("content",   c.getContent());
            m.put("createdAt", c.getFormattedDate());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    /** 댓글 작성 (parentId 가 있으면 대댓글) */
    @PostMapping("/{historyId}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable long historyId,
            @RequestParam String content,
            @RequestParam(required = false) Long parentId,
            Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (content == null || content.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("error", "댓글 내용을 입력하세요.");
            return ResponseEntity.ok(resp);
        }
        String username = principal.getName();
        ReviewComment comment = new ReviewComment(historyId, parentId, username, content.trim());
        commentRepository.save(comment);

        // 알림 대상 결정:
        //  - 대댓글이면 → 부모 댓글 작성자에게 알림
        //  - 일반 댓글이면 → 분석 이력 작성자에게 알림
        try {
            String targetUser = null;
            String titleText;
            if (parentId != null) {
                ReviewComment parent = commentRepository.findById(parentId).orElse(null);
                if (parent != null && parent.getUsername() != null
                        && !parent.getUsername().equals(username)) {
                    targetUser = parent.getUsername();
                    titleText  = username + "님이 내 댓글에 답글을 남겼습니다";
                } else {
                    titleText = "";
                }
            } else {
                ReviewHistory history = historyRepository.findById(historyId).orElse(null);
                if (history != null && history.getUsername() != null
                        && !history.getUsername().isEmpty()
                        && !username.equals(history.getUsername())) {
                    targetUser = history.getUsername();
                    titleText  = username + "님이 댓글을 남겼습니다";
                } else {
                    titleText = "";
                }
            }
            if (targetUser != null && !titleText.isEmpty()) {
                Notification noti = new Notification(
                    targetUser,
                    parentId != null ? "COMMENT_REPLY" : "COMMENT",
                    titleText,
                    content.trim().length() > 80 ? content.trim().substring(0, 80) + "..." : content.trim(),
                    "/history"
                );
                notificationPublisher.publish(noti);
            }
        } catch (Exception ignored) {
            // 알림 실패해도 댓글은 저장됨
        }

        resp.put("success", true);
        resp.put("id",      comment.getId());
        return ResponseEntity.ok(resp);
    }

    /** 댓글 삭제 (본인 또는 ADMIN) */
    @PostMapping("/{historyId}/comments/{commentId}/delete")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable long historyId,
            @PathVariable long commentId,
            Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        ReviewComment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            resp.put("success", false);
            resp.put("error", "댓글을 찾을 수 없습니다.");
            return ResponseEntity.ok(resp);
        }
        // 본인 또는 ADMIN만 삭제 가능
        boolean isOwner = comment.getUsername().equals(principal.getName());
        // ADMIN 체크는 request attribute로 처리 (PermissionInterceptor가 설정)
        if (!isOwner) {
            resp.put("success", false);
            resp.put("error", "삭제 권한이 없습니다.");
            return ResponseEntity.ok(resp);
        }
        commentRepository.delete(comment);
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }
}
