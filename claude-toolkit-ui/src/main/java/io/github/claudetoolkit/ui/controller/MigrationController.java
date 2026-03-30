package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.sql.migration.MigrationScriptService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Web controller for DB Migration Script Generator (/migration).
 */
@Controller
@RequestMapping("/migration")
public class MigrationController {

    private final MigrationScriptService migrationService;
    private final ReviewHistoryService   historyService;

    public MigrationController(MigrationScriptService migrationService,
                               ReviewHistoryService historyService) {
        this.migrationService = migrationService;
        this.historyService   = historyService;
    }

    @GetMapping
    public String showForm(Model model) {
        return "migration/index";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam("beforeDdl")                                   String beforeDdl,
            @RequestParam("afterDdl")                                    String afterDdl,
            @RequestParam(value = "format", defaultValue = "oracle")     String format,
            @RequestParam(value = "version", defaultValue = "1.0.0")     String version,
            Model model) {

        try {
            String result = migrationService.generate(beforeDdl, afterDdl, format);
            historyService.save("MIGRATION", "BEFORE:\n" + beforeDdl + "\n\nAFTER:\n" + afterDdl, result);

            model.addAttribute("result",    result);
            model.addAttribute("beforeDdl", beforeDdl);
            model.addAttribute("afterDdl",  afterDdl);
            model.addAttribute("format",    format);
            model.addAttribute("version",   version);

        } catch (Exception e) {
            model.addAttribute("error",     "생성 실패: " + e.getMessage());
            model.addAttribute("beforeDdl", beforeDdl);
            model.addAttribute("afterDdl",  afterDdl);
        }

        return "migration/index";
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("beforeDdl")                               String beforeDdl,
            @RequestParam("afterDdl")                                String afterDdl,
            @RequestParam(value = "format",  defaultValue = "oracle") String format,
            @RequestParam(value = "version", defaultValue = "1.0.0") String version) {

        String result = migrationService.generate(beforeDdl, afterDdl, format);

        String filename;
        if ("flyway".equalsIgnoreCase(format)) {
            filename = "V" + version + "__migration.sql";
        } else if ("liquibase".equalsIgnoreCase(format)) {
            filename = "changelog-" + version + ".xml";
        } else {
            filename = "migration.sql";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }
}
