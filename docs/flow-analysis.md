# 데이터 흐름 분석 (Flow Analysis)

> v4.4.x — 테이블 / SP / SQL_ID / MiPlatform 화면 시작점에서 시스템 전체의 데이터 흐름을
> 자동 추적해 ReactFlow 다이어그램 + LLM narrative 로 시각화.

## 한 줄 요약
"`T_SHOP_INVT_SIDE` 테이블에 데이터가 어떻게 들어가는지" 같은 질문을 받으면, 코드/DB 인덱스를
역방향으로 거슬러 올라가 **MiPlatform 화면 → Controller → Service → DAO → MyBatis → SP → 테이블**
의 실제 호출 경로를 그려준다.

---

## 1. 사용 흐름

```
좌측 메뉴 → 분석 → 데이터 흐름 분석  (또는 직접 /flow-analysis)
         ↓
┌─ 입력 ───────────────────────────────────────┐
│ • 질문(자연어)  : "T_SHOP_INVT_SIDE 가 어떻게 INSERT 되나"
│ • 시작점 타입   : 자동 / TABLE / SP / SQL_ID / MIPLATFORM_XML
│ • DML 필터     : INSERT / UPDATE / MERGE / DELETE / SELECT — 다중 선택
│ • 분기수       : 1 단계당 최대 추적 분기 (기본 3, 폭발 방지)
│ • 옵션 토글    : Oracle SP/Trigger 포함, MiPlatform 화면 매칭
└──────────────────────────────────────────────┘
         ↓ "분석 시작" 클릭
┌─ 진행 (SSE 스트림) ─────────────────────────┐
│ 🔍 Phase 1 추적 중 — 인덱서 검색 (1~30s)
│ ✨ Claude 가 흐름도 작성 중... (N 노드)
└──────────────────────────────────────────────┘
         ↓
┌─ 결과 ───────────────────────────────────────┐
│ 좌측 패널: 통계 chips + ⚠ 주의 + Claude markdown narrative
│ 우측 패널: ReactFlow 다이어그램 (column-by-type 레이아웃)
│           - 노드 클릭 → 슬라이드아웃에서 파일/SP/스니펫
└──────────────────────────────────────────────┘
```

## 2. 출력 구조 (Claude narrative)

LLM 이 system prompt 의 강제 구조에 따라 항상 4 섹션으로 답변:

```markdown
## 📌 한 줄 요약
질문에 대한 결론을 1~2 문장으로

## 🔁 데이터 흐름 다이어그램
```mermaid
flowchart TD
  ui[ShopInvtMain.xml] --> ctrl[/api/shop/saveInvt]
  ctrl --> svc[ShopInvtService.save]
  svc --> dao[[ShopInvtDao.insert]]
  dao --> mb[/MyBatis: insertShopInvt/]
  mb --> tbl[(T_SHOP_INVT_SIDE)]
```

## 📋 단계별 설명
1. [MiPlatform 화면] 사용자가 ... — `webapp/miplatform/app/shop/Main.xml`:42
2. [Controller] /api/shop/saveInvt POST → `ShopInvtController.java`:123
3. [Service] 검증 + 변환 → `ShopInvtService.java`:88
4. ...

## ⚠ 주의/추정
- 인덱서가 `service.foo()` 호출자를 못 찾았다면 reflection 가능성 ...
```

## 3. 백엔드 추적 알고리즘 (TABLE 시작점)

| Stage | 데이터 소스 | 동작 |
|-------|-----------|------|
| 1a | `MyBatisIndexer.byTable` | `<insert/update/merge/delete/select>` 중 활성 DML 매칭 |
| 1b | Oracle `ALL_SOURCE` | SP / Trigger 본문에 테이블 참조 + INS/UPD/MRG/DEL 카운트 |
| 2  | Java 파일 grep | `"namespace.id"` 문자열 호출자 → DAO/Service |
| 3  | `SpringUrlIndexer.byCallee` | 그 메서드를 호출하는 `@Get/Post/PutMapping` |
| 4  | `MiPlatformIndexer.byUrl` | URL 을 호출하는 화면 XML (정확/partial) |
| 5  | 결과 조립 | nodes + edges + steps + 자동 mermaid + Claude 호출 |

각 단계 폭발 방지: `maxBranches` (기본 3) 로 분기 cap, 초과시 `warnings` 에 기록.

## 4. 인덱서 (3 종)

각 인덱서는 `@PostConstruct` + `@DependsOn("settingsPersistenceService")` 로 앱 기동 시
프로젝트 스캔 → 메모리 캐시. WAS 가 트래픽을 받기 전에 인덱싱 완료 보장.

| Indexer | 스캔 대상 | 인덱스 |
|---------|---------|--------|
| `MyBatisIndexer`   | scanPath/**/*.xml | byId (namespace.id), byTable (TABLE → statements) |
| `SpringUrlIndexer` | scanPath/**/*.java | byUrl, byCallee (메서드명) |
| `MiPlatformIndexer`| webapp/miplatform/** | byUrl (URL → 화면 XML) |

수동 재인덱싱: 페이지 상단 **"인덱스 재빌드"** 버튼 또는 `POST /api/v1/flow/reindex`.

## 5. 권한 관리

`flow-analysis` featureKey 로 보호.
**Admin → 권한 관리 → "분석" 카테고리 → "데이터 흐름 분석"** 으로 사용자별 on/off.
차단된 사용자는 좌측 메뉴에서 자동 숨김 + 직접 URL 접근 시 redirect.

## 6. 분석 이력 + 공유 링크 (Phase 4)

- 분석 완료 시 자동 저장 (사용자별 최대 50개, 30일 보관)
- 페이지 상단 **"이력"** 토글로 최근 20개 표시 → 클릭 시 재실행 없이 즉시 다이어그램 + narrative 복원
- 분석 후 **"공유"** 버튼 → 7일 유효 short URL 생성 → 클립보드 복사
- 공유 URL: `/flow-analysis?share={token}` — 받는 사람은 read-only 모드로 결과 열람

## 7. 메트릭 (Phase 5 / Prometheus)

| 메트릭 | 타입 | 라벨 | 의미 |
|-------|-----|-----|------|
| `toolkit_flow_analyses_total` | Counter | `targetType`, `result` (`ok`/`empty`/`error`) | 분석 호출 수 |
| `toolkit_flow_stage_duration_seconds` | Timer | `stage` (`phase1`/`llm`) | 단계별 소요 시간 |

Grafana 에서 SSE 채널 분포, p95 레이턴시, 실패율 모니터링 가능.

## 8. Custom MiPlatform 패턴 (Phase 5)

사이트마다 화면 XML 의 URL 호출 컨벤션이 달라 기본 정규식만으론 못 잡는 경우가 있음.
**Settings → Project** 에 추가:

- `miplatformPatterns` — 콤마/줄바꿈 구분 정규식. 그룹 1 캡처가 URL.
  - 예: `xajaxRequest\s*\(\s*['"]([^'"]+)['"]`
  - 예: `callService\s*\(\s*['"]([^'"]+)['"]`
- `miplatformRoot` — 자동 감지 안되는 사이트 (예: `frontend/screen` 등) 직접 지정

설정 후 `/api/v1/flow/reindex` 또는 페이지의 "인덱스 재빌드" 한 번 실행.

## 9. REST API

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/v1/flow/analyze` | 동기 분석 (no LLM, JSON 결과만) |
| `POST` | `/flow/stream/start` | SSE 분석 요청 적재 |
| `GET`  | `/flow/stream` | SSE 스트림 (status / trace / chunks / done) |
| `GET`  | `/api/v1/flow/status` | 인덱서 상태 (statements/endpoints/screens 카운트) |
| `POST` | `/api/v1/flow/reindex` | 모든 인덱스 강제 재빌드 |
| `GET`  | `/api/v1/flow/history?limit=N` | 내 분석 이력 |
| `GET`  | `/api/v1/flow/history/{id}` | 이력 상세 (trace JSON + narrative) |
| `DELETE` | `/api/v1/flow/history/{id}` | 이력 삭제 |
| `POST` | `/api/v1/flow/history/{id}/share` | 공유 링크 생성 (7일) |

전체 OpenAPI 스펙: `/swagger-ui.html` (ADMIN) → "Flow Analysis" 태그.

## 10. 한계 & 알려진 이슈

- **정규식 기반 파싱** — JavaParser 같은 AST 가 아니라 정규식이라 어노테이션 분할/메타 어노테이션
  케이스에선 놓칠 수 있음. 환각 방지를 위해 인덱스에 없는 파일은 narrative 에서 추정 라벨.
- **Reflection / 동적 SQL** — `BeanUtils.invoke` / `EXECUTE IMMEDIATE` 는 추적 불가. AI 가 ⚠ 섹션에 "외부/동적 가능성" 으로 안내.
- **MiPlatform 패턴 사이트별 차이** — 기본 정규식이 못 잡으면 #8 의 custom pattern 사용.
