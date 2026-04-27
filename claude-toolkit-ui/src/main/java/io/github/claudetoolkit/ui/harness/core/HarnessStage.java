package io.github.claudetoolkit.ui.harness.core;

/**
 * Phase A — 하네스 파이프라인의 단일 단계(stage)를 표현합니다.
 *
 * <p>하나의 stage는 한 번의 Claude API 호출에 대응하며,
 * {@link HarnessOrchestrator}가 stage 목록을 순차 실행합니다.
 *
 * <p>각 stage는 자신의 시스템/사용자 프롬프트를 직접 빌드하고,
 * {@link HarnessContext#getStageOutputs()}에서 이전 단계의 출력을 참조할 수 있습니다.
 *
 * <h3>토큰 예산</h3>
 * <ul>
 *   <li>{@link #maxTokens()} — 이 stage 한 번의 호출 출력 한도</li>
 *   <li>{@link #continuations()} — 잘릴 경우 이어쓰기 추가 호출 횟수
 *       (예: {@code maxTokens=8192} + {@code continuations=3} = 최대 32,768 토큰 출력)</li>
 * </ul>
 *
 * <h3>스트리밍 헤더/푸터</h3>
 * SSE 스트리밍 모드에서 {@link #streamHeader()}와 {@link #streamFooter()}는
 * stage 출력 앞뒤에 emit됩니다 (예: 코드 펜스 열기/닫기, 섹션 제목).
 */
public interface HarnessStage {

    /** Stage 식별자 — "analyst", "builder", "reviewer", "verifier" 또는 커스텀. */
    String name();

    /** 이 stage 한 번 호출의 최대 출력 토큰. */
    int maxTokens();

    /**
     * 응답이 {@code max_tokens}로 잘렸을 때 추가로 시도할 이어쓰기 호출 횟수.
     * <ul>
     *   <li>{@code 0} — 단발 (Analyst, Reviewer, Verifier 권장)</li>
     *   <li>{@code 3} — 대형 출력 단계 (Builder 권장, 최대 4배 토큰)</li>
     * </ul>
     */
    int continuations();

    /** 시스템 프롬프트를 빌드합니다 — context의 memo·templateHint를 반영하세요. */
    String buildSystem(HarnessContext ctx);

    /** 사용자 메시지를 빌드합니다 — 이전 stage 출력은 {@code ctx.getStageOutputs()} 참조. */
    String buildUser(HarnessContext ctx);

    /**
     * Claude의 원본 응답을 후처리합니다 (예: 코드 펜스 제거).
     * 기본은 trim만 수행합니다.
     */
    default String postProcess(String rawOutput) {
        return rawOutput == null ? "" : rawOutput.trim();
    }

    /**
     * SSE 스트리밍 시 stage 출력 앞에 emit할 텍스트.
     * 단계 경계를 알리는 sentinel은 Orchestrator가 자동으로 prepend하므로
     * 여기는 사람이 읽을 섹션 헤더만 (예: {@code "## 📋 분석 요약\n"}).
     */
    default String streamHeader() {
        return "";
    }

    /** SSE 스트리밍 시 stage 출력 뒤에 emit할 텍스트 (예: 코드 펜스 닫기). */
    default String streamFooter() {
        return "";
    }
}
