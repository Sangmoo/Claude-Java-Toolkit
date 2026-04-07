package io.github.claudetoolkit.ui.history;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Review history service backed by H2 file database (JPA).
 *
 * Persists up to MAX_HISTORY entries. Oldest entries are automatically
 * pruned when the limit is exceeded. Data survives server restarts.
 */
@Service
@Transactional
public class ReviewHistoryService {

    private static final int MAX_HISTORY = 100;

    private final ReviewHistoryRepository repository;
    private final ClaudeClient            claudeClient;

    public ReviewHistoryService(ReviewHistoryRepository repository, ClaudeClient claudeClient) {
        this.repository   = repository;
        this.claudeClient = claudeClient;
    }

    /**
     * Save a new history entry.
     *
     * @param type         history type key (e.g. "SQL_REVIEW", "DOC_GEN")
     * @param inputContent original input
     * @param outputContent Claude's generated output
     */
    public void save(String type, String inputContent, String outputContent) {
        save(type, inputContent, outputContent, null);
    }

    public void save(String type, String inputContent, String outputContent, Long costValue) {
        long inputTok  = claudeClient.getLastInputTokens();
        long outputTok = claudeClient.getLastOutputTokens();
        String title = buildTitle(inputContent);
        ReviewHistory h = new ReviewHistory(type, title, inputContent, outputContent, costValue,
                inputTok > 0 ? inputTok : null, outputTok > 0 ? outputTok : null);
        repository.save(h);

        // Prune oldest if over limit
        while (repository.count() > MAX_HISTORY) {
            ReviewHistory oldest = repository.findTopByOrderByCreatedAtAsc();
            if (oldest != null) {
                repository.delete(oldest);
            } else {
                break;
            }
        }
    }

    /**
     * Save a harness pipeline review result — includes original/improved code and language.
     */
    public void saveHarness(String originalCode, String response, String language, String improvedCode) {
        long inputTok  = claudeClient.getLastInputTokens();
        long outputTok = claudeClient.getLastOutputTokens();
        String title = buildTitle(originalCode);
        ReviewHistory h = new ReviewHistory("HARNESS_REVIEW", title, originalCode, response,
                null, inputTok > 0 ? inputTok : null, outputTok > 0 ? outputTok : null);
        h.setOriginalCode(originalCode);
        h.setImprovedCode(improvedCode);
        h.setAnalysisLanguage(language);
        repository.save(h);
        while (repository.count() > MAX_HISTORY) {
            ReviewHistory oldest = repository.findTopByOrderByCreatedAtAsc();
            if (oldest != null) repository.delete(oldest);
            else break;
        }
    }

    /** Return all history entries (most recent first, max MAX_HISTORY). */
    @Transactional(readOnly = true)
    public List<ReviewHistory> findAll() {
        return repository.findRecentEntries(PageRequest.of(0, MAX_HISTORY));
    }

    /** Return all EXPLAIN_PLAN entries sorted by time (for dashboard chart). */
    @Transactional(readOnly = true)
    public List<ReviewHistory> findExplainPlanHistory() {
        return repository.findByTypeOrderByCreatedAtAsc("EXPLAIN_PLAN");
    }

    /** Find entry by ID, or null if not found. */
    @Transactional(readOnly = true)
    public ReviewHistory findById(long id) {
        return repository.findById(id).orElse(null);
    }

    /** Delete a single entry by ID. */
    public void deleteById(long id) {
        repository.deleteById(id);
    }

    /** Clear all history. */
    public void clear() {
        repository.deleteAll();
    }

    /** Total entry count. */
    @Transactional(readOnly = true)
    public int count() {
        return (int) repository.count();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String buildTitle(String input) {
        if (input == null || input.trim().isEmpty()) return "(빈 입력)";
        String first = input.trim().split("\n")[0].trim();
        first = first.replaceAll("(?i)^(--|/\\*|CREATE\\s+OR\\s+REPLACE|CREATE|SELECT|UPDATE|DELETE|INSERT|@)", "")
                     .trim();
        if (first.isEmpty()) {
            first = input.trim().substring(0, Math.min(50, input.trim().length()));
        }
        return first.length() > 60 ? first.substring(0, 57) + "..." : first;
    }
}
