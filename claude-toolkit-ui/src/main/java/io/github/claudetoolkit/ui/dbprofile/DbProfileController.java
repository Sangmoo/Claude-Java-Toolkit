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
}
