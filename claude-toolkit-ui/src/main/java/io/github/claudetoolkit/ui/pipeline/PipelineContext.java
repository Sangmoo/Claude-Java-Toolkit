package io.github.claudetoolkit.ui.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 파이프라인 실행 중 단계 간 변수 저장소 (v2.9.5).
 *
 * <p>지원 변수:
 * <ul>
 *   <li>{@code ${pipeline.input}} — 원본 입력</li>
 *   <li>{@code ${pipeline.language}} — 입력 언어</li>
 *   <li>{@code ${stepId.output}} — 이전 단계 결과</li>
 *   <li>{@code ${stepId.executed}} — 실행 여부</li>
 *   <li>{@code ${stepId.status}} — COMPLETED / SKIPPED / FAILED</li>
 * </ul>
 */
public class PipelineContext {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    private final Map<String, Object> variables = new LinkedHashMap<String, Object>();

    public void set(String key, Object value) {
        variables.put(key, value);
    }

    public Object get(String key) {
        return variables.get(key);
    }

    public Map<String, Object> getAll() {
        return new LinkedHashMap<String, Object>(variables);
    }

    /**
     * 문자열 내 {@code ${var}} 플레이스홀더를 변수 값으로 치환합니다.
     */
    public String resolve(String template) {
        if (template == null || template.isEmpty()) return template;
        if (template.indexOf("${") < 0) return template;

        Matcher m = VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).trim();
            Object val = variables.get(key);
            String replacement = val != null ? val.toString() : "";
            // Matcher.quoteReplacement으로 $와 \ 이스케이프
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
