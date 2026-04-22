package io.github.claudetoolkit.ui.flow.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 데이터 흐름 다이어그램의 한 노드.
 *
 * <p>{@code type} 으로 시각화 측 (ReactFlow / Mermaid) 에서 색상·아이콘을 결정.
 * 가능한 type 값:
 * <ul>
 *   <li>{@code ui}         — MiPlatform / JSP / 화면 진입점</li>
 *   <li>{@code controller} — Spring Controller endpoint</li>
 *   <li>{@code service}    — Service / Manager 클래스 메서드</li>
 *   <li>{@code dao}        — DAO / Repository / Mapper 메서드</li>
 *   <li>{@code mybatis}    — &lt;insert&gt;/&lt;update&gt; 등 SQL 매퍼 statement</li>
 *   <li>{@code sp}         — Oracle PROCEDURE / FUNCTION / PACKAGE / TRIGGER</li>
 *   <li>{@code table}      — DB 테이블 (보통 종착지)</li>
 * </ul>
 */
public class FlowNode {
    public String id;
    public String type;
    public String label;
    public String file;          // 상대경로 (없으면 null)
    public Integer line;         // 라인 번호 (없으면 null)
    public Map<String, String> meta = new LinkedHashMap<String, String>();

    public FlowNode() {}

    public FlowNode(String id, String type, String label) {
        this.id = id; this.type = type; this.label = label;
    }

    public FlowNode put(String k, String v) {
        if (v != null) meta.put(k, v);
        return this;
    }

    // ── Jackson 용 getter (public 필드 + getter 둘 다 두면 직렬화 안정) ──
    public String getId()    { return id; }
    public String getType()  { return type; }
    public String getLabel() { return label; }
    public String getFile()  { return file; }
    public Integer getLine() { return line; }
    public Map<String, String> getMeta() { return meta; }
}
