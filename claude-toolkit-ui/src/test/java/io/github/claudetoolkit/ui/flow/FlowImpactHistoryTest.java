package io.github.claudetoolkit.ui.flow;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v4.6.1 — 테이블 영향 분석 (sync) 의 분석 이력 저장 회귀 방지.
 *
 * <p>{@code GET /api/v1/flow/impact} 는 동기 응답이지만, 이전 버전은
 * historyService.save() 호출 자체가 빠져 있어 검색·이력에서 추적할 수 없었다.
 * v4.6.1 에서 TABLE_IMPACT 타입으로 저장하도록 추가했고, 이 테스트가 회귀를 막는다.
 *
 * <p>이 엔드포인트는 LLM 호출 없이 정적 인덱서만 사용하므로 ClaudeClient 모킹 불필요.
 * test 프로파일에서 인덱서들은 비어 있는 결과를 반환하지만, 그 결과조차 review_history 에
 * 저장되어야 한다 (counts 가 모두 0 이어도 분석 자체는 수행되었음을 기록).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlowImpactHistoryTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ReviewHistoryRepository historyRepo;

    @Test
    @DisplayName("GET /api/v1/flow/impact 호출 시 review_history 에 TABLE_IMPACT 행 저장")
    @WithMockUser(username = "impact-tester", roles = {"ADMIN"})
    void impactAnalysis_savesHistory() throws Exception {
        int countBefore = historyRepo.findByTypeOrderByCreatedAtAsc("TABLE_IMPACT").size();

        mockMvc.perform(get("/api/v1/flow/impact")
                        .param("table", "T_SHOP_INVT_SIDE")
                        .param("dml", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.table").value("T_SHOP_INVT_SIDE"))
                .andExpect(jsonPath("$.data.counts").exists());

        List<ReviewHistory> rows = historyRepo.findByTypeOrderByCreatedAtAsc("TABLE_IMPACT");
        assertEquals(countBefore + 1, rows.size(),
                "테이블 영향 분석 호출 1회 → review_history 에 TABLE_IMPACT 행 정확히 1개 추가");

        ReviewHistory latest = rows.get(rows.size() - 1);
        assertTrue(latest.getInputContent().contains("T_SHOP_INVT_SIDE"),
                "input 이 테이블명을 포함해야 함");
        assertTrue(latest.getInputContent().contains("DML=ALL"),
                "input 이 DML 필터 정보를 포함해야 함");
        assertTrue(latest.getOutputContent().contains("counts"),
                "output 이 분석 결과 JSON 을 포함해야 함");
        assertEquals("impact-tester", latest.getUsername(),
                "현재 로그인 사용자명이 저장되어야 함");
    }

    @Test
    @DisplayName("DML 필터 다른 호출 — 각각 별도 행으로 저장")
    @WithMockUser(username = "impact-tester2", roles = {"ADMIN"})
    void impactAnalysis_differentDml_savesSeparately() throws Exception {
        int countBefore = historyRepo.findByTypeOrderByCreatedAtAsc("TABLE_IMPACT").size();

        mockMvc.perform(get("/api/v1/flow/impact")
                        .param("table", "T_FOO")
                        .param("dml", "INSERT"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/flow/impact")
                        .param("table", "T_FOO")
                        .param("dml", "UPDATE"))
                .andExpect(status().isOk());

        int countAfter = historyRepo.findByTypeOrderByCreatedAtAsc("TABLE_IMPACT").size();
        assertEquals(countBefore + 2, countAfter,
                "서로 다른 DML 필터 2회 호출 → review_history 에 각각 1행씩 총 2행 추가");
    }
}
