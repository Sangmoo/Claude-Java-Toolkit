package io.github.claudetoolkit.ui.livedb;

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
 * v4.7.x — #G3 Live DB Phase 5: 전체 컨트롤러 + 서비스 관통 e2e 통합 테스트.
 *
 * <p>실제 Oracle/Postgres 없이도 다음 흐름이 검증되어야 함:
 * <ul>
 *   <li>{@code POST /api/v1/livedb/simulate-index} 가 잘못된 입력 거부 (read-only 아님 / 5개 초과 등)</li>
 *   <li>{@code GET  /api/v1/admin/livedb/stats} ADMIN 만 접근 가능</li>
 *   <li>{@code GET  /db-profiles/active-live} 비밀번호 노출 없음</li>
 *   <li>{@code POST /api/v1/admin/livedb/breaker/{id}/close} ADMIN 만 가능</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveDbE2ETest {

    @Autowired
    private MockMvc mockMvc;

    // ── 인덱스 시뮬레이션 입력 검증 ──────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/livedb/simulate-index — userSql 누락 시 400")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void simulate_missingUserSql() throws Exception {
        mockMvc.perform(post("/api/v1/livedb/simulate-index")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"indexDefs\":[\"CREATE INDEX X ON T (A)\"], \"dbProfileId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/v1/livedb/simulate-index — indexDefs 빈 리스트 거부 400")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void simulate_emptyIndexDefs() throws Exception {
        mockMvc.perform(post("/api/v1/livedb/simulate-index")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"userSql\":\"SELECT 1\",\"indexDefs\":[],\"dbProfileId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/livedb/simulate-index — dbProfileId 누락 시 400")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void simulate_missingProfileId() throws Exception {
        mockMvc.perform(post("/api/v1/livedb/simulate-index")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"userSql\":\"SELECT 1\",\"indexDefs\":[\"CREATE INDEX X ON T (A)\"]}"))
                .andExpect(status().isBadRequest());
    }

    // ── 통계 / 회로 — ADMIN gate ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/admin/livedb/stats — VIEWER 는 403")
    @WithMockUser(username = "viewer1", roles = {"VIEWER"})
    void stats_forbiddenForViewer() throws Exception {
        mockMvc.perform(get("/api/v1/admin/livedb/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/livedb/stats — ADMIN 은 200 + config 필드 포함")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void stats_okForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/livedb/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.config").exists())
                .andExpect(jsonPath("$.config.enabled").exists())
                .andExpect(jsonPath("$.config.maxCallsPerMinute").exists())
                .andExpect(jsonPath("$.byProfile").exists())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    @DisplayName("POST /api/v1/admin/livedb/stats/reset — ADMIN 만")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void resetStats_okForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/livedb/stats/reset").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/admin/livedb/stats/reset — VIEWER 거부")
    @WithMockUser(username = "viewer1", roles = {"VIEWER"})
    void resetStats_forbiddenForViewer() throws Exception {
        mockMvc.perform(post("/api/v1/admin/livedb/stats/reset").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── 활성 프로필 목록 (ADMIN/VIEWER 모두 read 가능 — 비밀번호 노출 X) ──────

    @Test
    @DisplayName("GET /db-profiles/active-live — 응답에 password 키 없음 (보안)")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void activeLiveProfiles_noPasswordExposed() throws Exception {
        mockMvc.perform(get("/db-profiles/active-live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.password)]").doesNotExist())
                .andExpect(jsonPath("$[?(@.username)]").doesNotExist());
        // username 도 노출 X — 메타만 (id, name, description, maskedUrl)
    }
}
