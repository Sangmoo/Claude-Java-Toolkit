package io.github.claudetoolkit.ui.dbprofile;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/db-profiles")
public class DbProfileController {

    private final DbProfileService service;
    private final ToolkitSettings  settings;

    public DbProfileController(DbProfileService service, ToolkitSettings settings) {
        this.service  = service;
        this.settings = settings;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("profiles",    service.findAll());
        model.addAttribute("currentUrl",  settings.getDb().getUrl());
        return "db-profiles/index";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String url,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String description) {

        if (!name.isEmpty() && !url.isEmpty()) {
            service.save(name.trim(), url.trim(), username.trim(), password.trim(), description.trim());
        }
        return "redirect:/db-profiles";
    }

    @PostMapping("/{id}/update")
    public String update(
            @PathVariable Long id,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String url,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String description) {

        service.update(id, name.trim(), url.trim(), username.trim(), password, description.trim());
        return "redirect:/db-profiles";
    }

    @PostMapping("/{id}/apply")
    public String apply(@PathVariable Long id) {
        service.applyProfile(id);
        return "redirect:/db-profiles?applied=true";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.deleteById(id);
        return "redirect:/db-profiles";
    }

    /** AJAX: get profile detail for edit modal */
    @GetMapping("/{id}/json")
    @ResponseBody
    public java.util.Map<String, String> detail(@PathVariable Long id) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<String, String>();
        DbProfile p = service.findById(id);
        if (p == null) { map.put("error", "Not found"); return map; }
        map.put("id",          String.valueOf(p.getId()));
        map.put("name",        p.getName());
        map.put("url",         p.getUrl());
        map.put("username",    p.getUsername());
        map.put("description", p.getDescription() != null ? p.getDescription() : "");
        return map;
    }

    /**
     * v4.7.x — #G3 Live DB Phase 0: 분석 채널 활성/비활성 토글 (ADMIN 전용).
     *
     * <p>SecurityConfig 가 /db-profiles/** 를 ADMIN 으로 제한하므로 추가 권한 검사 없음.
     * 활성화는 사용자 책임 — 이 프로필의 user 가 read-only 권한만 가져야 한다는
     * 명시적 확인.
     */
    @PostMapping("/{id}/live-analysis")
    @ResponseBody
    public java.util.Map<String, Object> toggleLiveAnalysis(
            @PathVariable Long id,
            @RequestParam("enabled") boolean enabled) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        boolean result = service.toggleLiveAnalysis(id, enabled);
        resp.put("success", true);
        resp.put("id",      id);
        resp.put("enabled", result);
        return resp;
    }

    /**
     * v4.7.x — Live DB 분석에 사용 가능한 활성 프로필 목록 (분석 페이지 dropdown 용).
     * 모든 사용자 read 가능 — 비밀번호 제외 메타만 반환.
     */
    @GetMapping("/active-live")
    @ResponseBody
    public java.util.List<java.util.Map<String, Object>> activeLiveProfiles() {
        java.util.List<DbProfile> profiles = service.findActiveLiveAnalysisProfiles();
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (DbProfile p : profiles) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("id",          p.getId());
            m.put("name",        p.getName());
            m.put("description", p.getDescription() != null ? p.getDescription() : "");
            m.put("maskedUrl",   p.getMaskedUrl());
            // 비밀번호 / username 은 노출 X (보안)
            result.add(m);
        }
        return result;
    }
}
