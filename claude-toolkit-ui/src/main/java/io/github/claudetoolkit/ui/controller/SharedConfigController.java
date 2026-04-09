package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.share.SharedConfig;
import io.github.claudetoolkit.ui.share.SharedConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 팀 설정 공유 컨트롤러.
 */
@Controller
@RequestMapping("/settings/shared")
public class SharedConfigController {

    private final SharedConfigRepository repository;

    public SharedConfigController(SharedConfigRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public String index(Model model, Principal principal) {
        String username = principal != null ? principal.getName() : "anonymous";
        model.addAttribute("myConfigs", repository.findByCreatedByOrderByCreatedAtDesc(username));
        model.addAttribute("publicConfigs", repository.findByIsPublicTrueOrderByCreatedAtDesc());
        return "settings/shared";
    }

    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> save(
            @RequestParam String configType,
            @RequestParam String name,
            @RequestParam String content,
            @RequestParam(defaultValue = "false") boolean isPublic,
            Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            String username = principal != null ? principal.getName() : "anonymous";
            SharedConfig config = new SharedConfig(configType, name, content, username, isPublic);
            repository.save(config);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            SharedConfig config = repository.findById(id).orElse(null);
            if (config == null) {
                resp.put("success", false);
                resp.put("error", "설정을 찾을 수 없습니다.");
                return ResponseEntity.ok(resp);
            }
            repository.delete(config);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }
}
