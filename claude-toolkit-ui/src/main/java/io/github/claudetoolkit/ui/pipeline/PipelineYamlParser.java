package io.github.claudetoolkit.ui.pipeline;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SnakeYAML 기반 파이프라인 YAML 파서 (v2.9.5).
 *
 * <p>{@link SafeConstructor}를 사용하여 안전한 YAML 파싱만 허용합니다 (역직렬화 공격 방지).
 */
@Component
public class PipelineYamlParser {

    /**
     * YAML 문자열을 {@link PipelineSpec}으로 파싱합니다.
     *
     * @throws IllegalArgumentException YAML 문법 오류 또는 필수 필드 누락
     */
    public PipelineSpec parse(String yamlContent) {
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("파이프라인 YAML이 비어있습니다.");
        }

        Object parsed;
        try {
            Yaml yaml = new Yaml(new SafeConstructor());
            parsed = yaml.load(yamlContent);
        } catch (Exception e) {
            throw new IllegalArgumentException("YAML 파싱 오류: " + e.getMessage(), e);
        }

        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("YAML 루트는 객체(map)여야 합니다.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;

        PipelineSpec spec = new PipelineSpec();
        spec.setId(asString(root.get("id")));
        spec.setName(asString(root.get("name")));
        spec.setDescription(asString(root.get("description")));
        spec.setInputLanguage(asString(root.get("inputLanguage")));

        Object stepsObj = root.get("steps");
        if (!(stepsObj instanceof List)) {
            throw new IllegalArgumentException("'steps' 필드는 배열이어야 합니다.");
        }

        List<PipelineStepSpec> steps = new ArrayList<PipelineStepSpec>();
        @SuppressWarnings("unchecked")
        List<Object> stepList = (List<Object>) stepsObj;

        int idx = 0;
        for (Object stepObj : stepList) {
            idx++;
            if (!(stepObj instanceof Map)) {
                throw new IllegalArgumentException("steps[" + idx + "]는 객체여야 합니다.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> stepMap = (Map<String, Object>) stepObj;

            PipelineStepSpec step = new PipelineStepSpec();
            step.setId(asString(stepMap.get("id")));
            step.setAnalysis(asString(stepMap.get("analysis")));
            step.setInput(asString(stepMap.get("input")));
            step.setContext(asString(stepMap.get("context")));
            step.setCondition(asString(stepMap.get("condition")));
            // v3.0: 병렬 실행
            Object parallelObj = stepMap.get("parallel");
            if (parallelObj instanceof Boolean) step.setParallel((Boolean) parallelObj);
            else if ("true".equalsIgnoreCase(asString(parallelObj))) step.setParallel(true);
            step.setDependsOn(asString(stepMap.get("dependsOn")));

            if (step.getId() == null || step.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("steps[" + idx + "].id는 필수입니다.");
            }
            if (step.getAnalysis() == null || step.getAnalysis().trim().isEmpty()) {
                throw new IllegalArgumentException("steps[" + idx + "].analysis는 필수입니다.");
            }
            if (step.getInput() == null) {
                throw new IllegalArgumentException("steps[" + idx + "].input은 필수입니다.");
            }

            steps.add(step);
        }

        if (steps.isEmpty()) {
            throw new IllegalArgumentException("최소 1개 이상의 step이 필요합니다.");
        }

        spec.setSteps(steps);

        if (spec.getName() == null || spec.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("'name' 필드는 필수입니다.");
        }

        return spec;
    }

    /**
     * 저장 전 검증용 — 파싱 실패 여부만 반환 (예외를 던지지 않음).
     * @return 에러 메시지 (정상이면 null)
     */
    public String validate(String yamlContent) {
        try {
            parse(yamlContent);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String asString(Object obj) {
        return obj == null ? null : obj.toString();
    }
}
