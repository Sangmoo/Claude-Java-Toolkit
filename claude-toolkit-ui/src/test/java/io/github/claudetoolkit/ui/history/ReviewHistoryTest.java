package io.github.claudetoolkit.ui.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.4.0 — ReviewHistory 엔티티 단위 테스트.
 *
 * <p>토큰 합산 / 타입 라벨 매핑 / 배지 색상 / 출력 미리보기 정리 로직 검증.
 */
class ReviewHistoryTest {

    @Test
    @DisplayName("getTotalTokens — input + output 합산")
    void totalTokensSum() {
        ReviewHistory h = new ReviewHistory("X", "t", "in", "out", null, 100L, 200L);
        assertEquals(300L, h.getTotalTokens());
    }

    @Test
    @DisplayName("getTotalTokens — null 토큰은 0 으로 처리 (NPE 없음)")
    void totalTokensWithNulls() {
        ReviewHistory h1 = new ReviewHistory("X", "t", "in", "out");
        assertEquals(0L, h1.getTotalTokens());

        ReviewHistory h2 = new ReviewHistory("X", "t", "in", "out", null, 50L, null);
        assertEquals(50L, h2.getTotalTokens());

        ReviewHistory h3 = new ReviewHistory("X", "t", "in", "out", null, null, 75L);
        assertEquals(75L, h3.getTotalTokens());
    }

    @Test
    @DisplayName("getTypeLabel — 알려진 타입은 한국어 라벨 반환")
    void typeLabelKnown() {
        assertEquals("SQL 리뷰", new ReviewHistory("SQL_REVIEW", "t", "i", "o").getTypeLabel());
        assertEquals("코드 리뷰", new ReviewHistory("CODE_REVIEW", "t", "i", "o").getTypeLabel());
        assertEquals("하네스 리뷰", new ReviewHistory("HARNESS_REVIEW", "t", "i", "o").getTypeLabel());
        assertEquals("ERD 분석", new ReviewHistory("ERD", "t", "i", "o").getTypeLabel());
        assertEquals("실행계획", new ReviewHistory("EXPLAIN_PLAN", "t", "i", "o").getTypeLabel());
    }

    @Test
    @DisplayName("getTypeLabel — 알려지지 않은 타입은 원본 반환")
    void typeLabelUnknownFallback() {
        assertEquals("CUSTOM_TYPE", new ReviewHistory("CUSTOM_TYPE", "t", "i", "o").getTypeLabel());
    }

    @Test
    @DisplayName("getTypeBadgeColor — 모든 알려진 타입은 #으로 시작하는 hex 컬러")
    void typeBadgeColorFormat() {
        String[] types = { "SQL_REVIEW", "CODE_REVIEW", "DOC_GEN", "HARNESS_REVIEW", "EXPLAIN_PLAN" };
        for (String t : types) {
            String c = new ReviewHistory(t, "t", "i", "o").getTypeBadgeColor();
            assertTrue(c.startsWith("#") && c.length() == 7, t + " → " + c);
        }
    }

    @Test
    @DisplayName("getOutputPreview — 200자 절단 + 마크다운 기호 제거")
    void outputPreviewTrimsAndCleans() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("**bold** `code` # heading > quote ");
        ReviewHistory h = new ReviewHistory("X", "t", "in", sb.toString());
        String preview = h.getOutputPreview();
        assertTrue(preview.length() <= 200);
        assertFalse(preview.contains("**"), "별표 제거됨");
        assertFalse(preview.contains("`"),  "백틱 제거됨");
        assertFalse(preview.contains("#"),  "해시 제거됨");
        assertFalse(preview.contains(">"),  "꺾쇠 제거됨");
    }

    @Test
    @DisplayName("getOutputPreview — null/빈 출력 시 빈 문자열")
    void outputPreviewEmpty() {
        assertEquals("", new ReviewHistory("X", "t", "in", null).getOutputPreview());
        assertEquals("", new ReviewHistory("X", "t", "in", "").getOutputPreview());
    }

    @Test
    @DisplayName("getReviewStatus — null 시 기본값 PENDING")
    void reviewStatusDefault() {
        ReviewHistory h = new ReviewHistory("X", "t", "in", "out");
        assertEquals("PENDING", h.getReviewStatus());

        h.setReviewStatus(null);
        assertEquals("PENDING", h.getReviewStatus());

        h.setReviewStatus("ACCEPTED");
        assertEquals("ACCEPTED", h.getReviewStatus());
    }

    @Test
    @DisplayName("getFormattedDate — MM-dd HH:mm 형식")
    void formattedDateFormat() {
        ReviewHistory h = new ReviewHistory("X", "t", "in", "out");
        String formatted = h.getFormattedDate();
        // 형식: "MM-dd HH:mm" → 11자
        assertTrue(formatted.matches("\\d{2}-\\d{2} \\d{2}:\\d{2}"), "예: 04-22 14:30, 실제: " + formatted);
    }
}
