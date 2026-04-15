# Security Policy & Permission Matrix

> Claude Java Toolkit 의 권한 정책 및 민감 엔드포인트 감사 결과.
> 최종 감사: **v4.2.7** (Phase 1 & Item 5 Round)

## 보고 채널

**취약점 발견시**: 공개 이슈로 올리지 말고 [프로젝트 관리자](https://github.com/Sangmoo) 에게 직접 연락해주세요. 영향 범위 확인 후 패치 릴리즈로 대응합니다.

---

## 역할 정의

| Role | 설명 | 용도 |
|------|-----|------|
| **ADMIN** | 전체 관리자 | 사용자 관리, 시스템 설정, 전사 데이터 관리 |
| **REVIEWER** | 리뷰어 | 팀 리뷰 승인/거절, 스케줄 관리, 배치 운영 |
| **VIEWER** | 일반 사용자 | 본인 분석/리뷰 작성, 본인 데이터 읽기 |

기본 로그인 계정: `admin / admin1234` (최초 설치 후 반드시 변경)

---

## 인증 & 세션

- **방식**: Spring Security Form Login (세션 쿠키)
- **2FA**: `TwoFactorAuthHandler` — 사용자별 활성화 가능
- **로그인 실패 잠금**: 5회 → 10분 (`LoginAttemptHandler`)
- **세션**: 동시 1개 제한 + `invalidateHttpSession` 로그아웃
- **비밀번호**: BCrypt 해싱
- **REST 인증**: 세션 쿠키 기반 (`/api/v1/auth/login` JSON endpoint)
- **API Key**: 선택적 — 사용자별 personalApiKey 로 `X-API-Key` 헤더 인증

## CSRF 정책

프로젝트 전역 CSRF **전면 ignore** (SPA + 세션 쿠키 모델 기반). `SecurityConfig.ignoringAntMatchers` 목록 참조. 현재 정책 근거:
- SameSite 쿠키 기본 + `credentials: include` 패턴
- API 서버와 프론트가 동일 origin (Spring Boot 가 React 정적 리소스 함께 서빙)
- 외부 사이트에서 사용자 대신 임의 state-changing 요청을 보내기 어려움

추가 보강을 원한다면: Double Submit Cookie 또는 Origin/Referer 헤더 검증 추가 가능.

---

## 권한 매트릭스

아래는 v4.2.7 감사 시점에서 **본인 소유 + role 체크** 가 필요한 민감 엔드포인트 목록입니다. `SecurityConfig` 의 경로 기반 규칙으로 먼저 필터되고, 개별 컨트롤러에서 소유자 체크가 추가됩니다.

### 🔐 ADMIN 전용 경로 (SecurityConfig antMatchers 로 차단)

| Path | 메서드 | 설명 |
|------|------|------|
| `/admin/**` | * | Thymeleaf 관리자 페이지 (레거시 경로) |
| `/api/v1/admin/**` | * | 관리자 REST API (v4.2.7 추가) |
| `/settings`, `/settings/**` | * | 토큰/모델/API 키 설정 |
| `/security`, `/security/**` | * | 보안 설정 (로그인 정책, IP 화이트리스트 등) |

### 🔶 REVIEWER+ 경로

| Path | 메서드 | 설명 |
|------|------|------|
| `/settings/prompts`, `/settings/prompts/**` | * | 프롬프트 편집 |

### 🔸 사용자별 소유자 체크 (컨트롤러 레벨)

| Endpoint | 메서드 | 허용 대상 | 구현 파일 |
|----------|------|---------|----------|
| `/favorites/{id}/delete` | POST | Favorite.username == principal | `FavoriteController.java` |
| `/favorites/star` | POST | 인증된 사용자 (소유자로 저장) | `FavoriteController.java` |
| `/favorites/clear` 🆕 | POST | **ADMIN only** | `FavoriteController.java` |
| `/history/{id}/delete` | POST | **ADMIN/REVIEWER only** | `ReviewHistoryController.java` |
| `/history/clear` 🆕 | POST | **ADMIN only** | `ReviewHistoryController.java` |
| `/history/{id}/review-status` | POST | **REVIEWER/ADMIN only** | `ReviewStatusController.java` |
| `/history/{historyId}/comments/{commentId}/delete` | POST | 본인 OR ADMIN | `ReviewCommentController.java` |
| `/notifications/{id}/delete` | POST | Notification.recipient == principal | `NotificationController.java` |
| `/notifications/delete-all` | POST | principal 본인 알림만 | `NotificationController.java` |
| `/chat/sessions/{id}/delete` | POST | Session.owner == principal | `ChatController.java` |
| `/chat/sessions/{id}/clear` | POST | Session.owner == principal | `ChatController.java` |
| `/harness/batch/status/{id}` 🆕 | DELETE | **ADMIN/REVIEWER only** | `HarnessBatchController.java` |
| `/harness/batch/history/{id}` 🆕 | DELETE | **ADMIN/REVIEWER only** | `HarnessBatchController.java` |
| `/schedule/save` 🆕 | POST | **ADMIN/REVIEWER only** | `ScheduleController.java` |
| `/schedule/{id}/toggle` 🆕 | POST | **ADMIN/REVIEWER only** | `ScheduleController.java` |
| `/schedule/{id}/run` 🆕 | POST | **ADMIN/REVIEWER only** | `ScheduleController.java` |
| `/schedule/{id}/delete` 🆕 | POST | **ADMIN/REVIEWER only** | `ScheduleController.java` |
| `/pipelines/{id}/delete` | POST | Pipeline 생성자 OR ADMIN | `PipelineController.java` |
| `/shared-config/{id}/delete` | POST | Config 생성자 OR ADMIN | `SharedConfigController.java` |
| `/review-requests/{id}/delete` | POST | Request 생성자 OR ADMIN | `ReviewRequestController.java` |

**🆕** 표시는 **v4.2.7 감사에서 추가/보강**된 항목입니다.

---

## v4.2.7 감사에서 발견한 구멍

### 1. 권한 체크 완전 부재 (Critical)

| 엔드포인트 | 증상 | 조치 |
|---------|------|------|
| `POST /favorites/clear` | 로그인만 하면 전사 즐겨찾기 전부 삭제 가능 | ADMIN 전용으로 제한 |
| `POST /history/clear` | 로그인만 하면 전사 이력 전부 삭제 가능 | ADMIN 전용으로 제한 |
| `DELETE /harness/batch/history/{id}` | 권한 체크 없이 배치 이력 삭제 가능 | ADMIN/REVIEWER 로 제한 |
| `DELETE /harness/batch/status/{batchId}` | 권한 체크 없이 배치 상태 삭제 가능 | ADMIN/REVIEWER 로 제한 |
| `POST /schedule/save`, `/toggle`, `/run`, `/delete` | VIEWER 도 팀 공용 스케줄 조작 가능 | ADMIN/REVIEWER 로 제한 |

### 2. 프론트 사용처 없는 레거시 경로

`/favorites/clear`, `/history/clear` 는 프론트에서 사용하지 않는 레거시 엔드포인트였습니다. 삭제 대신 ADMIN-only 로 유지 — 운영자가 필요시 DB 정리용으로 쓸 수 있도록.

### 3. 이미 Phase 1 에서 해결된 항목 (재확인)

| 엔드포인트 | Phase 1 에서 추가된 방어 |
|---------|------------------------|
| `POST /favorites/{id}/delete` | Authentication 기반 소유자 체크 + HTTP 403 |
| `POST /history/{id}/delete` | `isUserInRole("ADMIN"/"REVIEWER")` 체크 → VIEWER 는 403 |
| `POST /history/.../comments/{id}/delete` | 본인 OR ADMIN (주석과 구현 일치) |
| `POST /favorites/star` | `(username, historyId)` 중복 저장 방지 |
| `GET  /api/v1/admin/**` | SecurityConfig `antMatchers` 에 `.hasRole("ADMIN")` 추가 |

---

## 민감 데이터 보호

### 입력 검증
- 분석 파이프라인 사용자 입력 → Claude API 전송 전 trim + length 체크
- SQL/코드 입력은 **실행하지 않음** (AI 분석용 텍스트 취급)
- 업로드 파일 없음 (모든 입력은 텍스트 body)

### 출력 정화
- Markdown 렌더는 `react-markdown` + `remark-gfm` 사용 (HTML raw 허용 X)
- 댓글 본문 `whiteSpace: pre-wrap` 으로 렌더 — XSS 위험 없음
- 멘션 드롭다운 username 은 정규식 `[A-Za-z0-9_.\-]+` 로만 허용

### 저장 데이터
- 비밀번호: BCrypt 해싱 (`BCryptPasswordEncoder`)
- API Key: `PersonalApiKeyService` 에서 AES 암호화 (마이그레이션 필요)
- 이력 / 댓글 / 알림: 평문 저장 (내부 도구 — 데이터 분류 등급 낮음)

### 네트워크
- **TLS**: 자체 서명 / 사내 CA 지원 (Docker 이미지에 `update-ca-certificates`)
- **Outbound**: `api.anthropic.com` HTTPS 만 — TLS 1.2/1.3 강제 (`-Dhttps.protocols`)
- **Forward Proxy**: `HTTP_PROXY` / `HTTPS_PROXY` 환경변수 지원 (사내망 전용)

---

## 회귀 방지

v4.2.7 에서 다음 스모크 테스트가 추가되어 **매 빌드마다** 권한 경로를 자동 검증합니다:

| 테스트 | 커버 범위 |
|------|---------|
| `SecuritySmokeTests.adminUsers_*` | `/api/v1/admin/**` × VIEWER/REVIEWER/ADMIN |
| `SecuritySmokeTests.historyDelete_*` | `POST /history/{id}/delete` × VIEWER/REVIEWER |
| `SecuritySmokeTests.adminUsers_unauthenticated_blocked` | 비로그인 차단 |
| `SecuritySmokeTests.authLogin_permitAll` | 로그인 엔드포인트 permitAll 유지 |
| `FavoriteDedupSmokeTest` | `(username, historyId)` 중복 저장 방지 |
| `MentionPatternTest` | 멘션 정규식 — 이메일 제외 등 |

실행: `mvn test -pl claude-toolkit-ui -Dtest="*SmokeTest*,*SmokeTests"`

---

## 향후 보안 로드맵

- [ ] 감사 로그 확장 — 현재 로그인/분석만. 삭제·권한 변경·설정 변경도 기록
- [ ] Flyway 도입 — 스키마 변경 이력 추적 (현재 `ddl-auto: update`)
- [ ] IP 화이트리스트 상세 로깅
- [ ] 2FA 강제 정책 (ADMIN 필수)
- [ ] API Key rotation 정책
- [ ] OAuth2 (Google/MS Entra) 로그인 지원

---

## 참고 문서

- [CHANGELOG.md](./CHANGELOG.md) — 버전별 보안 변경사항
- [README.md](./README.md) "버전 히스토리" 섹션 — 상세 배경
