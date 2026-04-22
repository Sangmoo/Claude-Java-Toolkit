# Changelog

All notable changes to **Claude Java Toolkit** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> 🤖 **v4.4.0+ 부터는 [release-please](https://github.com/googleapis/release-please-action) 가 자동 갱신합니다.** Conventional Commits (`feat:`/`fix:`/`docs:`) 형식으로 push 하면 master 브랜치의 이 파일과 GitHub Releases 페이지가 자동으로 업데이트됩니다. 직접 편집은 v4.3.x 이전 항목에만 적용하세요.

릴리즈별 상세 배경 + 기술적 의사결정은 [README.md](./README.md) "버전 히스토리"
섹션을 참조하세요. 이 파일은 변경사항의 **빠른 참조용 요약**입니다.

---

## [Unreleased] — v4.4.0 진행 중

### Added (✨ Features)
- **OpenAPI / Swagger UI** — `/swagger-ui.html` (ADMIN 전용) + `/v3/api-docs` JSON 스펙
- **자체 구축 에러 모니터링 (Sentry-style)** — `/admin/error-log` 페이지 + dedupe + 자동 unresolved 복귀
- **메트릭 통합 5종 신규** — 캐시 히트/미스, SSE 동시 연결 게이지, 하네스 4단계 Timer, 에러 발생률 카운터, 파이프라인 단계 카운트
- **Helm Chart Kind 검증 인프라** — `scripts/test-helm.sh` 자동화 + `VALIDATION.md` 5 시나리오
- **Helm Chart 안정성 강화** — `startupProbe` (5분 시작 허용) + `JAVA_OPTS` 옵션
- **자동 변경 로그** — release-please-action v4 GitHub workflow

### Changed (♻️ Refactoring)
- HomePage 위젯 토글 버그 수정 — 숨긴 위젯이 layout 에서 사라지지 않도록 보존
- 도구 카드 그리드 위젯 — 12개 카탈로그에서 사용자 임의 선택 + 순서 변경 모달
- **Hero 위젯 인사말/부제 사용자 편집 가능** — `{name}` 토큰 치환 + 시스템 상태 라인 토글 + 미리보기
- **언어 스위처 UI 개선** — 드롭다운 배경 불투명화 (var(--bg-card) → var(--bg-secondary)) + 즉시 적용 (window.location.reload)
- **하네스 헤더 줄바꿈 방지** — SSE 청크 flush 정책 변경 (마지막 \n 까지만 flush) → "## 📋" 만 잘려 렌더링되던 문제 해결
- IndexAdvisorService — Settings 외부 DB 우선 사용 (실패 시 H2 fallback)
- IndexAdvisorPage — Monaco Editor + 대상 DB 표시 + 샘플 SQL 드롭다운
- AdminEndpointStatsPage — 백엔드 error 응답 시 toLocaleString TypeError 방어

### Documentation (📚)
- **스크린샷 11개 추가** — `docs/screenshots/` (홈/SQL/파이프라인/하네스 + v4.3/v4.4 신기능)
- README 의 "📸 데모" 섹션 + docs/index.html "화면으로 보기" 갤러리 자동 활성화

### Tests (🧪)
- JaCoCo 0.8.11 플러그인 + 커버리지 리포트 자동화
- 6 신규 테스트 클래스 / 56 단위 테스트 케이스 (sqlindex, cost, metrics, export, pipeline, history, errorlog)
- ToolkitMetricsTest 신규 5 테스트 (캐시/Harness 단계/SSE/Error/Pipeline Step)

### Documentation (📚)
- `monitoring/METRICS.md` — Prometheus 메트릭 카탈로그 + PromQL + 권장 알람 5종
- `helm/claude-toolkit/VALIDATION.md` — 5 시나리오 (H2 / PostgreSQL / Ingress / HPA / ServiceMonitor) 체크리스트
- README — Kind 설치 (winget) + 자동 검증 스크립트 가이드

---

## [4.3.0] — 2026-04-17

### Added (✨ Features)
- SARIF 2.1.0 / Excel 워크북 내보내기 (IDE 연동 + 회의 자료 자동 생성)
- Prometheus + Grafana 모니터링 (`docker-compose --profile monitoring`)
- AI 모델 비용 옵티마이저 (Haiku/Sonnet/Opus 추천)
- SQL 인덱스 임팩트 시뮬레이션 (JDBC 메타조회 + DDL 추천)
- 다국어 5개 언어 (ko/en/ja/zh/de) + 사용자별 자동 동기화
- 대시보드 위젯 커스터마이징 (react-grid-layout + DB 영속화)
- 파이프라인 인터랙티브 그래프 (reactflow)
- Kubernetes Helm Chart (Deployment + Service + Ingress + HPA + ServiceMonitor)

### Changed
- DataRestController / AuthRestController silent catch → SLF4J 로깅 (~22 곳)
- SQL 인덱스 시뮬레이션 권한 분리 (`featureKey: 'index-advisor'`)

---

## [4.2.7] — 2026-04-15

### Security
- `/api/v1/admin/**` 역할 게이팅 누락 수정. 기존 `/admin/**` 규칙이
  `/api/v1/admin/**` 를 매칭하지 못해 VIEWER/REVIEWER 도 관리자 API 호출
  가능한 구멍이 있었음. `SecurityConfig` 에 `.hasRole("ADMIN")` 추가.
- `FavoriteController.delete` 소유자 체크 추가 — 본인 소유 아니면 HTTP 403.
- `ReviewHistoryController.delete` 역할 체크 추가 — VIEWER 는 HTTP 403.
- `ReviewCommentController.deleteComment` — 주석의 "본인 또는 ADMIN" 과
  구현 불일치 수정. ADMIN 도 삭제 가능.

### Fixed
- `/favorites/star` 중복 저장 — `(username, historyId)` 유니크 체크.
- 알림 SSE `onerror` fallback 폴링 — 기존 `setTimeout` 1회 → `setInterval`
  지속 폴링 + 재연결 시도.
- `toggleFavorite` 연속 클릭 경합 — `togglingIdsRef` 가드 추가.
- 알림 `EventSource` 가 이름 있는 `"notification"` 이벤트를 못 받던 버그 —
  `addEventListener('notification', ...)` 직접 구독으로 해결. 결과적으로
  종 아이콘 뱃지가 새 알림 즉시 증가하지 않던 문제가 해결됨.
- `markRead` 가 이미 읽은 알림을 다시 클릭해도 무조건 감산하던 버그 —
  `wasUnread` 체크 후에만 감산.
- `Favorite` 엔티티 `username` / `historyId` 컬럼 추가. 기존에 JPQL 쿼리가
  필드 부재로 예외 → catch 후 빈 배열 반환하여 **즐겨찾기 목록이 항상 비어
  보이던 치명적 버그** 수정.
- 하네스 "개선 코드" 탭 상단 `sql` 라벨 라인 제거 — `normalizeBuilderSection`
  정규식이 언어 이름만 있는 첫 줄을 제거.
- 하네스 중첩 펜스 정리 — Claude Builder 가 펜스/언어 라벨을 중복 출력해도
  프론트에서 하나의 단일 코드 블록으로 정규화.
- `OracleDbHealthIndicator` 가 요청 스레드에서 동기 프로브를 돌려 Tomcat
  워커를 최대 80초 점유하던 버그 — `@PostConstruct` + `@Scheduled(1h)` 로
  완전 분리. `health()` 는 캐시만 반환.
- `HarnessCacheService` 가 `ApplicationReadyEvent` 이후 백그라운드에서 캐시를
  채우던 구조 — `@PostConstruct` 로 전환하여 WAS 기동 전에 인라인 로드.
  사용자가 기동 직후 "소스 선택" 을 눌러도 "적용 중..." 이 뜨지 않음.

### Added
- **VIEWER 검토 대기 알림**: VIEWER 가 이력을 생성하면 REVIEWER/ADMIN
  전원에게 실시간 알림. `PendingReviewNotifier` 를 4개 저장 경로
  (`ReviewHistoryService.save` / `saveHarness` /
  `SseStreamController.saveHistory` / `PipelineExecutor`) 에 훅킹. 알림
  클릭시 `/review-requests?historyId=N` 로 이동해 해당 카드가 하이라이트됨.
- **댓글 `@멘션`**: `MentionInput` 공용 컴포넌트 (`@` 드롭다운,
  ArrowUp/Down/Enter/Tab/Esc), `/api/v1/users/mentions` 엔드포인트,
  `MENTION_PATTERN` 파싱 + 알림 발행. 이메일(`a@b.com`) 은 멘션으로 판정
  안 됨.
- **`ReviewActionDialog` 공용 다이얼로그**: 승인/거절 확정시 선택 코멘트
  입력 가능 (Ctrl+Enter / Esc). 모든 사용자가 읽을 수 있는 `ReviewNoteCard`
  로 피드백 표시.
- **이력 상세 모달** (팀 리뷰 요청 페이지): 리뷰어가 VIEWER 작성 본문을
  팝업으로 확인 + 모달 내에서 바로 승인/거절.
- **`HistoryPage` 다중 선택**: 체크박스 기반 내보내기 + 삭제 (canDelete).
- **`FavoritesPage` 검색 / 다중 선택 / 입력 표시**: 기존엔 검색도 없고
  본문이 비어 보이던 상태 → 완전 업그레이드.
- **알림 개별 / 전체 삭제**: 드롭다운 항목별 X 버튼 + 헤더 "전체 삭제".
  알림 제목 / 본문 / 시각을 계층적으로 표시.
- **이력/즐겨찾기 페이지네이션**: `?page=&size=` + `X-Has-More` 헤더.
  프론트 "더 보기 (+50)" 버튼.
- **리뷰 노트 표시**: `ReviewNoteCard` 로 모든 사용자에게 리뷰어 피드백을
  친화적으로 노출.

### Changed
- `toolkit.history.max` / `toolkit.favorites.max` 설정 외부화. 기본값 상향
  (100→200, 200→500). 환경변수 `TOOLKIT_HISTORY_MAX` /
  `TOOLKIT_FAVORITES_MAX` 로 오버라이드.
- 모달 공용 fade 애니메이션 (`modal-overlay-in` / `modal-body-in`) —
  `ReviewActionDialog` + 리뷰 상세 모달 적용.
- `utils/date.ts` 공통 유틸 추출 — `formatDate` / `formatRelative`. 기존
  중복 선언 제거.
- `utils/harnessMarkdown.tsx` 추출 — `CodeReviewPage.tsx` 에서 ~110줄 분리.
- `Dockerfile` `dependency:go-offline` 실패 로그 묵음 처리 + 이유 주석.
- 알림 드롭다운이 제목 / 본문 / 시각을 계층적으로 표시 (기존엔 message 만).

---

## [4.2.6] — 2026-04 (이전 릴리즈)

### Fixed
- `OracleDbHealthIndicator` 가 timeout 없는 JDBC 연결로 Tomcat 워커 80초씩
  점유하던 버그 (CONNECT_TIMEOUT/ReadTimeout/LoginTimeout 3초 + 60초 캐싱).
- 정규식 생성기 예시 버튼이 `clipboard.writeText` 만 호출해 HTTP IP 환경
  silent 실패 → `inputExamples` prop 으로 textarea 직접 채우기.
- `authStore.login()` 이 `disabledFeatures` 를 fetch 안 해서 권한 OFF 메뉴가
  사이드바에 보이던 버그.

### Changed
- 코드 리뷰 하네스 4단계 탭 분리 (`[[HARNESS_STAGE:N]]` sentinel 마커) +
  단계별 복사/MD/PDF/이메일 버튼.
- 리뷰 대시보드 권한 등록 3곳 누락 수정 (`AdminPermissionController` +
  `PermissionInterceptor` + `sidebarMenus.ts`).

---

## [4.2.5] — 2026-04

### Added
- 리뷰 이력 대시보드 페이지 (승인/거절/대기 % + 차트).
- 분석 파이프라인 결과가 리뷰 이력에 저장.

### Fixed
- `/history/{id}/review-status` CSRF 누락 — 승인/거절 실패 버그.
- 파이프라인 Broken pipe 로그 폭주 + max_tokens 잘림 자동 이어받기.
- 분석 파이프라인 결과가 리뷰 이력에 저장되지 않던 버그.

---

## [4.2.4] — 2026-03

### Fixed
- 파이프라인 단계 간 컨텍스트 전파 누락 (SQL 최적화 풀 스택 step2/3 빈 결과).

---

## [4.2.3] — 2026-03

### Added
- 리뷰 워크플로우 (승인/거절 + 대댓글 + 알림).
- 파이프라인 UX 개편 (스트리밍/탭/복사 fix) + 분석 결과 이메일 발송.

### Fixed
- 리뷰이력 저장 누락 + 파이프라인 실시간 스트리밍 (진행적 저장 + 폴링).
- 프로젝트 파일 스캔 중 'Java 파일 없음' 오해 — 진행 상태 표시 + 자동 폴링.
- 시스템 헬스의 DB 정보가 실제 운영 DB 를 반영하도록 수정.

---

## [4.2.2] — 2026-03

### Fixed
- 사내망 운영 이슈 정리 (TLS / DNS / Proxy 환경변수).

---

## [4.2.0] — 2026-03

### Added
- 운영 안정화 라운드 + UX 개선.

---

## [4.1.0] — 2026-02

### Changed
- Thymeleaf 템플릿 완전 제거. `claude-toolkit-ui/src/main/resources/templates/`
  디렉토리 및 `spring-boot-starter-thymeleaf` 의존성 삭제.

---

## [4.0.0] — 2026-01

### Added
- React 18 + TypeScript + Vite 기반 SPA 로 프론트엔드 전면 전환.
- 64개 React 페이지 + 공용 컴포넌트/훅/스토어 35+ 개.
- Phase 1 (프로젝트 셋업) → Phase 5 (React 루트 전환) 완료.

### Removed
- Thymeleaf 템플릿 69개 (~24,000 줄 HTML / ~15,000 줄 JS).

---

## 이전 버전

v3.x 이하의 자세한 릴리즈 히스토리는 [README.md](./README.md) 의
"버전 히스토리" 섹션을 참조하세요. 주요 마일스톤:

- **v3.x** — 사용자별 기능 권한 + 대시보드 + AES 암호화
- **v2.8** — SSE 실시간 알림 push (기존 60초 폴링 대체)
- **v2.4** — AI 채팅 + 분석 결과 댓글
- **v2.0** — 통합 워크스페이스 + 분석 파이프라인
- **v1.8** — 배치 리뷰
- **v1.0** — 초기 릴리즈 (SQL 리뷰 + 코드 리뷰 + Spring Boot Starter)

---

[4.2.7]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.6...v4.2.7
[4.2.6]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.5...v4.2.6
[4.2.5]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.4...v4.2.5
[4.2.4]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.3...v4.2.4
[4.2.3]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.2...v4.2.3
[4.2.2]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.0...v4.2.2
[4.2.0]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.1.0...v4.2.0
[4.1.0]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.0.0...v4.1.0
[4.0.0]: https://github.com/Sangmoo/Claude-Java-Toolkit/releases/tag/v4.0.0
