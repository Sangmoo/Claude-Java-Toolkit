/**
 * v4.7.x — #2 분석 체이닝 (Analysis Chaining).
 *
 * <p>분석 결과를 본 직후 자연스러운 후속 단계를 제안한다. 예: SQL 리뷰 결과 →
 * 실행계획 분석 → 인덱스 추천 → 다른 DB 로 번역. 각 페이지에서 결과를 복사해
 * 다음 페이지에 붙여넣는 수동 작업을 제거.
 *
 * <p><b>전달 메커니즘:</b> sessionStorage 사용 (URL 쿼리 X — 큰 입력은 URL 길이
 * 제한 + 인코딩 비용 + 로그 노출 위험). 키는 {@link CHAIN_PAYLOAD_KEY}, target
 * 페이지가 mount 시점에 한번만 읽고 즉시 삭제 (1회용).
 *
 * <p><b>설계 원칙:</b>
 * <ul>
 *   <li>입력 변환은 항상 client-side 함수 — 서버 호출 없음, 즉시 동작</li>
 *   <li>동일 input 그대로 전달이 가장 흔한 패턴 (helper {@link sameInput})</li>
 *   <li>2~4개 정도만 노출 — 너무 많으면 선택 피로 발생</li>
 * </ul>
 */

export interface ChainSuggestion {
  /** 라우터 경로 (e.g. '/explain') */
  targetPath: string
  /** 이모지 + 짧은 라벨 (e.g. '⚡ 실행계획 분석') */
  label: string
  /** 1줄 부연설명 — hover 가능 */
  description: string
  /**
   * payload 변환기. 입력은 (resultText, originalInput) 두 인자.
   * 반환값이 다음 페이지의 textarea 에 들어감.
   *
   * <p>대부분의 경우 originalInput 을 그대로 넘기는 것이 정답 (분석 결과는 사람용
   * 마크다운이라 다른 분석기에 입력으로 넣으면 노이즈가 됨).
   */
  buildPayload: (resultText: string, originalInput: string) => string
}

/** 동일 입력 그대로 전달 — 가장 흔한 패턴 */
const sameInput = (_r: string, input: string) => input

/**
 * 분석 결과 markdown 에서 코드 블록만 추출 (예: 변환된 SQL/Java 결과를
 * 다음 페이지 입력으로 사용). 첫 ``` 블록을 우선 반환 — 없으면 originalInput fallback.
 */
const extractFirstCodeBlock = (result: string, fallback: string): string => {
  const m = result.match(/```(?:[a-zA-Z0-9]*)?\n([\s\S]*?)```/)
  if (m && m[1]) return m[1].trim()
  return fallback
}

/**
 * feature key → 추천 후속 단계.
 * Analysis 페이지의 config.feature 와 동일 키 사용.
 */
export const ANALYSIS_CHAIN: Record<string, ChainSuggestion[]> = {
  // ── SQL 계열 ────────────────────────────────────────────────────────────
  sql_review: [
    {
      targetPath: '/explain',
      label: '⚡ 실행계획 분석',
      description: '리뷰한 SQL 의 실제 실행계획 + 비용 추정',
      buildPayload: sameInput,
    },
    {
      targetPath: '/sql/index-advisor',
      label: '🔎 인덱스 추천',
      description: '추가/조정해야 할 인덱스 제안',
      buildPayload: sameInput,
    },
    {
      targetPath: '/sql-translate',
      label: '🌐 다른 DB 로 번역',
      description: 'Oracle ↔ MySQL ↔ PostgreSQL ↔ MSSQL',
      buildPayload: sameInput,
    },
    {
      targetPath: '/erd',
      label: '📐 ERD 분석',
      description: 'SQL 에서 참조한 테이블 관계도',
      buildPayload: sameInput,
    },
  ],

  explain_plan: [
    {
      targetPath: '/explain/compare',
      label: '🔀 다른 플랜과 비교',
      description: '튜닝 전후 / 다른 환경 실행계획 비교',
      buildPayload: sameInput,
    },
    {
      targetPath: '/sql/index-advisor',
      label: '🔎 인덱스 추천',
      description: '병목 단계에 도움될 인덱스 제안',
      buildPayload: sameInput,
    },
    {
      targetPath: '/advisor',
      label: '📝 SQL 리뷰',
      description: '쿼리 자체 품질도 검토',
      buildPayload: sameInput,
    },
  ],

  sql_translate: [
    {
      targetPath: '/advisor',
      label: '📝 번역된 SQL 리뷰',
      description: '대상 DB 문법으로 번역된 결과를 검증',
      buildPayload: extractFirstCodeBlock,
    },
    {
      targetPath: '/explain',
      label: '⚡ 실행계획 분석',
      description: '번역 후 성능 차이 확인',
      buildPayload: extractFirstCodeBlock,
    },
  ],

  // ── Java/Code 계열 ──────────────────────────────────────────────────────
  code_review: [
    {
      targetPath: '/harness',
      label: '🔬 코드 리뷰 하네스 (심층)',
      description: '4단계 파이프라인으로 더 깊은 리뷰',
      buildPayload: sameInput,
    },
    {
      targetPath: '/complexity',
      label: '📊 복잡도 분석',
      description: 'Cyclomatic / Halstead / 유지보수 점수',
      buildPayload: sameInput,
    },
    {
      targetPath: '/depcheck',
      label: '🔗 의존성 분석',
      description: '클래스/패키지 의존 그래프',
      buildPayload: sameInput,
    },
    {
      targetPath: '/docgen',
      label: '📄 기술 문서 생성',
      description: '코드를 설명하는 문서 자동 생성',
      buildPayload: sameInput,
    },
  ],

  complexity: [
    {
      targetPath: '/harness',
      label: '🔬 코드 리뷰 하네스',
      description: '복잡한 부분을 어떻게 리팩터링할지 제안',
      buildPayload: sameInput,
    },
    {
      targetPath: '/converter',
      label: '🔄 코드 변환',
      description: '다른 패러다임/프레임워크로 리팩터링',
      buildPayload: sameInput,
    },
  ],

  converter: [
    {
      targetPath: '/harness',
      label: '🔬 변환된 코드 리뷰',
      description: '변환 결과의 품질을 검증',
      buildPayload: extractFirstCodeBlock,
    },
    {
      targetPath: '/complexity',
      label: '📊 복잡도 비교',
      description: '변환 전후 복잡도 비교',
      buildPayload: extractFirstCodeBlock,
    },
  ],

  // ── 문서/생성 계열 ──────────────────────────────────────────────────────
  doc_gen: [
    {
      targetPath: '/api_spec',
      label: '📜 API 명세 생성',
      description: '같은 코드의 API 명세도 함께',
      buildPayload: sameInput,
    },
    {
      targetPath: '/harness',
      label: '🔬 코드 리뷰',
      description: '문서화한 코드의 품질 검증',
      buildPayload: sameInput,
    },
  ],

  api_spec: [
    {
      targetPath: '/mockdata',
      label: '🎲 Mock 데이터 생성',
      description: 'API 스키마에 맞는 테스트 데이터',
      buildPayload: sameInput,
    },
    {
      targetPath: '/docgen',
      label: '📄 기술 문서',
      description: 'API 사용 예시가 들어간 문서',
      buildPayload: sameInput,
    },
  ],

  // ── ERD / DB 계열 ───────────────────────────────────────────────────────
  erd_analysis: [
    {
      targetPath: '/mockdata',
      label: '🎲 Mock 데이터',
      description: 'ERD 의 테이블에 맞는 테스트 데이터',
      buildPayload: sameInput,
    },
    {
      targetPath: '/migration',
      label: '🔁 DB 마이그레이션',
      description: '다른 DBMS 로 ERD 이전',
      buildPayload: sameInput,
    },
    {
      targetPath: '/sql-batch',
      label: '🗂 SQL 배치 분석',
      description: 'ERD 위에서 동작하는 쿼리들 검토',
      buildPayload: sameInput,
    },
  ],

  db_migration: [
    {
      targetPath: '/erd',
      label: '📐 ERD 분석',
      description: '마이그레이션 후 ERD 검증',
      buildPayload: extractFirstCodeBlock,
    },
    {
      targetPath: '/sql-translate',
      label: '🌐 SQL 번역',
      description: '관련 쿼리들도 새 DB 로 번역',
      buildPayload: sameInput,
    },
  ],

  // ── 로그/이슈 계열 ──────────────────────────────────────────────────────
  log_analysis: [
    {
      targetPath: '/regex',
      label: '🔎 정규식 생성',
      description: '발견된 패턴을 추출하는 정규식',
      buildPayload: sameInput,
    },
    {
      targetPath: '/maskgen',
      label: '🛡 마스킹 스크립트',
      description: '로그에서 발견된 민감정보 마스킹',
      buildPayload: sameInput,
    },
  ],

  // ── 보안/마스킹 계열 ────────────────────────────────────────────────────
  input_masking: [
    {
      targetPath: '/maskgen',
      label: '📜 마스킹 스크립트 생성',
      description: '같은 규칙을 코드로 자동화',
      buildPayload: sameInput,
    },
  ],

  mask_gen: [
    {
      targetPath: '/input-masking',
      label: '🛡 입력 마스킹 검증',
      description: '생성된 스크립트로 실제 마스킹 시뮬레이션',
      buildPayload: extractFirstCodeBlock,
    },
  ],

  // ── 마이그레이션 계열 ──────────────────────────────────────────────────
  spring_migrate: [
    {
      targetPath: '/depcheck',
      label: '🔗 의존성 분석',
      description: '마이그레이션 후 의존성 변경 확인',
      buildPayload: extractFirstCodeBlock,
    },
    {
      targetPath: '/harness',
      label: '🔬 코드 리뷰',
      description: '마이그레이션 결과 품질 검증',
      buildPayload: extractFirstCodeBlock,
    },
  ],

  dep_check: [
    {
      targetPath: '/migrate',
      label: '🚀 Spring 마이그레이션',
      description: '오래된 의존성 → 최신 Spring 으로 이전',
      buildPayload: sameInput,
    },
  ],

  // ── 하네스 / 데이터 흐름 ────────────────────────────────────────────────
  harness_review: [
    {
      targetPath: '/complexity',
      label: '📊 복잡도 분석',
      description: '리팩터링 우선순위 보강',
      buildPayload: sameInput,
    },
    {
      targetPath: '/docgen',
      label: '📄 문서 생성',
      description: '리뷰가 끝난 코드의 기술 문서',
      buildPayload: sameInput,
    },
  ],
}

/**
 * 특정 feature 의 후속 추천 목록 반환. 매핑 없으면 빈 배열.
 */
export function getNextStepsFor(feature: string): ChainSuggestion[] {
  return ANALYSIS_CHAIN[feature] || []
}

// ── sessionStorage handoff ─────────────────────────────────────────────────

/**
 * sessionStorage 키 — 페이지 간 입력 전달용.
 * `analysisChain.payload` JSON: { value: string, sourceFeature: string, ts: number }
 */
export const CHAIN_PAYLOAD_KEY = 'analysisChain.payload'

export interface ChainPayload {
  value: string
  sourceFeature: string
  ts: number
}

/** 다음 페이지로 입력 전달 — 호출 후 즉시 navigate 를 하면 된다. */
export function pushChainPayload(value: string, sourceFeature: string) {
  try {
    const payload: ChainPayload = { value, sourceFeature, ts: Date.now() }
    sessionStorage.setItem(CHAIN_PAYLOAD_KEY, JSON.stringify(payload))
  } catch {
    /* sessionStorage 불가 환경 — 사용자가 수동 복사해야 함 */
  }
}

/**
 * 다음 페이지가 mount 시점에 호출. 1회용 — 읽으면 즉시 삭제하여
 * 다른 페이지로 이동했다가 돌아왔을 때 같은 입력이 또 들어가는 것을 방지.
 *
 * <p>너무 오래된 payload (60초 초과) 는 무시. 사용자가 chain 으로 이동했지만
 * 중간에 다른 곳을 거쳐왔다는 뜻이므로 의도치 않은 자동 입력을 막는다.
 */
export function consumeChainPayload(): ChainPayload | null {
  try {
    const raw = sessionStorage.getItem(CHAIN_PAYLOAD_KEY)
    if (!raw) return null
    sessionStorage.removeItem(CHAIN_PAYLOAD_KEY)
    const parsed = JSON.parse(raw) as ChainPayload
    if (!parsed || typeof parsed.value !== 'string') return null
    if (Date.now() - parsed.ts > 60_000) return null
    return parsed
  } catch {
    return null
  }
}
