package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.harness.BatchHistory;
import io.github.claudetoolkit.ui.harness.HarnessBatchService;
import io.github.claudetoolkit.ui.harness.HarnessBatchService.BatchItem;
import io.github.claudetoolkit.ui.harness.HarnessBatchService.BatchStatus;
import io.github.claudetoolkit.ui.harness.HarnessBatchService.LogEntry;
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
     * Accepts JSON body:
     *   { items: [{label, code, language}], notifyEmails: ["a@b.com","c@d.com"] }
     * Backward-compat: also accepts notifyEmail (single string).
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
                Map<?, ?> m = (Map<?, ?>) rawItem;
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

            // Parse notifyEmails — support array or fall back to single string
            List<String> notifyEmails = new ArrayList<String>();
            Object emailsRaw = body.get("notifyEmails");
            if (emailsRaw instanceof List) {
                List<?> emailList = (List<?>) emailsRaw;
                for (Object e : emailList) {
                    if (e != null && !e.toString().trim().isEmpty()) {
                        notifyEmails.add(e.toString().trim());
                    }
                }
            } else {
                // Backward-compat: single notifyEmail string
                Object singleEmail = body.get("notifyEmail");
                if (singleEmail != null && !singleEmail.toString().trim().isEmpty()) {
                    notifyEmails.add(singleEmail.toString().trim());
                }
            }

            String batchId = batchService.startBatch(items, notifyEmails);
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
        result.put("found",     true);
        result.put("total",     s.total);
        result.put("done",      s.done);
        result.put("running",   s.running);
        result.put("finished",  s.finished);
        result.put("startedAt", s.startedAt);
        result.put("finishedAt",s.finishedAt);
        result.put("results",   s.results);

        // Build log list for JSON serialization
        List<Map<String, Object>> logList = new ArrayList<Map<String, Object>>();
        for (LogEntry entry : s.log) {
            Map<String, Object> logEntry = new LinkedHashMap<String, Object>();
            logEntry.put("seq",        entry.seq);
            logEntry.put("label",      entry.label);
            logEntry.put("language",   entry.language);
            logEntry.put("startedAt",  entry.startedAt);
            logEntry.put("finishedAt", entry.finishedAt);
            logEntry.put("status",     entry.status);
            logEntry.put("error",      entry.error);
            logList.add(logEntry);
        }
        result.put("log", logList);

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

    /** GET /harness/batch/history — returns list of recent batch history records */
    @GetMapping("/history")
    @ResponseBody
    public List<Map<String, Object>> history() {
        List<BatchHistory> list = batchService.getRecentHistory();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (BatchHistory h : list) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id",               h.getId());
            m.put("batchUuid",        h.getBatchUuid());
            m.put("startedAt",        h.getFormattedStartedAt());
            m.put("finishedAt",       h.getFormattedFinishedAt());
            m.put("totalCount",       h.getTotalCount());
            m.put("successCount",     h.getSuccessCount());
            m.put("failedCount",      h.getFailedCount());
            m.put("itemsSummaryJson", h.getItemsSummaryJson());
            result.add(m);
        }
        return result;
    }

    /** DELETE /harness/batch/history/{id} — delete a single history record */
    @DeleteMapping("/history/{id}")
    @ResponseBody
    public Map<String, Object> deleteHistory(@PathVariable long id) {
        batchService.deleteHistory(id);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("success", true);
        return r;
    }
}
