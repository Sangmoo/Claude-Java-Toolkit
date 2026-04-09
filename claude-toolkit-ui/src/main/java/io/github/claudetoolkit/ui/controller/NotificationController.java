package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.notification.Notification;
import io.github.claudetoolkit.ui.notification.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * 알림 센터 API.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** 미읽음 수 */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        long count = notificationRepository.countByRecipientUsernameAndIsReadFalse(principal.getName());
        resp.put("count", count);
        return ResponseEntity.ok(resp);
    }

    /** 알림 목록 (최근 50개) */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(Principal principal) {
        List<Notification> all = notificationRepository
                .findByRecipientUsernameOrderByCreatedAtDesc(principal.getName());
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        int limit = Math.min(all.size(), 50);
        for (int i = 0; i < limit; i++) {
            Notification n = all.get(i);
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("typeIcon", n.getTypeIcon());
            m.put("title", n.getTitle());
            m.put("message", n.getMessage());
            m.put("link", n.getLink());
            m.put("isRead", n.isRead());
            m.put("createdAt", n.getFormattedDate());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    /** 단일 알림 읽음 처리 */
    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable long id, Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        Notification n = notificationRepository.findById(id).orElse(null);
        if (n != null && n.getRecipientUsername().equals(principal.getName())) {
            n.setRead(true);
            notificationRepository.save(n);
        }
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }

    /** 전체 읽음 처리 */
    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> markAllRead(Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        notificationRepository.markAllReadByUsername(principal.getName());
        resp.put("success", true);
        return ResponseEntity.ok(resp);
    }
}
