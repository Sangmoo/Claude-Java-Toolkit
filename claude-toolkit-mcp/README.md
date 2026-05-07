# @claude-toolkit/mcp-server

> Claude Java Toolkit 의 MCP (Model Context Protocol) 서버. Claude Code / Cursor / Cline 같은 AI agent 가 IDE 안에서 직접 도구의 12+ 분석 기능을 호출 가능.

## 빠른 시작 (5분)

### 1. 사전 준비
- Claude Java Toolkit 백엔드 구동 중 (예: `http://localhost:8027`)
- ADMIN 계정으로 `/admin/api-keys` 접속 → "새 키 발급" → plaintext 복사
- Node.js 18+

### 2. Claude Code 통합
`~/.config/claude/mcp_settings.json` (macOS/Linux) 또는 `%APPDATA%\Claude\mcp_settings.json` (Windows):

```json
{
  "mcpServers": {
    "claude-toolkit": {
      "command": "npx",
      "args": ["-y", "@claude-toolkit/mcp-server"],
      "env": {
        "CTK_URL": "http://localhost:8027",
        "CTK_API_KEY": "ctk_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
        "CTK_DB_PROFILE_ID": "1"
      }
    }
  }
}
```

Claude Code 재시작 → 12개 tool 자동 발견.

### 3. 사용
Claude Code 안에서:
> "이 SQL 리뷰해줘: `SELECT * FROM T_ORDER WHERE STATUS='Y'`"

Claude 가 자동으로 `mcp__claude-toolkit__sql_review` tool 호출 → 결과 markdown 으로 IDE 안에 표시.

## 환경변수

| 변수 | 필수 | 설명 |
|------|------|------|
| `CTK_URL` | ✅ | 도구 base URL (예: `http://localhost:8027`) |
| `CTK_API_KEY` | ✅ | `/admin/api-keys` 발급 키 |
| `CTK_DB_PROFILE_ID` | - | Live DB 프로필 ID — 설정 시 SQL 분석에 자동 첨부 |

## 노출되는 Tools (12개)

| Tool | 설명 | Live DB |
|------|------|---------|
| `sql_review` | SQL 리뷰 (성능/보안/가독성) | ✓ |
| `explain_plan` | 실행계획 분석 | ✓ |
| `index_advisor` | 인덱스 추천 | ✓ |
| `sql_translate` | DB 간 SQL 번역 | - |
| `sql_batch` | 배치 SQL 일괄 분석 | ✓ |
| `erd_analysis` | ERD 분석 + Mermaid | ✓ |
| `code_review` | Java 코드 리뷰 | - |
| `doc_gen` | 기술 문서 생성 | - |
| `complexity` | 복잡도 분석 | - |
| `commit_msg` | 커밋 메시지 생성 | - |
| `regex_gen` | 정규식 생성 | - |
| `test_gen` | JUnit 5 테스트 생성 | - |

## 트러블슈팅

자세한 가이드: [docs/mcp-setup.md](https://github.com/Sangmoo/Claude-Java-Toolkit/blob/master/docs/mcp-setup.md)

## License
MIT
