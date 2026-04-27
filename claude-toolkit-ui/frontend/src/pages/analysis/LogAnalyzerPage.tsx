import { useState } from 'react'
import { FaBug, FaProjectDiagram } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'
import LogRcaHarnessSection from '../../components/loganalyzer/LogRcaHarnessSection'
import { useAuthStore } from '../../stores/authStore'

/**
 * Phase D — 로그 분석기 (모드 전환).
 *
 * <p>두 가지 모드:
 * <ul>
 *   <li><b>simple</b> — 기존 단발 분석 (AnalysisPageTemplate, feature='log_analysis')</li>
 *   <li><b>harness</b> — 신규 4-stage RCA 하네스 (LogRcaHarnessSection)</li>
 * </ul>
 *
 * <p>하네스 모드는 어드민 권한 화면에서 {@code loganalyzer-harness} 키로 ON/OFF 가능합니다.
 * 권한이 OFF인 사용자에게는 모드 토글 자체가 보이지 않습니다 (페이지 자체의
 * {@code loganalyzer} 권한은 별개로 유효).
 */
type Mode = 'simple' | 'harness'

export default function LogAnalyzerPage() {
  const [mode, setMode] = useState<Mode>('simple')
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  const harnessAllowed = isAdmin || !(user?.disabledFeatures || []).includes('loganalyzer-harness')

  return (
    <>
      {/* 모드 토글 — 권한 있을 때만 표시 */}
      {harnessAllowed && (
        <div style={modeBarStyle}>
          <span style={{ fontSize: '12px', color: 'var(--text-muted)', marginRight: '4px' }}>분석 방식</span>
          <ModeButton
            active={mode === 'simple'}
            onClick={() => setMode('simple')}
            icon={<FaBug />}
            label="단발 분석"
            hint="단일 LLM 호출로 빠른 결과"
          />
          <ModeButton
            active={mode === 'harness'}
            onClick={() => setMode('harness')}
            icon={<FaProjectDiagram />}
            label="RCA 하네스 (4단계)"
            hint="Analyst → Builder → Reviewer → Verifier"
          />
        </div>
      )}

      {mode === 'harness' && harnessAllowed
        ? <LogRcaHarnessSection />
        : <AnalysisPageTemplate config={SIMPLE_CONFIG} />}
    </>
  )
}

const SIMPLE_CONFIG = {
  title: '로그 분석기',
  icon: FaBug,
  iconColor: '#06b6d4',
  description: '로그 파일을 분석하여 오류 패턴과 보안 위협을 탐지합니다.',
  feature: 'log_analysis',
  inputLabel: '로그 입력',
  inputPlaceholder: '로그 텍스트를 입력하세요...',
  options: [
    {
      name: 'analysisType',
      label: '분석 유형',
      type: 'select' as const,
      defaultValue: 'general',
      options: [
        { value: 'general',  label: '일반 분석' },
        { value: 'security', label: '보안 위협 탐지' },
      ],
    },
  ],
}

function ModeButton({ active, onClick, icon, label, hint }: {
  active: boolean
  onClick: () => void
  icon: React.ReactNode
  label: string
  hint: string
}) {
  return (
    <button
      onClick={onClick}
      title={hint}
      style={{
        display: 'flex', alignItems: 'center', gap: '6px',
        padding: '6px 12px',
        background: active ? 'var(--accent)' : 'transparent',
        color: active ? '#fff' : 'var(--text-sub)',
        border: `1px solid ${active ? 'var(--accent)' : 'var(--border-color)'}`,
        borderRadius: '8px', cursor: 'pointer', fontSize: '12px', fontWeight: active ? 600 : 500,
      }}
    >
      {icon}
      {label}
    </button>
  )
}

const modeBarStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '8px',
  padding: '8px 0 12px', marginBottom: '4px',
  borderBottom: '1px solid var(--border-color)',
}
