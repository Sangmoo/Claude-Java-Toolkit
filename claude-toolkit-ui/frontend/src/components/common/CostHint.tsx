import { useEffect, useMemo, useState } from 'react'
import { FaCoins } from 'react-icons/fa'
import { estimateCost, formatCost, formatTokens } from '../../utils/tokenEstimator'

/**
 * v4.6.x — 분석 시작 버튼 옆에 *비용 미리보기* 인라인 표시.
 *
 * <p>예) `🪙 ~12K 토큰 · 예상 $0.062 (Sonnet 4)`
 *
 * <p>입력 텍스트 길이에 비례해 실시간으로 갱신. 모델은 마운트 시 한 번
 * `/api/v1/health` 로 가져와 캐시. 입력이 짧으면 (50자 미만) 표시 안 함.
 *
 * <p>주의: 토큰·비용 모두 *추정값*. Anthropic 공식 BPE tokenizer 가 결정하는
 * 정확한 값과 ±15% 오차. 큰 결정에는 사용 후 `/admin/cost-optimizer` 의
 * 실측 데이터 사용 권장.
 */
export interface CostHintProps {
  /** 분석에 들어갈 메인 입력 텍스트 (필수). 여러 필드 합치려면 호출부에서 join 후 전달. */
  inputText: string
  /**
   * 출력 토큰 비율 추정 (입력의 몇 배인지). 기본 2.0.
   * - 리뷰/하네스: 2.0\~3.0
   * - 변환: 1.0\~1.5
   * - 요약: 0.3\~0.7
   */
  outputRatio?: number
  /** 표시 시작 임계값 (입력 글자 수). 기본 50자. */
  minChars?: number
  /** 추가 스타일 */
  style?: React.CSSProperties
}

/** 현재 사용 중인 모델 — 페이지 단위가 아니라 앱 단위로 한 번만 fetch */
let cachedModel: string | null = null
let modelFetchPromise: Promise<string> | null = null

async function fetchCurrentModel(): Promise<string> {
  if (cachedModel) return cachedModel
  if (modelFetchPromise) return modelFetchPromise
  modelFetchPromise = (async () => {
    try {
      const res = await fetch('/api/v1/health', { credentials: 'include' })
      if (!res.ok) return 'default'
      const json = await res.json()
      const model = json?.data?.claudeModel || 'default'
      cachedModel = model
      return model
    } catch {
      return 'default'
    } finally {
      modelFetchPromise = null
    }
  })()
  return modelFetchPromise
}

export default function CostHint({
  inputText,
  outputRatio = 2.0,
  minChars = 50,
  style,
}: CostHintProps) {
  const [model, setModel] = useState<string>('default')

  useEffect(() => {
    let mounted = true
    fetchCurrentModel().then((m) => { if (mounted) setModel(m) })
    return () => { mounted = false }
  }, [])

  const breakdown = useMemo(
    () => estimateCost(inputText, model, outputRatio),
    [inputText, model, outputRatio],
  )

  if (!inputText || inputText.length < minChars) return null

  return (
    <span
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 5,
        fontSize: 11, color: 'var(--text-muted)',
        whiteSpace: 'nowrap',
        ...style,
      }}
      title={
        `정확한 비용은 분석 후 /admin/cost-optimizer 에서 확인할 수 있습니다 (±15% 오차).\n` +
        `입력 토큰 ~${formatTokens(breakdown.inputTokens)}\n` +
        `예상 출력 토큰 ~${formatTokens(breakdown.estimatedOutputTokens)} (입력의 ${outputRatio}배)\n` +
        `입력 비용 ~${formatCost(breakdown.inputCost)}\n` +
        `출력 비용 ~${formatCost(breakdown.outputCost)}\n` +
        `총 예상 ~${formatCost(breakdown.totalCost)}`
      }
    >
      <FaCoins size={10} style={{ color: 'var(--accent)', flexShrink: 0 }} />
      <span>
        ~{formatTokens(breakdown.inputTokens)} 토큰 · 예상 {formatCost(breakdown.totalCost)}
      </span>
      <span style={{ color: 'var(--text-muted)' }}>
        ({breakdown.pricing.label})
      </span>
    </span>
  )
}
