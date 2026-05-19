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

## 5-2. 사내망 공유 모드 (Streamable HTTP)

> **언제 쓰나?** MCP 서버를 한 PC(또는 사내 서버)에 1대만 띄우고, 같은 사내망의 여러 개발자 PC가 그 서버를 공유. 각 사용자는 자기 키로 접속 — audit log/rate limit 이 사람별로 분리됨.

### 아키텍처

```
[개발자 A 의 Claude Desktop]                      [MCP 호스트 (사내 1대)]
  npx mcp-remote ───stdio───┐                     ┌─ MCP HTTP :8028 ─┐
                            ├─ HTTP + X-Api-Key ─→│  per-request      │
[개발자 B 의 Cursor]        │   (각자 다른 키)    │  ToolkitClient    │
  npx mcp-remote ───stdio───┘                     │  ↓                │
                                                  │  POST /api/v1/    │
                                                  │  analyze          │
                                                  └────┬──────────────┘
                                                       ↓
                                              [도구 backend :8027]
                                              (PlatformAuthFilter 가
                                               각 사용자 키별 검증)
```

핵심: **MCP 서버는 X-Api-Key 헤더를 그대로 backend 에 전달** (per-request 패스스루). MCP 서버 자체는 키를 저장하지 않음 — backend 의 기존 보안 모델이 그대로 살아남음.

### Step A — MCP 서버 호스트 설정 (1대)

```bash
cd claude-toolkit-mcp
npm install
npm run build

# 환경변수
export CTK_URL=http://localhost:8027        # 같은 호스트의 backend
export MCP_TRANSPORT=http
export MCP_HTTP_HOST=0.0.0.0                # 사내망 노출 (default)
export MCP_HTTP_PORT=8028                   # default

npm run start:http
# → [MCP] http ready — listening on 0.0.0.0:8028 (12 tools, stateless, per-request X-Api-Key)
```

방화벽이 8028 을 사내망에 열어줘야 함. 외부 인터넷에 직접 노출하지 말 것 — VPN/사내망 안에서만 사용.

### Step B — 각 개발자가 자기 키 발급

`/admin/api-keys` 에서 사람별로 발급. 이름은 `홍길동 — Cursor` 처럼 식별 가능하게.

### Step C — 클라이언트 PC 의 mcp_settings.json

플랫폼별 경로:
- macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
- Windows: `%APPDATA%\Claude\claude_desktop_config.json`
- Cursor: `Settings → Features → Model Context Protocol`
- Cline: `VS Code Command Palette → Cline: Add MCP Server`

```json
{
  "mcpServers": {
    "claude-toolkit": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://mcp-host.사내도메인:8028/mcp",
        "--header",
        "X-Api-Key:ctk_live_본인키..."
      ]
    }
  }
}
```

> ⚠️ `--header` 와 키 사이는 콜론(`:`)으로 구분, 공백 없음. mcp-remote 가 stdio↔HTTP 브릿지 역할을 하므로 클라이언트 측 코드 수정 불요.

### Step D — health check
```bash
curl http://mcp-host.사내도메인:8028/healthz
# {"status":"ok","tools":12,"transport":"http"}
```

### 권장 운영 패턴

| 상황 | 권장 |
|------|------|
| 개인 노트북에서 단독 사용 | stdio (§2) — 가장 단순 |
| 팀 5명 이상 공유 (개발 PC 1대 기동) | mvn spring-boot:run + auto-launcher (§5-3) |
| 사내 서버 + Docker 배포 | docker-compose 분리 컨테이너 (§5-4) |
| 외부 인터넷 노출 필요 | 권장 안 함. 부득이하면 reverse proxy (nginx) + TLS + 사내망 ACL |

### 5-3. 자동 기동 (개발 환경 — mvn spring-boot:run)

`mvn spring-boot:run` 시 Spring Boot 의 `McpServerLauncher` 가 8028을 자동 spawn합니다. 별도 창에서 `npm run start:http` 를 띄울 필요 없음.

```yaml
# application.yml — 기본값 (변경 불필요)
toolkit:
  mcp:
    auto-start: true              # ${TOOLKIT_MCP_AUTOSTART:true}
    project-root: ../claude-toolkit-mcp
    host: 0.0.0.0
    port: 8028
    node: node                    # PATH 의 node 또는 절대경로
```

기동 후 Spring Boot 로그에 다음이 보이면 정상:
```
[MCP] 자동 기동 성공 — pid=..., listening on 0.0.0.0:8028, backend=http://localhost:8027
[MCP] [MCP] http ready — listening on 0.0.0.0:8028 (12 tools, ...)
```

**비활성화**: `TOOLKIT_MCP_AUTOSTART=false`

**`node` PATH 안 잡힐 때**: `TOOLKIT_MCP_NODE="C:/Program Files/nodejs/node.exe"`

**dist 가 없을 때**: launcher 가 `dist/index.js 미존재` 경고 후 스킵. `cd claude-toolkit-mcp && npm install && npm run build` 한 번 실행 후 재기동.

### 5-4. Docker 배포 (운영 — docker-compose)

Docker 환경에서는 **MCP 를 별도 컨테이너로 분리**합니다 (이미 `docker-compose.yml` 에 추가됨). claude-toolkit 컨테이너의 launcher 는 자동 OFF (`TOOLKIT_MCP_AUTOSTART=false`).

```bash
# 빌드 + 기동 — claude-toolkit-mcp 가 함께 올라옴
docker-compose build --no-cache claude-toolkit claude-toolkit-mcp
docker-compose up -d claude-toolkit claude-toolkit-mcp

# 모니터링 프로필도 함께
docker-compose --profile monitoring up -d
```

| 컨테이너 | 호스트 포트 | 컨테이너 내부 |
|---------|-----------|-------------|
| `claude-toolkit` | 8027 | 8027 (Spring Boot) |
| `claude-toolkit-mcp` | 8028 | 8028 (Streamable HTTP) |

컨테이너 간 통신: docker network DNS 로 `claude-toolkit-mcp → claude-toolkit:8027`.

**검증**:
```bash
# 호스트에서
curl http://localhost:8028/healthz
# {"status":"ok","tools":12,"transport":"http"}

# 컨테이너 로그
docker logs -f claude-toolkit-mcp
```

**MCP 만 재시작**:
```bash
docker-compose restart claude-toolkit-mcp
```

**MCP 만 재빌드**:
```bash
docker-compose build claude-toolkit-mcp
docker-compose up -d claude-toolkit-mcp
```

### 트러블슈팅 (HTTP 모드)

| 증상 | 점검 |
|------|------|
| `npx mcp-remote` 가 connection refused | 호스트 IP/포트, 방화벽 8028 inbound, MCP 서버가 `0.0.0.0` 으로 listen 중인지 |
| 401 X-Api-Key 헤더 필요 | mcp_settings.json 의 `--header` 형식 (`X-Api-Key:키`, 공백 없음) |
| 401 Invalid API key | backend 에서 키 회수/만료 — `/admin/api-keys` |
| 호출은 되는데 결과가 비어있음 | MCP 서버 로그(`[MCP] http 처리 실패: ...`) 확인 |

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
