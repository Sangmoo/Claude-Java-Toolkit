package io.github.claudetoolkit.ui.workspace.impl;

import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.stereotype.Service;

@Service
public class RefactorAnalysisService implements AnalysisService {

    @Override
    public AnalysisType getType() { return AnalysisType.REFACTOR; }

    @Override
    public String buildSystemPrompt(WorkspaceRequest request) {
        String lang = request.getLanguage() != null ? request.getLanguage() : "java";
        return "당신은 " + lang.toUpperCase() + " 리팩터링 전문가입니다.\n"
             + "주어진 코드의 문제점을 분석하고 다음 형식으로 출력하세요:\n\n"
             + "## 현재 코드 문제점\n[문제 항목 목록 — [SEVERITY: HIGH/MEDIUM/LOW]]\n\n"
             + "## 리팩터링 제안\n[적용 가능한 디자인 패턴·원칙 목록]\n\n"
             + "## 개선된 코드\n```" + lang + "\n[개선된 전체 코드]\n```\n\n"
             + "## 설명\n[각 변경 사항과 그 이유]\n\n"
             + "응답은 한국어로 작성하세요.";
    }

    @Override
    public String buildUserMessage(WorkspaceRequest request) {
        String lang = request.getLanguage() != null ? request.getLanguage() : "java";
        return "다음 코드를 리팩터링해주세요:\n\n"
             + "```" + lang + "\n" + request.getCode() + "\n```";
    }
}
