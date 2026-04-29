package io.github.claudetoolkit.ui.cost;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.4.0 — ModelCostService 단위 테스트.
 *
 * <p>비용 계산 + 모델 추천 로직(Haiku/Sonnet/Opus 분기)을 검증.
 */
class ModelCostServiceTest {

    private FakeRepo repo;
    private ModelCostService service;

    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        service = new ModelCostService(repo);
    }

    @Test
    @DisplayName("빈 이력 — 0 비용 + 빈 byType")
    void emptyHistory() {
        Map<String, Object> r = service.analyze(30, "claude-sonnet-4");
        assertEquals(0L, r.get("totalAnalyses"));
        assertEquals(0.0, r.get("totalCurrentCostUsd"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) r.get("byType");
        assertTrue(byType.isEmpty());
    }

    @Test
    @DisplayName("Haiku 추천 — 평균 입력 ≤ 2000 + 승인률 ≥ 0.85")
    void recommendHaikuForSmallHighAccept() {
        // 10건: 각 1500 input, 9 ACCEPTED, 1 REJECTED → 90% 승인률
        for (int i = 0; i < 10; i++) {
            ReviewHistory h = new ReviewHistory("CODE_REVIEW", "t", "in", "out", null, 1500L, 200L);
            h.setReviewStatus(i < 9 ? "ACCEPTED" : "REJECTED");
            setCreatedAt(h, LocalDateTime.now().minusDays(1));
            repo.entries.add(h);
        }
        Map<String, Object> r = service.analyze(30, "claude-opus-4");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) r.get("byType");
        assertEquals(1, byType.size());
        assertEquals("claude-haiku-4", byType.get(0).get("recommendedModelKey"));
        // 절감액 > 0 (Opus → Haiku)
        assertTrue((Double) byType.get(0).get("monthlySavingUsd") > 0);
    }

    @Test
    @DisplayName("Sonnet 추천 — 평균 입력 ≤ 8000 + 승인률 ≥ 0.70 (Haiku 자격 미달)")
    void recommendSonnetForMedium() {
        for (int i = 0; i < 10; i++) {
            ReviewHistory h = new ReviewHistory("SQL_REVIEW", "t", "in", "out", null, 5000L, 500L);
            h.setReviewStatus(i < 8 ? "ACCEPTED" : "REJECTED");  // 80% 승인률 (Haiku 미달)
            setCreatedAt(h, LocalDateTime.now().minusDays(1));
            repo.entries.add(h);
        }
        Map<String, Object> r = service.analyze(30, "claude-opus-4");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) r.get("byType");
        assertEquals("claude-sonnet-4", byType.get(0).get("recommendedModelKey"));
    }

    @Test
    @DisplayName("Opus 추천 — 평균 입력 큰 + 승인률 낮음 (정확도 우선)")
    void recommendOpusForLargeOrLowAccept() {
        for (int i = 0; i < 5; i++) {
            ReviewHistory h = new ReviewHistory("HARNESS_REVIEW", "t", "in", "out", null, 15000L, 3000L);
            h.setReviewStatus("ACCEPTED");
            setCreatedAt(h, LocalDateTime.now().minusDays(1));
            repo.entries.add(h);
        }
        Map<String, Object> r = service.analyze(30, "claude-sonnet-4");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) r.get("byType");
        assertEquals("claude-opus-4", byType.get(0).get("recommendedModelKey"));
        // 현재 Sonnet → 추천 Opus 면 비용 증가 (절감 < 0)
        assertTrue((Double) byType.get(0).get("monthlySavingUsd") <= 0);
    }

    @Test
    @DisplayName("기간 필터 — 31일 전 이력은 days=30 에서 제외")
    void filterOldByDays() {
        ReviewHistory recent = new ReviewHistory("X", "t", "in", "out", null, 100L, 100L);
        setCreatedAt(recent, LocalDateTime.now().minusDays(5));
        repo.entries.add(recent);

        ReviewHistory old = new ReviewHistory("X", "t", "in", "out", null, 100L, 100L);
        setCreatedAt(old, LocalDateTime.now().minusDays(60));
        repo.entries.add(old);

        Map<String, Object> r = service.analyze(30, "claude-sonnet-4");
        assertEquals(1L, r.get("totalAnalyses"), "30일 전 이력은 제외되어야 함");
    }

    @Test
    @DisplayName("비용 계산 — Sonnet 단가 $3/$15 per 1M tokens 정확성")
    void costCalculationAccuracy() throws Exception {
        // 1M input + 1M output, Sonnet 단가 = $3 + $15 = $18
        ReviewHistory h = new ReviewHistory("X", "t", "in", "out", null, 1_000_000L, 1_000_000L);
        h.setReviewStatus("ACCEPTED");
        setCreatedAt(h, LocalDateTime.now().minusDays(1));
        repo.entries.add(h);

        Map<String, Object> r = service.analyze(30, "claude-sonnet-4");
        // 추천이 무엇이든 currentCost 는 Sonnet 기준
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) r.get("byType");
        double cost = ((Number) byType.get(0).get("currentCostUsd")).doubleValue();
        assertEquals(18.0, cost, 0.01, "Sonnet 1M+1M = $18");
    }

    @Test
    @DisplayName("PRICING 표 — 모든 핵심 모델 키 등록 검증")
    void pricingTableComplete() {
        assertNotNull(ModelCostService.PRICING.get("claude-opus-4"));
        assertNotNull(ModelCostService.PRICING.get("claude-sonnet-4"));
        assertNotNull(ModelCostService.PRICING.get("claude-haiku-4"));
        assertNotNull(ModelCostService.PRICING.get("default"));
    }

    @Test
    @DisplayName("recommend() 경계값 — avgInput=2000 + acceptRate=0.85 정확히 만족 시 Haiku")
    void boundaryHaiku() throws Exception {
        Method m = ModelCostService.class.getDeclaredMethod("recommend", double.class, double.class);
        m.setAccessible(true);
        assertEquals("claude-haiku-4", m.invoke(service, 2000.0, 0.85));
        // 한 단위라도 초과/미달 시 Sonnet 또는 Opus
        assertEquals("claude-sonnet-4", m.invoke(service, 2001.0, 0.85));
        assertEquals("claude-opus-4",   m.invoke(service, 9000.0, 0.85));
    }

    private static void setCreatedAt(ReviewHistory h, LocalDateTime t) {
        try {
            Field f = ReviewHistory.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(h, t);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 메모리 기반 가짜 리포지토리 — JPA 의존 회피 */
    static class FakeRepo implements ReviewHistoryRepository {
        final List<ReviewHistory> entries = new ArrayList<>();
        public List<ReviewHistory> findRecentEntries(Pageable pageable) {
            int limit = Math.min(pageable.getPageSize(), entries.size());
            return new ArrayList<>(entries.subList(0, limit));
        }
        public ReviewHistory findTopByOrderByCreatedAtAsc() { return entries.isEmpty() ? null : entries.get(0); }
        public List<ReviewHistory> findByTypeOrderByCreatedAtAsc(String type) {
            List<ReviewHistory> r = new ArrayList<>();
            for (ReviewHistory h : entries) if (type.equals(h.getType())) r.add(h);
            return r;
        }
        public List<ReviewHistory> findWithTokenUsage() { return entries; }
        public List<ReviewHistory> searchByKeyword(String q, Pageable pageable) { return Collections.emptyList(); }
        // v4.7.x — #12 태그 시스템 추가 메서드 (테스트에서 호출 안 됨, 빈 결과)
        public List<ReviewHistory> findByUsernameAndTag(String username, String tag, Pageable pageable) { return Collections.emptyList(); }
        public List<String> findAllTagsByUsername(String username) { return Collections.emptyList(); }
        // ── JpaRepository stubs (호출 안 됨) ──
        public List<ReviewHistory> findAll() { return entries; }
        public List<ReviewHistory> findAll(org.springframework.data.domain.Sort sort) { return entries; }
        public Page<ReviewHistory> findAll(Pageable pageable) { return new PageImpl<>(entries); }
        public List<ReviewHistory> findAllById(Iterable<Long> ids) { return Collections.emptyList(); }
        public long count() { return entries.size(); }
        public void deleteById(Long id) {}
        public void delete(ReviewHistory entity) {}
        public void deleteAllById(Iterable<? extends Long> ids) {}
        public void deleteAll(Iterable<? extends ReviewHistory> entities) {}
        public void deleteAll() { entries.clear(); }
        public <S extends ReviewHistory> S save(S entity) { entries.add(entity); return entity; }
        public <S extends ReviewHistory> List<S> saveAll(Iterable<S> entities) {
            List<S> r = new ArrayList<>();
            for (S e : entities) { entries.add(e); r.add(e); }
            return r;
        }
        public Optional<ReviewHistory> findById(Long id) {
            return entries.stream().filter(h -> h.getId() == id).findFirst();
        }
        public boolean existsById(Long id) { return findById(id).isPresent(); }
        public void flush() {}
        public <S extends ReviewHistory> S saveAndFlush(S entity) { return save(entity); }
        public <S extends ReviewHistory> List<S> saveAllAndFlush(Iterable<S> entities) { return saveAll(entities); }
        public void deleteAllInBatch(Iterable<ReviewHistory> entities) {}
        public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        public void deleteAllInBatch() { entries.clear(); }
        public ReviewHistory getOne(Long id) { return findById(id).orElse(null); }
        public ReviewHistory getById(Long id) { return findById(id).orElse(null); }
        public ReviewHistory getReferenceById(Long id) { return findById(id).orElse(null); }
        public <S extends ReviewHistory> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        public <S extends ReviewHistory> List<S> findAll(org.springframework.data.domain.Example<S> example) { return Collections.emptyList(); }
        public <S extends ReviewHistory> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        public <S extends ReviewHistory> Page<S> findAll(org.springframework.data.domain.Example<S> example, Pageable pageable) { return Page.empty(); }
        public <S extends ReviewHistory> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        public <S extends ReviewHistory> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        public <S extends ReviewHistory, R> R findBy(org.springframework.data.domain.Example<S> example,
            java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            return null;
        }
    }
}
