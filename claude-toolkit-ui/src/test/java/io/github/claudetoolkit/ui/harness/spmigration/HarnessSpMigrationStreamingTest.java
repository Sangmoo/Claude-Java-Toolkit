package io.github.claudetoolkit.ui.harness.spmigration;

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
 * v4.6.1 — SSE 스트리밍 경로의 분석 이력 저장 회귀 방지.
 *
 * <p>이전 버전은 스트리밍 완료 시 {@code review_history} 테이블에 결과를 저장하지 않아
 * 사용자가 화면에서 본 분석 결과가 검색·이력에 잡히지 않던 버그가 있었다. v4.6.1
 * 패치에서 4개 하네스 (SP 마이그레이션 / SQL 최적화 / Log RCA / Impact) 모두
 * 저장하도록 통일했고, 이 테스트는 그 회귀를 막는다.
 *
 * <p>검증 시나리오 (SP→Java 마이그레이션 하네스):
 * <ol>
 *   <li>{@code POST /api/v1/sp-migration/stream-init} → success + streamId 반환</li>
 *   <li>{@code GET  /api/v1/sp-migration/stream/{id}} → SSE 비동기 시작</li>
 *   <li>백그라운드 스레드의 {@code analyzeStream} 완료 후
 *       {@code review_history} 에 type="SP_MIGRATION_HARNESS" 행이 추가되어야 함</li>
 * </ol>
 *
 * <p>{@link ClaudeClient} 는 {@link MockBean} 으로 교체하여 실제 API 호출 대신
 * Consumer&lt;String&gt; sink 에 가짜 chunk 를 즉시 흘려준다. 4-stage 파이프라인이
 * 정상 종료되어 컨트롤러의 {@code historyService.save(...)} 가 호출되는 흐름을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarnessSpMigrationStreamingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ReviewHistoryRepository historyRepo;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ClaudeClient claudeClient;

    @BeforeEach
    void stubClaudeClient() {
        // chatStream(system, user, maxTokens, sink) — Consumer 에 가짜 chunk 즉시 emit
        doAnswer(inv -> {
            Consumer<String> sink = inv.getArgument(3);
            sink.accept("[fake stage output]");
            return null;
        }).when(claudeClient).chatStream(anyString(), anyString(), anyInt(), any());

        // chatStreamWithContinuation(system, user, maxTokens, contCount, sink)
        doAnswer(inv -> {
            Consumer<String> sink = inv.getArgument(4);
            sink.accept("[fake builder output (continuation)]");
            return null;
        }).when(claudeClient).chatStreamWithContinuation(
                anyString(), anyString(), anyInt(), anyInt(), any());

        // 토큰/모델 메타 — ReviewHistoryService 가 metric 기록할 때 호출
        when(claudeClient.getLastInputTokens()).thenReturn(100L);
        when(claudeClient.getLastOutputTokens()).thenReturn(200L);
        when(claudeClient.getEffectiveModel()).thenReturn("test-model");
    }

    @Test
    @DisplayName("SP 마이그레이션 SSE 스트리밍 완료 시 review_history 에 SP_MIGRATION_HARNESS 행 저장")
    @WithMockUser(username = "test-user", roles = {"ADMIN"})
    void streamingCompletion_savesHistory() throws Exception {
        int countBefore = historyRepo.findByTypeOrderByCreatedAtAsc("SP_MIGRATION_HARNESS").size();

        // 1) POST /stream-init → streamId 추출
        MvcResult initResult = mockMvc.perform(post("/api/v1/sp-migration/stream-init")
                        .param("sp_source", "CREATE OR REPLACE PROCEDURE TEST_SP IS BEGIN NULL; END;")
                        .param("sp_name", "")
                        .param("sp_type", "PROCEDURE")
                        .param("table_ddl", "")
                        .param("index_ddl", "")
                        .param("call_example", "")
                        .param("business_context", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.streamId").exists())
                .andReturn();

        String streamId = extractStreamId(initResult.getResponse().getContentAsString());
        assertNotNull(streamId, "streamId 가 응답에 있어야 함");

        // 2) GET /stream/{id} → SSE 비동기 시작 (실제 청크 본문은 검증하지 않음)
        mockMvc.perform(get("/api/v1/sp-migration/stream/" + streamId))
                .andExpect(request().asyncStarted());

        // 3) 백그라운드 스레드 완료 대기 — historyRepo 에 행이 추가될 때까지 폴링
        boolean appeared = waitForHistoryAppearance("SP_MIGRATION_HARNESS", countBefore, 15_000);
        assertTrue(appeared,
                "SSE 스트리밍 완료 후 review_history 에 SP_MIGRATION_HARNESS 행이 추가되어야 함");

        // 추가 검증 — 저장된 행의 사용자명·input·output 이 정상
        List<ReviewHistory> rows = historyRepo.findByTypeOrderByCreatedAtAsc("SP_MIGRATION_HARNESS");
        ReviewHistory latest = rows.get(rows.size() - 1);
        assertTrue(latest.getInputContent().contains("TEST_SP"),
                "input 이 SP 본문을 포함해야 함");
        assertTrue(latest.getOutputContent().contains("[fake"),
                "output 이 가짜 stage 출력을 포함해야 함");
        // 백그라운드 스레드에서도 username 이 capture 되어 저장되었는지 확인
        assertTrue("test-user".equals(latest.getUsername()),
                "백그라운드 스레드에서 username 이 capture 되어 저장되어야 함");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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
