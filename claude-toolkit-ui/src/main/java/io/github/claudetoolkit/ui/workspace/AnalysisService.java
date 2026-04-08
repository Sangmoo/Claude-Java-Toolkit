package io.github.claudetoolkit.ui.workspace;

/**
 * 분석 서비스 플러그인 인터페이스.
 *
 * <p>각 구현체는 Spring Bean으로 등록되며 {@link AnalysisServiceRegistry}에 의해
 * 자동 수집됩니다. 커스텀 분석 서비스를 추가하려면 이 인터페이스를 구현하고
 * {@code @Service}로 등록하면 됩니다.
 *
 * <p>프롬프트 커스터마이징이 활성화된 경우 {@link io.github.claudetoolkit.ui.prompt.PromptService}가
 * {@code buildSystemPrompt} 결과를 DB 저장 프롬프트로 교체합니다.
 */
public interface AnalysisService {

    /**
     * 이 서비스가 처리하는 분석 유형을 반환합니다.
     */
    AnalysisType getType();

    /**
     * 주어진 요청에 대한 시스템 프롬프트를 생성합니다.
     *
     * @param request 분석 요청 (코드·언어·분석 유형·프로젝트 컨텍스트 포함)
     * @return Claude에 전달할 시스템 프롬프트 문자열
     */
    String buildSystemPrompt(WorkspaceRequest request);

    /**
     * 주어진 요청에 대한 사용자 메시지를 생성합니다.
     *
     * @param request 분석 요청
     * @return Claude에 전달할 사용자 메시지 문자열
     */
    String buildUserMessage(WorkspaceRequest request);
}
