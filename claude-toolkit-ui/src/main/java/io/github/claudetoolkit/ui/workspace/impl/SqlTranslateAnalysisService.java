package io.github.claudetoolkit.ui.workspace.impl;

import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.stereotype.Service;

/**
 * SQL DB 번역 분석 서비스.
 * <p>{@link WorkspaceRequest#getSourceDb()} → {@link WorkspaceRequest#getTargetDb()} 방향으로 번역합니다.
 * sourceDb / targetDb 가 null 이면 Oracle → MySQL 기본값 사용.
 */
@Service
public class SqlTranslateAnalysisService implements AnalysisService {

    @Override
    public AnalysisType getType() { return AnalysisType.SQL_TRANSLATE; }

    @Override
    public String buildSystemPrompt(WorkspaceRequest request) {
        String src = dbName(request.getSourceDb(), "Oracle");
        String tgt = dbName(request.getTargetDb(), "MySQL");
        return "당신은 SQL 데이터베이스 마이그레이션 전문가입니다.\n"
             + src + " SQL을 " + tgt + " 호환 SQL로 정확하게 변환하세요.\n\n"
             + "## 변환된 SQL\n```sql\n[변환된 SQL]\n```\n\n"
             + "## 주요 변경 사항\n[변경된 문법·함수·데이터타입 항목 목록]\n\n"
             + "## 주의 사항\n[호환성 이슈, 수동 확인 필요 항목]\n\n"
             + "응답은 한국어로 작성하세요.";
    }

    @Override
    public String buildUserMessage(WorkspaceRequest request) {
        String src = dbName(request.getSourceDb(), "Oracle");
        String tgt = dbName(request.getTargetDb(), "MySQL");
        return "다음 " + src + " SQL을 " + tgt + " 호환으로 변환해주세요:\n\n"
             + "```sql\n" + request.getCode() + "\n```";
    }

    private String dbName(String db, String fallback) {
        if (db == null || db.trim().isEmpty()) return fallback;
        switch (db.toLowerCase()) {
            case "oracle":     return "Oracle";
            case "mysql":      return "MySQL";
            case "postgresql": return "PostgreSQL";
            case "mssql":      return "MS SQL Server";
            default:           return db;
        }
    }
}
