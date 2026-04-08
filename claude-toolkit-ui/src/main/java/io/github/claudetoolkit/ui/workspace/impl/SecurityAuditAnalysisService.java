package io.github.claudetoolkit.ui.workspace.impl;

import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.stereotype.Service;

@Service
public class SecurityAuditAnalysisService implements AnalysisService {

    @Override
    public AnalysisType getType() { return AnalysisType.SECURITY_AUDIT; }

    @Override
    public String buildSystemPrompt(WorkspaceRequest request) {
        String lang = request.getLanguage();
        String langName = resolveLanguageName(lang);
        return "당신은 " + langName + " 보안 코드 리뷰 전문가입니다.\n"
             + "OWASP Top 10 기준으로 보안 취약점을 분석하고 "
             + "## 보안 취약점, ## 위험도, ## 해결 방법 형식으로 출력하세요.\n"
             + "각 항목은 [SEVERITY: HIGH/MEDIUM/LOW]로 표시하세요.\n"
             + "응답은 한국어로 작성하세요.";
    }

    @Override
    public String buildUserMessage(WorkspaceRequest request) {
        String lang = request.getLanguage() != null ? request.getLanguage() : "java";
        return "다음 " + resolveLanguageName(lang) + " 코드의 보안 취약점을 분석해주세요:\n\n"
             + "```" + lang + "\n" + request.getCode() + "\n```";
    }

    private String resolveLanguageName(String lang) {
        if ("kotlin".equalsIgnoreCase(lang))     return "Kotlin";
        if ("python".equalsIgnoreCase(lang))     return "Python";
        if ("javascript".equalsIgnoreCase(lang)) return "JavaScript";
        if ("typescript".equalsIgnoreCase(lang)) return "TypeScript";
        return "Java/Spring";
    }
}
