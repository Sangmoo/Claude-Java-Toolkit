package io.github.claudetoolkit.ui.prompt;

import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 커스텀 시스템 프롬프트 서비스.
 *
 * <p>분석 실행 시 {@link #resolveSystemPrompt(AnalysisService, WorkspaceRequest)}를 호출하면
 * DB에 저장된 커스텀 프롬프트가 있으면 그것을, 없으면 기본 내장 프롬프트를 반환합니다.
 */
@Service
public class PromptService {

    private final CustomPromptRepository repository;

    public PromptService(CustomPromptRepository repository) {
        this.repository = repository;
    }

    /**
     * 커스텀 프롬프트가 활성화된 경우 그것을, 없으면 기본 프롬프트를 반환합니다.
     *
     * @param service 분석 서비스 구현체
     * @param request 분석 요청
     * @return 최종 시스템 프롬프트
     */
    public String resolveSystemPrompt(AnalysisService service, WorkspaceRequest request) {
        Optional<CustomPrompt> custom =
                repository.findByAnalysisTypeAndIsActiveTrue(service.getType().name());
        if (custom.isPresent()) {
            return custom.get().getSystemPrompt();
        }
        return service.buildSystemPrompt(request);
    }

    /**
     * 분석 유형 이름(String)으로 직접 활성 프롬프트를 조회합니다.
     * 없으면 null 반환.
     */
    public String findActivePrompt(String analysisTypeName) {
        Optional<CustomPrompt> custom =
                repository.findByAnalysisTypeAndIsActiveTrue(analysisTypeName);
        return custom.isPresent() ? custom.get().getSystemPrompt() : null;
    }

    /**
     * 프롬프트를 저장하고 해당 유형의 기존 활성 프롬프트를 비활성화한 뒤 새 것을 활성화합니다.
     */
    @Transactional
    public CustomPrompt saveAndActivate(String analysisType, String promptName, String systemPrompt) {
        repository.deactivateAllByType(analysisType);

        CustomPrompt cp = new CustomPrompt();
        cp.setAnalysisType(analysisType);
        cp.setPromptName(promptName != null && !promptName.trim().isEmpty()
                ? promptName.trim() : "커스텀 프롬프트");
        cp.setSystemPrompt(systemPrompt);
        cp.setActive(true);
        return repository.save(cp);
    }

    /**
     * 특정 프롬프트를 활성화합니다 (같은 유형의 나머지는 비활성화).
     */
    @Transactional
    public void activate(Long id) {
        CustomPrompt cp = repository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    public RuntimeException get() {
                        return new IllegalArgumentException("프롬프트를 찾을 수 없습니다: " + id);
                    }
                });
        repository.deactivateAllByType(cp.getAnalysisType());
        cp.setActive(true);
        repository.save(cp);
    }

    /**
     * 특정 프롬프트를 비활성화합니다 (기본 프롬프트로 복귀).
     */
    @Transactional
    public void deactivate(Long id) {
        CustomPrompt cp = repository.findById(id)
                .orElseThrow(new java.util.function.Supplier<RuntimeException>() {
                    public RuntimeException get() {
                        return new IllegalArgumentException("프롬프트를 찾을 수 없습니다: " + id);
                    }
                });
        cp.setActive(false);
        repository.save(cp);
    }

    /**
     * 해당 유형의 모든 커스텀 프롬프트를 비활성화합니다 (기본 내장 프롬프트 복원).
     */
    @Transactional
    public void resetToDefault(String analysisType) {
        repository.deactivateAllByType(analysisType);
    }

    /**
     * 특정 프롬프트를 삭제합니다.
     */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
