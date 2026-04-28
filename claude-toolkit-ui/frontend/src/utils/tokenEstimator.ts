/**
 * v4.6.x — Claude API 토큰/비용 클라이언트 사이드 추정기.
 *
 * <p>분석 시작 버튼 옆에 *얼마나 들지 미리 보여주기* 위한 휴리스틱 도구.
 * 정확한 토큰 수는 Anthropic 의 BPE tokenizer 가 결정하지만, 클라이언트에
 * tokenizer 를 동봉하면 번들 크기 증가 + 라이선스 이슈가 있어
 * 실용적 휴리스틱으로 ±15% 오차 범위 내 추정.
 *
 * <p>비용 단가는 백엔드 {@code ModelCostService.PRICING} 과 일치.
 */

export interface ModelPricing {
  /** 사람이 보는 라벨 (예: "Sonnet 4") */
  label: string
  /** USD per 1M input tokens */
  inputPer1M: number
  /** USD per 1M output tokens */
  outputPer1M: number
}

/**
 * 모델별 단가표 — Anthropic 공식 가격 (per 1M tokens, USD).
 * 백엔드 {@code ModelCostService.PRICING} 과 동기화 유지 필요.
 */
export const MODEL_PRICING: Record<string, ModelPricing> = {
  // Opus
  'claude-opus-4':            { label: 'Opus 4',   inputPer1M: 15.00, outputPer1M: 75.00 },
  'claude-opus-4-5':          { label: 'Opus 4.5', inputPer1M: 15.00, outputPer1M: 75.00 },
  'claude-opus-4-1':          { label: 'Opus 4.1', inputPer1M: 15.00, outputPer1M: 75.00 },
  // Sonnet
  'claude-sonnet-4':          { label: 'Sonnet 4',         inputPer1M: 3.00, outputPer1M: 15.00 },
  'claude-sonnet-4-5':        { label: 'Sonnet 4.5',       inputPer1M: 3.00, outputPer1M: 15.00 },
  'claude-sonnet-4-20250514': { label: 'Sonnet 4 (May)',   inputPer1M: 3.00, outputPer1M: 15.00 },
  // Haiku
  'claude-haiku-4':           { label: 'Haiku 4',   inputPer1M: 1.00, outputPer1M: 5.00 },
  'claude-haiku-3-5':         { label: 'Haiku 3.5', inputPer1M: 0.80, outputPer1M: 4.00 },
  // 기본 fallback
  'default':                  { label: 'Default Sonnet', inputPer1M: 3.00, outputPer1M: 15.00 },
}

/**
 * 모델 이름으로 단가 조회. 정확 매칭 → contains 매칭 → 시리즈 매칭 → default.
 */
export function lookupPricing(model: string | null | undefined): ModelPricing {
  if (!model) return MODEL_PRICING['default']
  const exact = MODEL_PRICING[model]
  if (exact) return exact
  const lower = model.toLowerCase()
  for (const key of Object.keys(MODEL_PRICING)) {
    if (key !== 'default' && lower.includes(key.toLowerCase())) {
      return MODEL_PRICING[key]
    }
  }
  if (lower.includes('opus'))   return MODEL_PRICING['claude-opus-4']
  if (lower.includes('haiku'))  return MODEL_PRICING['claude-haiku-4']
  if (lower.includes('sonnet')) return MODEL_PRICING['claude-sonnet-4']
  return MODEL_PRICING['default']
}

/**
 * 텍스트 → 입력 토큰 추정.
 *
 * <p>휴리스틱: Claude/GPT 류 BPE tokenizer 의 경험적 비율 사용.
 * <ul>
 *   <li>한글(Hangul/Hanja): 약 1.5\~2 chars/token (CJK 는 토큰화 효율이 낮음)</li>
 *   <li>영문/숫자/공백/구두점: 약 3.5\~4 chars/token</li>
 *   <li>코드(들여쓰기 多): 약 3 chars/token</li>
 * </ul>
 * 보수적으로 한글 1.8, 그 외 3.5 사용 → 실제와 ±15% 오차.
 */
export function estimateTokens(text: string | null | undefined): number {
  if (!text) return 0
  // Hangul Syllables (AC00-D7AF), Hangul Jamo (1100-11FF), CJK (4E00-9FFF), Hangul compat (3130-318F)
  // eslint-disable-next-line no-misleading-character-class
  const cjkRegex = /[가-힯ᄀ-ᇿ㄰-㆏一-鿿]/g
  const cjkChars = (text.match(cjkRegex) || []).length
  const restChars = text.length - cjkChars
  return Math.ceil(cjkChars / 1.8) + Math.ceil(restChars / 3.5)
}

export interface CostBreakdown {
  inputTokens: number
  /** 출력 토큰 추정값 (input × outputRatio) */
  estimatedOutputTokens: number
  /** USD */
  inputCost: number
  outputCost: number
  totalCost: number
  pricing: ModelPricing
}

/**
 * 입력 텍스트 + 모델 + 출력비율 → 비용 추정.
 *
 * @param outputRatio 분석 출력이 입력의 몇 배인지 추정 (기본 2.0).
 *                    리뷰/생성: 1.5\~3.0, 단순 변환: 0.8\~1.2.
 */
export function estimateCost(
  inputText: string,
  model: string | null | undefined,
  outputRatio: number = 2.0,
): CostBreakdown {
  const inputTokens = estimateTokens(inputText)
  const outputTokens = Math.ceil(inputTokens * outputRatio)
  const pricing = lookupPricing(model)
  const inputCost  = (inputTokens  / 1_000_000) * pricing.inputPer1M
  const outputCost = (outputTokens / 1_000_000) * pricing.outputPer1M
  return {
    inputTokens,
    estimatedOutputTokens: outputTokens,
    inputCost,
    outputCost,
    totalCost: inputCost + outputCost,
    pricing,
  }
}

/**
 * USD 비용을 사람이 읽기 쉬운 문자열로 — 작은 값은 4자리, 큰 값은 2자리.
 */
export function formatCost(usd: number): string {
  if (usd === 0) return '$0'
  if (usd < 0.01) return `$${usd.toFixed(4)}`
  if (usd < 1)    return `$${usd.toFixed(3)}`
  return `$${usd.toFixed(2)}`
}

/**
 * 토큰 수를 사람이 읽기 쉬운 문자열로 — 1000 이상은 K, 백만 이상은 M.
 */
export function formatTokens(n: number): string {
  if (n < 1000)      return `${n}`
  if (n < 1_000_000) return `${(n / 1000).toFixed(n < 10000 ? 1 : 0)}K`
  return `${(n / 1_000_000).toFixed(2)}M`
}
