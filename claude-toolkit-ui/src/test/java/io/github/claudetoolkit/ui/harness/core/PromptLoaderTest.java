package io.github.claudetoolkit.ui.harness.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase A — PromptLoader 단위 테스트.
 *
 * <p>실제 classpath 파일은 Phase B/C/D에서 추가됩니다 — 여기서는
 * 누락 시 동작과 경로 인젝션 방어만 검증합니다.
 */
class PromptLoaderTest {

    private PromptLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PromptLoader();
    }

    @Test
    @DisplayName("존재하지 않는 프롬프트 → load는 IllegalStateException")
    void loadMissing_throws() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> loader.load("nonexistent-harness", "analyst"));
        assertTrue(ex.getMessage().contains("nonexistent-harness"), ex.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 프롬프트 → loadOrDefault는 fallback 반환")
    void loadMissing_returnsFallback() {
        String result = loader.loadOrDefault("nonexistent-harness", "analyst", "FALLBACK");
        assertEquals("FALLBACK", result);
    }

    @Test
    @DisplayName("경로 인젝션 — '../', '/' 차단")
    void pathInjection_blocked() {
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadOrDefault("../etc", "x", "fb"));
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadOrDefault("ok", "../passwd", "fb"));
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadOrDefault("foo/bar", "x", "fb"));
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadOrDefault("ok", "x.md", "fb"));  // 확장자도 차단
    }

    @Test
    @DisplayName("빈 식별자 → IllegalArgumentException")
    void emptyName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadOrDefault("", "x", "fb"));
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadOrDefault("x", "", "fb"));
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadOrDefault(null, "x", "fb"));
    }

    @Test
    @DisplayName("허용 문자 — 영숫자/하이픈/언더스코어 통과")
    void allowedChars_pass() {
        // 파일이 없어도 fallback이 반환되면 식별자 검증을 통과한 것
        assertEquals("fb", loader.loadOrDefault("sp-migration", "analyst",       "fb"));
        assertEquals("fb", loader.loadOrDefault("log_rca",      "verifier",      "fb"));
        assertEquals("fb", loader.loadOrDefault("v2",           "stage1",        "fb"));
        assertEquals("fb", loader.loadOrDefault("CamelCase",    "Sub-Stage_2",   "fb"));
    }

    @Test
    @DisplayName("clearCache — 호출 가능 + cacheSize 0")
    void clearCache_works() {
        loader.clearCache();
        assertEquals(0, loader.cacheSize());
    }
}
