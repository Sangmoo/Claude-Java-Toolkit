package io.github.claudetoolkit.ui.harness.core;

/**
 * Phase A — 단일 stage 실행 결과.
 *
 * <p>{@link HarnessOrchestrator}가 각 stage 완료 시 생성하며,
 * {@link HarnessRunResult#getStages()}에 누적됩니다.
 */
public class HarnessStageResult {

    private final String stageName;
    private final String output;
    private final long   elapsedMs;
    private final String error;  // null이면 성공

    public HarnessStageResult(String stageName, String output, long elapsedMs, String error) {
        this.stageName = stageName;
        this.output    = output != null ? output : "";
        this.elapsedMs = elapsedMs;
        this.error     = error;
    }

    public String  getStageName() { return stageName; }
    public String  getOutput()    { return output; }
    public long    getElapsedMs() { return elapsedMs; }
    public String  getError()     { return error; }
    public boolean isSuccess()    { return error == null; }
}
