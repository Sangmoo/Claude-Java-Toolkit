package io.github.claudetoolkit.ui.history;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * ONE-TIME startup task: deletes the 5 most recent HARNESS_REVIEW history entries.
 *
 * <p>Guarded by a marker file {@code ~/.claude-toolkit/.harness-cleanup-v1} so it runs
 * exactly once. Safe to leave in the codebase permanently — subsequent restarts are no-ops.
 */
@Component
public class HarnessHistoryCleanup {

    private static final String MARKER =
            System.getProperty("user.home") + File.separator
            + ".claude-toolkit" + File.separator + ".harness-cleanup-v1";

    private final ReviewHistoryService historyService;

    public HarnessHistoryCleanup(ReviewHistoryService historyService) {
        this.historyService = historyService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOnce() {
        File marker = new File(MARKER);
        if (marker.exists()) return; // already ran

        try {
            int deleted = historyService.deleteRecentByType("HARNESS_REVIEW", 5);
            System.out.println("[HarnessHistoryCleanup] 최근 HARNESS_REVIEW " + deleted + "건 삭제 완료.");
            // Create marker file so this never runs again
            File dir = marker.getParentFile();
            if (!dir.exists()) dir.mkdirs();
            marker.createNewFile();
        } catch (Exception e) {
            System.err.println("[HarnessHistoryCleanup] 삭제 실패: " + e.getMessage());
        }
    }
}
