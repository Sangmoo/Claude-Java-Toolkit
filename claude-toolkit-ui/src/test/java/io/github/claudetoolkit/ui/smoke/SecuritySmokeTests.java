package io.github.claudetoolkit.ui.smoke;

import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v4.2.7 — Phase 1 에서 고친 보안/권한 경로에 대한 회귀 방지 스모크 테스트.
 *
 * <p>이 테스트는 실제 DB 상태를 조작하지 않고 <b>접근 제어 규칙만</b> 검증한다.
 * Spring Security 필터 체인을 통과하는 요청의 HTTP 상태 코드를 검사하여
 * 이후 리팩터링 중 권한 규칙이 실수로 완화되는 것을 포착한다.
 *
 * <p>커버 항목:
 * <ul>
 *   <li>VIEWER → /api/v1/admin/** 는 403 (1.7)</li>
 *   <li>ADMIN  → /api/v1/admin/** 는 통과 (1.7)</li>
 *   <li>VIEWER → POST /history/{id}/delete 는 403 (이력 삭제 차단)</li>
 *   <li>REVIEWER → POST /history/{id}/delete 는 통과 (차단 대상 아님)</li>
 *   <li>비로그인 → /api/v1/admin/** 는 401/302 (리다이렉트)</li>
 *   <li>/api/v1/auth/login 은 비로그인에서도 접근 허용 (permitAll)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecuritySmokeTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppUserRepository appUserRepository;

    // ── 1.7: /api/v1/admin/** 역할 게이팅 ────────────────────────────

    @Test
    @DisplayName("1.7 — VIEWER 는 /api/v1/admin/users 접근 불가 (403)")
    @WithMockUser(username = "viewer1", roles = {"VIEWER"})
    void adminUsers_forbidden_for_viewer() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("1.7 — REVIEWER 도 /api/v1/admin/users 접근 불가 (403)")
    @WithMockUser(username = "reviewer1", roles = {"REVIEWER"})
    void adminUsers_forbidden_for_reviewer() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("1.7 — ADMIN 은 /api/v1/admin/users 접근 허용 (200)")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void adminUsers_ok_for_admin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());
    }

    // ── 이력 삭제: VIEWER 차단 / REVIEWER·ADMIN 통과 ─────────────────

    @Test
    @DisplayName("VIEWER 는 POST /history/{id}/delete 403 반환")
    @WithMockUser(username = "viewer1", roles = {"VIEWER"})
    void historyDelete_forbidden_for_viewer() throws Exception {
        // 존재하지 않는 id 이어도 상관 없음 — 권한 체크가 먼저 적용되어 403 이 돌아와야 한다.
        mockMvc.perform(post("/history/999999/delete"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("REVIEWER 는 POST /history/{id}/delete 가 404 or 200 (권한 통과 — 대상 없음 뿐)")
    @WithMockUser(username = "reviewer1", roles = {"REVIEWER"})
    void historyDelete_allowed_for_reviewer() throws Exception {
        // 권한이 통과되면 삭제 서비스까지 내려가고 대상이 없으면 서비스 레벨에서
        // 성공 응답(success:true)을 돌려주도록 되어 있음 (Spring Data JPA deleteById 가
        // 없는 id 에 대해 예외를 던지지 않음). 핵심은 403 이 아니라는 점.
        mockMvc.perform(post("/history/999999/delete"))
                .andExpect(status().isOk());
    }

    // ── 비인증 경로 ──────────────────────────────────────────────────

    @Test
    @DisplayName("비로그인 상태에서 /api/v1/admin/users 접근은 인증 필요 (401/403/302)")
    void adminUsers_unauthenticated_blocked() throws Exception {
        // Spring Security 는 비인증 요청에 대해 401, 403, 또는 로그인 페이지로 302 중 하나
        // (설정에 따라 다름). 여기선 "2xx 가 아니다" 만 보장.
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status >= 200 && status < 300) {
                        throw new AssertionError("비로그인 상태에서 admin API 가 통과되면 안 됨: " + status);
                    }
                });
    }

    @Test
    @DisplayName("/api/v1/auth/login 엔드포인트는 비로그인에서도 접근 가능")
    void authLogin_permitAll() throws Exception {
        // POST 메서드만 구현되어 있을 수 있음 — GET 은 405 Method Not Allowed 또는 404.
        // 어느 경우든 401/403 은 아니어야 한다 (permitAll 규칙).
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("auth/login 은 permitAll 이어야 함: " + status);
                    }
                });
    }

    // ── 2FA 인터셉터 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("2fa_pending=true 인 인증 사용자는 /login/2fa 로 리다이렉트 (인터셉터)")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void twoFaPending_interceptor_redirectsToTwoFaPage() throws Exception {
        mockMvc.perform(get("/")
                .sessionAttr("2fa_pending", Boolean.TRUE))
                .andExpect(status().isFound())
                .andExpect(result -> {
                    String location = result.getResponse().getHeader("Location");
                    if (location == null || !location.contains("/login/2fa")) {
                        throw new AssertionError("2fa_pending=true 면 /login/2fa 로 리다이렉트 해야 함. Location=" + location);
                    }
                });
    }

    @Test
    @DisplayName("2fa_pending 없으면 인터셉터가 차단하지 않음")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void noTwoFaPending_interceptor_doesNotBlock() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(result -> {
                    String location = result.getResponse().getHeader("Location");
                    if (location != null && location.contains("/login/2fa")) {
                        throw new AssertionError("2fa_pending 없으면 /login/2fa 로 리다이렉트 안 해야 함");
                    }
                });
    }

    // ── ADMIN 2FA 필수화 ──────────────────────────────────────────────────

    @Test
    @DisplayName("ADMIN 은 POST /account/disable-2fa 시 오류 반환 (2FA 필수)")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void admin_cannotDisable2fa_returnsFalse() throws Exception {
        AppUser admin = new AppUser("admin1", "hash", "ADMIN");
        when(appUserRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/account/disable-2fa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("REVIEWER 는 POST /account/disable-2fa 성공")
    @WithMockUser(username = "reviewer1", roles = {"REVIEWER"})
    void reviewer_canDisable2fa_returnsTrue() throws Exception {
        AppUser reviewer = new AppUser("reviewer1", "hash", "REVIEWER");
        when(appUserRepository.findByUsername("reviewer1")).thenReturn(Optional.of(reviewer));

        mockMvc.perform(post("/account/disable-2fa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
