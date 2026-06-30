package io.github.claudetoolkit.ui.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

    @TempDir
    Path tempDir;

    private ApiKeyFilter filter;
    private MockedStatic<SecuritySettings> settingsMock;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyFilter();
    }

    @AfterEach
    void tearDown() {
        if (settingsMock != null) settingsMock.close();
    }

    private SecuritySettings enabledSettings(String bcryptHash) {
        SecuritySettings s = new SecuritySettings();
        s.setApiKeyEnabled(true);
        s.setApiKeyHash(bcryptHash);
        return s;
    }

    @Test
    @DisplayName("513자 X-Api-Key → 401 (DoS 차단 — BCrypt 호출 없음)")
    void overLengthKeyReturns401() throws Exception {
        String longKey = "A".repeat(513);

        SecuritySettings settings = enabledSettings(
                "$2a$10$dummyHashThatWillNeverBeReachedBecauseOfLengthCheck");
        settingsMock = mockStatic(SecuritySettings.class);
        settingsMock.when(SecuritySettings::load).thenReturn(settings);

        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        req.addHeader("X-Api-Key", longKey);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus(), "513자 키는 BCrypt 전에 차단");
        assertNull(chain.getRequest(),  "FilterChain 이 호출되지 않아야 함");
    }

    @Test
    @DisplayName("512자 X-Api-Key → BCrypt 검증 단계까지 진행 (길이 차단 아님)")
    void exactLimitKeyPassesLengthCheck() throws Exception {
        String exactKey = "B".repeat(512);

        // 올바른 bcrypt 해시가 아니므로 401 이 나오지만 길이 차단이 아닌 hash mismatch 로 차단
        SecuritySettings settings = enabledSettings(
                "$2a$10$invalidhashbutlengthcheckshoulpassXXXXXXXXXXXXXXXXXX");
        settingsMock = mockStatic(SecuritySettings.class);
        settingsMock.when(SecuritySettings::load).thenReturn(settings);

        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        req.addHeader("X-Api-Key", exactKey);

        filter.doFilter(req, res, chain);

        // 401 이지만 "올바르지 않습니다" (길이 차단 메시지) 가 아니라 BCrypt 예외 또는 불일치 메시지여야 함
        assertEquals(401, res.getStatus());
        String body = res.getContentAsString();
        assertFalse(body.contains("헤더가 올바르지 않습니다"),
                "512자는 길이 차단이 아닌 BCrypt 검증 단계에서 처리");
    }

    @Test
    @DisplayName("API 키 비활성화 → 모든 요청 통과")
    void apiKeyDisabled_passesThrough() throws Exception {
        SecuritySettings settings = new SecuritySettings();
        settings.setApiKeyEnabled(false);

        settingsMock = mockStatic(SecuritySettings.class);
        settingsMock.when(SecuritySettings::load).thenReturn(settings);

        MockHttpServletRequest  req   = new MockHttpServletRequest();
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest(), "FilterChain 이 호출되어야 함");
    }
}
