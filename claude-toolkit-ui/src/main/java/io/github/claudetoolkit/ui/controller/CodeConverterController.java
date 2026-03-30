package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.docgen.converter.CodeConverterService;
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
 * Web controller for Code Converter feature (/converter).
 * Converts Oracle SP → Java/Spring+MyBatis, or SQL → MyBatis XML.
 */
@Controller
@RequestMapping("/converter")
public class CodeConverterController {

    private final CodeConverterService  converterService;
    private final ReviewHistoryService  historyService;

    public CodeConverterController(CodeConverterService converterService,
                                   ReviewHistoryService historyService) {
        this.converterService = converterService;
        this.historyService   = historyService;
    }

    @GetMapping
    public String showForm(Model model) {
        return "converter/index";
    }

    @PostMapping("/convert")
    public String convert(
            @RequestParam("sourceCode")                              String sourceCode,
            @RequestParam(value = "targetType", defaultValue = "java") String targetType,
            Model model) {

        try {
            String result = converterService.convert(sourceCode, targetType);
            historyService.save("CODE_CONVERT", sourceCode, result);

            model.addAttribute("result",     result);
            model.addAttribute("sourceCode", sourceCode);
            model.addAttribute("targetType", targetType);
        } catch (Exception e) {
            model.addAttribute("error",      "변환 실패: " + e.getMessage());
            model.addAttribute("sourceCode", sourceCode);
            model.addAttribute("targetType", targetType);
        }
        return "converter/index";
    }

    @PostMapping("/convert/file")
    public String convertFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetType", defaultValue = "java") String targetType,
            Model model) throws IOException {

        String sourceCode = new String(file.getBytes(), StandardCharsets.UTF_8);
        return convert(sourceCode, targetType, model);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("sourceCode")                              String sourceCode,
            @RequestParam(value = "targetType", defaultValue = "java") String targetType) {

        String result   = converterService.convert(sourceCode, targetType);
        String filename = "mybatis".equalsIgnoreCase(targetType) ? "mapper.xml"
                        : "java_to_sp".equalsIgnoreCase(targetType) ? "converted_sp.sql"
                        : "converted.java";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }
}
