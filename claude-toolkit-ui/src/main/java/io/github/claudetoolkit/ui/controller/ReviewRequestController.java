package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import io.github.claudetoolkit.ui.review.ReviewRequest;
import io.github.claudetoolkit.ui.review.ReviewRequestService;
import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * 팀 코드 리뷰 요청 컨트롤러 (v2.9.0).
 *
 * <p>경로:
 * <ul>
 *   <li>GET  /review-requests — 목록 페이지 (내 요청 + 내게 온 요청)</li>
 *   <li>GET  /review-requests/{id} — 상세 페이지</li>
 *   <li>POST /review-requests — 신규 요청 생성</li>
 *   <li>POST /review-requests/{id}/approve — 승인</li>
 *   <li>POST /review-requests/{id}/reject — 반려</li>
 *   <li>POST /review-requests/{id}/delete — 취소 (본인만, PENDING)</li>
 *   <li>GET  /review-requests/reviewers — 선택 가능 리뷰어 목록 (JSON)</li>
 *   <li>GET  /review-requests/history/{historyId} — 특정 이력의 요청 목록 (JSON)</li>
 * </ul>
 */
@Controller
@RequestMapping("/review-requests")
public class ReviewRequestController {

    private final ReviewRequestService    service;
    private final AppUserRepository       userRepository;
    private final ReviewHistoryRepository historyRepository;

    public ReviewRequestController(ReviewRequestService service,
                                   AppUserRepository userRepository,
                                   ReviewHistoryRepository historyRepository) {
        this.service           = service;
        this.userRepository    = userRepository;
        this.historyRepository = historyRepository;
    }

    // ── 목록 페이지 ───────────────────────────────────────────────────────

    @GetMapping
    public String listPage(Model model, Principal principal) {
        String username = principal.getName();
        List<ReviewRequest> requestedByMe = service.findRequestedByMe(username);
        List<ReviewRequest> assignedToMe  = service.findAssignedToMe(username);

        model.addAttribute("requestedByMe", toViewList(requestedByMe));
        model.addAttribute("assignedToMe",  toViewList(assignedToMe));
        model.addAttribute("requestedCount", requestedByMe.size());
        model.addAttribute("assignedCount",  assignedToMe.size());
        return "review-requests/list";
    }

    // ── 상세 페이지 ───────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String detailPage(@PathVariable Long id, Model model,
                             Principal principal, Authentication auth) {
        ReviewRequest req = service.findById(id);
        if (req == null) {
            model.addAttribute("errorMessage", "요청을 찾을 수 없습니다.");
            return "error";
        }
        String username = principal.getName();
        boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        if (!service.canAccess(req, username, isAdmin)) {
            model.addAttribute("errorMessage", "접근 권한이 없습니다.");
            return "error";
        }

        ReviewHistory history = historyRepository.findById(req.getHistoryId()).orElse(null);

        Map<String, Object> view = toView(req);
        model.addAttribute("req", view);
        model.addAttribute("history", history);
        model.addAttribute("currentUser", username);
        model.addAttribute("canRespond",
                req.isPending() && username.equals(req.getReviewerUsername()));
        model.addAttribute("canDelete",
                req.isPending() && username.equals(req.getAuthorUsername()));
        return "review-requests/detail";
    }

    // ── 생성 ──────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam Long historyId,
            @RequestParam String reviewerUsername,
            @RequestParam(required = false, defaultValue = "") String comment,
            Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            ReviewRequest req = service.create(historyId, principal.getName(), reviewerUsername, comment);
            resp.put("success", true);
            resp.put("id",      req.getId());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 승인 ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String comment,
            Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            service.approve(id, principal.getName(), comment);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 반려 ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/reject")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable Long id,
            @RequestParam String comment,
            Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            service.reject(id, principal.getName(), comment);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 삭제 (요청 취소) ──────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable Long id, Principal principal, Authentication auth) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            service.delete(id, principal.getName(), isAdmin);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 리뷰어 목록 (REVIEWER/ADMIN 역할) ─────────────────────────────────

    @GetMapping("/reviewers")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> reviewers(Principal principal) {
        List<AppUser> users = userRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        String me = principal.getName();
        for (AppUser u : users) {
            if (!u.isEnabled()) continue;
            if (me.equals(u.getUsername())) continue; // 본인 제외
            String role = u.getRole();
            if (!"REVIEWER".equals(role) && !"ADMIN".equals(role)) continue;
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("username",    u.getUsername());
            m.put("displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
            m.put("role",        role);
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    // ── 특정 이력의 요청 목록 ─────────────────────────────────────────────

    @GetMapping("/history/{historyId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> byHistory(@PathVariable Long historyId) {
        List<ReviewRequest> list = service.findByHistoryId(historyId);
        return ResponseEntity.ok(toViewList(list));
    }

    // ── 내부 변환 ─────────────────────────────────────────────────────────

    private List<Map<String, Object>> toViewList(List<ReviewRequest> list) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (ReviewRequest r : list) result.add(toView(r));
        return result;
    }

    private Map<String, Object> toView(ReviewRequest r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id",               r.getId());
        m.put("historyId",        r.getHistoryId());
        m.put("authorUsername",   r.getAuthorUsername());
        m.put("reviewerUsername", r.getReviewerUsername());
        m.put("status",           r.getStatus());
        m.put("statusLabel",      r.getStatusLabel());
        m.put("statusColor",      r.getStatusColor());
        m.put("requestComment",   r.getRequestComment());
        m.put("reviewComment",    r.getReviewComment());
        m.put("createdAt",        r.getFormattedCreatedAt());
        m.put("respondedAt",      r.getFormattedRespondedAt());
        // 연결된 이력의 제목/유형
        ReviewHistory h = historyRepository.findById(r.getHistoryId()).orElse(null);
        if (h != null) {
            m.put("historyTitle", h.getTitle());
            m.put("historyType",  h.getTypeLabel());
        } else {
            m.put("historyTitle", "(삭제된 이력)");
            m.put("historyType",  "-");
        }
        return m;
    }
}
