package io.github.claudetoolkit.ui.harness.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phase A — 한 번의 하네스 실행 동안 stage들 사이로 전달되는 컨텍스트.
 *
 * <p>불변 필드(harnessName, runId, inputs, memo, templateHint)는 생성 시 고정되고,
 * stage 출력은 {@link HarnessOrchestrator}가 단계마다
 * {@link #putStageOutput(String, String)}로 누적합니다.
 *
 * <p>이 객체는 한 실행에 한정되므로 thread-safe하지 않습니다 — Orchestrator가
 * 단일 스레드에서 stage들을 순차 실행하는 것을 전제로 합니다.
 */
public class HarnessContext {

    /** 하네스 이름 — "code-review", "sp-migration", "sql-optimization", "log-rca". */
    private final String harnessName;

    /** 실행별 고유 ID — 감사/로깅/캐시 키에 사용. */
    private final String runId;

    /**
     * 사용자 입력 묶음. 예: {@code {"code": "...", "language": "java"}} 또는
     * {@code {"sp_source": "...", "table_ddl": "...", "context": "..."}}.
     * 키 명명 규약은 각 하네스 stage 구현이 정의합니다.
     */
    private final Map<String, Object> inputs;

    /** 프로젝트 컨텍스트 메모 — {@code ToolkitSettings.getProjectContext()}에서 주입. */
    private final String memo;

    /** 분석 템플릿 힌트 — "performance", "security", "refactoring" 등. 빈 문자열 가능. */
    private final String templateHint;

    /** stage별 출력 누적 (이름 → 후처리된 출력). */
    private final Map<String, String> stageOutputs = new LinkedHashMap<String, String>();

    public HarnessContext(String harnessName, String runId,
                          Map<String, Object> inputs,
                          String memo, String templateHint) {
        if (harnessName == null || harnessName.isEmpty()) {
            throw new IllegalArgumentException("harnessName must not be empty");
        }
        this.harnessName  = harnessName;
        this.runId        = (runId != null && !runId.isEmpty()) ? runId : UUID.randomUUID().toString();
        this.inputs       = inputs       != null ? new LinkedHashMap<String, Object>(inputs) : new LinkedHashMap<String, Object>();
        this.memo         = memo         != null ? memo         : "";
        this.templateHint = templateHint != null ? templateHint : "";
    }

    public String              getHarnessName()  { return harnessName; }
    public String              getRunId()        { return runId; }
    public Map<String, Object> getInputs()       { return Collections.unmodifiableMap(inputs); }
    public String              getMemo()         { return memo; }
    public String              getTemplateHint() { return templateHint; }
    public Map<String, String> getStageOutputs() { return Collections.unmodifiableMap(stageOutputs); }

    /** 입력 값을 문자열로 반환합니다 — 누락/null이면 빈 문자열. */
    public String getInputAsString(String key) {
        Object v = inputs.get(key);
        return v == null ? "" : v.toString();
    }

    /** 이전 stage의 후처리된 출력을 반환합니다. 없으면 빈 문자열. */
    public String getStageOutput(String stageName) {
        String s = stageOutputs.get(stageName);
        return s == null ? "" : s;
    }

    /** Orchestrator가 stage 완료 시 호출 — 외부에서 직접 호출하지 마세요. */
    void putStageOutput(String stageName, String output) {
        stageOutputs.put(stageName, output != null ? output : "");
    }
}
