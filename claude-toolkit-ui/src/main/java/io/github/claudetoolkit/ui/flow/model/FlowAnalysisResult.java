package io.github.claudetoolkit.ui.flow.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flow 분석 결과 — 시각화 (nodes/edges) + 단계별 텍스트 (steps) + 진단 (warnings/stats).
 *
 * <p>Phase 1 에서는 인덱서가 직접 채운다 (LLM 미사용).
 * Phase 2 에서 LLM 이 이 result 를 받아 narrative summary 와 최적화된 mermaid 를 추가.
 */
public class FlowAnalysisResult {

    public String      query;
    public String      targetType;       // 실제 분석한 type (AUTO 가 무엇으로 결정됐는지)
    public String      summary;          // Phase 1 은 자동 생성, Phase 2 는 LLM 작성
    public String      mermaid;          // Phase 2 에서 채워질 자리 (Phase 1 은 자동 생성)
    public List<FlowNode> nodes  = new ArrayList<FlowNode>();
    public List<FlowEdge> edges  = new ArrayList<FlowEdge>();
    public List<FlowStep> steps  = new ArrayList<FlowStep>();
    public List<String>   warnings = new ArrayList<String>();
    public Map<String, Object> stats = new LinkedHashMap<String, Object>();

    public String getQuery()      { return query; }
    public String getTargetType() { return targetType; }
    public String getSummary()    { return summary; }
    public String getMermaid()    { return mermaid; }
    public List<FlowNode> getNodes() { return nodes; }
    public List<FlowEdge> getEdges() { return edges; }
    public List<FlowStep> getSteps() { return steps; }
    public List<String>   getWarnings() { return warnings; }
    public Map<String, Object> getStats() { return stats; }
}
