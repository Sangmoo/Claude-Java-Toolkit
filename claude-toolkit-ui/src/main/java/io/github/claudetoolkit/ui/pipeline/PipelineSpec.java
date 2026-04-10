package io.github.claudetoolkit.ui.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * YAML 파싱 결과 (v2.9.5).
 */
public class PipelineSpec {

    private String id;
    private String name;
    private String description;
    private String inputLanguage;
    private List<PipelineStepSpec> steps = new ArrayList<PipelineStepSpec>();

    public PipelineSpec() {}

    public String getId()            { return id; }
    public String getName()          { return name; }
    public String getDescription()   { return description; }
    public String getInputLanguage() { return inputLanguage; }
    public List<PipelineStepSpec> getSteps() { return steps; }

    public void setId(String v)            { this.id = v; }
    public void setName(String v)          { this.name = v; }
    public void setDescription(String v)   { this.description = v; }
    public void setInputLanguage(String v) { this.inputLanguage = v; }
    public void setSteps(List<PipelineStepSpec> v) { this.steps = v != null ? v : new ArrayList<PipelineStepSpec>(); }
}
