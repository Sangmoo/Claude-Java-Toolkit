package io.github.claudetoolkit.ui.flow.model;

/** 노드 간 엣지. {@code label} 에 호출 종류 (예: "POST /api/x", "INSERT") 를 담는다. */
public class FlowEdge {
    public String from;
    public String to;
    public String label;

    public FlowEdge() {}
    public FlowEdge(String from, String to, String label) {
        this.from = from; this.to = to; this.label = label;
    }

    public String getFrom()  { return from; }
    public String getTo()    { return to; }
    public String getLabel() { return label; }
}
