package io.github.claudetoolkit.ui.harness.logrca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v4.6.1 — 오류 로그 RCA 하네스 SSE 스트리밍의 분석 이력 저장 회귀 방지.
 *
 * <p>{@link io.github.claudetoolkit.ui.harness.spmigration.HarnessSpMigrationStreamingTest} 와
 * 동일한 패턴으로 LOG_RCA_HARNESS 타입 행이 review_history 에 저장되는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarnessLogRcaStreamingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ReviewHistoryRepository historyRepo;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ClaudeClient claudeClient;

    @BeforeEach
    void stubClaudeClient() {
        doAnswer(inv -> {
            Consumer<String> sink = inv.getArgument(3);
            sink.accept("[fake stage output]");
            return null;
        }).when(claudeClient).chatStream(anyString(), anyString(), anyInt(), any());

        doAnswer(inv -> {
            Consumer<String> sink = inv.getArgument(4);
            sink.accept("[fake builder output (continuation)]");
            return null;
        }).when(claudeClient).chatStreamWithContinuation(
                anyString(), anyString(), anyInt(), anyInt(), any());

        when(claudeClient.getLastInputTokens()).thenReturn(80);
        when(claudeClient.getLastOutputTokens()).thenReturn(180);
        when(claudeClient.getEffectiveModel()).thenReturn("test-model");
    }

    @Test
    @DisplayName("Log RCA SSE 스트리밍 완료 시 review_history 에 LOG_RCA_HARNESS 행 저장 (general 모드)")
    @WithMockUser(username = "rca-tester", roles = {"ADMIN"})
    void streamingCompletion_savesHistory_generalMode() throws Exception {
        int countBefore = historyRepo.findByTypeOrderByCreatedAtAsc("LOG_RCA_HARNESS").size();

        MvcResult initResult = mockMvc.perform(post("/api/v1/log-rca/stream-init")
                        .param("error_log",
                                "java.lang.NullPointerException: Cannot invoke ...\n  at com.foo.Bar.baz(Bar.java:42)")
                        .param("timeline", "14:23 PR #1234 배포 → 14:35 첫 NPE")
                        .param("related_code", "")
                        .param("env", "Spring Boot 2.7.18 / Oracle 19c")
                        .param("analysis_mode", "general"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.streamId").exists())
                .andReturn();

        String streamId = extractStreamId(initResult.getResponse().getContentAsString());
        assertNotNull(streamId);

        mockMvc.perform(get("/api/v1/log-rca/stream/" + streamId))
                .andExpect(request().asyncStarted());

        boolean appeared = waitForHistoryAppearance("LOG_RCA_HARNESS", countBefore, 15_000);
        assertTrue(appeared,
                "SSE 스트리밍 완료 후 review_history 에 LOG_RCA_HARNESS 행이 추가되어야 함");

        List<ReviewHistory> rows = historyRepo.findByTypeOrderByCreatedAtAsc("LOG_RCA_HARNESS");
        ReviewHistory latest = rows.get(rows.size() - 1);
        assertTrue(latest.getInputContent().contains("NullPointerException"),
                "input 이 오류 로그 본문을 포함해야 함");
        assertTrue("rca-tester".equals(latest.getUsername()),
                "백그라운드 스레드에서 username 이 capture 되어 저장되어야 함");
    }

    @Test
    @DisplayName("Log RCA security 모드 — 동일하게 LOG_RCA_HARNESS 행 저장")
    @WithMockUser(username = "security-rca-tester", roles = {"ADMIN"})
    void streamingCompletion_savesHistory_securityMode() throws Exception {
        int countBefore = historyRepo.findByTypeOrderByCreatedAtAsc("LOG_RCA_HARNESS").size();

        MvcResult initResult = mockMvc.perform(post("/api/v1/log-rca/stream-init")
                        .param("error_log", "ORA-00942 + suspicious '; DROP TABLE T_USER; -- input")
                        .param("timeline", "")
                        .param("related_code", "")
                        .param("env", "")
                        .param("analysis_mode", "security"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String streamId = extractStreamId(initResult.getResponse().getContentAsString());
        assertNotNull(streamId);

        mockMvc.perform(get("/api/v1/log-rca/stream/" + streamId))
                .andExpect(request().asyncStarted());

        boolean appeared = waitForHistoryAppearance("LOG_RCA_HARNESS", countBefore, 15_000);
        assertTrue(appeared, "security 모드도 동일하게 review_history 에 행 저장되어야 함");
    }

    private String extractStreamId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.has("streamId") ? node.get("streamId").asText() : null;
    }

    private boolean waitForHistoryAppearance(String type, int countBefore, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int now = historyRepo.findByTypeOrderByCreatedAtAsc(type).size();
            if (now > countBefore) return true;
            Thread.sleep(100);
        }
        return false;
    }
}
