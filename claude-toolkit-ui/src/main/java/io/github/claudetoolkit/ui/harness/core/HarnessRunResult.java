package io.github.claudetoolkit.ui.harness.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phase A — 하네스 한 번 실행의 전체 결과.
 *
 * <p>{@link HarnessOrchestrator#run}의 반환 타입.
 * 비스트리밍 모드에서만 의미가 있으며, 스트리밍은 chunk-by-chunk로 흘려보냅니다.
 */
public class HarnessRunResult {

    private final String runId;
    private final String harnessName;
    private final List<HarnessStageResult> stages;
    private final long   totalElapsedMs;
    private final String error;  // null = 전체 성공

    public HarnessRunResult(String runId, String harnessName,
                            List<HarnessStageResult> stages,
                            long totalElapsedMs, String error) {
        this.runId          = runId;
        this.harnessName    = harnessName;
        this.stages         = stages != null ? new ArrayList<HarnessStageResult>(stages) : new ArrayList<HarnessStageResult>();
        this.totalElapsedMs = totalElapsedMs;
        this.error          = error;
    }

    public String  getRunId()          { return runId; }
    public String  getHarnessName()    { return harnessName; }
    public List<HarnessStageResult> getStages() { return Collections.unmodifiableList(stages); }
    public long    getTotalElapsedMs() { return totalElapsedMs; }
    public String  getError()          { return error; }
    public boolean isSuccess()         { return error == null; }

    /** 특정 stage의 출력을 조회 — 없으면 빈 문자열. */
    public String getStageOutput(String stageName) {
        for (HarnessStageResult r : stages) {
            if (r.getStageName().equals(stageName)) return r.getOutput();
        }
        return "";
    }
}
