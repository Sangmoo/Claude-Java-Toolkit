package io.github.claudetoolkit.ui.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — AnalysisCacheService 의 정규화 + 4-arg key + hit/miss 카운터 검증.
 *
 * <p>Spring 컨텍스트 (H2 in-memory + JPA) 를 사용한 통합 테스트. 정규화 정책이
 * 의도대로 동작하는지 (공백/BOM/CRLF 차이가 같은 키로 매핑) 가 핵심.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalysisCacheServiceTest {

    @Autowired
    private AnalysisCacheService service;

    @Autowired
    private AnalysisCacheRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("기본 동작 — put 후 같은 키로 get 하면 결과 반환")
    void basicPutGet() {
        service.put("sql_review", "SELECT 1", "result-A");
        assertEquals("result-A", service.get("sql_review", "SELECT 1"));
    }

    @Test
    @DisplayName("정규화 — 양 끝 공백 차이는 같은 캐시 키")
    void normalize_trimsWhitespace() {
        service.put("sql_review", "SELECT 1", "result");
        assertEquals("result", service.get("sql_review", "  SELECT 1  "));
        assertEquals("result", service.get("sql_review", "\nSELECT 1\n"));
    }

    @Test
    @DisplayName("정규화 — CRLF / CR / LF 모두 같은 키")
    void normalize_lineEndings() {
        service.put("doc_gen", "line1\nline2", "result");
        assertEquals("result", service.get("doc_gen", "line1\r\nline2"));
        assertEquals("result", service.get("doc_gen", "line1\rline2"));
    }

    @Test
    @DisplayName("정규화 — 라인 끝 trailing 공백 제거")
    void normalize_trailingSpaces() {
        service.put("code_review", "line1\nline2", "result");
        assertEquals("result", service.get("code_review", "line1   \nline2  "));
    }

    @Test
    @DisplayName("정규화 — 연속 빈 줄 (3개+) 은 2개로 축약")
    void normalize_collapsesEmptyLines() {
        service.put("doc_gen", "line1\n\nline2", "result");
        assertEquals("result", service.get("doc_gen", "line1\n\n\n\n\nline2"));
    }

    @Test
    @DisplayName("정규화 — UTF-8 BOM 제거")
    void normalize_stripBom() {
        String bom = "﻿";
        service.put("doc_gen", "Hello", "result");
        assertEquals("result", service.get("doc_gen", bom + "Hello"));
    }

    @Test
    @DisplayName("4-arg 키 — input2 다르면 다른 캐시")
    void differentInput2_separateCache() {
        service.put("explain_plan", "SELECT 1", "PLAN_A", null, "result-A");
        service.put("explain_plan", "SELECT 1", "PLAN_B", null, "result-B");
        assertEquals("result-A", service.get("explain_plan", "SELECT 1", "PLAN_A", null));
        assertEquals("result-B", service.get("explain_plan", "SELECT 1", "PLAN_B", null));
    }

    @Test
    @DisplayName("4-arg 키 — sourceType 다르면 다른 캐시 (review vs security 충돌 버그 회귀 방지)")
    void differentSourceType_separateCache() {
        service.put("sql_review", "SELECT * FROM t",  null, "review",   "result-review");
        service.put("sql_review", "SELECT * FROM t",  null, "security", "result-security");
        assertEquals("result-review",   service.get("sql_review", "SELECT * FROM t", null, "review"));
        assertEquals("result-security", service.get("sql_review", "SELECT * FROM t", null, "security"));
    }

    @Test
    @DisplayName("hit/miss 카운터 + hit rate 계산")
    void hitMissCounters() {
        service.put("doc_gen", "Hello", "result");
        // 1 hit
        service.get("doc_gen", "Hello");
        // 2 misses (다른 입력 / 다른 feature)
        service.get("doc_gen", "Different input");
        service.get("other_feature", "Hello");

        Map<String, Object> stats = service.getStats();
        // 다른 테스트가 같은 service instance 의 카운터를 공유할 수 있어 >=
        long hits   = ((Number) stats.get("hits")).longValue();
        long misses = ((Number) stats.get("misses")).longValue();
        assertTrue(hits >= 1,   "hits >= 1 expected, got " + hits);
        assertTrue(misses >= 2, "misses >= 2 expected, got " + misses);
        assertNotNull(stats.get("hitRatePercent"));
    }

    @Test
    @DisplayName("기존 2-arg API 가 4-arg API 와 호환 (delegate)")
    void twoArgDelegatesToFourArg() {
        service.put("test_feat", "input-X", "result-X");
        assertEquals("result-X", service.get("test_feat", "input-X", null, null));
        assertEquals("result-X", service.get("test_feat", "input-X", "", ""));
    }

    @Test
    @DisplayName("null 입력 — get 미스 / put 무시")
    void nullInputs() {
        assertNull(service.get("test", null));
        service.put("test", null, "result");  // 무시되어야 함
        assertNull(service.get("test", null));
    }
}
