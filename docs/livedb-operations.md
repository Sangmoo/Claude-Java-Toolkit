# Live DB 운영 가이드 (#G3)

> v4.7.1 부터 도입된 **Live DB 직접 연결** 기능 (#G3 Phase 0~5) 의 활성화 / 권한 / 트러블슈팅 가이드. 운영팀(DBA) + ADMIN 사용자 대상.

---

## 1. 기능 개요

분석 페이지가 SQL 분석 시 백엔드가 **자동으로 EXPLAIN PLAN + 통계 + 인덱스 메타** 를 수집해 Claude system prompt 에 첨부 → *추측이 아닌 실데이터 기반* 답변. Oracle / PostgreSQL 1차 지원.

**적용 범위**: 6개 SQL 분석 페이지
- `/advisor` (SQL 리뷰)
- `/explain` (실행계획)
- `/sql/index-advisor` (인덱스 추천 + 시뮬레이션)
- `/sql-translate` (DB 번역)
- `/sql-batch` (배치 SQL)
- `/erd` (ERD)

---

## 2. 활성화 절차 (3-step)

### Step 1: 글로벌 feature flag 활성화

`application.yml` 또는 환경변수:
```yaml
toolkit:
  livedb:
    enabled: true                       # 기본 false
    default-timeout-seconds: 30
    max-rows: 1000
    max-calls-per-minute: 10            # 사용자 + 프로필 조합당 quota
```
또는 환경변수:
```bash
export TOOLKIT_LIVEDB_ENABLED=true
export TOOLKIT_LIVEDB_TIMEOUT=30
export TOOLKIT_LIVEDB_MAX_ROWS=1000
export TOOLKIT_LIVEDB_RATE_LIMIT=10
```

**Default OFF**: 운영 환경에서 자동 활성화 사고를 막기 위함. ADMIN 이 명시적으로 켜야 함.

### Step 2: read-only DB user 생성 + DbProfile 등록

ADMIN 사용자가 `/db-profiles` 페이지에서 *분석 전용* user 의 DbProfile 등록.

**중요**: 이 user 는 *읽기 전용* 권한만 가져야 함. 다음 권한이 필요:

#### Oracle
```sql
-- Phase 0~3 (컨텍스트 수집) — DBA 권한 시
GRANT SELECT ON dba_tables       TO ctk_analysis_user;
GRANT SELECT ON dba_indexes      TO ctk_analysis_user;
GRANT SELECT ON dba_ind_columns  TO ctk_analysis_user;
GRANT SELECT ON dba_tab_comments TO ctk_analysis_user;
GRANT SELECT ON v_$version       TO ctk_analysis_user;

-- DBA 권한 없을 때 — ALL_/USER_ 자동 fallback (별도 GRANT 불필요)

-- EXPLAIN PLAN 실행 권한
GRANT SELECT, INSERT ON sys.plan_table$ TO ctk_analysis_user;  -- 또는 PLAN_TABLE PUBLIC SYNONYM 사용

-- (옵션) 분석 대상 schema 의 SELECT 권한 — 본인 schema 면 불필요
GRANT SELECT ANY TABLE TO ctk_analysis_user;

-- Phase 4 (인덱스 시뮬레이터) 추가 권한
GRANT CREATE ANY INDEX TO ctk_analysis_user;  -- 또는 staging schema 한정
GRANT DROP   ANY INDEX TO ctk_analysis_user;
GRANT ALTER  SESSION   TO ctk_analysis_user;  -- optimizer_use_invisible_indexes 변경
```

#### PostgreSQL 11+
```sql
-- 컨텍스트 수집
GRANT SELECT ON pg_class, pg_namespace, pg_index, pg_attribute, pg_stats
       TO ctk_analysis_user;
GRANT SELECT ON pg_stat_user_tables TO ctk_analysis_user;

-- 분석 대상 schema 테이블 SELECT 권한
GRANT USAGE  ON SCHEMA app_schema TO ctk_analysis_user;
GRANT SELECT ON ALL TABLES IN SCHEMA app_schema TO ctk_analysis_user;

-- EXPLAIN 은 SELECT 권한만 있으면 자동 가능
```

### Step 3: 프로필별 Live 분석 활성화

**ADMIN 만 가능**:
```bash
# /db-profiles 페이지 또는 API 직접
curl -X POST -d 'enabled=true' \
     /db-profiles/{profileId}/live-analysis
```

활성화된 프로필은 `/db-profiles/active-live` 에서 노출되어, 일반 사용자가 분석 페이지 chip 에서 선택 가능.

> ⚠️ **명시적 활성화 의미**: "이 프로필의 user 가 read-only 권한만 가지고 있음" 을 ADMIN 이 보장한다는 의미. 코드 게이트 (SqlClassifier) + DB 권한 *이중 차단*.

---

## 3. 안전 모델 (다층 방어)

```
사용자 SQL → 분석 시작
    ↓
[L1] toolkit.livedb.enabled = false → kill switch ✋
    ↓
[L2] DbProfile.readOnlyForLiveAnalysis = false → 비활성 프로필 ✋
    ↓
[L3] CircuitBreaker.isOpen() → 회로 OPEN ✋ (10분 내 timeout 5건)
    ↓
[L4] RateLimiter.tryAcquire() → 분당 한도 초과 ✋
    ↓
[L5] SqlClassifier.isReadOnly() → DML/DDL/CALL 차단 ✋
    ↓
[L6] ReadOnlyJdbcTemplate — timeout 30s + maxRows 1000 강제
    ↓
실행 + audit_log + callStats 누적
```

---

## 4. 운영 모니터링

### `/admin/health` 카드

ADMIN 으로 로그인 후 `/admin/health` 페이지의 **Live DB 호출 통계** 카드:
- 글로벌 `✓ ENABLED` / `○ DISABLED` 칩
- 합계: 성공 / 실패 / timeout / 총 호출
- 프로필별 표: 이름 / 호출 카운트 / avg latency / 회로 상태
- 회로 OPEN 시 🔴 배지 + 자동 복구 남은 시간 + ADMIN 강제 복구 버튼

### REST API (ADMIN 전용)

| Method | Path | 용도 |
|--------|------|------|
| GET    | `/api/v1/admin/livedb/stats` | 통계 + 회로 상태 + 글로벌 설정 |
| POST   | `/api/v1/admin/livedb/breaker/{profileId}/close` | 회로 강제 복구 |
| POST   | `/api/v1/admin/livedb/stats/reset` | 통계 + 회로 모두 초기화 |

### audit_log

모든 facade-level Live DB 호출은 `audit_log` 테이블에 1 row 기록:
- `endpoint = "[livedb] livedb.fetch profile=<name>"` 또는 `"[livedb] livedb.simulate-index profile=<name> indexes=<n>"`
- `method = "INTERNAL"`
- `username` / `durationMs` / `statusCode` (200/500/504)

외부감사 시 SQL 한 줄로 추적 가능:
```sql
SELECT * FROM audit_log
WHERE endpoint LIKE '[livedb] %'
ORDER BY created_at DESC;
```

---

## 5. 트러블슈팅

### Q1. 분석 페이지 chip 에 "Live DB" 가 안 보임
- 원인: SQL 페이지가 아니거나 (`/advisor` 등 6개 외 페이지), 활성 프로필 0개
- 해결:
  1. ADMIN 로그인 → `/db-profiles` → 프로필 생성
  2. 해당 프로필의 "Live 분석 활성화" 토글 ON
  3. `application.yml` 의 `toolkit.livedb.enabled=true` 확인

### Q2. 회로가 자주 OPEN 됨 (🔴 표시)
- 원인: 10분 내 timeout 5건 발생 → 자동 차단
- 일반적 원인:
  - DB 부하 (운영 시간대)
  - 네트워크 지연
  - 분석 대상 SQL 이 너무 무거움 (대용량 테이블 + 복잡 쿼리)
- 해결:
  1. `/admin/health` 에서 timeout 빈도 확인 (특정 프로필인지 / 시간대 패턴인지)
  2. timeout 늘리기: `TOOLKIT_LIVEDB_TIMEOUT=60` (기본 30)
  3. DBA 와 협의 — 분석 user 에 statement priority lower 부여
  4. 회로 강제 복구는 *문제 해결 후만* — 그렇지 않으면 즉시 재차단

### Q3. "DBA_TABLES 권한 없음" warning 자주 발생
- 원인: 분석 user 에 DBA_TABLES SELECT 권한 미부여
- 동작: 자동으로 ALL_TABLES → USER_TABLES fallback. 단, 다른 schema 테이블 통계는 못 봄
- 해결: DBA 에게 `GRANT SELECT ON DBA_TABLES TO ctk_analysis_user` 요청 (read-only 권한이라 안전)

### Q4. 인덱스 시뮬레이션 시 "CREATE INDEX 권한 없음"
- Phase 4 의 INVISIBLE INDEX 시뮬레이션은 임시 인덱스 생성 필요
- 해결:
  - `GRANT CREATE ANY INDEX, DROP ANY INDEX TO ctk_analysis_user` (강한 권한)
  - 또는 staging schema 한정: `GRANT CREATE INDEX ON staging.* TO ctk_analysis_user`

### Q5. PLAN_TABLE 잔여 row 가 누적되는지?
- Oracle 의 PLAN_TABLE 은 일반적으로 GLOBAL TEMPORARY TABLE → 세션 종료 시 자동 정리
- DriverManagerDataSource 는 connection pool 이 아니라 매 호출마다 새 세션 → 누적 X
- 만약 legacy 환경에서 PLAN_TABLE 이 GLOBAL TEMPORARY 가 아니면:
  ```sql
  DELETE FROM plan_table WHERE statement_id LIKE 'ctk_%';
  ```

### Q6. DbProfile password 변경 후에도 옛 password 로 동작
- v4.7.1 부터 자동 invalidate (B1 보강) — 이슈 해결됨
- 이전 버전이라면 application restart 필요

### Q7. 분석 결과에 "Live DB 컨텍스트" 가 안 첨부됨
- 점검 순서:
  1. chip 이 "Live DB" 표시 + 프로필 선택됨 ?
  2. 프로필이 회로 OPEN 상태인가? (chip 🔴 표시)
  3. RateLimiter 한도 초과? (응답 warning 확인)
  4. `application.yml` 의 `enabled=true` ?
  5. ADMIN 가 해당 프로필 "Live 분석" 토글 ON ?
- 빠른 진단: `/admin/health` 카드의 마지막 호출 시각 확인

---

## 6. 보안 모델 핵심 요약

| 위협 | 대응 |
|------|------|
| 운영 SQL 변조 (DELETE/UPDATE 등) | **SqlClassifier** 가 SELECT/EXPLAIN/DESC/WITH 만 화이트리스트 통과. 이중 statement / 주석 우회 / 문자열 리터럴 안 ; 까지 처리. |
| 무거운 쿼리로 DB 부하 | `setQueryTimeout(30s)` 강제 + `setMaxRows(1000)` 메모리 보호 + RateLimiter 분당 quota |
| 분석 user 권한 escalation | Default OFF + DbProfile 별 명시적 ADMIN 토글 + read-only user 사용 권장 |
| 인덱스 시뮬레이션 잔여 인덱스 | `CTK_SIM_*` prefix 강제 + try/finally cleanup + INVISIBLE 옵션 강제 (운영 영향 0) |
| 운영 사고 (DB 장애) | CircuitBreaker — 10분 내 timeout 5건 → 5분 자동 비활성, 자동 복구 |
| 외부감사 추적 | facade-level audit_log 자동 기록 (endpoint / username / duration / status) |

---

## 7. 비활성화 (Kill Switch)

운영 사고 시:
```bash
# 즉시 비활성 (1분 내 적용)
export TOOLKIT_LIVEDB_ENABLED=false
# application restart
```

또는 특정 프로필만:
```bash
curl -X POST -d 'enabled=false' \
     /db-profiles/{profileId}/live-analysis
```
