package io.github.claudetoolkit.ui.errorlog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.4.0 — ErrorLogService 단위 테스트.
 *
 * <p>dedupe 로직 + 메시지 정규화 + 절단 + 안전 동작을 검증.
 */
class ErrorLogServiceTest {

    private FakeRepo repo;
    private ErrorLogService service;

    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        service = new ErrorLogService(repo);
    }

    @Test
    @DisplayName("같은 예외 + 같은 메시지 → dedupe (1행만 생성, count 증가)")
    void dedupeIdenticalErrors() {
        Exception ex1 = new RuntimeException("Connection failed");
        Exception ex2 = new RuntimeException("Connection failed");

        service.record(ex1, null);
        service.record(ex2, null);
        service.record(ex2, null);

        assertEquals(1, repo.entries.size(), "dedupe — 1행만 존재");
        assertEquals(3, repo.entries.get(0).getOccurrenceCount(), "occurrenceCount = 3");
    }

    @Test
    @DisplayName("메시지 내 숫자 정규화 → 같은 dedupeKey")
    void numbersNormalizedForDedupe() {
        service.record(new RuntimeException("User 12345 not found"), null);
        service.record(new RuntimeException("User 67890 not found"), null);

        assertEquals(1, repo.entries.size(), "숫자 다르더라도 같은 그룹");
        assertEquals(2, repo.entries.get(0).getOccurrenceCount());
    }

    @Test
    @DisplayName("UUID/타임스탬프 정규화 → dedupe")
    void uuidAndTimestampNormalized() {
        service.record(new RuntimeException("Request 550e8400-e29b-41d4-a716-446655440000 failed at 2026-04-22T14:30:00"), null);
        service.record(new RuntimeException("Request 7c9e6679-7425-40de-944b-e07fc1f90ae7 failed at 2026-04-22T15:45:00"), null);

        assertEquals(1, repo.entries.size(), "UUID + 타임스탬프 정규화 → 같은 그룹");
    }

    @Test
    @DisplayName("다른 예외 클래스 → 다른 그룹")
    void differentExceptionClassesAreDifferentGroups() {
        service.record(new RuntimeException("oops"), null);
        service.record(new IllegalArgumentException("oops"), null);

        assertEquals(2, repo.entries.size());
    }

    @Test
    @DisplayName("긴 메시지 자동 절단 (500자 제한)")
    void longMessageTruncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) sb.append("x");
        service.record(new RuntimeException(sb.toString()), null);

        assertEquals(1, repo.entries.size());
        assertTrue(repo.entries.get(0).getMessage().length() <= 500);
        assertTrue(repo.entries.get(0).getMessage().endsWith("..."));
    }

    @Test
    @DisplayName("null 메시지 안전 처리")
    void nullMessageHandled() {
        Exception ex = new RuntimeException((String) null);
        assertDoesNotThrow(() -> service.record(ex, null));
        assertEquals(1, repo.entries.size());
        assertEquals("(메시지 없음)", repo.entries.get(0).getMessage());
    }

    @Test
    @DisplayName("스택트레이스 캡처 — 10KB 절단")
    void stackTraceCaptured() {
        Exception ex = new RuntimeException("test error");
        service.record(ex, null);

        ErrorLog e = repo.entries.get(0);
        assertNotNull(e.getStackTrace());
        assertTrue(e.getStackTrace().contains("RuntimeException"));
        assertTrue(e.getStackTrace().contains("ErrorLogServiceTest"));
        assertTrue(e.getStackTrace().length() <= 10_100);  // 10KB + 절단 메시지 여유
    }

    @Test
    @DisplayName("해결된 오류가 다시 발생 → 자동 unresolved 복귀")
    void resolvedErrorBecomesUnresolvedOnReoccurrence() {
        service.record(new RuntimeException("intermittent"), null);
        service.markResolved(repo.entries.get(0).getId(), "admin");
        assertTrue(repo.entries.get(0).isResolved());

        service.record(new RuntimeException("intermittent"), null);
        assertFalse(repo.entries.get(0).isResolved(), "재발생 시 자동 unresolved");
        assertNull(repo.entries.get(0).getResolvedBy());
    }

    @Test
    @DisplayName("저장 실패해도 절대 예외 throw 안 함 (silent)")
    void recordIsSilentOnFailure() {
        ErrorLogService brokenService = new ErrorLogService(new ThrowingRepo());
        // 어떤 예외가 들어와도 record() 자체는 throw 하지 않아야 함
        assertDoesNotThrow(() -> brokenService.record(new RuntimeException("test"), null));
    }

    @Test
    @DisplayName("정규화 함수 — ISO timestamp 패턴 인식")
    void normalizeIsoTimestamp() throws Exception {
        Method m = ErrorLogService.class.getDeclaredMethod("normalizeForDedupe", String.class);
        m.setAccessible(true);
        String n1 = (String) m.invoke(service, "Failed at 2026-04-22T14:30:00 with code 500");
        // 숫자도 함께 정규화됨
        assertTrue(n1.contains("{ts}"));
        assertTrue(n1.contains("{n}"));
    }

    // ── 가짜 Repository ───────────────────────────────────────────────

    static class FakeRepo implements ErrorLogRepository {
        final List<ErrorLog> entries = new ArrayList<>();
        long nextId = 1;
        public Optional<ErrorLog> findByDedupeKey(String dedupeKey) {
            return entries.stream().filter(e -> dedupeKey.equals(e.getDedupeKey())).findFirst();
        }
        public List<ErrorLog> findRecent(Pageable p) { return entries; }
        public List<ErrorLog> findUnresolved(Pageable p) {
            List<ErrorLog> r = new ArrayList<>();
            for (ErrorLog e : entries) if (!e.isResolved()) r.add(e);
            return r;
        }
        public int deleteResolvedOlderThan(LocalDateTime cutoff) { return 0; }
        public long countByResolvedFalse() { return entries.stream().filter(e -> !e.isResolved()).count(); }

        // JpaRepository stubs
        public List<ErrorLog> findAll() { return entries; }
        public List<ErrorLog> findAll(org.springframework.data.domain.Sort s) { return entries; }
        public Page<ErrorLog> findAll(Pageable p) { return new PageImpl<>(entries); }
        public List<ErrorLog> findAllById(Iterable<Long> ids) { return Collections.emptyList(); }
        public long count() { return entries.size(); }
        public void deleteById(Long id) {}
        public void delete(ErrorLog entity) {}
        public void deleteAllById(Iterable<? extends Long> ids) {}
        public void deleteAll(Iterable<? extends ErrorLog> entities) {}
        public void deleteAll() { entries.clear(); }
        public <S extends ErrorLog> S save(S entity) {
            if (entity.getId() == null) {
                try {
                    java.lang.reflect.Field f = ErrorLog.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, nextId++);
                } catch (Exception e) { throw new RuntimeException(e); }
                entries.add(entity);
            }
            return entity;
        }
        public <S extends ErrorLog> List<S> saveAll(Iterable<S> entities) {
            List<S> r = new ArrayList<>();
            for (S e : entities) r.add(save(e));
            return r;
        }
        public Optional<ErrorLog> findById(Long id) {
            return entries.stream().filter(e -> e.getId().equals(id)).findFirst();
        }
        public boolean existsById(Long id) { return findById(id).isPresent(); }
        public void flush() {}
        public <S extends ErrorLog> S saveAndFlush(S entity) { return save(entity); }
        public <S extends ErrorLog> List<S> saveAllAndFlush(Iterable<S> entities) { return saveAll(entities); }
        public void deleteAllInBatch(Iterable<ErrorLog> entities) {}
        public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        public void deleteAllInBatch() { entries.clear(); }
        public ErrorLog getOne(Long id) { return findById(id).orElse(null); }
        public ErrorLog getById(Long id) { return findById(id).orElse(null); }
        public ErrorLog getReferenceById(Long id) { return findById(id).orElse(null); }
        public <S extends ErrorLog> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { return Optional.empty(); }
        public <S extends ErrorLog> List<S> findAll(org.springframework.data.domain.Example<S> ex) { return Collections.emptyList(); }
        public <S extends ErrorLog> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { return Collections.emptyList(); }
        public <S extends ErrorLog> Page<S> findAll(org.springframework.data.domain.Example<S> ex, Pageable p) { return Page.empty(); }
        public <S extends ErrorLog> long count(org.springframework.data.domain.Example<S> ex) { return 0; }
        public <S extends ErrorLog> boolean exists(org.springframework.data.domain.Example<S> ex) { return false; }
        public <S extends ErrorLog, R> R findBy(org.springframework.data.domain.Example<S> ex,
            java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> q) {
            return null;
        }
    }

    /** silent 동작 검증용 — 모든 호출이 RuntimeException 던짐 */
    static class ThrowingRepo extends FakeRepo {
        public Optional<ErrorLog> findByDedupeKey(String k) { throw new RuntimeException("DB down"); }
        public <S extends ErrorLog> S save(S entity) { throw new RuntimeException("DB down"); }
    }
}
