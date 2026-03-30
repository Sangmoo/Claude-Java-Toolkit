package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.sql.mockdata.MockDataGeneratorService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Web controller for Mock Data Generator feature (/mockdata).
 */
@Controller
@RequestMapping("/mockdata")
public class MockDataController {

    private final MockDataGeneratorService mockDataService;
    private final ReviewHistoryService     historyService;

    public MockDataController(MockDataGeneratorService mockDataService,
                              ReviewHistoryService historyService) {
        this.mockDataService = mockDataService;
        this.historyService  = historyService;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("rowCount", 10);
        return "mockdata/index";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam("ddl")                                        String ddl,
            @RequestParam(value = "rowCount", defaultValue = "10")      int    rowCount,
            @RequestParam(value = "format",   defaultValue = "insert")  String format,
            Model model) {

        // Clamp row count between 1 and 1000
        int clampedRows = Math.max(1, Math.min(1000, rowCount));

        try {
            String result = mockDataService.generate(ddl, clampedRows, format);
            historyService.save("MOCK_DATA", ddl, result);

            model.addAttribute("result",   result);
            model.addAttribute("ddl",      ddl);
            model.addAttribute("rowCount", clampedRows);
            model.addAttribute("format",   format);

        } catch (Exception e) {
            model.addAttribute("error",    "생성 실패: " + e.getMessage());
            model.addAttribute("ddl",      ddl);
            model.addAttribute("rowCount", clampedRows);
            model.addAttribute("format",   format);
        }

        return "mockdata/index";
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("ddl")                                       String ddl,
            @RequestParam(value = "rowCount", defaultValue = "10")     int    rowCount,
            @RequestParam(value = "format",   defaultValue = "insert") String format) {

        int clampedRows = Math.max(1, Math.min(1000, rowCount));
        String result   = mockDataService.generate(ddl, clampedRows, format);
        String ext      = "csv".equalsIgnoreCase(format) ? "csv" : "sql";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mock_data." + ext + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(result.getBytes(StandardCharsets.UTF_8));
    }
}
