import { useState, useEffect } from 'react'
import { FaPlay, FaSpinner, FaEraser, FaSearch, FaWrench, FaClipboardCheck, FaShieldAlt, FaBug } from 'react-icons/fa'
import HarnessStagePanels, { type StageDef } from '../common/HarnessStagePanels'
import { useHarnessStream } from '../../hooks/useHarnessStream'
import { useToast } from '../../hooks/useToast'

/**
 * Phase D — 오류 로그 RCA 하네스 입력/출력 섹션.
 *
 * <p>좌측: 다중 입력(error_log 필수 + timeline/related_code/env 선택)
 * <p>우측: {@link HarnessStagePanels}로 4-stage 결과 렌더링
 *
 * <p>SSE 엔드포인트: {@code POST /api/v1/log-rca/stream-init} → {@code GET /api/v1/log-rca/stream/{id}}
 */
type StageKey = 'analyst' | 'builder' | 'reviewer' | 'verifier'

const STAGES: StageDef<StageKey>[] = [
  { key: 'analyst',  num: 1, title: '증상·가설',     icon: <FaSearch />,         color: '#3b82f6', desc: 'Analyst — 증상 요약·타임라인·가설 후보' },
  { key: 'builder',  num: 2, title: '검증·패치',     icon: <FaWrench />,         color: '#10b981', desc: 'Builder — 가설별 검증 SQL·패치·롤백 계획' },
  { key: 'reviewer', num: 3, title: '우도·부작용',   icon: <FaClipboardCheck />, color: '#f59e0b', desc: 'Reviewer — 가설 우도 평가·부작용 분석·우선순위' },
  { key: 'verifier', num: 4, title: 'RCA 보고서',    icon: <FaShieldAlt />,      color: '#8b5cf6', desc: 'Verifier — 사내 표준 RCA 보고서·재발 방지 체크리스트' },
]

export default function LogRcaHarnessSection() {
  const [errorLog, setErrorLog]       = useState('')
  const [timeline, setTimeline]       = useState('')
  const [relatedCode, setRelatedCode] = useState('')
  const [env, setEnv]                 = useState('')
  const [analysisMode, setAnalysisMode] = useState<'general' | 'security'>('general')

  const { stages, streaming, activeStage, error, startStream, reset } =
    useHarnessStream<StageKey>(['analyst', 'builder', 'reviewer', 'verifier'])

  const toast = useToast()

  const start = async () => {
    if (!errorLog.trim() || streaming) return
    const body = new URLSearchParams({
      error_log:     errorLog.trim(),
      timeline:      timeline.trim(),
      related_code:  relatedCode.trim(),
      env:           env.trim(),
      analysis_mode: analysisMode,
    })
    await startStream({
      initUrl: '/api/v1/log-rca/stream-init',
      streamUrlPrefix: '/api/v1/log-rca/stream/',
      body,
    })
  }

  const clearAll = () => {
    setErrorLog('')
    setTimeline('')
    setRelatedCode('')
    setEnv('')
    reset()
  }

  // error 표시 — error 값이 새로 세팅될 때만 toast (렌더마다 호출 방지)
  useEffect(() => {
    if (error) toast.error(error)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [error])

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
        <FaBug style={{ fontSize: '20px', color: '#06b6d4' }} />
        <div>
          <h2 style={{ fontSize: '17px', fontWeight: 700, margin: 0 }}>로그 분석기 — RCA 하네스</h2>
          <p style={{ fontSize: '12px', color: 'var(--text-muted)', margin: 0 }}>
            4단계 파이프라인: Analyst → Builder → Reviewer → Verifier ({analysisMode === 'security' ? '보안 모드' : '일반 모드'})
          </p>
        </div>
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 480px), 1fr))',
        gap: '14px',
        height: 'calc(100vh - 200px)',
        minHeight: '520px',
      }}>
        {/* ── 좌측: 다중 입력 ── */}
        <div style={panelStyle}>
          <div style={panelHeaderStyle}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>RCA 입력</span>
            <button style={smallBtn} onClick={clearAll} title="초기화">
              <FaEraser /> 초기화
            </button>
          </div>

          {/* 모드 선택 */}
          <div style={{ padding: '8px 14px', display: 'flex', gap: '12px', alignItems: 'center', fontSize: '13px' }}>
            <label style={{ color: 'var(--text-muted)' }}>분석 모드</label>
            <select
              value={analysisMode}
              onChange={(e) => setAnalysisMode(e.target.value as 'general' | 'security')}
              style={{ fontSize: '12px', padding: '3px 6px' }}
              disabled={streaming}
            >
              <option value="general">일반 RCA</option>
              <option value="security">보안 RCA (OWASP)</option>
            </select>
          </div>

          {/* 입력 필드들 */}
          <div style={{ flex: 1, overflowY: 'auto', padding: '0 14px 14px', display: 'flex', flexDirection: 'column', gap: '10px', minHeight: 0 }}>
            <Field
              label="오류 로그 (필수)"
              hint="스택트레이스, ORA 에러, 예외 메시지 등"
              value={errorLog}
              onChange={setErrorLog}
              rows={8}
              required
              disabled={streaming}
              placeholder="ORA-00060: deadlock detected while waiting for resource..."
            />
            <Field
              label="타임라인 메모 (선택)"
              hint="발생 시각·영향 범위·재현 조건"
              value={timeline}
              onChange={setTimeline}
              rows={3}
              disabled={streaming}
              placeholder="14:23:11 첫 발생, 야간 배치 SP_WMS_DELV_NO_SALE 실행 중\n14:23:14 5건 동시 실패"
            />
            <Field
              label="관련 코드/SQL (선택)"
              hint="문제가 의심되는 SP/Java/SQL 일부"
              value={relatedCode}
              onChange={setRelatedCode}
              rows={5}
              disabled={streaming}
              placeholder="MERGE /*+ APPEND */ INTO INV_STOCK ..."
            />
            <Field
              label="환경 정보 (선택)"
              hint="JDK·DB 버전, 배포 정보, 동시 세션 수"
              value={env}
              onChange={setEnv}
              rows={2}
              disabled={streaming}
              placeholder="Oracle 19c, JDK 1.8, 야간 배치 동시 5세션"
            />
          </div>

          <div style={{ padding: '10px 14px', borderTop: '1px solid var(--border-color)', display: 'flex', justifyContent: 'flex-end' }}>
            <button
              onClick={start}
              disabled={streaming || !errorLog.trim()}
              style={{ ...analyzeBtn, opacity: streaming || !errorLog.trim() ? 0.5 : 1 }}
            >
              {streaming ? <><FaSpinner className="spin" /> RCA 분석 중 (4단계)...</> : <><FaPlay /> 4단계 RCA 시작</>}
            </button>
          </div>
        </div>

        {/* ── 우측: 4-stage 결과 ── */}
        <HarnessStagePanels<StageKey>
          stages={STAGES}
          buffers={stages}
          streaming={streaming}
          activeStreamingStage={activeStage}
          filePrefix={analysisMode === 'security' ? 'log-rca-security' : 'log-rca'}
          emptyMessage="오류 로그를 입력하고 'RCA 시작' 버튼을 누르세요"
        />
      </div>
    </>
  )
}

function Field({ label, hint, value, onChange, rows, required, disabled, placeholder }: {
  label: string
  hint: string
  value: string
  onChange: (v: string) => void
  rows: number
  required?: boolean
  disabled?: boolean
  placeholder?: string
}) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: '4px' }}>
        <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-primary)' }}>
          {label} {required && <span style={{ color: 'var(--red, #ef4444)' }}>*</span>}
        </label>
        <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>{hint}</span>
      </div>
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        rows={rows}
        disabled={disabled}
        placeholder={placeholder}
        style={{
          width: '100%', boxSizing: 'border-box',
          fontFamily: 'Consolas, Monaco, monospace', fontSize: '12px', lineHeight: '1.5',
          padding: '6px 8px', border: '1px solid var(--border-color)', borderRadius: '6px',
          background: 'var(--bg-primary)', color: 'var(--text-primary)', resize: 'vertical',
          opacity: disabled ? 0.6 : 1,
        }}
      />
    </div>
  )
}

const panelStyle: React.CSSProperties = { background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', display: 'flex', flexDirection: 'column', overflow: 'hidden' }
const panelHeaderStyle: React.CSSProperties = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: '1px solid var(--border-color)' }
const smallBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
const analyzeBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 20px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '13px', fontWeight: 600 }
