package io.github.claudetoolkit.sql.explain;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single node in the Oracle EXPLAIN PLAN tree.
 *
 * <p>Mirrors one row from the PLAN_TABLE and holds child nodes
 * for recursive tree rendering on the UI side.
 */
public class ExplainPlanNode {

    private int     id;
    private Integer parentId;
    private int     depth;
    private int     position;
    private String  operation;   // e.g. "TABLE ACCESS", "HASH JOIN"
    private String  options;     // e.g. "FULL", "BY INDEX ROWID"
    private String  objectName;  // table / index name
    private Long    cardinality; // estimated rows (nullable)
    private Long    bytes;       // estimated bytes (nullable)
    private Long    cost;        // estimated cost  (nullable)
    private Long    cpuCost;
    private Long    ioCost;

    private List<ExplainPlanNode> children = new ArrayList<>();

    public ExplainPlanNode() {}

    // ── getters / setters ────────────────────────────────────────────────────

    public int getId()                         { return id; }
    public void setId(int id)                  { this.id = id; }

    public Integer getParentId()               { return parentId; }
    public void setParentId(Integer parentId)  { this.parentId = parentId; }

    public int getDepth()                      { return depth; }
    public void setDepth(int depth)            { this.depth = depth; }

    public int getPosition()                   { return position; }
    public void setPosition(int position)      { this.position = position; }

    public String getOperation()               { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getOptions()                 { return options; }
    public void setOptions(String options)     { this.options = options; }

    public String getObjectName()              { return objectName; }
    public void setObjectName(String n)        { this.objectName = n; }

    public Long getCardinality()               { return cardinality; }
    public void setCardinality(Long c)         { this.cardinality = c; }

    public Long getBytes()                     { return bytes; }
    public void setBytes(Long b)               { this.bytes = b; }

    public Long getCost()                      { return cost; }
    public void setCost(Long cost)             { this.cost = cost; }

    public Long getCpuCost()                   { return cpuCost; }
    public void setCpuCost(Long c)             { this.cpuCost = c; }

    public Long getIoCost()                    { return ioCost; }
    public void setIoCost(Long c)              { this.ioCost = c; }

    public List<ExplainPlanNode> getChildren() { return children; }
    public void setChildren(List<ExplainPlanNode> children) { this.children = children; }

    /** Convenience: full operation label including options. */
    public String getFullOperation() {
        return (options != null && !options.isEmpty())
                ? operation + " " + options
                : operation;
    }
}
