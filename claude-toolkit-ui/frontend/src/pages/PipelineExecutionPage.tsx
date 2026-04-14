import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaCheckCircle, FaTimesCircle, FaSpinner, FaMinusCircle,
  FaProjectDiagram, FaCopy, FaCheck, FaDownload, FaFilePdf,
  FaEnvelope, FaPlayCircle,
} from 'react-icons/fa'
import { useToast } from '../hooks/useToast'
import { copyToClipboard, printAsHtml, markdownToHtml } from '../utils/clipboard'
import EmailModal from '../components/common/EmailModal'

interface StepResult {
  stepId: string
  analysisType: string
  status: string
  outputContent: string
  errorMessage: string
  skipReason: string
  durationMs: number
  stepOrder: number
}

interface ExecutionData {
  id: number
  pipelineName: string
  status: string
  totalSteps: number
  completedSteps: number
  startedAt: string
  completedAt: string
  errorMessage: string
  steps: StepResult[]
}

const STATUS_LABEL: Record<string, string> = {
  PENDING:   '대기',
  RUNNING:   '실행 중',
  COMPLETED: '완료',
  FAILED:    '실패',
  SKIPPED:   '건너뜀',
}

const STATUS_COLOR: Record<string, string> = {
  PENDING:   'var(--text-muted)',
  RUNNING:   'var(--accent)',
  COMPLETED: 'var(--green)',
  FAILED:    'var(--red)',
  SKIPPED:   'var(--text-muted)',
}

function StatusIcon({ status }: { status: string }) {
  switch (status) {
    case 'COMPLETED': return <FaCheckCircle style={{ color: 'var(--green)' }} />
    case 'FAILED':    return <FaTimesCircle style={{ color: 'var(--red)' }} />
    case 'RUNNING':   return <FaSpinner style={{ color: 'var(--accent)' }} className="spin" />
    case 'SKIPPED':   return <FaMinusCircle style={{ color: 'var(--text-muted)' }} />
    default:          return <FaPlayCircle style={{ color: 'var(--text-muted)' }} />
  }
}

export default function PipelineExecutionPage() {
  const { id } = useParams<{ id: string }>()
  const [data, setData] = useState<ExecutionData | null>(null)
  const [activeTab, setActiveTab] = useState<string | null>(null)
  const [copiedKey, setCopiedKey] = useState<string | null>(null)
  /** 실시간 스트리밍 청크 누적: stepId → text */
  const [liveChunks, setLiveChunks] = useState<Record<string, string>>({})
  const [emailOpen, setEmailOpen] = useState<{ stepId: string | 'all' } | null>(null)
  const esRef = useRef<EventSource | null>(null)
  const toast = useToast()

  const loadData = useCallback(async () => {
    try {
      const res = await fetch(`/pipelines/executions/${id}/data`, { credentials: 'include' })
      if (!res.ok) return
      const json = await res.json()
      setData(json)
      // 처음 로드 시: 실행 중 단계가 있으면 그 탭, 아니면 첫번째 탭
      setActiveTab((cur) => {
        if (cur) return cur
        if (!json.steps || json.steps.length === 0) return null
        const running = json.steps.find((s: StepResult) => s.status === 'RUNNING')
        return running ? running.stepId : json.steps[0].stepId
      })
    } catch {
      toast.error('실행 데이터를 불러올 수 없습니다.')
    }
  }, [id, toast])

  useEffect(() => {
    loadData()

    // SSE 구독 — step-chunk 로 실시간 출력 누적
    const es = new EventSource(`/pipelines/executions/${id}/stream`, { withCredentials: true })
    esRef.current = es

    es.addEventListener('step-start', (e: MessageEvent) => {
      try {
        const payload = JSON.parse(e.data)
        // 새 단계 시작 — 빈 청크 버퍼 초기화 + 자동으로 그 탭 활성화
        setLiveChunks((prev) => ({ ...prev, [payload.stepId]: '' }))
        setActiveTab(payload.stepId)
      } catch { /* noop */ }
      loadData()
    })

    es.addEventListener('step-chunk', (e: MessageEvent) => {
      try {
        const payload = JSON.parse(e.data)
        setLiveChunks((prev) => ({
          ...prev,
          [payload.stepId]: (prev[payload.stepId] || '') + (payload.chunk || ''),
        }))
      } catch { /* noop */ }
    })

    es.addEventListener('step-completed', () => loadData())
    es.addEventListener('step-skipped',   () => loadData())
    es.addEventListener('error',          () => loadData())
    es.addEventListener('done', () => { loadData(); es.close() })

    es.onerror = () => { loadData() }

    return () => {
      es.close()
      esRef.current = null
    }
  }, [id, loadData])

  /** 한 단계의 표시할 출력 — DB 에 저장된 outputContent 우선, 없으면 라이브 청크 */
  const stepOutput = (s: StepResult): string => {
    if (s.outputContent && s.outputContent.length > 0) return s.outputContent
    return liveChunks[s.stepId] || ''
  }

  const copyText = async (key: string, text: string) => {
    if (!text) { toast.error('복사할 내용이 없습니다.'); return }
    const ok = await copyToClipboard(text)
    if (ok) {
      setCopiedKey(key)
      setTimeout(() => setCopiedKey((cur) => cur === key ? null : cur), 3000)
      toast.success('클립보드에 복사되었습니다.')
    } else {
      toast.error('복사 실패 — 브라우저 권한을 확인해주세요.')
    }
  }

  const downloadMd = (filename: string, body: string) => {
    const blob = new Blob([body], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${filename}_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  const printPdf = (title: string, body: string) => {
    if (!body) { toast.warning('인쇄할 내용이 없습니다.'); return }
    printAsHtml(`<h1>${title}</h1>` + markdownToHtml(body), title)
  }

  const allOutput = (): string => {
    if (!data) return ''
    return (data.steps || [])
      .sort((a, b) => a.stepOrder - b.stepOrder)
      .map((s) => `# [${s.stepOrder + 1}] ${s.stepId} (${s.analysisType})\n\n${stepOutput(s)}\n\n---\n`)
      .join('\n')
  }

  if (!data) {
    return (
      <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
        <FaSpinner className="spin" style={{ fontSize: '24px', marginBottom: '12px' }} />
        <p>실행 데이터 로딩 중...</p>
      </div>
    )
  }

  const sortedSteps = (data.steps || []).slice().sort((a, b) => a.stepOrder - b.stepOrder)
  const activeStep = sortedSteps.find((s) => s.stepId === activeTab) || sortedSteps[0] || null
  const progress = data.totalSteps > 0
    ? Math.round((data.completedSteps / data.totalSteps) * 100)
    : 0
  const activeOutput = activeStep ? stepOutput(activeStep) : ''

  return (
    <>
      {/* Header */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        marginBottom: '16px', flexWrap: 'wrap', gap: '12px',
      }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px', margin: 0 }}>
          <FaProjectDiagram style={{ color: 'var(--purple)' }} />
          {data.pipelineName}
          <StatusIcon status={data.status} />
          <span style={{ fontSize: '12px', color: STATUS_COLOR[data.status] || 'var(--text-muted)', fontWeight: 500 }}>
            {STATUS_LABEL[data.status] || data.status}
          </span>
        </h2>
        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
          <button onClick={() => copyText('all', allOutput())} style={hdrBtn}>
            {copiedKey === 'all' ? <><FaCheck style={{ color: 'var(--green)' }} /> 전체 복사됨</> : <><FaCopy /> 전체 복사</>}
          </button>
          <button onClick={() => downloadMd(`pipeline_${data.id}`, allOutput())} style={hdrBtn}>
            <FaDownload /> 전체 MD
          </button>
          <button onClick={() => printPdf(`${data.pipelineName} - 분석 결과`, allOutput())} style={hdrBtn}>
            <FaFilePdf /> 전체 PDF
          </button>
          <button onClick={() => setEmailOpen({ stepId: 'all' })} style={{ ...hdrBtn, background: 'var(--accent-subtle)', color: 'var(--accent)', border: '1px solid var(--accent)' }}>
            <FaEnvelope /> 전체 이메일
          </button>
        </div>
      </div>

      {/* Meta */}
      <div style={{ display: 'flex', gap: '14px', fontSize: '12px', color: 'var(--text-muted)', marginBottom: '12px', flexWrap: 'wrap' }}>
        <span>시작: {data.startedAt}</span>
        {data.completedAt && <span>완료: {data.completedAt}</span>}
        <span>총 {data.totalSteps} 단계</span>
      </div>

      {/* Progress Bar */}
      <div style={{
        background: 'var(--bg-secondary)', borderRadius: '10px',
        border: '1px solid var(--border-color)', padding: '12px 14px', marginBottom: '16px',
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', fontSize: '13px' }}>
          <span>{data.completedSteps} / {data.totalSteps} 단계 완료</span>
          <span style={{ fontWeight: 600 }}>{progress}%</span>
        </div>
        <div style={{ background: 'var(--bg-primary)', borderRadius: '6px', height: '8px', overflow: 'hidden' }}>
          <div style={{
            height: '100%', borderRadius: '6px',
            background: data.status === 'FAILED' ? 'var(--red)' : 'var(--accent)',
            width: `${progress}%`, transition: 'width 0.5s ease',
          }} />
        </div>
        {data.errorMessage && (
          <div style={{ marginTop: '8px', fontSize: '12px', color: 'var(--red)' }}>
            ⚠ {data.errorMessage}
          </div>
        )}
      </div>

      {/* 탭 네비게이션 */}
      <div style={{
        display: 'flex', gap: '4px', overflowX: 'auto', paddingBottom: '6px',
        borderBottom: '1px solid var(--border-color)', marginBottom: '14px',
      }}>
        {sortedSteps.map((s, i) => {
          const isActive = activeStep?.stepId === s.stepId
          const live = liveChunks[s.stepId] && s.status === 'RUNNING' ? liveChunks[s.stepId].length : 0
          return (
            <button
              key={s.stepId}
              onClick={() => setActiveTab(s.stepId)}
              style={{
                display: 'flex', alignItems: 'center', gap: '6px',
                padding: '8px 14px', borderRadius: '8px 8px 0 0',
                border: 'none',
                borderBottom: isActive ? '2px solid var(--accent)' : '2px solid transparent',
                background: isActive ? 'var(--bg-secondary)' : 'transparent',
                color: isActive ? 'var(--text-primary)' : 'var(--text-sub)',
                cursor: 'pointer', fontSize: '13px',
                fontWeight: isActive ? 700 : 400,
                whiteSpace: 'nowrap', flexShrink: 0,
              }}
              title={`${s.analysisType} · ${STATUS_LABEL[s.status] || s.status}`}>
              <span style={{ fontSize: '11px', color: 'var(--text-muted)', minWidth: '16px' }}>{i + 1}.</span>
              <StatusIcon status={s.status} />
              <span>{s.stepId}</span>
              {s.status === 'RUNNING' && live > 0 && (
                <span style={{ fontSize: '10px', color: 'var(--accent)' }}>{live}자</span>
              )}
              {s.status === 'COMPLETED' && s.durationMs > 0 && (
                <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>{(s.durationMs / 1000).toFixed(1)}s</span>
              )}
            </button>
          )
        })}
      </div>

      {/* 활성 탭 본문 */}
      {activeStep && (
        <div style={{
          background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
          borderRadius: '0 10px 10px 10px', padding: '16px',
        }}>
          {/* 탭 헤더 */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px', flexWrap: 'wrap' }}>
            <StatusIcon status={activeStep.status} />
            <span style={{ fontWeight: 700, fontSize: '14px' }}>{activeStep.stepId}</span>
            <span style={{ fontSize: '11px', color: 'var(--text-muted)', padding: '2px 8px', background: 'var(--bg-primary)', borderRadius: '4px' }}>
              {activeStep.analysisType}
            </span>
            <span style={{ fontSize: '11px', color: STATUS_COLOR[activeStep.status] || 'var(--text-muted)' }}>
              {STATUS_LABEL[activeStep.status] || activeStep.status}
            </span>
            {activeStep.durationMs > 0 && (
              <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                {(activeStep.durationMs / 1000).toFixed(1)}s
              </span>
            )}
            <div style={{ flex: 1 }} />
            {activeOutput && (
              <div style={{ display: 'flex', gap: '4px' }}>
                <button onClick={() => copyText(activeStep.stepId, activeOutput)} style={miniBtn} title="복사">
                  {copiedKey === activeStep.stepId
                    ? <><FaCheck style={{ color: 'var(--green)' }} /> <span style={{ color: 'var(--green)', fontWeight: 600 }}>복사됨</span></>
                    : <><FaCopy /> 복사</>}
                </button>
                <button onClick={() => downloadMd(`step_${activeStep.stepId}`, activeOutput)} style={miniBtn} title="MD 내려받기">
                  <FaDownload /> MD
                </button>
                <button onClick={() => printPdf(`${activeStep.stepId} - ${activeStep.analysisType}`, activeOutput)} style={miniBtn} title="PDF">
                  <FaFilePdf /> PDF
                </button>
                <button onClick={() => setEmailOpen({ stepId: activeStep.stepId })} style={{ ...miniBtn, color: 'var(--accent)', borderColor: 'var(--accent)' }} title="이메일 발송">
                  <FaEnvelope /> 이메일
                </button>
              </div>
            )}
          </div>

          {/* 본문 */}
          {activeStep.skipReason && (
            <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '8px', padding: '10px', background: 'var(--bg-primary)', borderRadius: '6px' }}>
              건너뜀: {activeStep.skipReason}
            </div>
          )}
          {activeStep.errorMessage && (
            <div style={{ fontSize: '13px', color: 'var(--red)', marginBottom: '8px', padding: '10px', background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.3)', borderRadius: '6px' }}>
              ⚠ 오류: {activeStep.errorMessage}
            </div>
          )}
          {activeOutput ? (
            <div className="markdown-body" style={{ fontSize: '13px', minHeight: '200px' }}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{activeOutput}</ReactMarkdown>
              {activeStep.status === 'RUNNING' && (
                <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', color: 'var(--accent)', fontSize: '12px', marginTop: '8px' }}>
                  <FaSpinner className="spin" /> 스트리밍 중...
                </div>
              )}
            </div>
          ) : activeStep.status === 'RUNNING' ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--accent)', fontSize: '13px', padding: '20px' }}>
              <FaSpinner className="spin" /> AI 응답 대기 중...
            </div>
          ) : activeStep.status === 'PENDING' ? (
            <div style={{ color: 'var(--text-muted)', fontSize: '13px', padding: '20px', textAlign: 'center' }}>
              앞 단계가 완료되면 실행됩니다.
            </div>
          ) : (
            <div style={{ color: 'var(--text-muted)', fontSize: '13px', padding: '20px', textAlign: 'center' }}>
              결과 없음
            </div>
          )}
        </div>
      )}

      {/* 이메일 발송 모달 */}
      <EmailModal
        open={emailOpen !== null}
        onClose={() => setEmailOpen(null)}
        defaultSubject={
          emailOpen?.stepId === 'all'
            ? `[Claude Toolkit] ${data.pipelineName} 파이프라인 결과`
            : emailOpen ? `[Claude Toolkit] ${data.pipelineName} - ${emailOpen.stepId}` : ''
        }
        content={
          emailOpen?.stepId === 'all'
            ? allOutput()
            : emailOpen && activeStep ? stepOutput(activeStep) : ''
        }
        contentLabel={emailOpen?.stepId === 'all' ? '전체 파이프라인 결과' : '단계 결과'}
      />
    </>
  )
}

const hdrBtn: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '5px',
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '6px', padding: '6px 12px', color: 'var(--text-sub)',
  cursor: 'pointer', fontSize: '12px',
}
const miniBtn: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '4px',
  background: 'none', border: '1px solid var(--border-color)',
  color: 'var(--text-sub)', cursor: 'pointer',
  padding: '4px 8px', borderRadius: '6px', fontSize: '11px',
}
