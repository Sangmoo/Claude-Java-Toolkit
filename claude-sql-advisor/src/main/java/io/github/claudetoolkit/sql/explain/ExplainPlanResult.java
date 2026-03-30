package io.github.claudetoolkit.sql.explain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Aggregated result of an Oracle EXPLAIN PLAN analysis.
 *
 * <p>Contains:
 * <ul>
 *   <li>Structured tree ({@link ExplainPlanNode}) for interactive rendering</li>
 *   <li>Raw DBMS_XPLAN.DISPLAY() text output</li>
 *   <li>Claude AI analysis in Markdown</li>
 *   <li>Max cost across all nodes (used to draw proportional cost bars)</li>
 * </ul>
 */
public class ExplainPlanResult {

    private ExplainPlanNode root;
    private String          rawPlanText;
    private String          aiAnalysis;
    private long            maxCost;
    private boolean         planTableAvailable;
    private String          analyzedAt;

    public ExplainPlanResult() {
        this.analyzedAt = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ── getters / setters ────────────────────────────────────────────────────

    public ExplainPlanNode getRoot()              { return root; }
    public void setRoot(ExplainPlanNode root)     { this.root = root; }

    public String getRawPlanText()                { return rawPlanText; }
    public void setRawPlanText(String t)          { this.rawPlanText = t; }

    public String getAiAnalysis()                 { return aiAnalysis; }
    public void setAiAnalysis(String a)           { this.aiAnalysis = a; }

    public long getMaxCost()                      { return maxCost; }
    public void setMaxCost(long maxCost)          { this.maxCost = maxCost; }

    public boolean isPlanTableAvailable()         { return planTableAvailable; }
    public void setPlanTableAvailable(boolean b)  { this.planTableAvailable = b; }

    public String getAnalyzedAt()                 { return analyzedAt; }
    public void setAnalyzedAt(String t)           { this.analyzedAt = t; }
}
