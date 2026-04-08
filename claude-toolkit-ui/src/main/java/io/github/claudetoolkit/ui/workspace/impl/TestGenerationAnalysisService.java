package io.github.claudetoolkit.ui.workspace.impl;

import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.stereotype.Service;

@Service
public class TestGenerationAnalysisService implements AnalysisService {

    @Override
    public AnalysisType getType() { return AnalysisType.TEST_GENERATION; }

    @Override
    public String buildSystemPrompt(WorkspaceRequest request) {
        String lang = request.getLanguage();
        String framework = resolveTestFramework(lang);
        return "당신은 " + framework + " 테스트 전문가입니다.\n"
             + "주어진 코드에 대한 완전한 테스트 코드를 생성하세요.\n"
             + "경계값, 예외 케이스, 정상 케이스를 모두 포함해야 합니다.\n"
             + "응답은 한국어 주석과 함께 작성하세요.";
    }

    @Override
    public String buildUserMessage(WorkspaceRequest request) {
        String lang = request.getLanguage() != null ? request.getLanguage() : "java";
        return "다음 코드에 대한 테스트 코드를 생성해주세요:\n\n"
             + "```" + lang + "\n" + request.getCode() + "\n```";
    }

    private String resolveTestFramework(String lang) {
        if ("python".equalsIgnoreCase(lang))                           return "pytest";
        if ("javascript".equalsIgnoreCase(lang))                       return "Jest";
        if ("typescript".equalsIgnoreCase(lang))                       return "Jest/TypeScript";
        if ("kotlin".equalsIgnoreCase(lang))                           return "JUnit 5 + Kotlin";
        return "JUnit 5";
    }
}
