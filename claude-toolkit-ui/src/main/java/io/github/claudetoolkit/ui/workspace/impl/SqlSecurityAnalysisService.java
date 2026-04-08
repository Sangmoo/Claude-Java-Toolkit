package io.github.claudetoolkit.ui.workspace.impl;

import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.stereotype.Service;

@Service
public class SqlSecurityAnalysisService implements AnalysisService {

    @Override
    public AnalysisType getType() { return AnalysisType.SQL_SECURITY; }

    @Override
    public String buildSystemPrompt(WorkspaceRequest request) {
        return "당신은 데이터베이스 보안 전문가입니다.\n"
             + "SQL 코드에서 SQL 인젝션, 권한 문제, 민감 데이터 노출 등 보안 취약점을 다음 형식으로 출력하세요:\n\n"
             + "## 보안 감사 결과\n[각 항목 — [SEVERITY: HIGH/MEDIUM/LOW]]\n\n"
             + "## 위험 SQL 패턴\n[동적 SQL, 하드코딩 값, 권한 남용 등]\n\n"
             + "## 수정 권고 사항\n[구체적인 수정 방법]\n\n"
             + "응답은 한국어로 작성하세요.";
    }

    @Override
    public String buildUserMessage(WorkspaceRequest request) {
        return "다음 SQL의 보안 취약점을 분석해주세요:\n\n```sql\n" + request.getCode() + "\n```";
    }
}
