package io.github.claudetoolkit.ui.flow.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claudetoolkit.ui.flow.model.FlowAnalysisRequest;
import io.github.claudetoolkit.ui.flow.model.FlowAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 사용자별 Flow Analysis 이력 영속화 + 보관기간 관리. */
@Service
public class FlowHistoryService {

    private static final Logger log = LoggerFactory.getLogger(FlowHistoryService.class);

    /** 사용자당 최대 보관 개수 (초과 시 가장 오래된 것 자동 삭제) */
    private static final int    MAX_PER_USER     = 50;
    /** 자동 정리 — N 일 이상 된 이력 일괄 삭제 */
    private static final int    RETENTION_DAYS   = 30;

    private final FlowHistoryRepository repo;
    private final ObjectMapper          mapper;

    public FlowHistoryService(FlowHistoryRepository repo, ObjectMapper mapper) {
        this.repo   = repo;
        this.mapper = mapper;
    }

    /**
     * Flow 분석이 정상 완료된 직후 호출 — Phase 1 trace JSON + LLM narrative 함께 보관.
     * 실패는 조용히 로그만 (이력 저장 실패가 채팅 흐름에 영향 X).
     */
    @Transactional
    public void save(String userId, FlowAnalysisRequest req, FlowAnalysisResult result, String narrative) {
        if (userId == null || userId.trim().isEmpty()) return;
        try {
            String dmls = String.join(",", req.getEffectiveDmls().stream()
                    .map(Enum::name).sorted().toArray(String[]::new));
            Long elapsedMs = null;
            Object e = result.getStats() != null ? result.getStats().get("elapsedMs") : null;
            if (e instanceof Number) elapsedMs = ((Number) e).longValue();

            FlowHistory h = new FlowHistory(
                    userId,
                    truncate(req.getQuery(), 500),
                    result.getTargetType(),
                    dmls,
                    result.getNodes() != null ? result.getNodes().size() : 0,
                    result.getEdges() != null ? result.getEdges().size() : 0,
                    elapsedMs,
                    mapper.writeValueAsString(result),
                    narrative);
            repo.save(h);
            log.info("[FlowHistory] save OK user={} id={} nodes={} edges={}",
                    userId, h.getId(), h.getNodesCount(), h.getEdgesCount());
            pruneIfNeeded(userId);
        } catch (Exception ex) {
            log.warn("[FlowHistory] save 실패 user={} : {}", userId, ex.getMessage(), ex);
        }
    }

    public List<FlowHistory> recent(String userId, int limit) {
        if (limit < 1)  limit = 20;
        if (limit > 100) limit = 100;
        return repo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
    }

    public Optional<FlowHistory> findOwned(Long id, String userId) {
        return repo.findByIdAndUserId(id, userId);
    }

    @Transactional
    public boolean delete(Long id, String userId) {
        Optional<FlowHistory> h = repo.findByIdAndUserId(id, userId);
        if (!h.isPresent()) return false;
        repo.delete(h.get());
        return true;
    }

    /** 사용자가 MAX_PER_USER 를 초과하면 오래된 것부터 삭제 */
    private void pruneIfNeeded(String userId) {
        long count = repo.countByUserId(userId);
        if (count <= MAX_PER_USER) return;
        // 안전한 fallback — recent N 개 받아서 N 개 이후를 삭제
        List<FlowHistory> all = repo.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, (int) Math.min(count, 1000)));
        if (all.size() > MAX_PER_USER) {
            List<FlowHistory> excess = all.subList(MAX_PER_USER, all.size());
            repo.deleteAll(excess);
            log.info("[FlowHistory] prune user={} deleted={}", userId, excess.size());
        }
    }

    /** 매일 새벽 3시 30분 — 보관기간 초과분 일괄 삭제 (ShareController cleanup 와 시간 분산) */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanupOld() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = repo.deleteOlderThan(cutoff);
        if (deleted > 0) log.info("[FlowHistory] cleanup: {} 개 (cutoff={})", deleted, cutoff);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
