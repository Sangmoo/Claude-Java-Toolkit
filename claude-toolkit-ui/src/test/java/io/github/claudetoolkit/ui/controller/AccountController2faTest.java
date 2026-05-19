package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import io.github.claudetoolkit.ui.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ADMIN 2FA 필수화 — AccountController.disable2fa 단위 테스트.
 *
 * <p>커버:
 * <ul>
 *   <li>ADMIN → success=false, 오류 메시지 반환, save 호출 안 함</li>
 *   <li>REVIEWER → success=true, totpSecret=null 저장</li>
 *   <li>VIEWER → success=true, totpSecret=null 저장</li>
 *   <li>사용자 없음 → success=true (no-op, 기존 동작 유지)</li>
 * </ul>
 */
class AccountController2faTest {

    private AppUserRepository  userRepository;
    private AccountController  controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        controller     = new AccountController(
                userRepository, mock(UserService.class), mock(BCryptPasswordEncoder.class));
    }

    @Test
    @DisplayName("ADMIN은 disable-2fa 불가 — success=false, save 없음")
    void admin_cannotDisable2fa() {
        AppUser admin = new AppUser("admin1", "hash", "ADMIN");
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));

        ResponseEntity<Map<String, Object>> resp = controller.disable2fa(principal("admin1"));

        assertFalse((Boolean) resp.getBody().get("success"));
        assertNotNull(resp.getBody().get("error"), "에러 메시지가 있어야 함");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("ADMIN 에러 메시지에 '2FA' 포함")
    void admin_errorMessageMentions2fa() {
        AppUser admin = new AppUser("admin1", "hash", "ADMIN");
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));

        ResponseEntity<Map<String, Object>> resp = controller.disable2fa(principal("admin1"));

        String error = (String) resp.getBody().get("error");
        assertTrue(error.contains("2FA") || error.contains("2fa"),
                "오류 메시지에 2FA 키워드 포함: " + error);
    }

    @Test
    @DisplayName("REVIEWER는 disable-2fa 가능 — success=true, save 호출됨")
    void reviewer_canDisable2fa() {
        AppUser reviewer = new AppUser("reviewer1", "hash", "REVIEWER");
        when(userRepository.findByUsername("reviewer1")).thenReturn(Optional.of(reviewer));

        ResponseEntity<Map<String, Object>> resp = controller.disable2fa(principal("reviewer1"));

        assertTrue((Boolean) resp.getBody().get("success"));
        verify(userRepository).save(reviewer);
    }

    @Test
    @DisplayName("VIEWER는 disable-2fa 가능 — success=true")
    void viewer_canDisable2fa() {
        AppUser viewer = new AppUser("viewer1", "hash", "VIEWER");
        when(userRepository.findByUsername("viewer1")).thenReturn(Optional.of(viewer));

        ResponseEntity<Map<String, Object>> resp = controller.disable2fa(principal("viewer1"));

        assertTrue((Boolean) resp.getBody().get("success"));
        verify(userRepository).save(viewer);
    }

    @Test
    @DisplayName("사용자 없으면 success=true 반환 (no-op)")
    void userNotFound_returnsSuccessNoOp() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> resp = controller.disable2fa(principal("ghost"));

        assertTrue((Boolean) resp.getBody().get("success"));
        verify(userRepository, never()).save(any());
    }

    private Principal principal(String username) {
        return () -> username;
    }
}
