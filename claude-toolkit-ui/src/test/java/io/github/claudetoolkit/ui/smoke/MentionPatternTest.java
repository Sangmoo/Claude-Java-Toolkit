package io.github.claudetoolkit.ui.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v4.2.7 — Phase 2.1: 댓글 본문에서 @username 멘션을 추출하는 정규식이
 * 프론트(`MentionInput.tsx`) / 백엔드(`ReviewCommentController.MENTION_PATTERN`)
 * 에서 일치한 동작을 하는지 회귀 검증.
 *
 * <p>이 테스트는 Spring 컨텍스트를 띄우지 않는 순수 단위 테스트이므로 매우 빠름.
 * 정규식만 복사해 독립 검증 — 프론트 테스트(브라우저 콘솔 eval) 5 케이스와
 * 동일한 케이스를 백엔드에서 재실행.
 */
class MentionPatternTest {

    /** ReviewCommentController 의 MENTION_PATTERN 과 반드시 동일하게 유지. */
    private static final Pattern MENTION_PATTERN =
            Pattern.compile("(?:^|\\s)@([A-Za-z0-9_.\\-]+)");

    private static Set<String> extract(String text) {
        Set<String> out = new LinkedHashSet<String>();
        if (text == null || text.isEmpty()) return out;
        Matcher m = MENTION_PATTERN.matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    // JDK 1.8 호환 — Set.of 사용 불가
    private static Set<String> setOf(String... items) {
        return new HashSet<String>(Arrays.asList(items));
    }

    @Test
    @DisplayName("@admin 단일 멘션 — username 추출")
    void singleMentionAtStart() {
        assertEquals(setOf("admin"), extract("@admin 리뷰 요청함"));
    }

    @Test
    @DisplayName("공백 뒤 멘션 — 정상 추출")
    void mentionAfterWhitespace() {
        assertEquals(setOf("admin"), extract("리뷰 끝 @admin 감사합니다"));
    }

    @Test
    @DisplayName("이메일 주소(a@b.com) 는 멘션으로 잡히면 안 됨")
    void emailNotAMention() {
        assertTrue(extract("이메일 a@b.com 은 멘션 아님").isEmpty(),
                "앞 문자 'a' 가 공백이 아니므로 멘션 매칭되면 안 됨");
    }

    @Test
    @DisplayName("여러 멘션 — 모두 추출, 순서 유지")
    void multipleMentions() {
        Set<String> result = extract("@a @b @c 둘 다 호출");
        assertEquals(3, result.size());
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    @Test
    @DisplayName("멘션 없음 — 빈 Set")
    void noMention() {
        assertTrue(extract("그냥 평문 댓글입니다").isEmpty());
    }

    @Test
    @DisplayName("한글 뒤 공백 + 멘션")
    void koreanContextMention() {
        assertEquals(setOf("admin"), extract("리뷰요청 @admin 부탁드립니다"));
    }

    @Test
    @DisplayName("토큰 문자: _, ., - 허용")
    void tokenCharacters() {
        assertEquals(setOf("user_1", "user.name", "user-two"),
                extract("@user_1 @user.name @user-two"));
    }

    @Test
    @DisplayName("null/빈 입력 안전")
    void nullAndEmpty() {
        assertTrue(extract(null).isEmpty());
        assertTrue(extract("").isEmpty());
    }
}
