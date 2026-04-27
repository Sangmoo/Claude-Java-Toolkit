package io.github.claudetoolkit.ui.harness.sqloptimization;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
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
 * Phase C — HarnessSqlOptimizationService 단위 테스트.
 */
class HarnessSqlOptimizationServiceTest {

    private ClaudeClient        claudeClient;
    private HarnessOrchestrator orchestrator;
    private PromptLoader        prompts;
    private ToolkitSettings     settings;
    private HarnessSqlOptimizationService service;

    @BeforeEach
    void setUp() {
        claudeClient = mock(ClaudeClient.class);
        prompts      = mock(PromptLoader.class);
        settings     = mock(ToolkitSettings.class);
        orchestrator = new HarnessOrchestrator(claudeClient);

        when(prompts.loadOrDefault(eq("sql-optimization"), anyString(), anyString()))
                .thenAnswer(inv -> "SYS[" + inv.getArgument(1) + "]");
        when(settings.getProjectContext()).thenReturn("");

        service = new HarnessSqlOptimizationService(orchestrator, prompts, settings);
    }

    @Test
    @DisplayName("토큰 예산 — Analyst 4096 / Builder 8192+3cont / Reviewer 4096 / Verifier 4096")
    void tokenBudgets() {
        stubAllStages();
        service.analyze("SELECT * FROM t", null, null, null, null, null);

        verify(claudeClient).chat(eq("SYS[analyst]"),  anyString(), eq(4096));
        verify(claudeClient).chatWithContinuation(eq("SYS[builder]"), anyString(), eq(8192), eq(3));
        verify(claudeClient).chat(eq("SYS[reviewer]"), anyString(), eq(4096));
        verify(claudeClient).chat(eq("SYS[verifier]"), anyString(), eq(4096));
    }

    @Test
    @DisplayName("4-stage 출력 chaining — Verifier는 Builder+Reviewer를 받음")
    void fourStageChaining() {
        when(claudeClient.chat(eq("SYS[analyst]"),  anyString(), eq(4096))).thenReturn("ANALYST_OUT");
        when(claudeClient.chatWithContinuation(eq("SYS[builder]"), anyString(), eq(8192), eq(3))).thenReturn("BUILDER_OUT");
        when(claudeClient.chat(eq("SYS[reviewer]"), anyString(), eq(4096))).thenReturn("REVIEWER_OUT");
        when(claudeClient.chat(eq("SYS[verifier]"), anyString(), eq(4096))).thenReturn("VERIFIER_OUT");

        HarnessRunResult run = service.analyze("SELECT 1", null, null, null, null, null);

        assertTrue(run.isSuccess(), run.getError());

        ArgumentCaptor<String> verifierUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(eq("SYS[verifier]"), verifierUser.capture(), eq(4096));
        assertTrue(verifierUser.getValue().contains("BUILDER_OUT"));
        assertTrue(verifierUser.getValue().contains("REVIEWER_OUT"));
        assertFalse(verifierUser.getValue().contains("ANALYST_OUT"),
                "Verifier에 Analyst 직접 주입 안 함 — Reviewer 거쳐서만");
    }

    @Test
    @DisplayName("query 비어있으면 IllegalArgumentException")
    void emptyQuery_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.analyze("",   null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.analyze(null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.analyze("   ", null, null, null, null, null));
    }

    @Test
    @DisplayName("Analyst user 메시지에 6개 입력이 모두 포함 (값이 비지 않은 것만)")
    void analystUserIncludesNonEmptyInputs() {
        stubAllStages();

        service.analyze("Q_X", "PLAN_X", "STAT_X", "IDX_X", "VOL_X", "CONST_X");

        ArgumentCaptor<String> analystUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(eq("SYS[analyst]"), analystUser.capture(), eq(4096));
        String u = analystUser.getValue();
        assertTrue(u.contains("Q_X"));
        assertTrue(u.contains("PLAN_X"));
        assertTrue(u.contains("STAT_X"));
        assertTrue(u.contains("IDX_X"));
        assertTrue(u.contains("VOL_X"));
        assertTrue(u.contains("CONST_X"));
    }

    @Test
    @DisplayName("선택 입력이 비면 해당 섹션 출력 생략")
    void emptyOptionalInputs_omittedFromUserMessage() {
        stubAllStages();

        service.analyze("only query", "", "", "", "", "");

        ArgumentCaptor<String> analystUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(eq("SYS[analyst]"), analystUser.capture(), eq(4096));
        String u = analystUser.getValue();
        assertTrue(u.contains("only query"));
        assertFalse(u.contains("실행계획"),       "빈 execution_plan 섹션 출력됨");
        assertFalse(u.contains("테이블 통계"),    "빈 table_stats 섹션 출력됨");
        assertFalse(u.contains("기존 인덱스"),    "빈 existing_indexes 섹션 출력됨");
        assertFalse(u.contains("데이터 볼륨"),    "빈 data_volume 섹션 출력됨");
        assertFalse(u.contains("변경 불가 제약"), "빈 constraints 섹션 출력됨");
    }

    @Test
    @DisplayName("Builder user 메시지에 'constraints'가 포함 — 변경 불가 제약을 반영하도록")
    void builderUserPropagatesConstraints() {
        stubAllStages();

        service.analyze("SELECT 1", null, null, null, null, "T_INV는 절대 건드리지 마세요");

        ArgumentCaptor<String> builderUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chatWithContinuation(eq("SYS[builder]"), builderUser.capture(), eq(8192), eq(3));
        assertTrue(builderUser.getValue().contains("T_INV"),
                "Builder가 constraints를 받아야 함: " + builderUser.getValue());
    }

    private void stubAllStages() {
        when(claudeClient.chat(anyString(), anyString(), anyInt())).thenReturn("ok");
        when(claudeClient.chatWithContinuation(anyString(), anyString(), anyInt(), anyInt())).thenReturn("ok");
    }
}
