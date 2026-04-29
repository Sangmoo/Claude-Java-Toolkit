import { useNavigate } from 'react-router-dom'
import { FaArrowRight, FaRoute } from 'react-icons/fa'
import { getNextStepsFor, pushChainPayload, type ChainSuggestion } from './analysisChain'

/**
 * v4.7.x — #2 분석 체이닝의 표시 컴포넌트.
 *
 * <p>분석 결과 패널 안 (FollowUpQAPanel 옆) 에 노출되어, 사용자가 결과를 본
 * 직후 자연스러운 후속 단계를 1-클릭으로 이동할 수 있게 한다. 입력은
 * sessionStorage 로 전달되어 다음 페이지의 textarea 가 자동 채워짐.
 *
 * <p>매핑이 없는 feature 는 컴포넌트 자체를 렌더하지 않음 (null 반환) —
 * AnalysisPageTemplate 가 항상 렌더해도 안전.
 */
interface Props {
  feature: string
  resultText: string
  inputText: string
}

export default function NextStepHints({ feature, resultText, inputText }: Props) {
  const navigate    = useNavigate()
  const suggestions = getNextStepsFor(feature)

  if (!suggestions.length) return null
  if (!resultText || !resultText.trim()) return null

  const handleClick = (s: ChainSuggestion) => {
    const payload = s.buildPayload(resultText, inputText)
    if (!payload || !payload.trim()) return
    pushChainPayload(payload, feature)
    navigate(s.targetPath)
  }

  return (
    <div style={containerStyle}>
      <div style={headerStyle}>
        <FaRoute style={{ color: '#f97316' }} />
        <span>다음 단계 제안</span>
        <span style={{ flex: 1 }} />
        <span style={hintStyle}>클릭하면 입력이 자동 전달됩니다</span>
      </div>
      <div style={cardsRow}>
        {suggestions.map((s) => (
          <button
            key={s.targetPath}
            type="button"
            onClick={() => handleClick(s)}
            title={s.description}
            style={cardStyle}>
            <div style={cardHeader}>
              <span style={cardLabel}>{s.label}</span>
              <FaArrowRight style={{ fontSize: 11, color: 'var(--text-muted)' }} />
            </div>
            <div style={cardDesc}>{s.description}</div>
          </button>
        ))}
      </div>
    </div>
  )
}

// ── styles ────────────────────────────────────────────────────────────────

const containerStyle: React.CSSProperties = {
  marginTop: 12,
  border: '1px solid var(--border-color)',
  borderRadius: 10,
  background: 'var(--bg-secondary)',
  padding: '10px 12px',
}
const headerStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 8,
  fontSize: 13, fontWeight: 600,
  marginBottom: 8,
  color: 'var(--text-primary)',
}
const hintStyle: React.CSSProperties = {
  fontSize: 11, fontWeight: 400, color: 'var(--text-muted)',
}
const cardsRow: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
  gap: 8,
}
const cardStyle: React.CSSProperties = {
  display: 'flex', flexDirection: 'column', gap: 4,
  padding: '8px 12px',
  background: 'var(--bg-primary)',
  border: '1px solid var(--border-color)',
  borderRadius: 8,
  cursor: 'pointer',
  textAlign: 'left',
  transition: 'all 0.15s',
}
const cardHeader: React.CSSProperties = {
  display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6,
}
const cardLabel: React.CSSProperties = {
  fontSize: 12, fontWeight: 600,
  color: 'var(--text-primary)',
}
const cardDesc: React.CSSProperties = {
  fontSize: 11, lineHeight: 1.4,
  color: 'var(--text-muted)',
}
