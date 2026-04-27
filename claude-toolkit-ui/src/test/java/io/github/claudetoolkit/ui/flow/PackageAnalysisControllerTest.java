package io.github.claudetoolkit.ui.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v4.5 — Package Analysis REST API 회귀 방지 스모크 테스트.
 *
 * <p>핵심 검증:
 * <ul>
 *   <li>VIEWER 가 POST /api/v1/package/refresh 호출 → 403 (Phase 1 보안 픽스)</li>
 *   <li>ADMIN 이 POST /api/v1/package/refresh 호출 → 200 통과</li>
 *   <li>GET /api/v1/package/overview 페이지네이션 파라미터 없으면 기존 shape</li>
 *   <li>GET /api/v1/package/overview ?page=&size= 주면 paged shape</li>
 *   <li>POST /api/v1/package/settings 가 prefix 200자 초과를 거절</li>
 *   <li>POST /api/v1/package/settings 가 level 11 같은 범위 외 값을 거절</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PackageAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Phase 1 보안 픽스 회귀 방지 ──────────────────────────────────

    @Test
    @DisplayName("VIEWER 는 POST /api/v1/package/refresh 호출 시 403")
    @WithMockUser(username = "viewer1", roles = {"VIEWER"})
    void refresh_forbidden_for_viewer() throws Exception {
        mockMvc.perform(post("/api/v1/package/refresh").with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("REVIEWER 도 POST /api/v1/package/refresh 호출 시 403")
    @WithMockUser(username = "reviewer1", roles = {"REVIEWER"})
    void refresh_forbidden_for_reviewer() throws Exception {
        mockMvc.perform(post("/api/v1/package/refresh").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 은 POST /api/v1/package/refresh 호출 시 200 통과")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void refresh_ok_for_admin() throws Exception {
        // 인덱서 빌더는 scanPath 가 비어 있으면 즉시 비어있는 인덱스로 끝나도록 되어 있으므로
        // application-test.yml 의 toolkit.project.scan-path: "" 환경에서 200 + success:true.
        mockMvc.perform(post("/api/v1/package/refresh").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── /overview 페이지네이션 ──────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/package/overview (페이지네이션 없이) → packages 키 노출, page 메타 없음")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void overview_default_returnsLegacyShape() throws Exception {
        mockMvc.perform(get("/api/v1/package/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.packages").exists())
                .andExpect(jsonPath("$.data.pageNumber").doesNotExist())
                .andExpect(jsonPath("$.data.totalPages").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/package/overview?page=0&size=5 → paged shape (pageNumber/totalPages 포함)")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void overview_paged_returnsPagedShape() throws Exception {
        mockMvc.perform(get("/api/v1/package/overview?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.packages").exists())
                .andExpect(jsonPath("$.data.pageNumber").value(0))
                .andExpect(jsonPath("$.data.pageSize").value(5))
                .andExpect(jsonPath("$.data.totalPages").exists())
                .andExpect(jsonPath("$.data.total").exists());
    }

    // ── saveSettings 검증 회귀 방지 ──────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/package/settings level=11 거절")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void saveSettings_rejectsLevelOutOfRange() throws Exception {
        mockMvc.perform(post("/api/v1/package/settings")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"level\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("2~10")));
    }

    @Test
    @DisplayName("POST /api/v1/package/settings prefix 200자 초과 거절")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void saveSettings_rejectsTooLongPrefix() throws Exception {
        StringBuilder longPrefix = new StringBuilder();
        for (int i = 0; i < 250; i++) longPrefix.append('a');
        String body = "{\"prefix\":\"" + longPrefix + "\"}";
        mockMvc.perform(post("/api/v1/package/settings")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("200")));
    }
}
