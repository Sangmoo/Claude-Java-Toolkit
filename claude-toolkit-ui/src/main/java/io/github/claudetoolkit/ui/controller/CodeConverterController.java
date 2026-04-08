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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Web controller for Code Converter feature (/converter).
 * Converts Oracle SP → Java/Spring+MyBatis, or SQL → MyBatis XML.
 *
 * Post-Redirect-Get 패턴 적용:
 * - POST /converter/convert → 변환 후 redirect:/converter (F5 재실행 방지)
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

    /** GET /converter — 폼 표시. flash attributes에 이전 결과가 있으면 함께 표시 */
    @GetMapping
    public String showForm(Model model) {
        // flash attributes(result, sourceCode, targetType 등)는 Spring이 model에 자동 병합
        return "converter/index";
    }

    /** POST /converter/convert — PRG: 변환 후 redirect */
    @PostMapping("/convert")
    public String convert(
            @RequestParam("sourceCode")                              String sourceCode,
            @RequestParam(value = "targetType", defaultValue = "java") String targetType,
            RedirectAttributes redirectAttrs) {

        // 빈 입력 방어
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            redirectAttrs.addFlashAttribute("error", "소스 코드를 입력하세요.");
            return "redirect:/converter";
        }

        redirectAttrs.addFlashAttribute("sourceCode", sourceCode);
        redirectAttrs.addFlashAttribute("targetType", targetType);

        try {
            String result = converterService.convert(sourceCode, targetType);
            historyService.save("CODE_CONVERT", sourceCode, result);
            redirectAttrs.addFlashAttribute("result", result);
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("error", "변환 실패: " + e.getMessage());
        }
        return "redirect:/converter";
    }

    /** POST /converter/convert/file — 파일 업로드 후 PRG */
    @PostMapping("/convert/file")
    public String convertFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetType", defaultValue = "java") String targetType,
            RedirectAttributes redirectAttrs) throws IOException {

        String sourceCode = new String(file.getBytes(), StandardCharsets.UTF_8);
        return convert(sourceCode, targetType, redirectAttrs);
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
