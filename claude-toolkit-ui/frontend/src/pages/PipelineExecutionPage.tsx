import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaCheckCircle, FaTimesCircle, FaSpinner, FaMinusCircle,
  FaProjectDiagram, FaCopy, FaCheck,
} from 'react-icons/fa'
import { useToast } from '../hooks/useToast'

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

const statusIcon = (status: string) => {
  switch (status) {
    case 'COMPLETED': return <FaCheckCircle style={{ color: 'var(--green)' }} />
    case 'FAILED': return <FaTimesCircle style={{ color: 'var(--red)' }} />
    case 'RUNNING': return <FaSpinner style={{ color: 'var(--accent)' }} className="spin" />
    case 'SKIPPED': return <FaMinusCircle style={{ color: 'var(--text-muted)' }} />
    default: return <FaMinusCircle style={{ color: 'var(--text-muted)' }} />
  }
}

export default function PipelineExecutionPage() {
  const { id } = useParams<{ id: string }>()
  const [data, setData] = useState<ExecutionData | null>(null)
  const [expandedStep, setExpandedStep] = useState<string | null>(null)
  const [copiedStep, setCopiedStep] = useState<string | null>(null)
  const esRef = useRef<EventSource | null>(null)
  const toast = useToast()

  const loadData = useCallback(async () => {
    try {
      const res = await fetch(`/pipelines/executions/${id}/data`, { credentials: 'include' })
      if (res.ok) {
        const json = await res.json()
        setData(json)
        // Auto-expand first running or latest completed step
        if (json.steps) {
          const running = json.steps.find((s: StepResult) => s.status === 'RUNNING')
          if (running) setExpandedStep(running.stepId)
          else if (json.steps.length > 0) setExpandedStep(json.steps[json.steps.length - 1].stepId)
        }
      }
    } catch {
      toast.error('실행 데이터를 불러올 수 없습니다.')
    }
  }, [id, toast])

  useEffect(() => {
    loadData()

    // Subscribe to SSE for live updates
    const es = new EventSource(`/pipelines/executions/${id}/stream`, { withCredentials: true })
    esRef.current = es

    es.addEventListener('step_start', () => loadData())
    es.addEventListener('step_complete', () => loadData())
    es.addEventListener('step_failed', () => loadData())
    es.addEventListener('execution_complete', () => { loadData(); es.close() })
    es.addEventListener('execution_failed', () => { loadData(); es.close() })

    es.onerror = () => {
      // Reconnection will be handled by EventSource automatically
      // After execution completes, close
      loadData()
    }

    return () => {
      es.close()
      esRef.current = null
    }
  }, [id, loadData])

  const copyStepOutput = (step: StepResult) => {
    navigator.clipboard.writeText(step.outputContent || '')
    setCopiedStep(step.stepId)
    setTimeout(() => setCopiedStep(null), 2000)
  }

  if (!data) {
    return (
      <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
        <FaSpinner className="spin" style={{ fontSize: '24px', marginBottom: '12px' }} />
        <p>실행 데이터 로딩 중...</p>
      </div>
    )
  }

  const progress = data.totalSteps > 0
    ? Math.round((data.completedSteps / data.totalSteps) * 100)
    : 0

  return (
    <>
      {/* Header */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        marginBottom: '20px', flexWrap: 'wrap', gap: '12px',
      }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaProjectDiagram style={{ color: 'var(--purple)' }} />
          {data.pipelineName}
          {statusIcon(data.status)}
        </h2>
        <div style={{ display: 'flex', gap: '12px', fontSize: '13px', color: 'var(--text-muted)' }}>
          <span>시작: {data.startedAt}</span>
          {data.completedAt && <span>완료: {data.completedAt}</span>}
        </div>
      </div>

      {/* Progress Bar */}
      <div style={{
        background: 'var(--bg-secondary)', borderRadius: '10px',
        border: '1px solid var(--border-color)', padding: '16px', marginBottom: '20px',
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '13px' }}>
          <span>{data.completedSteps} / {data.totalSteps} 단계</span>
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
          <div style={{ marginTop: '8px', fontSize: '13px', color: 'var(--red)' }}>
            {data.errorMessage}
          </div>
        )}
      </div>

      {/* Step Results */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {(data.steps || []).sort((a, b) => a.stepOrder - b.stepOrder).map((step) => (
          <div key={step.stepId} style={{
            background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
            borderRadius: '10px', overflow: 'hidden',
          }}>
            {/* Step Header */}
            <div
              style={{
                display: 'flex', alignItems: 'center', gap: '10px',
                padding: '12px 16px', cursor: 'pointer',
              }}
              onClick={() => setExpandedStep(expandedStep === step.stepId ? null : step.stepId)}
            >
              {statusIcon(step.status)}
              <span style={{ fontWeight: 600, fontSize: '14px', flex: 1 }}>
                {step.stepId}
              </span>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)', padding: '2px 8px', background: 'var(--bg-primary)', borderRadius: '4px' }}>
                {step.analysisType}
              </span>
              {step.durationMs > 0 && (
                <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                  {(step.durationMs / 1000).toFixed(1)}s
                </span>
              )}
            </div>

            {/* Step Content (expanded) */}
            {expandedStep === step.stepId && (
              <div style={{ borderTop: '1px solid var(--border-color)', padding: '16px' }}>
                {step.skipReason && (
                  <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '8px' }}>
                    건너뜀: {step.skipReason}
                  </div>
                )}
                {step.errorMessage && (
                  <div style={{ fontSize: '13px', color: 'var(--red)', marginBottom: '8px' }}>
                    오류: {step.errorMessage}
                  </div>
                )}
                {step.outputContent && (
                  <>
                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '6px' }}>
                      <button
                        onClick={() => copyStepOutput(step)}
                        style={{
                          display: 'flex', alignItems: 'center', gap: '4px',
                          background: 'none', border: '1px solid var(--border-color)',
                          borderRadius: '6px', padding: '4px 10px', fontSize: '12px',
                          color: 'var(--text-sub)', cursor: 'pointer',
                        }}
                      >
                        {copiedStep === step.stepId ? <><FaCheck style={{ color: 'var(--green)' }} /> 복사됨</> : <><FaCopy /> 복사</>}
                      </button>
                    </div>
                    <div className="markdown-body" style={{ fontSize: '13px' }}>
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>{step.outputContent}</ReactMarkdown>
                    </div>
                  </>
                )}
                {step.status === 'RUNNING' && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--accent)', fontSize: '13px' }}>
                    <FaSpinner className="spin" /> 분석 중...
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </>
  )
}
