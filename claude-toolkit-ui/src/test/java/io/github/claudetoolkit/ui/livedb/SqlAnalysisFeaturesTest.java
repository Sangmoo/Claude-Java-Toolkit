package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 2: SqlAnalysisFeatures 화이트리스트 검증.
 *
 * <p>화이트리스트는 *프론트엔드와 동기화* 되어야 하므로 (AnalysisPageTemplate.tsx 의
 * LIVE_DB_FEATURES Set), 어느 한쪽이 추가/삭제될 때 둘 다 갱신해야 함.
 * 이 테스트는 백엔드 화이트리스트가 의도된 feature 만 포함하는지 검증.
 */
class SqlAnalysisFeaturesTest {

    @Test
    @DisplayName("SQL 관련 feature — true 반환")
    void sqlFeatures_attached() {
        assertTrue(SqlAnalysisFeatures.shouldAttachLiveDbContext("sql_review"));
        assertTrue(SqlAnalysisFeatures.shouldAttachLiveDbContext("explain_plan"));
        assertTrue(SqlAnalysisFeatures.shouldAttachLiveDbContext("index_advisor"));
        assertTrue(SqlAnalysisFeatures.shouldAttachLiveDbContext("sql_translate"));
        assertTrue(SqlAnalysisFeatures.shouldAttachLiveDbContext("sql_batch"));
        assertTrue(SqlAnalysisFeatures.shouldAttachLiveDbContext("erd_analysis"));
    }

    @Test
    @DisplayName("Java/문서 관련 feature — false 반환 (노이즈 방지)")
    void nonSqlFeatures_notAttached() {
        assertFalse(SqlAnalysisFeatures.shouldAttachLiveDbContext("doc_gen"));
        assertFalse(SqlAnalysisFeatures.shouldAttachLiveDbContext("api_spec"));
        assertFalse(SqlAnalysisFeatures.shouldAttachLiveDbContext("code_review"));
        assertFalse(SqlAnalysisFeatures.shouldAttachLiveDbContext("complexity"));
        assertFalse(SqlAnalysisFeatures.shouldAttachLiveDbContext("converter"));
        assertFalse(SqlAnalysisFeatures.shouldAttachLiveDbContext("harness_review"));
        assertFalse(SqlAnalysisFeatures.shouldAttachLiveDbContext("commit_msg"));
        assertFalse(SqlAnalysisFeatures.shouldAttachLiveDbContext("regex_gen"));
    }

    @Test
    @DisplayName("null/빈 입력 — false (안전 측 default)")
    void nullSafe() {
        assertFalse(SqlAnalysisFeatures.shouldAttachLiveDbContext(null));
        assertFalse(SqlAnalysisFeatures.shouldAttachLiveDbContext(""));
    }

    @Test
    @DisplayName("화이트리스트 대상 6개 정확히 — UI 동기화 회귀 방지")
    void whitelistSize() {
        // 의도적: 추가/제거 시 이 테스트가 실패하면 프론트엔드도 함께 업데이트해야 함을 알림
        assertEquals(6, SqlAnalysisFeatures.all().size(),
                "화이트리스트 크기 변경 — frontend AnalysisPageTemplate.LIVE_DB_FEATURES 도 함께 업데이트 필요");
    }
}
