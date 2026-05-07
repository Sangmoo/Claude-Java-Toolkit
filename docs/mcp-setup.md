# MCP Server 운영 가이드 (#M3)

> v4.7.x 부터 도입된 **Claude Toolkit MCP Server** — Claude Code / Cursor / Cline 등 AI agent 가 IDE 안에서 도구의 12+ 분석 기능을 직접 호출.

## 1. 개요

### 정체성 변화: "도구 → 플랫폼"

```
Before: 도구 사용 = 우리 웹 페이지로 와야 함
After:  도구 = 사내 모든 시스템에서 호출 가능한 플랫폼
        ├─ 웹 페이지 (사용자가 직접)
        ├─ MCP Server (IDE 안에서)
        ├─ CLI (CI/CD pipeline)
        └─ REST API (사내 자동화)
```

### 통합 가능한 client

| Client | 지원 |
|--------|------|
| **Claude Code** (Anthropic 공식) | ✅ stdio MCP |
| **Cursor** | ✅ MCP |
| **Cline** (VS Code) | ✅ MCP |
| **Continue.dev** | ✅ MCP |
| **자체 AI Agent** | ✅ stdio + MCP SDK |

---

## 2. 빠른 시작 — Claude Code (5분)

### Step 1: 도구 백엔드 구동
```bash
cd claude-toolkit-ui
mvn spring-boot:run
# → http://localhost:8027
```

### Step 2: API Key 발급
1. ADMIN 으로 로그인 → 사이드바 **[관리] → API Key 관리** (`/admin/api-keys`)
2. **+ 새 키 발급** 클릭
3. 입력:
   - 이름: `Claude Code MCP` (식별용)
   - Role: `READ_ONLY` (권장 — 분석만 가능)
   - 분당 호출 한도: 60
   - TTL: 90일
4. **발급** → plaintext 모달이 뜸:
   ```
   ctk_live_a1b2c3d4e5f6g7h8...
   ```
   → **[복사]** 버튼 클릭. **이 키는 다시 표시되지 않음**.

### Step 3: Claude Code 통합
`~/.config/claude/mcp_settings.json` (macOS/Linux) 또는 `%APPDATA%\Claude\mcp_settings.json` (Windows):

```json
{
  "mcpServers": {
    "claude-toolkit": {
      "command": "npx",
      "args": ["-y", "@claude-toolkit/mcp-server"],
      "env": {
        "CTK_URL": "http://localhost:8027",
        "CTK_API_KEY": "ctk_live_a1b2c3d4e5f6g7h8...",
        "CTK_DB_PROFILE_ID": "1"
      }
    }
  }
}
```

> ⚠️ **CTK_DB_PROFILE_ID** 는 옵션 — Live DB 활성 프로필 ID 입력 시 SQL 분석에 자동 컨텍스트 첨부.

### Step 4: Claude Code 재시작 + 사용
Claude Code 안에서:
```
> 이 SQL 리뷰해줘:
  SELECT * FROM T_ORDER WHERE STATUS='Y' AND ORDER_DATE >= SYSDATE - 30
```

Claude 가 자동으로 `claude-toolkit__sql_review` tool 호출 → 결과를 IDE 안에 표시.

---

## 3. Cursor / Cline 통합

### Cursor
Settings → Features → Model Context Protocol → Add MCP Server:
```json
{
  "name": "claude-toolkit",
  "command": "npx",
  "args": ["-y", "@claude-toolkit/mcp-server"],
  "env": {
    "CTK_URL": "http://localhost:8027",
    "CTK_API_KEY": "ctk_live_..."
  }
}
```

### Cline (VS Code)
VS Code Command Palette → `Cline: Add MCP Server` → 위와 동일 형식 입력.

---

## 4. 노출되는 12개 Tools

### SQL 분석 (Live DB 통합 — 6개)

| Tool | Args | 설명 |
|------|------|------|
| `sql_review` | `input`, `reviewType?`, `dbProfileId?` | 성능/보안/가독성 이슈 |
| `explain_plan` | `input`, `explainPlan?`, `dbProfileId?` | 실행계획 분석 — Live DB 시 자동 EXPLAIN |
| `index_advisor` | `input`, `dbProfileId?` | 인덱스 추천 DDL |
| `sql_translate` | `input`, `sourceDb`, `targetDb` | DB 간 번역 (Oracle/MySQL/PG/MSSQL) |
| `sql_batch` | `input`, `dbProfileId?` | 여러 SQL 일괄 분석 |
| `erd_analysis` | `input`, `dbProfileId?` | ERD + Mermaid 다이어그램 |

### Java / Code 분석 (6개)

| Tool | Args | 설명 |
|------|------|------|
| `code_review` | `input` | Java 코드 리뷰 |
| `doc_gen` | `input` | 기술 문서 생성 |
| `complexity` | `input` | 복잡도 점수 |
| `commit_msg` | `input` | git diff → conventional commits |
| `regex_gen` | `input`, `language?` | 자연어 → 정규식 |
| `test_gen` | `input` | Java → JUnit 5 |

---

## 5. REST API 직접 호출 (CLI / curl 등)

MCP server 외에도 같은 backend 를 *REST API 로 직접* 호출 가능 — 인증은 같은 X-Api-Key 사용.

### Curl 예시
```bash
curl -X POST http://localhost:8027/api/v1/analyze \
  -H "X-Api-Key: ctk_live_a1b2c3d4..." \
  -H "Content-Type: application/json" \
  -d '{
    "feature": "sql_review",
    "input": "SELECT * FROM T_ORDER WHERE STATUS='Y'",
    "sourceType": "review",
    "dbProfileId": 1
  }'
```

### Response
```json
{
  "success": true,
  "feature": "sql_review",
  "result": "## 분석 결과\n\n### 1. 성능 이슈\n- ...",
  "cached": false,
  "tokens": { "input": 1200, "output": 850 },
  "costUsd": 0.0042,
  "elapsedMs": 4231,
  "model": "claude-sonnet-4-20250514"
}
```

### CI/CD pipeline 통합 (GitHub Actions 예시)
```yaml
- name: Claude SQL Review
  run: |
    curl -X POST $CTK_URL/api/v1/analyze \
      -H "X-Api-Key: $CTK_API_KEY" \
      -H "Content-Type: application/json" \
      -d "{\"feature\":\"sql_review\",\"input\":\"$(cat changed.sql)\"}" \
      | jq -r '.result'
  env:
    CTK_URL: https://ctk.company.com
    CTK_API_KEY: ${{ secrets.CTK_API_KEY }}
```

---

## 6. 보안 모델

```
[1] 도구 backend
    ├─ 세션 인증 (브라우저 사용자) → 그대로 통과
    └─ X-Api-Key 헤더 (외부 client) → PlatformAuthFilter 검증

[2] PlatformAuthFilter
    ├─ DB 의 SHA-256 hash 와 timing-safe 비교
    ├─ 만료/회수/usable 검증
    ├─ 분당 호출 한도 (sliding window)
    └─ 통과 시 SecurityContext set (key.createdBy 으로 인증)

[3] 통제 권한
    ├─ ADMIN 만 키 발급/회수 가능
    ├─ 평문 키는 발급 직후 1회만 노출 — DB 에는 해시만
    └─ 회수 시 즉시 401 (모든 client 차단)
```

### 위험 시나리오 + 대응

| 위험 | 대응 |
|------|------|
| 키 노출 의심 | `/admin/api-keys` 즉시 회수 → 401 |
| 한 키가 비정상 호출 폭증 | 분당 60회 한도 초과 시 429 자동 차단 |
| 도구 backend 침해 | `toolkit.platform.enabled=false` (kill switch) |
| 평문 키 DB 노출 | DB 에는 SHA-256 해시만. 평문은 발급 직후 1회만 메모리 |

---

## 7. 트러블슈팅

### Q1. Claude Code 가 tool 을 못 찾음
- `~/.config/claude/mcp_settings.json` 파일 존재 + JSON 문법 정상 확인
- Claude Code 완전 재시작
- npx 가 패키지 다운로드 못 했을 가능성 — 콘솔에 *"@claude-toolkit/mcp-server is not found"* 에러? 그러면:
  ```bash
  npx -y @claude-toolkit/mcp-server  # 직접 실행 테스트
  ```

### Q2. *"환경변수 CTK_URL 미설정"* 에러
- `mcp_settings.json` 의 env 객체 안에 정확히 입력
- URL 끝에 `/` 없이 — `http://localhost:8027` (O), `http://localhost:8027/` (X)

### Q3. *"401 Invalid API key"*
- 키 만료/회수 — `/admin/api-keys` 에서 상태 확인
- 환경변수 오타 — Bash 와 mcp_settings.json 둘 다 검토

### Q4. *"429 분당 호출 한도 초과"*
- 한 IDE 가 분당 60회 초과 — 일반적으로 일어나지 않음, 자동화 스크립트 의심
- ADMIN 페이지에서 *해당 키의 rate limit* 증가 (예: 120/min)

### Q5. SQL 분석 결과에 *"실시간 DB 메타"* 가 안 나옴
- `CTK_DB_PROFILE_ID` 환경변수 설정 확인
- `/admin/health` 의 Live DB 카드가 ✓ ENABLED 인지 확인 (관련: [Live DB 가이드](./livedb-operations.md))
- 해당 프로필이 활성화됐는지 (`/db-profiles` 의 🔌 Live: ON)

### Q6. 응답이 너무 느림
- Claude API latency — 5~30초가 일반적
- Live DB 통합 시 EXPLAIN 추가 호출 — 1~3초 추가
- timeout 으로 끊기면 backend 의 `claude.api.timeout` 설정 확인

---

## 8. 운영 모니터링

### `/admin/api-keys` 페이지
- 발급된 모든 키 목록
- 키별 마지막 사용 시각 + 호출 카운트
- 의심 키 즉시 회수

### `audit_log` 추적
모든 X-Api-Key 호출은 audit_log 에 기록 (Spring Security 의 audit 통합):
```sql
SELECT * FROM audit_log
 WHERE endpoint LIKE '/api/v1/analyze%'
 ORDER BY created_at DESC;
```

### 비용 모니터링
- `/usage` 에서 사용자별 (= 키 발급자별) 토큰 사용량
- API 응답의 `costUsd` 가 client 측에서 비용 인지 가능

---

## 9. 비활성화 (Kill Switch)

운영 사고 시:
```bash
# 즉시 비활성 (재시작 필요)
export TOOLKIT_PLATFORM_ENABLED=false  # (옵션) — 추후 보강 가능
```

또는 ADMIN 페이지에서 *모든 키 회수* — 같은 효과.
