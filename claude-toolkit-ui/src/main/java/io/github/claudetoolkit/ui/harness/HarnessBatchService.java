package io.github.claudetoolkit.ui.harness;

import io.github.claudetoolkit.ui.email.EmailService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Batch analysis service: runs multiple code snippets through the harness pipeline.
 */
@Service
public class HarnessBatchService {

    private final HarnessReviewService reviewService;
    private final ReviewHistoryService historyService;
    private final EmailService         emailService;

    /** Map of batchId → current progress info */
    private final ConcurrentHashMap<String, BatchStatus> statusMap = new ConcurrentHashMap<String, BatchStatus>();

    public HarnessBatchService(HarnessReviewService reviewService,
                               ReviewHistoryService historyService,
                               EmailService emailService) {
        this.reviewService = reviewService;
        this.historyService = historyService;
        this.emailService   = emailService;
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
        public String  batchId;
        public int     total;
        public int     done;
        public boolean running;
        public boolean finished;
        public String  error;
        public String  startedAt;
        public String  finishedAt;
        public final List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
        public final List<LogEntry>           log     = new ArrayList<LogEntry>();
    }

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Starts batch analysis asynchronously.
     * @param items        list of code snippets to analyze
     * @param notifyEmails optional list of email addresses to notify on completion
     * @return batchId to poll status
     */
    public String startBatch(final List<BatchItem> items, final List<String> notifyEmails) {
        final String batchId = java.util.UUID.randomUUID().toString();
        final BatchStatus status = new BatchStatus();
        status.batchId  = batchId;
        status.total    = items.size();
        status.running  = true;
        status.startedAt = LocalDateTime.now().format(TIME_FMT);
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
                        historyService.saveHarness(item.code, response, item.language, improved);
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
                status.finishedAt = LocalDateTime.now().format(TIME_FMT);
                status.running    = false;
                status.finished   = true;

                // Email notification — send to all addresses
                if (notifyEmails != null && !notifyEmails.isEmpty()) {
                    try {
                        String subject  = "[하네스 배치] " + items.size() + "건 분석 완료";
                        String body     = buildEmailBody(items.size(), status);
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

    /** Builds a rich HTML/plain-text email body with per-item summaries. */
    private String buildEmailBody(int total, BatchStatus status) {
        int successCount = countSuccess(status.results);
        int failCount    = total - successCount;

        StringBuilder sb = new StringBuilder();
        sb.append("하네스 배치 분석이 완료되었습니다.\n\n");
        sb.append("시작: ").append(status.startedAt != null ? status.startedAt : "-")
          .append("  /  완료: ").append(status.finishedAt != null ? status.finishedAt : "-")
          .append("\n");
        sb.append("총 ").append(total).append("건 처리  |  ")
          .append("성공: ").append(successCount).append("건  |  ")
          .append("실패: ").append(failCount).append("건\n");
        sb.append("\n");
        sb.append("────────────────────────────────────────\n");
        sb.append("항목별 요약\n");
        sb.append("────────────────────────────────────────\n\n");

        for (int i = 0; i < status.results.size(); i++) {
            Map<String, Object> r = status.results.get(i);
            String label    = r.get("label")    != null ? r.get("label").toString()    : "항목 " + (i + 1);
            String language = r.get("language") != null ? r.get("language").toString() : "";
            boolean success = Boolean.TRUE.equals(r.get("success"));

            sb.append((i + 1)).append(". [").append(label).append("] (").append(language).append(")\n");

            if (i < status.log.size()) {
                LogEntry entry = status.log.get(i);
                sb.append("   시작: ").append(entry.startedAt != null ? entry.startedAt : "-")
                  .append("  완료: ").append(entry.finishedAt != null ? entry.finishedAt : "-")
                  .append("\n");
            }

            if (success) {
                String response = r.get("response") != null ? r.get("response").toString() : "";
                // Extract verdict
                String verdict = "UNKNOWN";
                if (response.contains("APPROVED")) {
                    verdict = "APPROVED";
                } else if (response.contains("NEEDS_REVISION")) {
                    verdict = "NEEDS_REVISION";
                }
                sb.append("   판정: ").append(verdict).append("\n");
                // First 400 chars of response
                String preview = response.length() > 400 ? response.substring(0, 400) + "..." : response;
                sb.append("   분석 요약:\n");
                // Indent each line of preview
                String[] lines = preview.split("\n");
                for (String line : lines) {
                    sb.append("     ").append(line).append("\n");
                }
            } else {
                String errMsg = r.get("error") != null ? r.get("error").toString() : "알 수 없는 오류";
                sb.append("   오류: ").append(errMsg).append("\n");
            }
            sb.append("\n");
        }

        sb.append("────────────────────────────────────────\n");
        sb.append("결과를 확인하려면 하네스 배치 페이지를 방문하세요.\n");
        return sb.toString();
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
