package io.github.claudetoolkit.ui.workspace.impl;

import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.stereotype.Service;

@Service
public class JavadocAnalysisService implements AnalysisService {

    @Override
    public AnalysisType getType() { return AnalysisType.JAVADOC; }

    @Override
    public String buildSystemPrompt(WorkspaceRequest request) {
        String lang = request.getLanguage();
        String commentStyle = resolveCommentStyle(lang);
        return "당신은 " + lang + " 전문가입니다.\n"
             + "주어진 소스 코드에 " + commentStyle + " 문서 주석을 추가하여 완전한 코드를 반환하세요.\n"
             + "모든 public 클래스, 메서드, 필드에 한국어 주석을 작성하세요.\n"
             + "원본 코드 구조와 로직은 변경하지 마세요.";
    }

    @Override
    public String buildUserMessage(WorkspaceRequest request) {
        String lang = request.getLanguage() != null ? request.getLanguage() : "java";
        return "다음 코드에 문서 주석을 추가해주세요:\n\n"
             + "```" + lang + "\n" + request.getCode() + "\n```";
    }

    private String resolveCommentStyle(String lang) {
        if ("python".equalsIgnoreCase(lang))                           return "Google Style docstring";
        if ("javascript".equalsIgnoreCase(lang)
         || "typescript".equalsIgnoreCase(lang))                       return "JSDoc";
        if ("kotlin".equalsIgnoreCase(lang))                           return "KDoc";
        return "Javadoc";
    }
}
