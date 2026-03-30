package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.commitmsg.CommitMsgService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Web controller for Commit Message Generator feature (/commitmsg).
 */
@Controller
@RequestMapping("/commitmsg")
public class CommitMsgController {

    private final CommitMsgService     commitMsgService;
    private final ReviewHistoryService historyService;
    private final ToolkitSettings      settings;

    public CommitMsgController(CommitMsgService commitMsgService,
                               ReviewHistoryService historyService,
                               ToolkitSettings settings) {
        this.commitMsgService = commitMsgService;
        this.historyService   = historyService;
        this.settings         = settings;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("projectContextActive", settings.isProjectContextSet());
        return "commitmsg/index";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam("diffContent")                                           String diffContent,
            @RequestParam(value = "commitStyle", defaultValue = "conventional")   String commitStyle,
            @RequestParam(value = "inputMode",   defaultValue = "diff")           String inputMode,
            Model model) {

        try {
            String result;
            if ("description".equals(inputMode)) {
                result = commitMsgService.generateFromDescription(diffContent, settings.getProjectContext());
            } else {
                result = commitMsgService.generate(diffContent, commitStyle, settings.getProjectContext());
            }

            historyService.save("COMMIT_MSG", diffContent, result);

            model.addAttribute("result",               result);
            model.addAttribute("diffContent",          diffContent);
            model.addAttribute("commitStyle",          commitStyle);
            model.addAttribute("inputMode",            inputMode);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());

        } catch (Exception e) {
            model.addAttribute("error",                "커밋 메시지 생성 실패: " + e.getMessage());
            model.addAttribute("diffContent",          diffContent);
            model.addAttribute("projectContextActive", settings.isProjectContextSet());
        }

        return "commitmsg/index";
    }

    @PostMapping("/generate/file")
    public String generateFromFile(
            @RequestParam("file")                                                  MultipartFile file,
            @RequestParam(value = "commitStyle", defaultValue = "conventional")   String        commitStyle,
            @RequestParam(value = "inputMode",   defaultValue = "diff")           String        inputMode,
            Model model) throws IOException {

        String diffContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        return generate(diffContent, commitStyle, inputMode, model);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("diffContent")                                          String diffContent,
            @RequestParam(value = "commitStyle", defaultValue = "conventional")  String commitStyle,
            @RequestParam(value = "inputMode",   defaultValue = "diff")          String inputMode) {

        String result;
        if ("description".equals(inputMode)) {
            result = commitMsgService.generateFromDescription(diffContent, settings.getProjectContext());
        } else {
            result = commitMsgService.generate(diffContent, commitStyle, settings.getProjectContext());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"commit-message.md\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }
}
