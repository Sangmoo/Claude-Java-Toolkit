package io.github.claudetoolkit.ui.workspace;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 모든 {@link AnalysisService} 구현체를 유형별로 관리하는 레지스트리.
 *
 * <p>Spring이 {@code List<AnalysisService>}를 자동 주입합니다.
 * 커스텀 서비스를 {@code @Service}로 등록하면 자동으로 포함됩니다.
 */
@Component
public class AnalysisServiceRegistry {

    private final Map<AnalysisType, AnalysisService> registry =
            new LinkedHashMap<AnalysisType, AnalysisService>();

    public AnalysisServiceRegistry(List<AnalysisService> services) {
        for (AnalysisService svc : services) {
            registry.put(svc.getType(), svc);
        }
    }

    /**
     * 유형에 해당하는 서비스를 반환합니다.
     *
     * @param type 분석 유형
     * @return 서비스 구현체, 없으면 null
     */
    public AnalysisService find(AnalysisType type) {
        return registry.get(type);
    }

    /**
     * 특정 언어를 지원하는 모든 서비스를 반환합니다.
     *
     * @param language 언어 식별자 (예: "java", "sql")
     * @return 지원 가능한 서비스 목록
     */
    public List<AnalysisService> findByLanguage(String language) {
        List<AnalysisService> result = new ArrayList<AnalysisService>();
        for (AnalysisService svc : registry.values()) {
            if (svc.getType().supports(language)) {
                result.add(svc);
            }
        }
        return result;
    }

    /**
     * 등록된 모든 서비스를 반환합니다.
     */
    public List<AnalysisService> findAll() {
        return new ArrayList<AnalysisService>(registry.values());
    }
}
