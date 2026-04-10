package io.github.claudetoolkit.ui.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 내장 파이프라인 시드 로더 (v2.9.5).
 *
 * <p>앱 시작 시 {@code is_builtin = true} 파이프라인이 DB에 없으면 자동 등록합니다.
 * 기존 내장 파이프라인이 있는 경우 YAML 내용도 업데이트 (최신 버전 반영).
 */
@Component
@Order(5)
public class PipelineBuiltinLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineBuiltinLoader.class);

    private final PipelineDefinitionRepository repository;

    public PipelineBuiltinLoader(PipelineDefinitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            upsertBuiltin("SQL 최적화 풀 스택",
                    "SQL 리뷰 → 리팩터링 → 최종 요약까지 자동 실행",
                    "sql", SQL_OPTIMIZATION_YAML);

            upsertBuiltin("Java 품질 풀 체크",
                    "코드 리뷰 → 보안 감사 → 리팩터링 → 테스트 생성",
                    "java", JAVA_QUALITY_YAML);

            upsertBuiltin("문서화 패키지",
                    "코드 리뷰로 구조 파악 후 Javadoc 자동 생성",
                    "java", DOCUMENTATION_YAML);

            upsertBuiltin("보안 감사 풀 플로우",
                    "코드 리뷰 → 보안 감사 (심각도 자동 연계)",
                    "java", SECURITY_AUDIT_YAML);

            log.info("[PipelineBuiltin] 내장 파이프라인 시드 완료");
        } catch (Exception e) {
            log.error("[PipelineBuiltin] 시드 실패: {}", e.getMessage(), e);
        }
    }

    private void upsertBuiltin(String name, String description, String lang, String yaml) {
        Optional<PipelineDefinition> existing = repository.findByName(name);
        if (existing.isPresent()) {
            PipelineDefinition def = existing.get();
            if (!def.isBuiltin()) return;  // 사용자가 같은 이름으로 만든 것은 건드리지 않음
            def.setDescription(description);
            def.setYamlContent(yaml);
            def.setInputLanguage(lang);
            def.touch();
            repository.save(def);
        } else {
            PipelineDefinition def = new PipelineDefinition(name, description, yaml, lang, true, null);
            repository.save(def);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 내장 파이프라인 YAML 정의
    // ═══════════════════════════════════════════════════════════════

    private static final String SQL_OPTIMIZATION_YAML =
            "id: sql-optimization-full\n" +
            "name: SQL 최적화 풀 스택\n" +
            "description: SQL 리뷰 → 리팩터링 → 최종 요약\n" +
            "inputLanguage: sql\n" +
            "\n" +
            "steps:\n" +
            "  - id: review\n" +
            "    analysis: SQL_REVIEW\n" +
            "    input: ${pipeline.input}\n" +
            "\n" +
            "  - id: refactor\n" +
            "    analysis: SQL_REVIEW\n" +
            "    input: ${pipeline.input}\n" +
            "    context: |\n" +
            "      [이전 리뷰 결과]\n" +
            "      ${review.output}\n" +
            "      \n" +
            "      위 리뷰 결과를 바탕으로 최적화된 SQL을 제안해주세요.\n" +
            "\n" +
            "  - id: summary\n" +
            "    analysis: CODE_REVIEW\n" +
            "    input: |\n" +
            "      [원본 SQL]\n" +
            "      ${pipeline.input}\n" +
            "      \n" +
            "      [리뷰 결과]\n" +
            "      ${review.output}\n" +
            "      \n" +
            "      [리팩터링 제안]\n" +
            "      ${refactor.output}\n" +
            "      \n" +
            "      위 분석 내용을 종합하여 최종 실행 권장사항을 요약해주세요.\n";

    private static final String JAVA_QUALITY_YAML =
            "id: java-quality-full\n" +
            "name: Java 품질 풀 체크\n" +
            "description: 코드 리뷰 → 보안 감사 → 리팩터링 → 테스트 생성\n" +
            "inputLanguage: java\n" +
            "\n" +
            "steps:\n" +
            "  - id: review\n" +
            "    analysis: CODE_REVIEW\n" +
            "    input: ${pipeline.input}\n" +
            "\n" +
            "  - id: security\n" +
            "    analysis: SECURITY_AUDIT\n" +
            "    input: ${pipeline.input}\n" +
            "\n" +
            "  - id: refactor\n" +
            "    analysis: REFACTOR\n" +
            "    input: ${pipeline.input}\n" +
            "    context: |\n" +
            "      [코드 리뷰 결과]\n" +
            "      ${review.output}\n" +
            "      \n" +
            "      [보안 감사 결과]\n" +
            "      ${security.output}\n" +
            "\n" +
            "  - id: tests\n" +
            "    analysis: TEST_GENERATION\n" +
            "    input: ${pipeline.input}\n";

    private static final String DOCUMENTATION_YAML =
            "id: documentation-package\n" +
            "name: 문서화 패키지\n" +
            "description: 코드 리뷰 → Javadoc 생성\n" +
            "inputLanguage: java\n" +
            "\n" +
            "steps:\n" +
            "  - id: review\n" +
            "    analysis: CODE_REVIEW\n" +
            "    input: ${pipeline.input}\n" +
            "\n" +
            "  - id: javadoc\n" +
            "    analysis: JAVADOC\n" +
            "    input: ${pipeline.input}\n" +
            "    context: |\n" +
            "      [코드 분석 결과]\n" +
            "      ${review.output}\n";

    private static final String SECURITY_AUDIT_YAML =
            "id: security-audit-flow\n" +
            "name: 보안 감사 풀 플로우\n" +
            "description: 코드 리뷰 → 보안 감사\n" +
            "inputLanguage: java\n" +
            "\n" +
            "steps:\n" +
            "  - id: review\n" +
            "    analysis: CODE_REVIEW\n" +
            "    input: ${pipeline.input}\n" +
            "\n" +
            "  - id: security\n" +
            "    analysis: SECURITY_AUDIT\n" +
            "    input: ${pipeline.input}\n" +
            "    context: |\n" +
            "      [코드 리뷰 결과]\n" +
            "      ${review.output}\n";
}
