package io.github.claudetoolkit.ui.export;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.4.0 — SarifExportService 단위 테스트.
 *
 * <p>SARIF 2.1.0 구조 + severity 마커 추출 + 메시지 매핑을 검증.
 */
class SarifExportServiceTest {

    private SarifExportService service;

    @BeforeEach
    void setUp() {
        service = new SarifExportService();
    }

    @Test
    @DisplayName("기본 SARIF 구조 — version, $schema, runs 존재")
    void basicSarifStructure() {
        ReviewHistory h = makeHistory("CODE_REVIEW", "테스트", "input", "output");
        Map<String, Object> sarif = service.toSarif(h);

        assertEquals("2.1.0", sarif.get("version"));
        assertNotNull(sarif.get("$schema"));
        assertNotNull(sarif.get("runs"));
        Object[] runs = (Object[]) sarif.get("runs");
        assertEquals(1, runs.length);
    }

    @Test
    @DisplayName("[SEVERITY: HIGH] → SARIF level=error 매핑")
    void severityHighMapping() {
        ReviewHistory h = makeHistory("SQL_REVIEW", "보안 검사",
                "SELECT * FROM USERS",
                "[SEVERITY: HIGH] SQL Injection 위험: 사용자 입력 직접 결합");
        Map<String, Object> sarif = service.toSarif(h);
        Object[] runs = (Object[]) sarif.get("runs");
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) runs[0];
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) run.get("results");
        assertFalse(results.isEmpty());
        assertEquals("error", results.get(0).get("level"));
    }

    @Test
    @DisplayName("[SEVERITY: MEDIUM] → warning, [SEVERITY: LOW] → note")
    void severityMediumLowMapping() {
        ReviewHistory h = makeHistory("CODE_REVIEW", "리뷰", "code",
                "[SEVERITY: MEDIUM] 가독성 개선 권장\n[SEVERITY: LOW] 변수명 카멜케이스 권장");
        Map<String, Object> sarif = service.toSarif(h);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) ((Map<String, Object>) ((Object[]) sarif.get("runs"))[0]).get("results");
        assertEquals(2, results.size());
        boolean hasWarning = results.stream().anyMatch(r -> "warning".equals(r.get("level")));
        boolean hasNote    = results.stream().anyMatch(r -> "note".equals(r.get("level")));
        assertTrue(hasWarning, "warning 레벨 결과 존재");
        assertTrue(hasNote,    "note 레벨 결과 존재");
    }

    @Test
    @DisplayName("severity 마커 없는 출력 — 단일 note 결과로 변환")
    void noSeverityMarker() {
        ReviewHistory h = makeHistory("DOC_GEN", "문서", "code", "단순 분석 결과 텍스트");
        Map<String, Object> sarif = service.toSarif(h);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) ((Map<String, Object>) ((Object[]) sarif.get("runs"))[0]).get("results");
        assertEquals(1, results.size());
        assertEquals("note", results.get(0).get("level"));
    }

    @Test
    @DisplayName("동일 severity+메시지 중복 제거")
    void duplicateRemoved() {
        ReviewHistory h = makeHistory("CODE_REVIEW", "리뷰", "code",
                "[SEVERITY: HIGH] N+1 쿼리\n다른 라인\n[SEVERITY: HIGH] N+1 쿼리");  // 같은 메시지 2회
        Map<String, Object> sarif = service.toSarif(h);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) ((Map<String, Object>) ((Object[]) sarif.get("runs"))[0]).get("results");
        assertEquals(1, results.size(), "동일 severity+메시지는 1번만");
    }

    @Test
    @DisplayName("rule 메타데이터 — id=type, name=typeLabel")
    void ruleMetadata() {
        ReviewHistory h = makeHistory("SQL_REVIEW", "검사", "in", "out");
        Map<String, Object> sarif = service.toSarif(h);
        @SuppressWarnings("unchecked")
        Map<String, Object> tool = (Map<String, Object>) ((Map<String, Object>) ((Object[]) sarif.get("runs"))[0]).get("tool");
        @SuppressWarnings("unchecked")
        Map<String, Object> driver = (Map<String, Object>) tool.get("driver");
        Object[] rules = (Object[]) driver.get("rules");
        @SuppressWarnings("unchecked")
        Map<String, Object> rule = (Map<String, Object>) rules[0];
        assertEquals("SQL_REVIEW", rule.get("id"));
        assertEquals("SQL 리뷰", rule.get("name"));   // typeLabel
    }

    @Test
    @DisplayName("null ReviewHistory → IllegalArgumentException")
    void nullHistoryThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.toSarif(null));
    }

    private ReviewHistory makeHistory(String type, String title, String input, String output) {
        ReviewHistory h = new ReviewHistory(type, title, input, output);
        try {
            Field f = ReviewHistory.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(h, LocalDateTime.now());
            Field id = ReviewHistory.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(h, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return h;
    }
}
