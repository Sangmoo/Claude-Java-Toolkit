package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.schedule.ScheduledJob;
import io.github.claudetoolkit.ui.schedule.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping("/save")
    public String save(@ModelAttribute ScheduledJob job, RedirectAttributes ra) {
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
    public ResponseEntity<String> toggle(@PathVariable Long id) {
        scheduleService.toggle(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{id}/run")
    @ResponseBody
    public ResponseEntity<String> runNow(@PathVariable Long id) {
        try {
            String result = scheduleService.runNow(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("오류: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        scheduleService.delete(id);
        ra.addFlashAttribute("saveSuccess", "스케줄이 삭제되었습니다.");
        return "redirect:/schedule";
    }
}
