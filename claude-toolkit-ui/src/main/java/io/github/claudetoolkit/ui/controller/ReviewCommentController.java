package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewComment;
import io.github.claudetoolkit.ui.history.ReviewCommentRepository;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import io.github.claudetoolkit.ui.notification.Notification;
import io.github.claudetoolkit.ui.notification.NotificationPublisher;
import io.github.claudetoolkit.ui.notification.NotificationRepository;
import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 분석 이력 댓글 CRUD API.
 */
@RestController
@RequestMapping("/history")
public class ReviewCommentController {

    /**
     * v4.2.7 — 댓글 본문의 @username 멘션 감지 정규식.
     * username 허용문자: 영숫자, 언더스코어, 점, 하이픈. 선행 경계: 문자열 시작 또는 공백/개행.
     */
    private static final Pattern MENTION_PATTERN =
            Pattern.compile("(?:^|\\s)@([A-Za-z0-9_.\\-]+)");

    private final ReviewCommentRepository commentRepository;
    private final ReviewHistoryRepository historyRepository;
    private final NotificationRepository  notificationRepository;
    private final NotificationPublisher   notificationPublisher;
    private final AppUserRepository       userRepository;

    public ReviewCommentController(ReviewCommentRepository commentRepository,
                                   ReviewHistoryRepository historyRepository,
                                   NotificationRepository notificationRepository,
                                   NotificationPublisher notificationPublisher,
                                   AppUserRepository userRepository) {
        this.commentRepository      = commentRepository;
        this.historyRepository      = historyRepository;
        this.notificationRepository = notificationRepository;
        this.notificationPublisher  = notificationPublisher;
        this.userRepository         = userRepository;
    }

    /** 댓글 목록 조회 */
    @GetMapping("/{historyId}/comments")
    public ResponseEntity<List<Map<String, Object>>> getComments(@PathVariable long historyId) {
        List<ReviewComment> comments = commentRepository.findByHistoryIdOrderByCreatedAtAsc(historyId);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (ReviewComment c : comments) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id",           c.getId());
            m.put("parentId",     c.getParentId());
            m.put("username",     c.getUsername());
            m.put("content",      c.getContent());
            m.put("createdAt",    c.getFormattedDate());
            // v4.2.7: 프론트에서 formatRelative 로 "N분 전" 표기가 가능하도록
            // 원본 LocalDateTime 의 ISO 문자열을 함께 제공.
            m.put("createdAtIso", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
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
        String username   = principal.getName();
        String trimmed    = content.trim();
        ReviewComment comment = new ReviewComment(historyId, parentId, username, trimmed);
        commentRepository.save(comment);

        // 알림 대상 수집 — 중복 방지용 Set. 본인 제외.
        // v4.2.7: @멘션을 1차 수신자로, 부모/이력 작성자는 폴백 수신자로 처리.
        Set<String> alreadyNotified = new HashSet<String>();
        alreadyNotified.add(username); // 본인에게는 알림 안 보냄

        // ── 1차: @username 멘션 기반 알림 ─────────────────────────
        // 존재하는 사용자만 대상으로 하고 각 수신자에게 "X님이 @멘션 했습니다" 알림 생성.
        try {
            Set<String> mentioned = extractMentions(trimmed);
            // 멘션 메시지 길이 제한
            String preview = trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
            // 이력 제목이 있으면 링크 라벨에 포함
            ReviewHistory history = historyRepository.findById(historyId).orElse(null);
            String historyTitle = (history != null && history.getTitle() != null && !history.getTitle().isEmpty())
                    ? history.getTitle()
                    : ("이력 #" + historyId);
            for (String target : mentioned) {
                if (alreadyNotified.contains(target)) continue;
                // 실제로 존재하는 활성 사용자인지 확인 — 오타/비존재 username 에 알림 생성 방지
                if (!userRepository.findByUsernameAndEnabledTrue(target).isPresent()) continue;

                Notification noti = new Notification(
                    target,
                    "MENTION",
                    username + "님이 댓글에서 회원님을 호출했습니다",
                    "[" + historyTitle + "]\n" + preview,
                    "/history#" + historyId
                );
                notificationPublisher.publish(noti);
                alreadyNotified.add(target);
            }
        } catch (Exception ignored) {
            // 멘션 알림 실패해도 댓글은 저장됨
        }

        // ── 2차: 기존 정책 — 부모 댓글 작성자 또는 이력 작성자 ─────
        try {
            String targetUser = null;
            String titleText;
            if (parentId != null) {
                ReviewComment parent = commentRepository.findById(parentId).orElse(null);
                if (parent != null && parent.getUsername() != null
                        && !alreadyNotified.contains(parent.getUsername())) {
                    targetUser = parent.getUsername();
                    titleText  = username + "님이 내 댓글에 답글을 남겼습니다";
                } else {
                    titleText = "";
                }
            } else {
                ReviewHistory history = historyRepository.findById(historyId).orElse(null);
                if (history != null && history.getUsername() != null
                        && !history.getUsername().isEmpty()
                        && !alreadyNotified.contains(history.getUsername())) {
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
                    trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed,
                    "/history#" + historyId
                );
                notificationPublisher.publish(noti);
                alreadyNotified.add(targetUser);
            }
        } catch (Exception ignored) {
            // 알림 실패해도 댓글은 저장됨
        }

        resp.put("success", true);
        resp.put("id",      comment.getId());
        return ResponseEntity.ok(resp);
    }

    /**
     * 댓글 삭제 — 본인 또는 ADMIN.
     *
     * <p>v4.2.7: 주석엔 "본인 또는 ADMIN" 이라고 적혀 있었지만 실제로는 본인 체크만
     * 되어 있어 ADMIN 도 남의 댓글을 지울 수 없었던 버그를 수정. Spring Security 의
     * {@code HttpServletRequest.isUserInRole("ADMIN")} 로 안정적으로 역할 확인.
     */
    @PostMapping("/{historyId}/comments/{commentId}/delete")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable long historyId,
            @PathVariable long commentId,
            Principal principal,
            HttpServletRequest request) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        ReviewComment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            resp.put("success", false);
            resp.put("error", "댓글을 찾을 수 없습니다.");
            return ResponseEntity.ok(resp);
        }
        boolean isOwner = principal != null
                && comment.getUsername() != null
                && comment.getUsername().equals(principal.getName());
        boolean isAdmin = request.isUserInRole("ADMIN");
        if (!isOwner && !isAdmin) {
            resp.put("success", false);
            resp.put("error", "삭제 권한이 없습니다. (본인 또는 ADMIN 만 가능)");
            return ResponseEntity.ok(resp);
        }
        commentRepository.delete(comment);
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    /**
     * v4.2.7 — 댓글 본문에서 @username 패턴을 추출.
     * 대소문자 유지 + 중복 제거. 선행 경계는 문자열 시작 또는 공백/개행이어야 하며,
     * 이메일 주소(xx@yy.zz) 같은 패턴은 선행 문자가 공백이 아니므로 매칭되지 않는다.
     */
    private static Set<String> extractMentions(String text) {
        Set<String> out = new LinkedHashSet<String>();
        if (text == null || text.isEmpty()) return out;
        Matcher m = MENTION_PATTERN.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
