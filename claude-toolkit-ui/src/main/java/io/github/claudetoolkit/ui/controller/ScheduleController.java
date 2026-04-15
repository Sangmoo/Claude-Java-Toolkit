package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.schedule.ScheduledJob;
import io.github.claudetoolkit.ui.schedule.ScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /**
     * v4.2.7 — 스케줄은 팀 공용 리소스이므로 모든 변경(save/toggle/run/delete) 은
     * ADMIN/REVIEWER 만 허용. VIEWER 가 실수로 운영 스케줄을 변경하지 못하도록 차단.
     */
    private static boolean canManageSchedule(HttpServletRequest request) {
        return request.isUserInRole("ADMIN") || request.isUserInRole("REVIEWER");
    }

    @PostMapping("/save")
    public String save(@ModelAttribute ScheduledJob job, RedirectAttributes ra,
                       HttpServletRequest request) {
        if (!canManageSchedule(request)) {
            ra.addFlashAttribute("saveError", "ADMIN 또는 REVIEWER 권한이 필요합니다.");
            return "redirect:/schedule";
        }
        try {
            scheduleService.save(job);
            ra.addFlashAttribute("saveSuccess", "스케줄이 저장되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("saveError", "저장 실패: " + e.getMessage());
        }
        return "redirect:/schedule";
    }

    @PostMapping("/{id}/toggle")
    @ResponseBody
    public ResponseEntity<String> toggle(@PathVariable Long id, HttpServletRequest request) {
        if (!canManageSchedule(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("forbidden");
        }
        scheduleService.toggle(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{id}/run")
    @ResponseBody
    public ResponseEntity<String> runNow(@PathVariable Long id, HttpServletRequest request) {
        if (!canManageSchedule(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("forbidden");
        }
        try {
            String result = scheduleService.runNow(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("오류: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra,
                         HttpServletRequest request) {
        if (!canManageSchedule(request)) {
            ra.addFlashAttribute("saveError", "ADMIN 또는 REVIEWER 권한이 필요합니다.");
            return "redirect:/schedule";
        }
        scheduleService.delete(id);
        ra.addFlashAttribute("saveSuccess", "스케줄이 삭제되었습니다.");
        return "redirect:/schedule";
    }
}
