package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 4: IndexSimulationResult 단위 테스트.
 * 주로 {@link IndexSimulationResult#getImprovementPercent()} 계산 검증.
 */
class IndexSimulationResultTest {

    @Test
    @DisplayName("improvement — null cost 면 null 반환")
    void improvement_nullSafe() {
        IndexSimulationResult r = new IndexSimulationResult();
        assertNull(r.getImprovementPercent());
        r.setBeforeCost(100L);
        assertNull(r.getImprovementPercent());
        r.setBeforeCost(null);
        r.setAfterCost(50L);
        assertNull(r.getImprovementPercent());
    }

    @Test
    @DisplayName("improvement — beforeCost 가 0 이하면 null (DIV/0 회피)")
    void improvement_zeroBefore() {
        IndexSimulationResult r = new IndexSimulationResult();
        r.setBeforeCost(0L);
        r.setAfterCost(0L);
        assertNull(r.getImprovementPercent());
    }

    @Test
    @DisplayName("improvement — 80% 감소 정확히 계산")
    void improvement_80percent() {
        IndexSimulationResult r = new IndexSimulationResult();
        r.setBeforeCost(1000L);
        r.setAfterCost(200L);
        Double imp = r.getImprovementPercent();
        assertNotNull(imp);
        assertEquals(80.0, imp, 0.1);
    }

    @Test
    @DisplayName("improvement — cost 동일이면 0%")
    void improvement_zero() {
        IndexSimulationResult r = new IndexSimulationResult();
        r.setBeforeCost(1000L);
        r.setAfterCost(1000L);
        assertEquals(0.0, r.getImprovementPercent());
    }

    @Test
    @DisplayName("improvement — after > before 면 음수 (악화)")
    void improvement_negative() {
        IndexSimulationResult r = new IndexSimulationResult();
        r.setBeforeCost(100L);
        r.setAfterCost(150L);
        Double imp = r.getImprovementPercent();
        assertNotNull(imp);
        assertTrue(imp < 0, "악화: " + imp);
    }

    @Test
    @DisplayName("hasComparison — 둘 다 cost 있어야 true")
    void hasComparison() {
        IndexSimulationResult r = new IndexSimulationResult();
        assertFalse(r.hasComparison());
        r.setBeforeCost(100L);
        assertFalse(r.hasComparison());
        r.setAfterCost(50L);
        assertTrue(r.hasComparison());
    }

    @Test
    @DisplayName("warnings — 추가 + unmodifiable 반환")
    void warnings() {
        IndexSimulationResult r = new IndexSimulationResult();
        assertTrue(r.getWarnings().isEmpty());
        r.addWarning("test1");
        r.addWarning("test2");
        r.addWarning(null);  // null 무시
        assertEquals(2, r.getWarnings().size());
        assertThrows(UnsupportedOperationException.class, () ->
                r.getWarnings().add("forbidden"));
    }
}
