package io.github.claudetoolkit.ui.harness.logrca;

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
 * Phase D — HarnessLogRcaService 단위 테스트.
 *
 * <p>실제 4-stage 파이프라인 동작은 HarnessOrchestratorTest에서 검증되므로,
 * 여기서는 LogRca 특유의 동작만 본다:
 * <ul>
 *   <li>4-stage가 정확히 순서대로 호출되는가</li>
 *   <li>각 stage 토큰 예산이 명세대로 적용되는가 (3072/8192+3cont/4096/4096)</li>
 *   <li>이전 stage 출력이 다음 stage 입력에 주입되는가</li>
 *   <li>입력 검증 (errorLog 필수)</li>
 *   <li>analysis_mode가 templateHint로 전달되는가</li>
 * </ul>
 */
class HarnessLogRcaServiceTest {

    private ClaudeClient        claudeClient;
    private HarnessOrchestrator orchestrator;
    private PromptLoader        prompts;
    private ToolkitSettings     settings;
    private HarnessLogRcaService service;

    @BeforeEach
    void setUp() {
        claudeClient = mock(ClaudeClient.class);
        prompts      = mock(PromptLoader.class);
        settings     = mock(ToolkitSettings.class);

        // 실제 Orchestrator 사용 — Service↔Orchestrator↔ClaudeClient 흐름 검증
        orchestrator = new HarnessOrchestrator(claudeClient);

        // PromptLoader는 모든 호출에서 stage 이름 반영된 가짜 system prompt 반환
        when(prompts.loadOrDefault(eq("log-rca"), anyString(), anyString()))
                .thenAnswer(inv -> "SYSPROMPT[" + inv.getArgument(1) + "]");
        when(settings.getProjectContext()).thenReturn("PROJECT_MEMO");

        service = new HarnessLogRcaService(orchestrator, prompts, settings);
    }

    @Test
    @DisplayName("4-stage가 순서대로 호출되고 각 stage가 이전 출력을 user 메시지에 포함")
    void fourStagesCalledInOrderWithChaining() {
        when(claudeClient.chat(startsWith("SYSPROMPT[analyst]"),  anyString(), eq(3072)))
                .thenReturn("ANALYST_OUT");
        when(claudeClient.chatWithContinuation(
                startsWith("SYSPROMPT[builder]"), anyString(), eq(8192), eq(3)))
                .thenReturn("BUILDER_OUT");
        when(claudeClient.chat(startsWith("SYSPROMPT[reviewer]"), anyString(), eq(4096)))
                .thenReturn("REVIEWER_OUT");
        when(claudeClient.chat(startsWith("SYSPROMPT[verifier]"), anyString(), eq(4096)))
                .thenReturn("VERIFIER_OUT");

        HarnessRunResult run = service.analyze(
                "ORA-00060 deadlock", "14:23 발생", "MERGE INTO ...", "Oracle 19c", "general");

        assertTrue(run.isSuccess(), run.getError());
        assertEquals(4, run.getStages().size());
        assertEquals("ANALYST_OUT",  run.getStageOutput("analyst"));
        assertEquals("BUILDER_OUT",  run.getStageOutput("builder"));
        assertEquals("REVIEWER_OUT", run.getStageOutput("reviewer"));
        assertEquals("VERIFIER_OUT", run.getStageOutput("verifier"));

        // Builder의 user 메시지에 Analyst 출력이 포함됨
        ArgumentCaptor<String> builderUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chatWithContinuation(startsWith("SYSPROMPT[builder]"),
                builderUser.capture(), eq(8192), eq(3));
        assertTrue(builderUser.getValue().contains("ANALYST_OUT"),
                "Builder 입력에 Analyst 출력 포함: " + builderUser.getValue());

        // Reviewer의 user 메시지에 Analyst+Builder 출력 포함
        ArgumentCaptor<String> reviewerUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(startsWith("SYSPROMPT[reviewer]"), reviewerUser.capture(), eq(4096));
        assertTrue(reviewerUser.getValue().contains("ANALYST_OUT"));
        assertTrue(reviewerUser.getValue().contains("BUILDER_OUT"));

        // Verifier의 user 메시지에 3개 모두 포함
        ArgumentCaptor<String> verifierUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(startsWith("SYSPROMPT[verifier]"), verifierUser.capture(), eq(4096));
        assertTrue(verifierUser.getValue().contains("ANALYST_OUT"));
        assertTrue(verifierUser.getValue().contains("BUILDER_OUT"));
        assertTrue(verifierUser.getValue().contains("REVIEWER_OUT"));
    }

    @Test
    @DisplayName("Analyst의 user 메시지에 입력 4종이 모두 들어감 (값이 비지 않은 것만)")
    void analystUserIncludesNonEmptyInputs() {
        stubAllStages();

        service.analyze("LOG_X", "TIME_X", "CODE_X", "ENV_X", "general");

        ArgumentCaptor<String> analystUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(startsWith("SYSPROMPT[analyst]"), analystUser.capture(), eq(3072));
        String u = analystUser.getValue();
        assertTrue(u.contains("LOG_X"),  "error_log 누락: "    + u);
        assertTrue(u.contains("TIME_X"), "timeline 누락: "     + u);
        assertTrue(u.contains("CODE_X"), "related_code 누락: " + u);
        assertTrue(u.contains("ENV_X"),  "env 누락: "          + u);
    }

    @Test
    @DisplayName("선택 입력이 비면 해당 섹션은 출력에서 생략")
    void emptyOptionalInputsAreOmitted() {
        stubAllStages();

        service.analyze("only error", "", "", "", "general");

        ArgumentCaptor<String> analystUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(startsWith("SYSPROMPT[analyst]"), analystUser.capture(), eq(3072));
        String u = analystUser.getValue();
        assertTrue(u.contains("only error"));
        assertFalse(u.contains("타임라인 메모"),  "빈 timeline 섹션 출력됨: " + u);
        assertFalse(u.contains("관련 코드/SQL"),  "빈 related_code 섹션 출력됨: " + u);
        assertFalse(u.contains("환경 정보"),      "빈 env 섹션 출력됨: " + u);
    }

    @Test
    @DisplayName("errorLog가 비면 IllegalArgumentException")
    void emptyErrorLogThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.analyze("",   "t", "c", "e", "general"));
        assertThrows(IllegalArgumentException.class,
                () -> service.analyze(null, "t", "c", "e", "general"));
        assertThrows(IllegalArgumentException.class,
                () -> service.analyze("   ", "t", "c", "e", "general"));
    }

    @Test
    @DisplayName("프로젝트 컨텍스트 메모가 system prompt에 결합되어 전달")
    void projectMemoAppendedToSystemPrompt() {
        stubAllStages();

        service.analyze("log", "", "", "", "general");

        // 4개 stage 모두 memo가 결합되어 호출됨
        verify(claudeClient).chat(
                eq("SYSPROMPT[analyst]\n\n[프로젝트 컨텍스트]\nPROJECT_MEMO"),
                anyString(), eq(3072));
        verify(claudeClient).chatWithContinuation(
                eq("SYSPROMPT[builder]\n\n[프로젝트 컨텍스트]\nPROJECT_MEMO"),
                anyString(), eq(8192), eq(3));
    }

    @Test
    @DisplayName("memo가 비어있으면 [프로젝트 컨텍스트] 섹션이 추가되지 않음")
    void emptyMemoNotAppended() {
        when(settings.getProjectContext()).thenReturn("");
        stubAllStages();

        service.analyze("log", "", "", "", "general");

        // 정확 매칭 — memo가 없을 때는 [프로젝트 컨텍스트] 섹션이 절대 추가되면 안 됨
        verify(claudeClient).chat(eq("SYSPROMPT[analyst]"), anyString(), eq(3072));
    }

    @Test
    @DisplayName("토큰 예산 — Analyst 3072, Builder 8192+3cont, Reviewer 4096, Verifier 4096")
    void tokenBudgets() {
        stubAllStages();
        service.analyze("log", "", "", "", "general");

        verify(claudeClient).chat(startsWith("SYSPROMPT[analyst]"),  anyString(), eq(3072));
        verify(claudeClient).chatWithContinuation(startsWith("SYSPROMPT[builder]"), anyString(), eq(8192), eq(3));
        verify(claudeClient).chat(startsWith("SYSPROMPT[reviewer]"), anyString(), eq(4096));
        verify(claudeClient).chat(startsWith("SYSPROMPT[verifier]"), anyString(), eq(4096));
    }

    @Test
    @DisplayName("security 모드 — Analyst가 analyst-security.md를 로드")
    void securityMode_loadsSecurityVariantForAnalyst() {
        // 일반 모드 시 analyst, security 모드 시 analyst-security가 로드되는지 추적
        when(prompts.loadOrDefault(eq("log-rca"), eq("analyst"),         anyString())).thenReturn("SYS[analyst-general]");
        when(prompts.loadOrDefault(eq("log-rca"), eq("analyst-security"),anyString())).thenReturn("SYS[analyst-security]");
        when(prompts.loadOrDefault(eq("log-rca"), eq("builder"),         anyString())).thenReturn("SYS[builder]");
        when(prompts.loadOrDefault(eq("log-rca"), eq("reviewer"),        anyString())).thenReturn("SYS[reviewer]");
        when(prompts.loadOrDefault(eq("log-rca"), eq("verifier"),        anyString())).thenReturn("SYS[verifier]");
        when(claudeClient.chat(anyString(), anyString(), anyInt())).thenReturn("ok");
        when(claudeClient.chatWithContinuation(anyString(), anyString(), anyInt(), anyInt())).thenReturn("ok");

        // security 모드 호출
        service.analyze("log", "", "", "", "security");

        // analyst-security가 로드됐는지 확인
        verify(prompts).loadOrDefault(eq("log-rca"), eq("analyst-security"), anyString());
        // analyst (일반)는 호출되지 않아야 함
        verify(prompts, never()).loadOrDefault(eq("log-rca"), eq("analyst"), anyString());
        // 다른 stage들은 일반 prompt 그대로
        verify(prompts).loadOrDefault(eq("log-rca"), eq("builder"),  anyString());
        verify(prompts).loadOrDefault(eq("log-rca"), eq("reviewer"), anyString());
        verify(prompts).loadOrDefault(eq("log-rca"), eq("verifier"), anyString());
    }

    @Test
    @DisplayName("general 모드 — Analyst가 analyst.md를 로드 (security 변종 안 씀)")
    void generalMode_loadsDefaultAnalyst() {
        when(prompts.loadOrDefault(eq("log-rca"), eq("analyst"),         anyString())).thenReturn("SYS[analyst]");
        when(prompts.loadOrDefault(eq("log-rca"), eq("analyst-security"),anyString())).thenReturn("SYS[analyst-security]");
        when(prompts.loadOrDefault(eq("log-rca"), eq("builder"),         anyString())).thenReturn("SYS[builder]");
        when(prompts.loadOrDefault(eq("log-rca"), eq("reviewer"),        anyString())).thenReturn("SYS[reviewer]");
        when(prompts.loadOrDefault(eq("log-rca"), eq("verifier"),        anyString())).thenReturn("SYS[verifier]");
        when(claudeClient.chat(anyString(), anyString(), anyInt())).thenReturn("ok");
        when(claudeClient.chatWithContinuation(anyString(), anyString(), anyInt(), anyInt())).thenReturn("ok");

        service.analyze("log", "", "", "", "general");

        verify(prompts).loadOrDefault(eq("log-rca"), eq("analyst"), anyString());
        verify(prompts, never()).loadOrDefault(eq("log-rca"), eq("analyst-security"), anyString());
    }

    @Test
    @DisplayName("security 모드 — Analyst의 user 메시지에 보안 분석 hint가 포함")
    void securityMode_userMessageHasSecurityHint() {
        when(prompts.loadOrDefault(eq("log-rca"), anyString(), anyString())).thenAnswer(inv -> "SYS[" + inv.getArgument(1) + "]");
        when(claudeClient.chat(anyString(), anyString(), anyInt())).thenReturn("ok");
        when(claudeClient.chatWithContinuation(anyString(), anyString(), anyInt(), anyInt())).thenReturn("ok");

        service.analyze("malicious log", "", "", "", "security");

        ArgumentCaptor<String> analystUser = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).chat(startsWith("SYS[analyst-security]"), analystUser.capture(), eq(3072));
        assertTrue(analystUser.getValue().contains("보안 위협"),
                "security 모드 user 메시지에 '보안 위협' 키워드가 있어야 함: " + analystUser.getValue());
    }

    private void stubAllStages() {
        // memo 결합 후의 system prompt 형태: "SYSPROMPT[xxx]\n\n[프로젝트 컨텍스트]\nPROJECT_MEMO"
        // 단, HarnessOrchestrator가 memo를 직접 prepend하지 않고 Stage가 ctx.getMemo()를 쓰지 않으므로
        // 실제로는 SYSPROMPT[xxx] 그대로 전달됨. 테스트는 contains 매칭으로 우회.
        when(claudeClient.chat(anyString(), anyString(), anyInt())).thenReturn("ok");
        when(claudeClient.chatWithContinuation(anyString(), anyString(), anyInt(), anyInt())).thenReturn("ok");
    }
}
