# Harness Prompts

Phase A에서 도입된 하네스 파이프라인의 외부화된 프롬프트 디렉토리입니다.

## 경로 규약

```
prompts/harness/{harnessName}/{stageName}.md
```

- `{harnessName}` — 하네스 식별자 (kebab-case, [a-zA-Z0-9_-]만 허용)
- `{stageName}`  — stage 식별자 (kebab-case, [a-zA-Z0-9_-]만 허용)

## 등록 예정 하네스

| harnessName        | 도입 단계 | 비고                          |
|--------------------|----------|-------------------------------|
| `code-review`      | (기존)   | HarnessReviewService — 인라인 프롬프트 유지 |
| `log-rca`          | Phase D  | 4-stage RCA 분석              |
| `sp-migration`     | Phase B  | Oracle SP → Java/MyBatis 변환 |
| `sql-optimization` | Phase C  | SQL 성능 최적화               |

## Stage 표준 이름

기본 4-stage 패턴:
- `analyst.md`  — 입력 분석
- `builder.md`  — 산출물 작성
- `reviewer.md` — 변경 검토
- `verifier.md` — 정적 검증

확장은 자유 — 하네스마다 stage 수와 이름은 자체 정의 가능.

## 로딩

`PromptLoader.load(harnessName, stageName)` — classpath에서 읽고 in-memory 캐시.
`PromptLoader.clearCache()` — 운영 중 프롬프트 수정 시 호출.

## 보안

`PromptLoader`가 식별자에 대해 `[a-zA-Z0-9_-]+`만 허용 — 경로 인젝션 (`../`, 슬래시) 차단.
