package io.github.claudetoolkit.ui.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 파이프라인 조건식 평가기 (v2.9.5).
 *
 * <p>Spring SpEL 기반으로 {@code condition} 표현식을 평가합니다.
 * 변수는 {@code ${var}} 형태로 작성하며, 내부적으로 {@link PipelineContext#resolve(String)}로
 * 실제 값으로 치환한 뒤 SpEL로 평가합니다.
 *
 * <p>{@link SimpleEvaluationContext}를 사용하여 메서드 호출/생성자/리플렉션을 제한합니다.
 *
 * <p>사용 예:
 * <pre>
 *   condition: "${review.output}.length() > 100"
 *   condition: "${refactor.executed}"
 *   condition: "'${review.output}'.contains('최적화')"
 * </pre>
 */
@Component
public class PipelineExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PipelineExpressionEvaluator.class);

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 조건식을 평가하여 true/false 반환.
     * 에러 발생 시 false 반환 (실행 차단).
     */
    public boolean evaluate(String expression, PipelineContext ctx) {
        if (expression == null || expression.trim().isEmpty()) return true;
        try {
            String resolved = ctx.resolve(expression);
            // 읽기 전용 컨텍스트 — 메서드 호출 일부만 허용 (문자열 contains, length 등)
            SimpleEvaluationContext evalCtx = SimpleEvaluationContext.forReadOnlyDataBinding()
                    .withInstanceMethods()
                    .build();
            Object result = parser.parseExpression(resolved).getValue(evalCtx);
            if (result instanceof Boolean) return (Boolean) result;
            if (result instanceof Number)  return ((Number) result).doubleValue() != 0.0;
            if (result instanceof String)  return !((String) result).isEmpty();
            return result != null;
        } catch (Exception e) {
            log.warn("[PipelineExpression] 조건 평가 실패: expression='{}', error={}", expression, e.getMessage());
            return false;
        }
    }
}
