# 📸 스크린샷 캡처 가이드

이 디렉토리는 README + GitHub Pages 에 사용되는 UI 스크린샷을 보관합니다.
모든 파일은 **사람이 직접 캡처**합니다 (자동 생성 X).

## 📋 캡처 체크리스트

아래 11개 스크린샷을 동일한 파일명으로 이 디렉토리에 저장해주세요.
체크박스에 ✅ 표시하여 진행 상황을 추적할 수 있습니다.

### 핵심 (4개)

- [ ] **`01-home-dashboard.png`**
  - **URL**: `/`
  - **무엇이 보여야**: Hero (인사말 + 시스템 상태) + 도구 카드 그리드 + 팀 활동 피드
  - **데이터 사전 준비**: 이력 5건+ 생성 (팀 활동 피드가 비어있지 않도록)

- [ ] **`02-sql-review.png`**
  - **URL**: `/advisor`
  - **무엇이 보여야**: SQL 입력 + 리뷰 결과 (severity 마커 [HIGH/MEDIUM/LOW] 보이게)
  - **샘플 SQL**:
    ```sql
    SELECT * FROM ORDERS WHERE customer_id IN
      (SELECT id FROM CUSTOMERS WHERE name LIKE '%kim%');
    ```

- [ ] **`03-pipeline-graph.png`**
  - **URL**: `/pipelines/{id}/edit` → 우측 패널 "📊 인터랙티브 그래프" 탭 클릭
  - **무엇이 보여야**: reactflow 그래프 (병렬 노드 같은 컬럼, 순차 노드 가로 진행)
  - **데이터 사전 준비**: 파이프라인 1개 생성 (3~4 step, 1개는 `parallel: true`)

- [ ] **`04-harness-result.png`**
  - **URL**: `/harness`
  - **무엇이 보여야**: 4단계 결과 (분석/개선/검토/검증 탭) + Diff 뷰 + 품질 점수
  - **샘플 코드**: 간단한 Java Service 클래스

### v4.3.0 신규 (4개)

- [ ] **`05-index-advisor.png`** ✨
  - **URL**: `/sql/index-advisor`
  - **무엇이 보여야**: 상단 대상 DB 배너 + Monaco SQL 에디터 + 결과 카드 (기존 인덱스 + 신규 DDL 추천)
  - **샘플 SQL**:
    ```sql
    SELECT o.id, u.email FROM orders o
    JOIN users u ON o.user_id = u.id
    WHERE u.email = 'test@example.com' AND o.status = 'NEW';
    ```

- [ ] **`06-cost-optimizer.png`** ✨
  - **URL**: `/admin/cost-optimizer` (ADMIN 로그인 필요)
  - **무엇이 보여야**: 4개 요약 카드 + 비용 비교 막대 차트 + 추천 테이블
  - **데이터 사전 준비**: 이력 10건+ (토큰 정보 있는 것 — `inputTokens`/`outputTokens` 채워진 항목)

- [ ] **`07-language-switcher.png`** ✨
  - **URL**: 아무 페이지 (예: `/`)
  - **무엇이 보여야**: TopBar 우측 🌐 버튼 클릭 → 5개 언어 (한국어 / English / 日本語 / 简体中文 / Deutsch) 펼쳐진 드롭다운
  - **사이즈 권장**: 800×400 (좁은 가로 — 드롭다운만 잘 보이게)

- [ ] **`08-dashboard-edit.png`** ✨
  - **URL**: `/` → 우측 상단 "대시보드 편집" 클릭
  - **무엇이 보여야**: 위젯에 점선 테두리 (편집 모드 표시) + 위젯 토글 버튼 (👁 / 🚫) + ⚙️ 설정 버튼
  - **추가**: 도구 카드 그리드의 ⚙️ 클릭 → 설정 모달이 살짝 보이게 캡처해도 좋음

### v4.4.0 신규 (3개)

- [ ] **`09-swagger-ui.png`** ✨
  - **URL**: `/swagger-ui.html` (ADMIN 로그인 필요)
  - **무엇이 보여야**: Swagger UI 메인 화면 + 좌측 11개 카테고리 (Auth, SQL, Code, Doc, ERD, Pipeline, History, Dashboard, Export, Health, Admin) 펼쳐진 모습
  - **추가**: SQL 카테고리 1개 펼친 상태로 캡처하면 더 좋음

- [ ] **`10-error-log.png`** ✨
  - **URL**: `/admin/error-log` (ADMIN 로그인 필요)
  - **무엇이 보여야**: 카드 리스트 (발생 횟수 + 예외 클래스 + 경로 + 메시지) + 미해결 배지 + "30s 자동" 체크박스
  - **데이터 사전 준비**: 일부러 잘못된 SQL을 입력하거나 잘못된 API 호출 → 에러 로그 3~5건 생성

- [ ] **`11-grafana.png`** ✨
  - **URL**: `http://localhost:3000` → "Claude Toolkit Overview" 대시보드
  - **무엇이 보여야**: 상위 6 패널 (Claude API 호출 / 토큰 / 모델별 추이 등)
  - **사전 준비**: `docker-compose --profile monitoring up -d` 실행

## 🎯 캡처 권장 사항

| 항목 | 권장 |
|------|------|
| **브라우저 크기** | 1280×800 또는 1440×900 (16:10) |
| **테마** | **다크 모드** (대비가 좋아 더 선명) |
| **언어** | 한글 (주 타깃 사용자) |
| **포맷** | **PNG** (jpg 는 텍스트가 흐려짐) |
| **개당 용량** | 100~500KB (1MB 초과 시 [tinypng.com](https://tinypng.com) 으로 압축) |
| **도구** | Windows: `Win + Shift + S` 또는 Snipping Tool / Mac: `⌘+Shift+5` |

## 📦 작업 흐름

```bash
# 1. 앱 실행 + 데이터 채우기
docker-compose --profile monitoring up -d
# 브라우저에서 admin/admin1234 로 로그인 후 샘플 데이터 생성

# 2. 11개 스크린샷 캡처 → docs/screenshots/ 에 저장
# (위 파일명 그대로)

# 3. (선택) 압축
# 모든 PNG 를 tinypng.com 에 드래그 → 압축본 다운로드 → 같은 위치 덮어쓰기

# 4. Git 에 커밋 + 푸시
git add docs/screenshots/*.png
git commit -m "docs: v4.4.0 스크린샷 추가 (11개)"
git push
```

## 🔗 임베드된 위치

이 스크린샷들은 다음 위치에서 자동 표시됩니다:

| 파일 | 임베드 형태 |
|------|-----------|
| `README.md` | "✨ 데모" 섹션 — 4 핵심 + 4 v4.3 + 3 v4.4 분류로 갤러리 |
| `docs/index.html` | Hero 섹션 직후 갤러리 + Features 섹션 카드 옆 작은 미리보기 |

> ⚠️ **파일이 없으면**: README/docs 에 깨진 이미지 아이콘 표시. 캡처 후 즉시 push 하면 GitHub Pages 자동 반영.

## 📝 다 채울 시간이 없다면?

먼저 **핵심 4개 (01~04)** 만이라도 캡처하면 README 의 첫인상이 크게 좋아집니다.
나머지 7개는 v4.3/v4.4 기능을 보여주는 부가 자료이므로 점진적으로 추가해도 됩니다.

빈 파일 슬롯의 임베드 코드는 이미 README 에 포함되어 있어, 파일만 추가하면
즉시 표시됩니다. 별도 코드 수정 불필요.
