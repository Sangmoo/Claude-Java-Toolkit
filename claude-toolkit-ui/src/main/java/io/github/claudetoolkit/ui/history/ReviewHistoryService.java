package io.github.claudetoolkit.ui.history;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.metrics.ToolkitMetrics;
import io.github.claudetoolkit.ui.notification.PendingReviewNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Review history service backed by H2 file database (JPA).
 *
 * Persists up to maxHistory entries. Oldest entries are automatically
 * pruned when the limit is exceeded. Data survives server restarts.
 */
@Service
@Transactional
public class ReviewHistoryService {

    /**
     * v4.2.7 — 이력 상한을 설정(`toolkit.history.max`) 으로 외부화.
     * 기본 200 (이전 하드코딩값 100 대비 상향). `application.yml` 또는
     * `TOOLKIT_HISTORY_MAX` 환경변수로 조정 가능.
     */
    @Value("${toolkit.history.max:200}")
    private int maxHistory;

    private final ReviewHistoryRepository repository;
    private final ClaudeClient            claudeClient;

    /**
     * v4.2.7 — VIEWER 생성 이력에 대한 REVIEWER/ADMIN 알림 발행.
     * 순환 의존성 우려는 없지만 setter 주입으로 두어 PipelineExecutor 등 타 서비스
     * 쪽에서도 동일한 빈을 받게 한다.
     */
    @Autowired(required = false)
    private PendingReviewNotifier pendingReviewNotifier;

    /** v4.3.0: Prometheus 메트릭. 옵셔널 — 메트릭 없는 테스트 환경에서도 동작 */
    @Autowired(required = false)
    private ToolkitMetrics metrics;

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
        saveInternal(type, inputContent, outputContent, null, currentUsername());
    }

    public void save(String type, String inputContent, String outputContent, Long costValue) {
        saveInternal(type, inputContent, outputContent, costValue, currentUsername());
    }

    /**
     * 백그라운드 스레드(예: SSE 스트리밍 핸들러)에서 호출할 때 사용하는 오버로드.
     * SecurityContext 가 비어 있어 {@link #currentUsername()} 가 null 이 되므로,
     * 호출부가 요청 스레드에서 미리 capture 한 username 을 명시적으로 전달한다.
     */
    public void save(String type, String inputContent, String outputContent, String username) {
        saveInternal(type, inputContent, outputContent, null, username);
    }

    private void saveInternal(String type, String inputContent, String outputContent,
                              Long costValue, String username) {
        long inputTok  = claudeClient.getLastInputTokens();
        long outputTok = claudeClient.getLastOutputTokens();
        String title = buildTitle(inputContent);
        ReviewHistory h = new ReviewHistory(type, title, inputContent, outputContent, costValue,
                inputTok > 0 ? inputTok : null, outputTok > 0 ? outputTok : null);
        h.setUsername(username);  // v4.2.x: GET /history 가 username 으로 필터링하므로 필수
        ReviewHistory saved = repository.save(h);

        // Prune oldest if over limit
        while (repository.count() > maxHistory) {
            ReviewHistory oldest = repository.findTopByOrderByCreatedAtAsc();
            if (oldest != null) {
                repository.delete(oldest);
            } else {
                break;
            }
        }
        // v4.2.7: VIEWER 가 생성한 이력이면 REVIEWER/ADMIN 에게 대기 알림
        if (pendingReviewNotifier != null) pendingReviewNotifier.notifyIfViewerCreated(saved);

        // v4.3.0: Prometheus 메트릭 — 비스트리밍 분석 흐름 (REST 컨트롤러가 이 save() 호출)
        if (metrics != null) {
            String model = claudeClient.getEffectiveModel();
            String featureKey = type != null ? type.toLowerCase() : "unknown";
            metrics.recordClaudeApiCall(model, featureKey, "success");
            metrics.recordClaudeTokens(model, inputTok, outputTok);
        }
    }

    /**
     * Save a harness pipeline review result — includes original/improved code and language.
     */
    /**
     * Save a harness pipeline review result — returns the saved entity so callers
     * can retrieve the generated ID (e.g., for linking batch history to review history).
     */
    public ReviewHistory saveHarness(String originalCode, String response, String language, String improvedCode) {
        long inputTok  = claudeClient.getLastInputTokens();
        long outputTok = claudeClient.getLastOutputTokens();
        String title = buildTitle(originalCode);
        ReviewHistory h = new ReviewHistory("HARNESS_REVIEW", title, originalCode, response,
                null, inputTok > 0 ? inputTok : null, outputTok > 0 ? outputTok : null);
        h.setOriginalCode(originalCode);
        h.setImprovedCode(improvedCode);
        h.setAnalysisLanguage(language);
        h.setUsername(currentUsername());  // v4.2.x
        ReviewHistory saved = repository.save(h);
        while (repository.count() > maxHistory) {
            ReviewHistory oldest = repository.findTopByOrderByCreatedAtAsc();
            if (oldest != null) repository.delete(oldest);
            else break;
        }
        // v4.2.7: VIEWER 가 생성한 하네스 결과면 REVIEWER/ADMIN 에게 대기 알림
        if (pendingReviewNotifier != null) pendingReviewNotifier.notifyIfViewerCreated(saved);

        // v4.3.0: Prometheus 메트릭 — Harness 파이프라인은 항상 4단계 후 한 번 저장
        if (metrics != null) {
            String model = claudeClient.getEffectiveModel();
            metrics.recordClaudeApiCall(model, "harness_review", "success");
            metrics.recordClaudeTokens(model, inputTok, outputTok);
        }
        return saved;
    }

    /** Return all history entries (most recent first, max maxHistory). */
    @Transactional(readOnly = true)
    public List<ReviewHistory> findAll() {
        return repository.findRecentEntries(PageRequest.of(0, maxHistory));
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

    /**
     * Deletes the N most recent history entries of the given type.
     *
     * @param type  history type key (e.g. "HARNESS_REVIEW")
     * @param count number of recent entries to delete
     * @return number of entries actually deleted
     */
    public int deleteRecentByType(String type, int count) {
        List<ReviewHistory> all = findAll(); // already sorted most-recent first
        int deleted = 0;
        for (ReviewHistory h : all) {
            if (deleted >= count) break;
            if (type.equals(h.getType())) {
                repository.delete(h);
                deleted++;
            }
        }
        return deleted;
    }

    /** Total entry count. */
    @Transactional(readOnly = true)
    public int count() {
        return (int) repository.count();
    }

    // ── v4.7.x: 태그 관리 ─────────────────────────────────────────

    /**
     * 이력 단일 항목의 태그를 업데이트.
     * @param id     이력 ID
     * @param rawTags 콤마 구분 태그 (정규화는 entity 가 담당)
     * @return 업데이트된 엔티티 (없으면 null)
     */
    public ReviewHistory updateTags(long id, String rawTags) {
        ReviewHistory h = repository.findById(id).orElse(null);
        if (h == null) return null;
        h.setTags(rawTags);
        return repository.save(h);
    }

    /**
     * 사용자가 보유한 모든 태그를 빈도순으로 반환.
     * @return [{tag, count}, ...] 빈도 내림차순
     */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> aggregateTagsByUsername(String username) {
        List<String> rows = repository.findAllTagsByUsername(username);
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<String, Integer>();
        for (String row : rows) {
            if (row == null) continue;
            for (String t : row.split(",")) {
                String trimmed = t.trim();
                if (trimmed.isEmpty()) continue;
                String key = trimmed; // 대소문자 그대로 (한글 태그 보존)
                Integer prev = counts.get(key);
                counts.put(key, prev == null ? 1 : prev + 1);
            }
        }
        // 빈도 내림차순 정렬
        java.util.List<java.util.Map.Entry<String, Integer>> entries = new java.util.ArrayList<java.util.Map.Entry<String, Integer>>(counts.entrySet());
        entries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (java.util.Map.Entry<String, Integer> e : entries) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("tag",   e.getKey());
            m.put("count", e.getValue());
            result.add(m);
        }
        return result;
    }

    /**
     * 사용자 + 태그로 이력 검색 (페이지네이션).
     */
    @Transactional(readOnly = true)
    public List<ReviewHistory> findByUsernameAndTag(String username, String tag, int page, int size) {
        int effectiveSize = Math.max(1, Math.min(size, 500));
        int effectivePage = Math.max(0, page);
        return repository.findByUsernameAndTag(
                username, tag,
                PageRequest.of(effectivePage, effectiveSize));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /** SecurityContext 에서 현재 로그인 사용자명 추출 — 백그라운드 스레드에서는 null */
    private String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return null;
    }

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
