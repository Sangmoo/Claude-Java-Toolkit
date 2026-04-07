package io.github.claudetoolkit.ui.harness;

import io.github.claudetoolkit.ui.email.EmailService;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Batch analysis service: runs multiple code snippets through the harness pipeline.
 */
@Service
public class HarnessBatchService {

    private final HarnessReviewService   reviewService;
    private final ReviewHistoryService   historyService;
    private final EmailService           emailService;
    private final BatchHistoryRepository batchHistoryRepo;

    /** Map of batchId -> current progress info */
    private final ConcurrentHashMap<String, BatchStatus> statusMap = new ConcurrentHashMap<String, BatchStatus>();

    private static final DateTimeFormatter TIME_FMT    = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HarnessBatchService(HarnessReviewService reviewService,
                               ReviewHistoryService historyService,
                               EmailService emailService,
                               BatchHistoryRepository batchHistoryRepo) {
        this.reviewService    = reviewService;
        this.historyService   = historyService;
        this.emailService     = emailService;
        this.batchHistoryRepo = batchHistoryRepo;
    }

    /** Represents a single item in a batch. */
    public static class BatchItem {
        public String label;
        public String code;
        public String language;
    }

    /** Per-item log entry recorded during batch execution. */
    public static class LogEntry {
        public int    seq;
        public String label;
        public String language;
        public String startedAt;
        public String finishedAt;
        public String status;  // "success" | "failed"
        public String error;
    }

    /** Current batch status. */
    public static class BatchStatus {
        public String        batchId;
        public int           total;
        public int           done;
        public boolean       running;
        public boolean       finished;
        public String        error;
        public String        startedAt;      // HH:mm:ss display
        public String        finishedAt;     // HH:mm:ss display
        public LocalDateTime startedAtDt;   // for DB save
        public LocalDateTime finishedAtDt;  // for DB save
        public final List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        public final List<LogEntry>            log     = new ArrayList<LogEntry>();
    }

    /**
     * Starts batch analysis asynchronously.
     * @param items        list of code snippets to analyze
     * @param notifyEmails optional list of email addresses to notify on completion
     * @return batchId to poll status
     */
    public String startBatch(final List<BatchItem> items, final List<String> notifyEmails) {
        final String batchId = java.util.UUID.randomUUID().toString();
        final BatchStatus status = new BatchStatus();
        status.batchId     = batchId;
        status.total       = items.size();
        status.running     = true;
        status.startedAtDt = LocalDateTime.now();
        status.startedAt   = status.startedAtDt.format(TIME_FMT);
        statusMap.put(batchId, status);

        Thread t = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < items.size(); i++) {
                    BatchItem item = items.get(i);
                    LogEntry entry = new LogEntry();
                    entry.seq       = i + 1;
                    entry.label     = item.label;
                    entry.language  = item.language;
                    entry.startedAt = LocalDateTime.now().format(TIME_FMT);

                    Map<String, Object> result = new LinkedHashMap<String, Object>();
                    result.put("label",    item.label);
                    result.put("language", item.language);
                    try {
                        String response = reviewService.analyze(item.code, item.language);
                        String improved = reviewService.extractImprovedCode(response, item.language);
                        result.put("success",  true);
                        result.put("response", response);
                        result.put("improved", improved);
                        ReviewHistory savedHist = historyService.saveHarness(item.code, response, item.language, improved);
                        result.put("reviewHistoryId", savedHist != null ? savedHist.getId() : -1L);
                        entry.status = "success";
                    } catch (Exception ex) {
                        String errMsg = ex.getMessage() != null ? ex.getMessage() : "오류 발생";
                        result.put("success", false);
                        result.put("error",   errMsg);
                        entry.status = "failed";
                        entry.error  = errMsg;
                    }
                    entry.finishedAt = LocalDateTime.now().format(TIME_FMT);
                    status.log.add(entry);
                    status.results.add(result);
                    status.done = i + 1;
                }
                status.finishedAtDt = LocalDateTime.now();
                status.finishedAt   = status.finishedAtDt.format(TIME_FMT);
                status.running      = false;
                status.finished     = true;

                // Persist to DB
                saveBatchHistory(batchId, status);

                // Email notification — send to all addresses
                if (notifyEmails != null && !notifyEmails.isEmpty()) {
                    try {
                        String subject = "[하네스 배치] " + items.size() + "건 분석 완료";
                        String body    = buildEmailBody(items.size(), status, batchId);
                        for (String email : notifyEmails) {
                            if (email != null && !email.trim().isEmpty()) {
                                try {
                                    emailService.sendJobResult(email.trim(), subject, body);
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
        t.setDaemon(true);
        t.setName("harness-batch-" + batchId.substring(0, 8));
        t.start();
        return batchId;
    }

    private long saveBatchHistory(String batchId, BatchStatus status) {
        try {
            BatchHistory h = new BatchHistory();
            h.setBatchUuid(batchId);
            h.setStartedAt(status.startedAtDt);
            h.setFinishedAt(status.finishedAtDt);
            h.setTotalCount(status.total);
            int success = countSuccess(status.results);
            h.setSuccessCount(success);
            h.setFailedCount(status.total - success);
            h.setItemsSummaryJson(buildItemsJson(status));
            BatchHistory saved = batchHistoryRepo.save(h);
            return saved.getId();
        } catch (Exception e) {
            System.err.println("[HarnessBatchService] DB 저장 실패: " + e.getMessage());
            return -1L;
        }
    }

    /** Build JSON array string for itemsSummaryJson field. */
    private String buildItemsJson(BatchStatus status) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < status.results.size(); i++) {
            Map<String, Object> r = status.results.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"label\":\"").append(jsonEscape(str(r.get("label")))).append("\",");
            sb.append("\"language\":\"").append(jsonEscape(str(r.get("language")))).append("\",");
            boolean success = Boolean.TRUE.equals(r.get("success"));
            sb.append("\"status\":\"").append(success ? "success" : "failed").append("\",");
            if (success) {
                String resp = str(r.get("response"));
                String verdict = "UNKNOWN";
                if (resp.contains("APPROVED"))            verdict = "APPROVED";
                else if (resp.contains("NEEDS_REVISION")) verdict = "NEEDS_REVISION";
                sb.append("\"verdict\":\"").append(verdict).append("\",");
                sb.append("\"error\":\"\"");
            } else {
                sb.append("\"verdict\":\"FAILED\",");
                sb.append("\"error\":\"").append(jsonEscape(str(r.get("error")))).append("\"");
            }
            // log times
            if (i < status.log.size()) {
                LogEntry le = status.log.get(i);
                sb.append(",\"startedAt\":\"").append(jsonEscape(le.startedAt != null ? le.startedAt : "")).append("\"");
                sb.append(",\"finishedAt\":\"").append(jsonEscape(le.finishedAt != null ? le.finishedAt : "")).append("\"");
            }
            // link to ReviewHistory record for full result lookup
            Object histId = r.get("reviewHistoryId");
            long reviewHistoryId = (histId instanceof Long) ? (Long) histId
                                 : (histId instanceof Number) ? ((Number) histId).longValue() : -1L;
            sb.append(",\"reviewHistoryId\":").append(reviewHistoryId);
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String str(Object o) { return o != null ? o.toString() : ""; }

    /** Builds a rich plain-text email body with per-item summaries. */
    private String buildEmailBody(int total, BatchStatus status, String batchUuid) {
        int successCount = countSuccess(status.results);
        int failCount    = total - successCount;

        StringBuilder sb = new StringBuilder();
        sb.append("하네스 배치 분석이 완료되었습니다.\n\n");
        sb.append("시작: ").append(status.startedAt != null ? status.startedAt : "-")
          .append("  /  완료: ").append(status.finishedAt != null ? status.finishedAt : "-").append("\n");
        sb.append("총 ").append(total).append("건  |  성공: ").append(successCount)
          .append("건  |  실패: ").append(failCount).append("건\n\n");
        sb.append("----------------------------------------\n항목별 요약\n----------------------------------------\n\n");

        for (int i = 0; i < status.results.size(); i++) {
            Map<String, Object> r = status.results.get(i);
            String label    = str(r.get("label"));
            String language = str(r.get("language"));
            boolean success = Boolean.TRUE.equals(r.get("success"));
            sb.append((i + 1)).append(". [").append(label).append("] (").append(language).append(")\n");
            if (i < status.log.size()) {
                LogEntry le = status.log.get(i);
                sb.append("   시작: ").append(le.startedAt != null ? le.startedAt : "-")
                  .append("  완료: ").append(le.finishedAt != null ? le.finishedAt : "-").append("\n");
            }
            if (success) {
                String response = str(r.get("response"));
                String verdict = response.contains("APPROVED") ? "APPROVED"
                               : response.contains("NEEDS_REVISION") ? "NEEDS_REVISION" : "UNKNOWN";
                sb.append("   판정: ").append(verdict).append("\n");
                String preview = response.replaceAll("(?m)^#{1,3}\\s+", "").trim();
                if (preview.length() > 400) preview = preview.substring(0, 400) + "...";
                String[] lines = preview.split("\n");
                sb.append("   분석 요약:\n");
                for (String line : lines) sb.append("     ").append(line).append("\n");
            } else {
                sb.append("   오류: ").append(str(r.get("error"))).append("\n");
            }
            sb.append("\n");
        }

        sb.append("----------------------------------------\n");
        sb.append("배치 분석 이력에서 확인하세요:\n");
        sb.append("http://localhost:8027/harness/batch?highlight=").append(batchUuid).append("\n");
        return sb.toString();
    }

    /** Returns recent batch history entries (max 50). */
    public List<BatchHistory> getRecentHistory() {
        return batchHistoryRepo.findRecentBatches(PageRequest.of(0, 50));
    }

    /** Delete a single history record by id. */
    public void deleteHistory(long id) {
        batchHistoryRepo.deleteById(id);
    }

    public BatchStatus getStatus(String batchId) {
        return statusMap.get(batchId);
    }

    public void clearStatus(String batchId) {
        statusMap.remove(batchId);
    }

    private int countSuccess(List<Map<String, Object>> results) {
        int count = 0;
        for (Map<String, Object> r : results) {
            if (Boolean.TRUE.equals(r.get("success"))) count++;
        }
        return count;
    }
}
