package io.github.claudetoolkit.ui.harness.spmigration;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.harness.HarnessCacheService;
import io.github.claudetoolkit.ui.harness.core.HarnessOrchestrator;
import io.github.claudetoolkit.ui.harness.core.HarnessRunResult;
import io.github.claudetoolkit.ui.harness.core.PromptLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase B — HarnessSpMigrationService 단위 테스트.
 *
 * 커버:
 * <ul>
 *   <li>4-stage 토큰 예산 (4096 / 8192+3cont / 4096 / 4096)</li>
 *   <li>이전 stage 출력이 다음 stage user 메시지에 주입됨</li>
 *   <li>입력 검증: sp_source 또는 sp_name 중 하나는 필수</li>
 *   <li>sp_source 비어 있고 sp_name 주어지면 HarnessCacheService로 ALL_SOURCE 조회</li>
 *   <li>auto-fetch 실패 시 명확한 에러</li>
 *   <li>선택 입력(table_ddl 등)이 비면 user 메시지에서 생략</li>
 * </ul>
 */
class HarnessSpMigrationServiceTest {

    private ClaudeClient        claudeClient;
    private HarnessOrchestrator orchestrator;
    private PromptLoader        prompts;
    private ToolkitSettings     settings;
    private HarnessCacheService cacheService;
    private HarnessSpMigrationService service;

    @BeforeEach
    void setUp() {
        claudeClient = mock(ClaudeClient.class);
        prompts      = mock(PromptLoader.class);
        settings     = mock(ToolkitSettings.class);
        cacheService = mock(HarnessCacheService.class);
        orchestrator = new HarnessOrchestrator(claudeClient);

        when(prompts.loadOrDefault(eq("sp-migration"), anyString(), anyString()))
                .thenAnswer(inv -> "SYS[" + inv.getArgument(1) + "]");
        when(settings.getProjectContext()).thenReturn("");

        service = new HarnessSpMigrationService(orchestrator, prompts, settings, cacheService);
    }

    @Test
    @DisplayName("토큰 예산 — Analyst 4096 / Builder 8192+3cont / Reviewer 4096 / Verifier 4096")
    void tokenBudgets() {
        stubAllStages();
        service.analyze("SP_BODY", null, null, null, null, null, null);

        verify(claudeClient).chat(eq("SYS[analyst]"),  anyString(), eq(4096));
        verify(claudeClient).chatWithContinuation(eq("SYS[builder]"), anyString(), eq(8192), eq(3));
        verify(claudeClient).chat(eq("SYS[reviewer]"), anyString(), eq(4096));
        verify(claudeClient).chat(eq("SYS[verifier]"), anyString(), eq(4096));
    }

    @Test
    @DisplayName("4-stage 출력 chaining — Builder는 Analyst 출력, Reviewer는 두 단계 출력, Verifier는 Builder+Reviewer 포함")
    void fourStageChaining() {
        when(claudeClient.chat(eq("SYS[analyst]"),  anyString(), eq(4096))).thenReturn("ANALYST_OUT");
        when(claudeClient.chatWithContinuation(eq("SYS[builder]"), anyString(), eq(8192), eq(3))).thenReturn("BUILDER_OUT");
        when(claudeClient.chat(eq("SYS[reviewer]"), anyString(), eq(4096))).thenReturn("REVIEWER_OUT");
        when(claudeClient.chat(eq("SYS[verifier]"), anyString(), eq(4096))).thenReturn("VERIFIER_OUT");

        HarnessRunResult run = service.analyze("CREATE PROCEDURE...", null, null,
                null, null, null, null);

        assertTrue(run.isSuccess(), run.getError());
        assertEquals(4, run.getStages().size());

        ArgumentCaptor<String> builderUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chatWithContinuation(eq("SYS[builder]"), builderUser.capture(), eq(8192), eq(3));
        assertTrue(builderUser.getValue().contains("ANALYST_OUT"), "Builder에 Analyst 출력 누락");

        ArgumentCaptor<String> reviewerUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(eq("SYS[reviewer]"), reviewerUser.capture(), eq(4096));
        assertTrue(reviewerUser.getValue().contains("ANALYST_OUT"));
        assertTrue(reviewerUser.getValue().contains("BUILDER_OUT"));

        ArgumentCaptor<String> verifierUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(eq("SYS[verifier]"), verifierUser.capture(), eq(4096));
        assertTrue(verifierUser.getValue().contains("BUILDER_OUT"));
        assertTrue(verifierUser.getValue().contains("REVIEWER_OUT"));
    }

    @Test
    @DisplayName("sp_source / sp_name 둘 다 비어있으면 IllegalArgumentException")
    void neitherSourceNorName_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.analyze("", "", null, null, null, null, null));
        assertTrue(ex.getMessage().contains("필수"), ex.getMessage());
    }

    @Test
    @DisplayName("sp_source 비어있고 sp_name 주어지면 HarnessCacheService로 ALL_SOURCE 조회")
    void emptySource_fetchesViaCacheService() {
        when(cacheService.getDbObjectSource(eq("SP_WMS_DELV"), eq("PROCEDURE")))
                .thenReturn("CREATE PROCEDURE SP_WMS_DELV ...");
        stubAllStages();

        service.analyze("", "SP_WMS_DELV", "PROCEDURE", null, null, null, null);

        verify(cacheService).getDbObjectSource("SP_WMS_DELV", "PROCEDURE");

        // Analyst의 user 메시지에 fetched 본문이 포함됐는지 확인
        ArgumentCaptor<String> analystUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(eq("SYS[analyst]"), analystUser.capture(), eq(4096));
        assertTrue(analystUser.getValue().contains("SP_WMS_DELV"), "fetched SP body in user message");
    }

    @Test
    @DisplayName("sp_type이 비어 있으면 PROCEDURE로 기본값 적용")
    void emptyType_defaultsToProcedure() {
        when(cacheService.getDbObjectSource(eq("MY_SP"), eq("PROCEDURE")))
                .thenReturn("source");
        stubAllStages();

        service.analyze("", "MY_SP", "", null, null, null, null);

        verify(cacheService).getDbObjectSource("MY_SP", "PROCEDURE");
    }

    @Test
    @DisplayName("sp_type 소문자 입력 → 대문자로 정규화")
    void lowercaseType_normalizedToUpper() {
        when(cacheService.getDbObjectSource(eq("MY_FN"), eq("FUNCTION")))
                .thenReturn("source");
        stubAllStages();

        service.analyze("", "MY_FN", "function", null, null, null, null);

        verify(cacheService).getDbObjectSource("MY_FN", "FUNCTION");
    }

    @Test
    @DisplayName("auto-fetch 실패 (DB 미연결) → 명확한 에러 메시지")
    void fetchFailure_throwsWithGuidance() {
        when(cacheService.getDbObjectSource(anyString(), anyString())).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.analyze("", "MISSING_SP", "PROCEDURE", null, null, null, null));
        assertTrue(ex.getMessage().contains("MISSING_SP"), ex.getMessage());
        assertTrue(ex.getMessage().contains("DB 연결") || ex.getMessage().contains("sp_source"),
                "사용자 안내 메시지: " + ex.getMessage());
    }

    @Test
    @DisplayName("선택 입력이 비면 Analyst user 메시지의 해당 섹션 생략")
    void emptyOptionalInputs_omittedFromUserMessage() {
        stubAllStages();

        service.analyze("only sp body", null, null, "", "", "", "");

        ArgumentCaptor<String> analystUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(eq("SYS[analyst]"), analystUser.capture(), eq(4096));
        String u = analystUser.getValue();
        assertTrue(u.contains("only sp body"));
        assertFalse(u.contains("관련 테이블 DDL"),  "빈 table_ddl 섹션 출력됨: " + u);
        assertFalse(u.contains("관련 인덱스 DDL"),  "빈 index_ddl 섹션 출력됨");
        assertFalse(u.contains("호출 예시"),       "빈 call_example 섹션 출력됨");
        assertFalse(u.contains("비즈니스 컨텍스트"), "빈 business_context 섹션 출력됨");
    }

    @Test
    @DisplayName("sp_source가 직접 주어지면 cacheService 호출 안 함")
    void directSource_doesNotCallCache() {
        stubAllStages();

        service.analyze("CREATE PROC ...", null, null, null, null, null, null);

        verify(cacheService, never()).getDbObjectSource(anyString(), anyString());
    }

    private void stubAllStages() {
        when(claudeClient.chat(anyString(), anyString(), anyInt())).thenReturn("ok");
        when(claudeClient.chatWithContinuation(anyString(), anyString(), anyInt(), anyInt())).thenReturn("ok");
    }
}
