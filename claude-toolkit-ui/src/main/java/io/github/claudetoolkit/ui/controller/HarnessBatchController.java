package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.harness.HarnessBatchService;
import io.github.claudetoolkit.ui.harness.HarnessBatchService.BatchItem;
import io.github.claudetoolkit.ui.harness.HarnessBatchService.BatchStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the batch harness analysis page (/harness/batch).
 */
@Controller
@RequestMapping("/harness/batch")
public class HarnessBatchController {

    private final HarnessBatchService batchService;

    public HarnessBatchController(HarnessBatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public String index(Model model) {
        return "harness/batch";
    }

    /**
     * Start a batch analysis job.
     * Accepts JSON body: { items: [{label, code, language}], notifyEmail: "..." }
     */
    @PostMapping("/start")
    @ResponseBody
    public Map<String, Object> start(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            Object itemsRaw = body.get("items");
            if (!(itemsRaw instanceof List)) {
                result.put("success", false);
                result.put("error", "items must be a list");
                return result;
            }
            List<BatchItem> items = new ArrayList<BatchItem>();
            List<?> rawList = (List<?>) itemsRaw;
            for (Object rawItem : rawList) {
                if (!(rawItem instanceof Map)) continue;
                Map<?,?> m = (Map<?,?>) rawItem;
                BatchItem item = new BatchItem();
                item.label    = m.get("label")    != null ? m.get("label").toString()    : "";
                item.code     = m.get("code")     != null ? m.get("code").toString()     : "";
                item.language = m.get("language") != null ? m.get("language").toString() : "java";
                if (!item.code.trim().isEmpty()) items.add(item);
            }
            if (items.isEmpty()) {
                result.put("success", false);
                result.put("error", "분석할 코드가 없습니다.");
                return result;
            }
            String notifyEmail = body.get("notifyEmail") != null ? body.get("notifyEmail").toString() : "";
            String batchId = batchService.startBatch(items, notifyEmail);
            result.put("success", true);
            result.put("batchId", batchId);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getMessage() != null ? e.getMessage() : "오류 발생");
        }
        return result;
    }

    /** Poll batch status. */
    @GetMapping("/status/{batchId}")
    @ResponseBody
    public Map<String, Object> status(@PathVariable String batchId) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        BatchStatus s = batchService.getStatus(batchId);
        if (s == null) {
            result.put("found", false);
            return result;
        }
        result.put("found",    true);
        result.put("total",    s.total);
        result.put("done",     s.done);
        result.put("running",  s.running);
        result.put("finished", s.finished);
        result.put("results",  s.results);
        return result;
    }

    /** Clear completed batch from memory. */
    @DeleteMapping("/status/{batchId}")
    @ResponseBody
    public Map<String, Object> clear(@PathVariable String batchId) {
        batchService.clearStatus(batchId);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("success", true);
        return r;
    }
}
