package io.github.claudetoolkit.ui.harness.core;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase A — HarnessOrchestrator 단위 테스트.
 *
 * 커버:
 * <ul>
 *   <li>2-stage 순차 실행 → 두 stage 출력이 누적되고 두 번째 stage가 첫 번째 출력을 참조 가능</li>
 *   <li>Builder류 stage(continuations &gt; 0) → chatWithContinuation 호출됨</li>
 *   <li>중간 stage 실패 → 후속 stage 실행 안 됨, error 메시지 세팅</li>
 *   <li>postProcess 적용 → 코드 펜스 제거 확인</li>
 *   <li>스트리밍 — stage 마커 sentinel + header/footer 정상 emit</li>
 *   <li>빈 stage 목록 → IllegalArgumentException</li>
 * </ul>
 */
class HarnessOrchestratorTest {

    private ClaudeClient        claudeClient;
    private HarnessOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        claudeClient = mock(ClaudeClient.class);
        orchestrator = new HarnessOrchestrator(claudeClient);
    }

    @Test
    @DisplayName("2-stage 순차 실행 — 두 번째 stage가 첫 번째 출력을 참조")
    void twoStagePipeline_passesOutputForward() {
        when(claudeClient.chat(eq("sys-A"), eq("user-A"), anyInt())).thenReturn("OUTPUT_A");
        when(claudeClient.chat(eq("sys-B"), eq("got: OUTPUT_A"), anyInt())).thenReturn("OUTPUT_B");

        HarnessStage a = stage("a", 100, 0, "sys-A", ctx -> "user-A");
        HarnessStage b = stage("b", 100, 0, "sys-B", ctx -> "got: " + ctx.getStageOutput("a"));

        HarnessRunResult result = orchestrator.run("test-harness",
                Arrays.<HarnessStage>asList(a, b), emptyInputs(), "", "");

        assertTrue(result.isSuccess(), result.getError());
        assertEquals(2, result.getStages().size());
        assertEquals("OUTPUT_A", result.getStageOutput("a"));
        assertEquals("OUTPUT_B", result.getStageOutput("b"));
        verify(claudeClient).chat("sys-A", "user-A", 100);
        verify(claudeClient).chat("sys-B", "got: OUTPUT_A", 100);
    }

    @Test
    @DisplayName("continuations > 0 — chatWithContinuation으로 호출")
    void builderStage_usesContinuation() {
        when(claudeClient.chatWithContinuation(eq("sys"), eq("user"), eq(8192), eq(3)))
                .thenReturn("BIG_OUTPUT");

        HarnessStage builder = stage("builder", 8192, 3, "sys", ctx -> "user");

        HarnessRunResult result = orchestrator.run("test",
                java.util.Collections.<HarnessStage>singletonList(builder), emptyInputs(), "", "");

        assertTrue(result.isSuccess());
        assertEquals("BIG_OUTPUT", result.getStageOutput("builder"));
        verify(claudeClient).chatWithContinuation("sys", "user", 8192, 3);
        verify(claudeClient, never()).chat(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("중간 stage 실패 — 후속 stage 실행 중단 + error 세팅")
    void middleStageFailure_stopsPipeline() {
        when(claudeClient.chat(eq("sys-A"), anyString(), anyInt())).thenReturn("ok");
        when(claudeClient.chat(eq("sys-B"), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Claude 폭발"));

        HarnessStage a = stage("a", 100, 0, "sys-A", ctx -> "u");
        HarnessStage b = stage("b", 100, 0, "sys-B", ctx -> "u");
        HarnessStage c = stage("c", 100, 0, "sys-C", ctx -> "u");

        HarnessRunResult result = orchestrator.run("test",
                Arrays.<HarnessStage>asList(a, b, c), emptyInputs(), "", "");

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("b"), "error 메시지에 stage 이름 포함: " + result.getError());
        assertTrue(result.getError().contains("폭발"), "원본 에러 포함: " + result.getError());
        assertEquals(2, result.getStages().size(), "a 성공 + b 실패까지만 기록");
        verify(claudeClient, never()).chat(eq("sys-C"), anyString(), anyInt());
    }

    @Test
    @DisplayName("postProcess — 코드 펜스 제거 적용")
    void postProcess_appliedToOutput() {
        when(claudeClient.chat(anyString(), anyString(), anyInt()))
                .thenReturn("```java\nreal code\n```");

        HarnessStage codeStage = new HarnessStage() {
            public String name() { return "builder"; }
            public int maxTokens() { return 1000; }
            public int continuations() { return 0; }
            public String buildSystem(HarnessContext ctx) { return "sys"; }
            public String buildUser(HarnessContext ctx) { return "u"; }
            public String postProcess(String raw) {
                // 간단 펜스 제거 시뮬레이션
                String s = raw.trim();
                if (s.startsWith("```java\n")) s = s.substring("```java\n".length());
                if (s.endsWith("\n```")) s = s.substring(0, s.length() - "\n```".length());
                return s;
            }
        };

        HarnessRunResult result = orchestrator.run("test",
                java.util.Collections.<HarnessStage>singletonList(codeStage),
                emptyInputs(), "", "");

        assertEquals("real code", result.getStageOutput("builder"));
    }

    @Test
    @DisplayName("스트리밍 — stage sentinel + header/footer + 본문 chunk 순서대로 emit")
    void streaming_emitsSentinelHeaderBodyFooter() throws IOException {
        // chatStream을 호출하면 onChunk로 두 chunk를 흘려보내도록 stub
        doAnswer(inv -> {
            Consumer<String> onChunk = inv.getArgument(3);
            onChunk.accept("chunk1");
            onChunk.accept("chunk2");
            return null;
        }).when(claudeClient).chatStream(anyString(), anyString(), anyInt(), any());

        HarnessStage stageWithDecor = new HarnessStage() {
            public String name() { return "analyst"; }
            public int maxTokens() { return 1000; }
            public int continuations() { return 0; }
            public String buildSystem(HarnessContext ctx) { return "sys"; }
            public String buildUser(HarnessContext ctx) { return "u"; }
            public String streamHeader() { return "[HEADER]"; }
            public String streamFooter() { return "[FOOTER]"; }
        };

        StringBuilder out = new StringBuilder();
        orchestrator.runStream("test",
                java.util.Collections.<HarnessStage>singletonList(stageWithDecor),
                emptyInputs(), "", "", out::append);

        String full = out.toString();
        // 순서: 마커 → 헤더 → chunk1 → chunk2 → 푸터
        int posMarker = full.indexOf("[[HARNESS_STAGE:1]]");
        int posHeader = full.indexOf("[HEADER]");
        int posChunk1 = full.indexOf("chunk1");
        int posChunk2 = full.indexOf("chunk2");
        int posFooter = full.indexOf("[FOOTER]");
        assertTrue(posMarker >= 0, "sentinel 누락: " + full);
        assertTrue(posMarker < posHeader && posHeader < posChunk1
                && posChunk1 < posChunk2 && posChunk2 < posFooter,
                "순서 위반: " + full);
    }

    @Test
    @DisplayName("스트리밍 — 다단계 stage 사이에 sentinel 자동 삽입")
    void streaming_multiStage_insertsSentinelBetween() throws IOException {
        doAnswer(inv -> {
            Consumer<String> onChunk = inv.getArgument(3);
            onChunk.accept("body");
            return null;
        }).when(claudeClient).chatStream(anyString(), anyString(), anyInt(), any());

        HarnessStage s1 = stage("s1", 100, 0, "sys", ctx -> "u");
        HarnessStage s2 = stage("s2", 100, 0, "sys", ctx -> "u");

        StringBuilder out = new StringBuilder();
        orchestrator.runStream("test", Arrays.<HarnessStage>asList(s1, s2),
                emptyInputs(), "", "", out::append);

        String full = out.toString();
        assertTrue(full.contains("[[HARNESS_STAGE:1]]"), "1단계 마커 누락");
        assertTrue(full.contains("[[HARNESS_STAGE:2]]"), "2단계 마커 누락");
        assertTrue(full.indexOf("[[HARNESS_STAGE:1]]") < full.indexOf("[[HARNESS_STAGE:2]]"));
    }

    @Test
    @DisplayName("빈 stage 목록 → IllegalArgumentException")
    void emptyStages_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.run("test", java.util.Collections.<HarnessStage>emptyList(),
                        emptyInputs(), "", ""));
    }

    @Test
    @DisplayName("HarnessContext — 입력 누락 시 빈 문자열, stage 출력은 누적 후 immutable")
    void context_handlesMissingInputAndAccumulates() {
        Map<String, Object> inputs = new LinkedHashMap<String, Object>();
        inputs.put("code", "SELECT 1");
        HarnessContext ctx = new HarnessContext("test", null, inputs, null, null);

        assertEquals("SELECT 1", ctx.getInputAsString("code"));
        assertEquals("",         ctx.getInputAsString("missing"));
        assertEquals("",         ctx.getMemo());
        assertEquals("",         ctx.getTemplateHint());
        assertNotNull(ctx.getRunId(), "runId 자동 생성");

        // immutable view
        assertThrows(UnsupportedOperationException.class,
                () -> ctx.getInputs().put("x", "y"));
        assertThrows(UnsupportedOperationException.class,
                () -> ctx.getStageOutputs().put("x", "y"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Object> emptyInputs() {
        return new LinkedHashMap<String, Object>();
    }

    private interface UserBuilder {
        String build(HarnessContext ctx);
    }

    private static HarnessStage stage(final String name, final int tokens, final int conts,
                                      final String system, final UserBuilder userFn) {
        return new HarnessStage() {
            public String name() { return name; }
            public int maxTokens() { return tokens; }
            public int continuations() { return conts; }
            public String buildSystem(HarnessContext ctx) { return system; }
            public String buildUser(HarnessContext ctx) { return userFn.build(ctx); }
        };
    }
}
