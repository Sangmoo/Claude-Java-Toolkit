// =============================================================================
// Claude Java Toolkit — 사용자 가이드
// =============================================================================
// 이 문서는 Typst 형식입니다 (https://typst.app).
// PDF 로 변환하려면:  typst compile Claude-Java-Toolkit-Guide.typ
// =============================================================================

#set document(
  title: "Claude Java Toolkit Guide",
  author: "Claude Java Toolkit Team",
)

#set text(font: ("Noto Sans CJK KR", "Noto Sans", "Helvetica"), size: 10pt, lang: "ko")
#set heading(numbering: "1.")
#set page(
  paper: "a4",
  margin: (x: 2.2cm, y: 2.4cm),
  numbering: "1 / 1",
  header: align(right)[#text(size: 8pt, fill: gray)[Claude Java Toolkit Guide · v4.6.1]],
)

#show heading.where(level: 1): it => block(below: 1.2em, above: 1.4em)[
  #text(size: 18pt, weight: "bold")[#it]
  #v(-0.6em)
  #line(length: 100%, stroke: 1pt + rgb("#f97316"))
]
#show heading.where(level: 2): it => block(below: 0.6em, above: 1.2em)[
  #text(size: 14pt, weight: "bold", fill: rgb("#1e293b"))[#it]
]
#show heading.where(level: 3): it => block(below: 0.4em, above: 0.9em)[
  #text(size: 11.5pt, weight: "bold", fill: rgb("#334155"))[#it]
]

#show raw: it => box(fill: rgb("#f1f5f9"), inset: (x: 4pt, y: 2pt), radius: 2pt)[#it]
#show raw.where(block: true): it => block(
  fill: rgb("#0f172a"),
  inset: 12pt,
  radius: 6pt,
  width: 100%,
)[#text(fill: rgb("#e2e8f0"), font: "Consolas", size: 9pt)[#it]]

#let tip(body) = block(
  fill: rgb("#ecfeff"),
  stroke: (left: 3pt + rgb("#06b6d4")),
  inset: (x: 12pt, y: 8pt),
  radius: 2pt,
  width: 100%,
)[*💡 팁* #h(0.5em) #body]

#let warn(body) = block(
  fill: rgb("#fef3c7"),
  stroke: (left: 3pt + rgb("#f59e0b")),
  inset: (x: 12pt, y: 8pt),
  radius: 2pt,
  width: 100%,
)[*⚠️ 주의* #h(0.5em) #body]

#let note(body) = block(
  fill: rgb("#f3e8ff"),
  stroke: (left: 3pt + rgb("#8b5cf6")),
  inset: (x: 12pt, y: 8pt),
  radius: 2pt,
  width: 100%,
)[*📝 참고* #h(0.5em) #body]

// ====== Cover ======
#align(center)[
  #v(4cm)
  #text(size: 32pt, weight: "bold", fill: rgb("#f97316"))[
    Claude Java Toolkit
  ]
  #v(0.4em)
  #text(size: 22pt, weight: "bold", fill: rgb("#1e293b"))[사용자 가이드]
  #v(2cm)
  #text(size: 14pt, fill: rgb("#475569"))[
    AI-powered tools for Oracle DB \& Java/Spring \
    enterprise development
  ]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#64748b"))[
    Powered by Anthropic Claude API
  ]
  #v(4cm)
  #text(size: 10pt, fill: rgb("#94a3b8"))[
    초보자를 위한 단계별 가이드 · v4.6.1
  ]
]

#pagebreak()

#outline(title: [목차], indent: auto, depth: 3)

#pagebreak()

// =============================================================================
= 시작하기 전에

== 이 도구는 무엇인가요?

*Claude Java Toolkit* 은 Java/Spring 으로 만들어진 *대형 엔터프라이즈 시스템*
(국내 SI · 금융 · 유통 ERP 등) 에서 일하는 개발자 · DBA · 운영팀이 *Anthropic
Claude AI* 를 자기 코드와 데이터에 바로 적용할 수 있게 해 주는 *웹 대시보드* 입니다.

다음과 같은 일을 할 수 있습니다:

- Oracle SQL · PL/SQL 의 성능 문제, 보안 취약점, 안티패턴을 *AI 가 자동으로 리뷰*
- Java/Spring 코드를 4 단계 *하네스 파이프라인* (분석 → 개선 → 검토 → 검증) 으로 품질 점검
- *오류 로그* 를 붙여 넣으면 가설 · 검증 SQL · 패치 · 롤백 계획까지 한 번에 생성
- *Oracle Stored Procedure* 를 Java + Spring + MyBatis 로 자동 마이그레이션
- *느린 쿼리* 의 병목 분석 + 후보 인덱스 + 단계별 Rollout Plan 작성
- 특정 *테이블 변경* 이 어떤 화면 · API · 매퍼 · Java 클래스에 영향을 주는지 *역추적*
- ERD · 패키지 구조 · 데이터 흐름 · 의존성을 *다이어그램으로 시각화*
- 분석 결과를 *Excel · SARIF · Markdown · HTML 로 내보내기* — 회의 · IDE · 위키 어디든

== 누구를 위한 도구인가요?

#table(
  columns: (1fr, 2fr),
  inset: 8pt,
  align: (left, left),
  stroke: 0.5pt + gray,
  table.header(
    [*사용자 유형*], [*활용 시나리오*]
  ),
  [*Java/Spring 개발자*],
  [코드 리뷰, Javadoc/테스트 자동 생성, Spring Boot 3 마이그레이션 체크리스트],
  [*DBA · SQL 작성자*],
  [실행계획 분석, 인덱스 추천, SQL 보안 감사, SP → Java 마이그레이션],
  [*운영 · SRE*],
  [오류 로그 RCA, 사내 표준 RCA 보고서 자동 작성, 감사 로그],
  [*테크 리드 · 아키텍트*],
  [패키지 의존성 시각화, 전사 패키지 지도, 품질 대시보드],
  [*신규 입사자*],
  [패키지 스토리 (Claude 가 풀어주는 한국어 설명서), 데이터 흐름 다이어그램],
)

#tip[Python · Node 기반 AI 도구는 많지만, *JDK 1.8 / Oracle 11g+ / Spring Boot
2.x* 같은 한국 SI 환경의 *레거시* 에 그대로 붙는 통합 도구는 거의 없습니다.
이 도구는 정확히 그 빈 자리를 메우려고 만들어졌습니다.]

== 작동 방식 (한 장 요약)

```
┌─────────────────────┐         ┌──────────────────────┐
│  React 대시보드     │  REST   │  Spring Boot 백엔드  │
│  (claude-toolkit-ui)│ ───────▶│  (Java + JPA + H2)   │
└─────────────────────┘         └──────────┬───────────┘
                                           │
                                           ▼
                                ┌──────────────────────┐
                                │  Anthropic Claude API│
                                │  (claude-sonnet-4.x) │
                                └──────────────────────┘
                                           │
                  ┌────────────────────────┼─────────────────────────┐
                  ▼                        ▼                         ▼
          Oracle / MySQL /        프로젝트 소스 파일          분석 이력 (H2)
          PostgreSQL DB           (스캔 인덱스)               리뷰 / 즐겨찾기
```

- *React + TypeScript* 로 작성된 SPA 가 모든 기능의 사용자 인터페이스를 제공합니다.
- *Spring Boot 2.7* 백엔드가 인증, 분석 이력, Claude API 호출 프록시, 인덱서를
  담당합니다.
- *Claude API* 가 실제 AI 분석을 수행합니다.
- *프로젝트 스캔 인덱스* 는 시작 시 한 번 빌드되어 메모리에 캐시되므로
  분석할 때마다 디스크를 다시 읽지 않습니다.
- 모든 분석 결과는 *H2 (또는 MySQL/PostgreSQL) 에 자동 저장* 되어 검색 · 즐겨찾기 ·
  공유 링크가 가능합니다.

#pagebreak()

= 사전 준비물

== 필수 항목

+ *JDK 1.8 또는 그 이상* (Oracle JDK / Adopt OpenJDK / Amazon Corretto 모두 OK)
+ *Maven 3.6+* — 빌드 도구
+ *Anthropic API Key* — #link("https://console.anthropic.com")[console.anthropic.com] 에서 발급
+ 모던 브라우저 (Chrome · Edge · Firefox · Safari 최신 버전)

== 선택 항목 (있으면 더 좋음)

- *Oracle DB* (11g 이상) — 실행계획 분석, SP/Function 자동 로드 기능에 필요
- *Docker · Docker Compose* — 컨테이너 배포 시
- *Node.js 18+* — 프론트엔드만 따로 개발할 때 (Maven 빌드는 자동으로 설치해 줍니다)

#warn[*API Key 보관 주의*: 실수로 Git 저장소에 커밋하지 마세요. 환경변수 또는
`/setup` 마법사를 통해서만 입력하세요. 노출된 키는 즉시 콘솔에서 회수하세요.]

#pagebreak()

= 5분 설치

== 1단계: 클론 + 빌드

```bash
git clone https://github.com/Sangmoo/Claude-Java-Toolkit.git
cd Claude-Java-Toolkit

# Maven 빌드 (React 프론트엔드도 자동 빌드)
mvn clean package -DskipTests
```

빌드가 성공하면 `claude-toolkit-ui/target/claude-toolkit-ui-4.6.1.jar` 이
생성됩니다. 빌드 시간은 처음에는 5\~10 분 (Maven 의존성 + Node.js 다운로드),
이후엔 1\~2 분입니다.

#tip[*IntelliJ IDEA 사용자*: Maven 패널에서 `Lifecycle → package` 더블클릭
이면 끝입니다. 명령어를 외울 필요가 없습니다.]

== 2단계: 실행

#text(weight: "bold")[Mac / Linux:]

```bash
export CLAUDE_API_KEY=sk-ant-...
cd claude-toolkit-ui
mvn spring-boot:run
```

#text(weight: "bold")[Windows PowerShell:]

```powershell
$env:CLAUDE_API_KEY="sk-ant-..."
cd claude-toolkit-ui
mvn spring-boot:run
```

브라우저로 #link("http://localhost:8027")[`http://localhost:8027`] 접속.

== 3단계: 첫 로그인

초기 계정으로 로그인합니다:

- ID: `admin`
- Password: `admin1234`

#warn[*첫 로그인 직후 비밀번호를 변경하세요.* 우상단 사용자 메뉴 → "비밀번호 변경"]

== 4단계: 설치 마법사 (`/setup`)

처음 로그인하면 설치 마법사로 자동 이동합니다. 4 단계 입력만 하면 됩니다:

#table(
  columns: (auto, 1fr),
  inset: 8pt,
  align: (left, left),
  stroke: 0.5pt + gray,
  [*1. API 키*], [Anthropic Claude API 키 입력 → 연결 테스트],
  [*2. 프로젝트 경로*], [분석할 Java/MyBatis 프로젝트의 *루트 디렉터리* 절대 경로],
  [*3. Oracle DB*], [(선택) JDBC URL · 사용자 · 비밀번호 — SP 자동 로드 등에 사용],
  [*4. 회사 컨텍스트*], [(선택) 모든 분석에 자동 첨부될 회사·도메인 정보 메모],
)

#note[*프로젝트 경로 예시*: `C:\workspace\my-erp-project` (Windows) 또는
`/home/user/projects/my-erp` (Linux/Mac). Maven multi-module 프로젝트도 그대로 OK.]

#pagebreak()

= 메뉴 한눈에 보기

좌측 사이드바는 6 개의 그룹으로 나뉩니다:

#table(
  columns: (auto, 1fr),
  inset: 7pt,
  align: (left, left),
  stroke: 0.5pt + gray,
  table.header([*그룹*], [*포함된 메뉴*]),
  [🚀 *바로가기*], [홈, 검색, AI 채팅],
  [⚡ *분석*], [통합 워크스페이스, 파이프라인, SQL 리뷰, 인덱스 시뮬레이션, SQL 번역, 배치 SQL,
    ERD, 복잡도, 실행계획, 코드 리뷰 하네스, 데이터 흐름, 패키지 개요, 전사 지도,
    *테이블 영향 분석*, SP→Java 하네스, SQL 최적화 하네스],
  [🛠 *생성*], [기술 문서, API 명세, 코드 변환, Mock 데이터, DB 마이그레이션,
    Batch 처리, 의존성 분석, Spring 마이그레이션],
  [📚 *기록*], [리뷰 이력, 즐겨찾기, 사용량, ROI 리포트, 분석 스케줄링,
    리뷰 요청, 리뷰 대시보드],
  [🧰 *도구*], [로그 분석기, 정규식 생성기, 커밋 메시지, 마스킹 스크립트, 민감정보 마스킹],
  [👮 *관리* (ADMIN 전용)], [사용자 관리, 권한 관리, 팀 설정 공유, 감사 로그,
    엔드포인트 통계, 비용 옵티마이저, 시스템 헬스],
)

상단 바 가운데에는 *전역 검색창* 이 항상 노출됩니다 (테두리는 Settings 의 팔레트 accent
색상을 자동 따라갑니다). 어디서든 키워드를 입력하면 *메뉴 + 분석 이력* 두 종류 결과를
한 화면에 보여 줍니다.

#pagebreak()

= 핵심 기능 따라하기

== 5-1. SQL 리뷰 (가장 자주 쓰는 기능)

*경로*: 좌측 메뉴 → "분석" → "SQL 리뷰" 또는 `/advisor`

+ 텍스트 영역에 분석할 SQL 을 붙여넣습니다 (또는 `.sql` 파일 업로드).
+ "리뷰" 버튼을 누르면 *SSE 스트리밍* 으로 결과가 실시간으로 흘러나옵니다.
+ 결과는 다음 4 개 섹션으로 정리됩니다:
  - *📊 성능 리뷰* — 인덱스 활용, 카디널리티, 안티패턴 (`[SEVERITY: HIGH/MED/LOW]`)
  - *🔒 보안 감사* — SQL Injection, 권한, 민감 데이터 노출
  - *💡 개선 제안* — `CREATE INDEX` 구문 포함된 구체적 액션
  - *📈 Before/After 비교* — AI 가 제안한 수정본 Diff
+ 결과는 자동으로 *리뷰 이력* (`/history`) 에 저장됩니다 → 나중에 검색 가능.

#tip[3 천 줄 이상 긴 SQL 도 잘 처리하지만, *한 번에 1\~2 개 쿼리* 정도로 잘라서
넣으면 분석 품질이 더 좋습니다.]

== 5-2. 코드 리뷰 하네스 (4 단계 파이프라인)

*경로*: `/harness`

이름은 어렵게 들리지만 *체계적인 코드 리뷰* 라고 생각하세요. 한 번 실행하면 4 명의
가상 리뷰어가 차례로 동작합니다:

+ *Analyst (분석가)* — 성능 · 가독성 · 보안 · 안티패턴을 항목별로 나열
+ *Builder (개선가)* — 분석 결과를 적용해 *전체 개선 코드* 생성
+ *Reviewer (검토자)* — 변경 내역 · 기대 효과 · 최종 판정 (`APPROVED` / `NEEDS_REVISION`)
+ *Verifier (검증자)* — 정적 검증 (Java/SQL Verifier) — 컴파일 가능 여부, 위험 변경 감지

각 단계는 *별도 패널* 로 분리되어 표시되어, 어디서 어떤 의사결정이 일어났는지
명확히 추적할 수 있습니다.

#note[*"전 항상 1번 분석가만 보고 끝내고 싶은데?"* — 그래도 4 단계가 다 돌게
되어 있습니다. Builder/Reviewer 단계의 결과는 검증된 코드와 판정으로 별도 가치가 있어요.]

== 5-3. 테이블 영향 분석 (TABLE → MyBatis → Java → Controller)

*경로*: `/impact`

#text(weight: "bold")[언제 쓰나요?] _"이 테이블에 컬럼 하나 추가하려는데, 어떤 화면 / API / 매퍼 / Java 클래스에 영향이 있을까?"_ 같은 변경 영향 평가에 씁니다.

+ 테이블 이름을 입력하거나 *"소스선택하기"* 버튼으로 DB 테이블 픽커에서 고릅니다.
+ DML 종류를 선택 (ALL / SELECT / INSERT / UPDATE / MERGE / DELETE).
+ "분석" 버튼을 누르면 정적 인덱스 (LLM 호출 없음, 매우 빠름) 가 4 단계 역추적 결과를 보여줍니다:
  - 📋 MyBatis 구문 (해당 테이블 사용)
  - ☕ Java 파일 (그 구문을 호출하는 매퍼/서비스)
  - 🎯 Controller 엔드포인트 (그 Java 파일이 노출하는 REST API)
+ *어떤 행이든 클릭하면* 모달 팝업이 열려 해당 파일의 *전체 소스* 를 볼 수 있고,
  우상단 *"전체 복사"* 버튼으로 클립보드에 복사할 수 있습니다.

#tip[*v4.6.1 패치*: 한국 SI 환경의 MS949 (CP949) 인코딩 Java 파일도 자동으로
디코드합니다. 한글이 깨지지 않습니다. 파일 모달 헤더에 감지된 인코딩이 표시됩니다.]

== 5-4. SP → Java 마이그레이션 하네스

*경로*: `/sp-migration-harness`

Oracle Stored Procedure 를 Java + Spring + MyBatis 로 *자동 변환* 해 주는 도구입니다.

+ *"소스선택하기"* 버튼으로 Oracle DB 의 PROCEDURE / FUNCTION / PACKAGE / TRIGGER
  를 선택 → ALL_SOURCE 에서 본문이 자동 로드됩니다.
+ (선택) 관련 테이블 DDL · 인덱스 DDL · 호출 예시 · 비즈니스 컨텍스트도 입력 가능.
+ "4단계 분석 시작" 버튼:
  - *Analyst*: SP 의 입출력, DB 부수효과, 트랜잭션 경계, 루프, 위험 포인트 분석
  - *Builder*: Service 클래스 + Mapper 인터페이스 + MyBatis XML + DTO + JUnit 5 테스트 생성
  - *Reviewer*: 행위 동등성 검증 (예: 원본 SP 와 결과가 같은가? N+1 위험은?)
  - *Verifier*: 컴파일 가능성, MyBatis XML 정합성, 위험 변경 감지

== 5-5. SQL 최적화 하네스

*경로*: `/sql-optimization-harness`

느린 쿼리를 단순히 "최적화하라" 가 아닌, *체계적인 의사결정* 으로 풀어줍니다.

+ 입력: *쿼리* (필수) + 실행계획 + 통계 + 인덱스 + 변경 불가 제약 (선택, 강력 권장).
+ 4단계 결과:
  - *Analyst*: 병목 지점, 카디널리티, 인덱스 활용도, 안티패턴
  - *Builder*: *N 개의 개선 후보* (rewrite + DDL + 힌트, 각 후보별 비용·리스크·롤백)
  - *Reviewer*: 결과 동등성, 다른 쿼리 영향, 점수표
  - *Verifier*: 정적 검증 + *단계별 Rollout Plan* + 모니터링 포인트 + 롤백

#warn[운영 DB 는 절대 직접 인덱스를 추가하지 마세요. *Verifier 단계의 Rollout
Plan* 을 따라 야간 윈도우 · DDL 허용 시간 · 모니터링 절차를 거쳐 적용합니다.]

== 5-6. 오류 로그 RCA 하네스

*경로*: `/loganalyzer` → 우측 토글 *"RCA 하네스 (4단계)"* ON

운영에서 터진 오류 로그를 그대로 붙여넣으면 *Root Cause Analysis 보고서* 가
자동으로 만들어집니다.

+ *Analyst*: 가설 후보 N 개 (각 가설의 근거, 영향 범위, 시간선)
+ *Builder*: 가설을 검증할 *SQL · 패치 코드 · 롤백 절차*
+ *Reviewer*: 각 가설의 우도 (likelihood) 평가 + 우선순위
+ *Verifier*: *사내 표준 RCA 보고서* (요약, 원인, 조치, 재발 방지 체크리스트)

두 가지 모드:

- *일반 RCA* — 평범한 NPE / SQL Exception / 타임아웃 분석
- *보안 RCA (OWASP Top 10 기준)* — SQL Injection, XSS, 인증 우회 등 보안 사고

== 5-7. 데이터 흐름 분석

*경로*: `/flow-analysis`

전체 데이터 흐름을 *ReactFlow 다이어그램* 으로 시각화합니다.

+ 분석 대상 (테이블/SP/SQL_ID/MiPlatform XML) 을 자연어로 질문 (예: _"T_SHOP_INVT 가 INSERT 되는 흐름"_).
+ DML 필터 + 분기 수 + DB/UI 포함 여부 선택.
+ "분석 시작" → 좌측에 진행상황과 Claude 내러티브, 우측에 *7 컬럼 다이어그램*
  (UI → Controller → Service → DAO → MyBatis → SP → Table) 이 실시간으로 그려집니다.
+ 노드 클릭 → 슬라이드아웃 패널에서 *파일/SP 메타 + 스니펫* 미리보기.
+ "공유" 버튼 → *7 일 유효 read-only URL* 생성 (로그인 불필요).

== 5-8. 패키지 개요 + 전사 패키지 지도

*경로*: `/package-overview`, `/project-map`

신입 입사자가 *처음 코드베이스를 이해할 때* 가장 좋은 메뉴입니다.

- *패키지 개요*: Java 패키지 단위 4 탭
  + *📊 요약* — Controller / Service / DAO / MyBatis 통계
  + *🔗 ERD* — 연관 테이블 Mermaid 다이어그램 + heatmap (히트카운트)
  + *🌊 풀 흐름도* — 패키지가 건드리는 모든 테이블 → MyBatis → Java → Controller
  + *📜 스토리* — Claude 가 신입 친화 한국어 마크다운으로 풀어주는 패키지 설명서
- *전사 패키지 지도*: 드릴다운 카드 그리드. 클릭으로 깊이 탐색 + 검색 필터.

#tip[*패키지 스토리* 는 Markdown 으로 export 됩니다. 사내 위키에 그대로 붙여넣어 신입 온보딩 문서로 활용하세요.]

== 5-9. 통합 워크스페이스

*경로*: `/workspace`

*하나의 입력으로 여러 분석을 병렬로* 실행합니다. 예를 들어 SQL 한 개를 넣고
체크박스로 _"리뷰 + 보안 감사 + 인덱스 추천 + 실행계획 분석"_ 을 동시에 켜면
4 개 결과가 한 페이지에 나란히 펼쳐집니다.

언어를 Java 로 바꾸면 _"코드 리뷰 + Javadoc 생성 + 복잡도 분석 + 테스트 코드 생성"_
같은 조합이 가능합니다.

== 5-10. 분석 파이프라인

*경로*: `/pipelines`

*YAML* 로 다단계 분석 파이프라인을 정의해 자동 실행합니다.

```yaml
name: 일일 SQL 품질 검사
schedule: "0 3 * * *"  # 매일 새벽 3시
steps:
  - id: review
    type: sql_review
    input: ./queries/today.sql
  - id: security
    type: sql_security
    input: ${review.input}
    dependsOn: [review]
  - id: report
    type: report
    input: ${review.output}\n${security.output}
```

ReactFlow 기반 *인터랙티브 그래프 뷰* 로 의존성을 시각적으로 확인할 수 있습니다.

#pagebreak()

= 자주 쓰는 워크플로 시나리오

== 시나리오 A. "이 SP 를 Java 로 옮기고 싶은데, 어디서부터 시작하지?"

+ `/sp-migration-harness` 페이지로 이동
+ "소스선택하기" 버튼 → DB 에서 해당 PROCEDURE 클릭 → 본문 자동 로드
+ (선택) 관련 테이블 DDL 을 두 번째 입력칸에 붙여넣기
+ "4단계 분석 시작" 클릭
+ 약 30\~90 초 후 4 개 패널이 모두 채워집니다
+ Builder 패널에서 생성된 Service / Mapper / XML / DTO 코드를 *각 코드블록 우상단
  복사 버튼* 으로 클립보드에 가져가 IDE 에 붙여넣기
+ Reviewer 의 행위 동등성 체크리스트를 *PR 본문* 으로 사용
+ 분석 이력은 자동 저장 → 나중에 `/history` 또는 *상단바 검색* 에서 다시 찾기

== 시나리오 B. "운영에서 NPE 가 났는데 원인 분석 보고서가 필요해"

+ `/loganalyzer` 이동 → 우측 *"RCA 하네스 (4단계)"* 토글 ON
+ 오류 로그 전문 (스택트레이스 + 직전 INFO 로그) 을 붙여넣기
+ (선택) Timeline 칸에 _"14:23 PR #1234 배포 → 14:35 첫 NPE 발생"_ 같은 사건 흐름 입력
+ 모드 선택: *일반 RCA* (보통 사건) / *보안 RCA* (OWASP Top 10 의심 시)
+ 분석 시작 → 4 개 패널이 차례로 채워짐
+ Verifier 패널의 *사내 표준 RCA 보고서* 를 그대로 사후 분석 회의 자료로 사용

== 시나리오 C. "특정 컬럼 추가했을 때 어떤 화면에 영향이 있는지 PM 에게 알려줘야 해"

+ `/impact` 페이지 이동
+ 테이블 이름 입력 (또는 "소스선택하기" 버튼으로 DB 에서 선택)
+ DML 을 *ALL* 로 두고 분석
+ 4 개 카운트 카드 (MyBatis 구문 / Java 파일 / Controller / 화면) 가 표시됨
+ 각 행을 클릭 → 모달로 전체 소스 보고 *영향 범위 한눈에 확인*
+ 결과를 PM 에게 보낼 때:
  - 페이지 우상단에서 *"공유 링크"* (7 일 유효) 또는
  - 분석 이력에서 *Excel/SARIF 내보내기*

== 시나리오 D. "느린 쿼리를 들고 다 함께 트러블슈팅 회의를 해야 해"

+ `/sql-optimization-harness` 이동
+ 입력 6 종 (쿼리 / 실행계획 / 통계 / 인덱스 / 데이터 볼륨 / 변경 불가 제약)
  중 *최소 쿼리 + 실행계획* 은 꼭 채우기 (정확도가 결정적으로 달라짐)
+ 분석 시작 → 4 개 패널 채워짐
+ Builder 의 *N 개 후보* 비교 표를 회의 자료로 사용
+ Verifier 의 *Rollout Plan* 을 DBA 와 합의해 야간 작업 일정으로 등록

#pagebreak()

= 설정 가이드

== Settings 페이지 (`/settings`, ADMIN 전용)

#table(
  columns: (auto, 1fr),
  inset: 7pt,
  align: (left, left),
  stroke: 0.5pt + gray,
  table.header([*항목*], [*설명 / 권장값*]),
  [*Claude API Key*], [Anthropic API 키. `sk-ant-...`],
  [*Model*], [`claude-sonnet-4-6` (균형) 또는 `claude-opus-4-7` (고품질, 비싸짐)],
  [*Project Scan Path*], [Java 프로젝트 루트의 절대 경로],
  [*Oracle DB*], [JDBC URL · 사용자 · 비밀번호 — *연결 테스트 버튼* 필수 사용],
  [*Project Memo*], [회사 도메인 · 코딩 규칙 등 모든 분석에 자동 첨부될 컨텍스트],
  [*Cache Refresh Cron*], [예: `0 3 * * *` (매일 새벽 3시 자동 인덱스 재빌드)],
  [*Palette*], [Light / Dark / Dracula / Nord / Solarized / GitHub / Monokai 7종],
  [*Locale*], [한 / 영 / 일 / 중 / 독],
)

#tip[*팔레트* 를 바꾸면 상단바 검색창 테두리, 사이드바 강조 색상, 모든 강조
표시가 즉시 동기화됩니다 (페이지 새로고침 불필요).]

== 권한 관리 (`/admin/permissions`)

각 사용자 별로 *기능 단위 ON/OFF* 가 가능합니다 (`featureKey` 기반):

- 50+ 개의 feature key 가 정의되어 있고, 사이드바 메뉴와 URL 라우터, REST API
  모두에 동일하게 적용됩니다.
- VIEWER 는 분석만 가능하고 결과 저장은 REVIEWER/ADMIN 의 승인을 거칩니다.

#pagebreak()

= REST API 사용

웹 UI 의 모든 기능은 *순수 JSON REST API* 로도 호출 가능합니다.
CI/CD · GitHub Actions · Postman 에서 그대로 활용하세요.

== 자주 쓰는 엔드포인트

#table(
  columns: (auto, 1fr, 1fr),
  inset: 6pt,
  align: (left, left, left),
  stroke: 0.5pt + gray,
  table.header([*Method*], [*경로*], [*용도*]),
  [`POST`], [`/api/v1/sql/review`], [SQL 성능 · 품질 리뷰],
  [`POST`], [`/api/v1/sql/security`], [SQL 보안 취약점 검사],
  [`POST`], [`/api/v1/code/review`], [Java 코드 리뷰],
  [`POST`], [`/api/v1/sp-migration/analyze`], [SP → Java 마이그레이션],
  [`POST`], [`/api/v1/sql-optimization/analyze`], [SQL 최적화 (4단계)],
  [`POST`], [`/api/v1/log-rca/analyze`], [오류 로그 RCA (4단계)],
  [`GET`], [`/api/v1/flow/impact?table=T_FOO`], [테이블 영향 분석],
  [`GET`], [`/api/v1/flow/file?path=src/main/...`], [프로젝트 파일 내용 조회],
  [`GET`], [`/api/v1/search?q=하네스`], [메뉴 + 이력 통합 검색],
  [`GET`], [`/api/v1/history`], [분석 이력 목록],
)

#text(weight: "bold")[curl 예시 — SQL 리뷰:]

```bash
curl -X POST http://localhost:8027/api/v1/sql/review \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data "code=SELECT * FROM T_USER WHERE name='admin'" \
  -u admin:admin1234
```

#text(weight: "bold")[Swagger UI:] `/swagger-ui.html` (ADMIN 권한 필요) — 모든 엔드포인트의
대화형 문서.

#pagebreak()

= 자주 묻는 질문 (FAQ)

== Q1. 분석 결과가 너무 짧거나 일반적입니다.

대부분 *컨텍스트 부족* 이 원인입니다. 다음을 확인하세요:

- Settings 의 *Project Memo* 에 _"우리는 ERP 시스템이고 ..."_ 같은 도메인 정보를 입력
- 분석 입력에 *관련 테이블 DDL · 인덱스 DDL · 호출 예시* 도 함께 첨부
- *Model* 을 `claude-sonnet-4-6` → `claude-opus-4-7` 로 일시 전환

== Q2. "프로젝트 스캔 중..." 이 너무 오래 걸려요.

프로젝트가 크면 (5\,000+ 파일) 첫 인덱스 빌드에 30\~60초 걸립니다.

- `application.yml` 의 `toolkit.indexer.maxJavaScan` 으로 상한 조정
- `toolkit.indexer.maxFileSize` 로 파일 크기 한도 조정
- 시작이 끝나면 메모리에 캐시되므로 *런타임 분석은 ms 단위*

== Q3. 한글이 깨져서 보입니다.

*v4.6.1 부터* 파일 모달은 UTF-8 / MS949 자동 감지 디코딩으로 한글 깨짐 문제가
해결되었습니다. 그래도 깨지면:

- 파일을 열어 인코딩을 *직접 UTF-8 로 변환* 후 다시 시도 (IDE 에서 가능)
- 모달 헤더에 표시된 *감지된 인코딩* 이 예상과 다른지 확인

== Q4. 토큰 사용량이 무서워요. 비용을 줄일 수 있나요?

- ADMIN → `/admin/cost-optimizer` 페이지에서 *모델별 비용 비교*
- 단순 분석 (커밋 메시지, 정규식 등) 은 *Haiku 모델* 로 전환 → 최대 *80% 절감*
- 사용량 모니터링 (`/usage`) 에서 *사용자별 일일 한도* 설정

== Q5. 분석 결과를 IDE 에서 바로 확인하고 싶습니다.

- 결과를 *SARIF 2.1.0 JSON* 으로 내보내기 (`/history` 페이지 우상단 버튼)
- VS Code 의 _SARIF Viewer_ 또는 JetBrains _Qodana_ 에서 import
- GitHub Code Scanning 도 SARIF 를 그대로 받습니다

== Q6. SSE 스트리밍이 중간에 끊깁니다.

- 회사 프록시가 *60 초 idle timeout* 일 가능성 — 분석이 그보다 길면 재시도
- Spring `application.yml` 의 `server.tomcat.connection-timeout` 값 확인
- nginx/Apache 앞단을 거치면 `proxy_read_timeout` 도 늘려야 함 (예: 600s)

== Q7. 서버를 재기동해도 인덱스가 다시 빌드되어 시작이 느립니다.

- 정상입니다. `@PostConstruct` 에서 동기 빌드해야 첫 분석이 즉시 동작.
- Cache Refresh Cron 으로 *야간에 자동 갱신* 으로 두면 평소엔 부담 없음.

#pagebreak()

= 트러블슈팅

== 빌드 실패: "frontend-maven-plugin: Could not download Node.js"

원인: 사내 프록시 환경.

해결: 환경변수 `HTTPS_PROXY` / `HTTP_PROXY` 설정 또는
`~/.m2/settings.xml` 에 프록시 정보 추가.

== 로그인 실패: "Invalid username or password"

- 초기 계정은 `admin / admin1234`
- 비밀번호를 잊었다면: H2 파일을 직접 열거나
  `application.yml` 에 임시로 `app.security.bypass-login=true` 설정 후 재시작

== 분석 시작 직후 "Connection refused" 에러

- Claude API Key 가 잘못됨 — Settings → 연결 테스트로 검증
- 회사 방화벽이 `api.anthropic.com` 을 차단 — 네트워크 팀에 화이트리스트 요청

== Oracle DB 자동 로드 안됨 / "DB 연결 실패"

- `application.yml` 의 JDBC URL · 드라이버 의존성 확인
  (`com.oracle.database.jdbc:ojdbc8` 추가)
- `ALL_OBJECTS` / `ALL_SOURCE` 에 대한 SELECT 권한 보유 확인

== "DML 필터 / 인덱스 재빌드" 후에도 결과가 갱신 안 됨

- Settings 에서 *프로젝트 경로* 가 정확한지 재확인
- 우상단 *"인덱스 재빌드"* 버튼으로 강제 재스캔
- 그래도 안 되면 WAS 재시작 → `@PostConstruct` 가 처음부터 인덱싱

#pagebreak()

= 참고 자료

== 공식 링크

- *프로젝트 GitHub*: #link("https://github.com/Sangmoo/Claude-Java-Toolkit")
- *Anthropic Console*: #link("https://console.anthropic.com")
- *Claude API 문서*: #link("https://docs.anthropic.com")
- *프로젝트 소개 페이지*: #link("https://sangmoo.github.io/Claude-Java-Toolkit/#ko")

== 동봉 모듈

#table(
  columns: (auto, 1fr),
  inset: 7pt,
  align: (left, left),
  stroke: 0.5pt + gray,
  table.header([*모듈*], [*역할*]),
  [`claude-spring-boot-starter`], [`application.yml` 만으로 `ClaudeClient` Bean 자동 주입],
  [`claude-sql-advisor`], [SQL · SP 리뷰, 보안 감사, 인덱스 최적화, ERD, Mock 데이터],
  [`claude-doc-generator`], [기술 문서, Javadoc, 리팩터링, 테스트, 코드 변환, 로그/정규식/커밋],
  [`claude-toolkit-ui`], [위 기능들을 묶은 React SPA + Spring Boot 백엔드],
)

== 라이선스

*Apache License 2.0* — 상업적 사용 가능. 자세한 내용은 LICENSE 파일 참고.

== 기여 / 버그 리포트

- 이슈: #link("https://github.com/Sangmoo/Claude-Java-Toolkit/issues")
- PR 환영. 가이드라인은 `docs/CONTRIBUTING.md` 참고.

#v(2cm)
#align(center)[
  #text(size: 9pt, fill: gray)[
    이 문서는 v4.6.1 기준입니다. 최신 정보는 `CHANGELOG.md` 와 README 를 참고하세요. \
    문서 생성: Typst — `typst compile Claude-Java-Toolkit-Guide.typ`
  ]
]
