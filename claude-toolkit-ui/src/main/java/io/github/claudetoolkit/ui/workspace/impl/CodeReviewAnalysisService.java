package io.github.claudetoolkit.ui.workspace.impl;

import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.stereotype.Service;

@Service
public class CodeReviewAnalysisService implements AnalysisService {

    @Override
    public AnalysisType getType() { return AnalysisType.CODE_REVIEW; }

    @Override
    public String buildSystemPrompt(WorkspaceRequest request) {
        String lang = request.getLanguage();
        String langName = resolveLanguageName(lang);
        return "당신은 " + langName + " 코드 리뷰 전문가입니다.\n"
             + "## 코드 품질, ## 버그 위험, ## 개선 제안, ## 우수한 점 형식으로 리뷰하세요.\n"
             + "각 항목은 [SEVERITY: HIGH/MEDIUM/LOW]로 표시하세요.\n"
             + "응답은 한국어로 작성하세요.";
    }

    @Override
    public String buildUserMessage(WorkspaceRequest request) {
        String lang = request.getLanguage() != null ? request.getLanguage() : "java";
        return "다음 " + resolveLanguageName(lang) + " 코드를 리뷰해주세요:\n\n"
             + "```" + lang + "\n" + request.getCode() + "\n```";
    }

    private String resolveLanguageName(String lang) {
        if ("kotlin".equalsIgnoreCase(lang))     return "Kotlin";
        if ("python".equalsIgnoreCase(lang))     return "Python";
        if ("javascript".equalsIgnoreCase(lang)) return "JavaScript";
        if ("typescript".equalsIgnoreCase(lang)) return "TypeScript";
        return "Java";
    }
}
