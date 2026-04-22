package io.github.claudetoolkit.ui.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.4.0 — PipelineYamlParser 단위 테스트.
 *
 * <p>SnakeYAML SafeConstructor 기반 파싱 + 필수 필드 검증 + 병렬/조건부 step 처리.
 */
class PipelineYamlParserTest {

    private PipelineYamlParser parser;

    @BeforeEach
    void setUp() {
        parser = new PipelineYamlParser();
    }

    @Test
    @DisplayName("정상 YAML — 단일 step 파싱")
    void parseSingleStep() {
        String yaml = ""
                + "id: test-pipe\n"
                + "name: Test Pipeline\n"
                + "inputLanguage: java\n"
                + "steps:\n"
                + "  - id: step1\n"
                + "    analysis: CODE_REVIEW\n"
                + "    input: ${pipeline.input}\n";
        PipelineSpec spec = parser.parse(yaml);
        assertEquals("test-pipe", spec.getId());
        assertEquals("Test Pipeline", spec.getName());
        assertEquals(1, spec.getSteps().size());
        assertEquals("step1", spec.getSteps().get(0).getId());
        assertEquals("CODE_REVIEW", spec.getSteps().get(0).getAnalysis());
    }

    @Test
    @DisplayName("다중 step + 병렬 + 조건부 + 컨텍스트")
    void parseComplexPipeline() {
        String yaml = ""
                + "id: complex\n"
                + "name: Complex\n"
                + "inputLanguage: sql\n"
                + "steps:\n"
                + "  - id: a\n"
                + "    analysis: SQL_REVIEW\n"
                + "    input: ${pipeline.input}\n"
                + "  - id: b\n"
                + "    analysis: SQL_SECURITY\n"
                + "    input: ${pipeline.input}\n"
                + "    parallel: true\n"
                + "  - id: c\n"
                + "    analysis: REFACTOR\n"
                + "    input: ${pipeline.input}\n"
                + "    context: ${a.output}\n"
                + "    condition: \"${a.severity} == 'HIGH'\"\n";
        PipelineSpec spec = parser.parse(yaml);
        assertEquals(3, spec.getSteps().size());
        assertTrue(spec.getSteps().get(1).isParallel());
        assertEquals("${a.output}", spec.getSteps().get(2).getContext());
        assertEquals("${a.severity} == 'HIGH'", spec.getSteps().get(2).getCondition());
    }

    @Test
    @DisplayName("필수 필드 누락 (id 없음) — 예외")
    void missingIdThrows() {
        String yaml = ""
                + "name: bad\n"
                + "steps:\n"
                + "  - analysis: CODE_REVIEW\n"
                + "    input: ${pipeline.input}\n";
        assertThrows(Exception.class, () -> parser.parse(yaml));
    }

    @Test
    @DisplayName("validate() — 정상 YAML 은 null 반환 (오류 메시지 없음)")
    void validateValid() {
        String yaml = ""
                + "id: x\n"
                + "name: x\n"
                + "inputLanguage: java\n"
                + "steps:\n"
                + "  - id: s\n"
                + "    analysis: CODE_REVIEW\n"
                + "    input: ${pipeline.input}\n";
        assertNull(parser.validate(yaml), "정상 YAML 은 null 반환");
    }

    @Test
    @DisplayName("validate() — 깨진 YAML 은 오류 메시지 문자열 반환")
    void validateInvalid() {
        assertNotNull(parser.validate("id: x\nsteps:\n  - bad: [unclosed"), "깨진 YAML 은 오류 메시지 반환");
    }

    @Test
    @DisplayName("parallel 필드 — boolean true / 'true' 문자열 모두 지원")
    void parallelBooleanOrString() {
        String yamlBool = ""
                + "id: x\nname: x\nsteps:\n"
                + "  - id: s\n    analysis: A\n    input: x\n    parallel: true\n";
        String yamlStr = ""
                + "id: x\nname: x\nsteps:\n"
                + "  - id: s\n    analysis: A\n    input: x\n    parallel: \"true\"\n";
        assertTrue(parser.parse(yamlBool).getSteps().get(0).isParallel());
        assertTrue(parser.parse(yamlStr).getSteps().get(0).isParallel());
    }

    @Test
    @DisplayName("dependsOn — 콤마 구분 다중 의존성 파싱")
    void dependsOnMultiple() {
        String yaml = ""
                + "id: x\nname: x\nsteps:\n"
                + "  - id: a\n    analysis: A\n    input: x\n"
                + "  - id: b\n    analysis: B\n    input: x\n"
                + "  - id: c\n    analysis: C\n    input: x\n    dependsOn: \"a,b\"\n";
        PipelineSpec spec = parser.parse(yaml);
        java.util.List<String> deps = spec.getSteps().get(2).getDependsOnList();
        assertEquals(2, deps.size());
        assertTrue(deps.contains("a"));
        assertTrue(deps.contains("b"));
    }
}
