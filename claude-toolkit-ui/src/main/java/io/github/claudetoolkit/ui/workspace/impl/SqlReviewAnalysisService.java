package io.github.claudetoolkit.ui.workspace.impl;

import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.stereotype.Service;

@Service
public class SqlReviewAnalysisService implements AnalysisService {

    @Override
    public AnalysisType getType() { return AnalysisType.SQL_REVIEW; }

    @Override
    public String buildSystemPrompt(WorkspaceRequest request) {
        return "당신은 Oracle SQL 전문가입니다.\n"
             + "SQL 코드를 분석하여 성능 문제, 안티패턴, 개선 방안을 다음 형식으로 출력하세요:\n\n"
             + "## 리뷰 결과\n[각 항목 — [SEVERITY: HIGH/MEDIUM/LOW]]\n\n"
             + "## 인덱스 최적화 제안\n[CREATE INDEX 구문 포함]\n\n"
             + "## 개선된 SQL\n```sql\n[최적화된 SQL]\n```\n\n"
             + "응답은 한국어로 작성하세요.";
    }

    @Override
    public String buildUserMessage(WorkspaceRequest request) {
        return "다음 SQL을 분석해주세요:\n\n```sql\n" + request.getCode() + "\n```";
    }
}
