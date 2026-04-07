package io.github.claudetoolkit.ui.harness;

import io.github.claudetoolkit.ui.email.EmailService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Service;

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

    /** Current batch status. */
    public static class BatchStatus {
        public String  batchId;
        public int     total;
        public int     done;
        public boolean running;
        public boolean finished;
        public String  error;
        public final List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
    }

    /**
     * Starts batch analysis asynchronously.
     * @param items      list of code snippets to analyze
     * @param notifyEmail optional email to notify on completion
     * @return batchId to poll status
     */
    public String startBatch(final List<BatchItem> items, final String notifyEmail) {
        final String batchId = java.util.UUID.randomUUID().toString();
        final BatchStatus status = new BatchStatus();
        status.batchId = batchId;
        status.total   = items.size();
        status.running = true;
        statusMap.put(batchId, status);

        Thread t = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < items.size(); i++) {
                    BatchItem item = items.get(i);
                    Map<String, Object> result = new LinkedHashMap<String, Object>();
                    result.put("label", item.label);
                    result.put("language", item.language);
                    try {
                        String response    = reviewService.analyze(item.code, item.language);
                        String improved    = reviewService.extractImprovedCode(response, item.language);
                        result.put("success",  true);
                        result.put("response", response);
                        result.put("improved", improved);
                        historyService.saveHarness(item.code, response, item.language, improved);
                    } catch (Exception ex) {
                        result.put("success", false);
                        result.put("error",   ex.getMessage() != null ? ex.getMessage() : "오류 발생");
                    }
                    status.results.add(result);
                    status.done = i + 1;
                }
                status.running  = false;
                status.finished = true;
                // Email notification
                if (notifyEmail != null && !notifyEmail.trim().isEmpty()) {
                    try {
                        String subject = "[하네스 배치] " + items.size() + "건 분석 완료";
                        String body    = "배치 분석이 완료되었습니다.\n\n"
                                       + "총 " + items.size() + "건 처리\n"
                                       + "성공: " + countSuccess(status.results) + "건\n\n"
                                       + "결과를 확인하려면 하네스 배치 페이지를 방문하세요.";
                        emailService.sendJobResult(notifyEmail.trim(), subject, body);
                    } catch (Exception ignored) {}
                }
            }
        });
        t.setDaemon(true);
        t.setName("harness-batch-" + batchId.substring(0,8));
        t.start();
        return batchId;
    }

    public BatchStatus getStatus(String batchId) {
        return statusMap.get(batchId);
    }

    public void clearStatus(String batchId) {
        statusMap.remove(batchId);
    }

    private int countSuccess(List<Map<String,Object>> results) {
        int count = 0;
        for (Map<String,Object> r : results) {
            if (Boolean.TRUE.equals(r.get("success"))) count++;
        }
        return count;
    }
}
