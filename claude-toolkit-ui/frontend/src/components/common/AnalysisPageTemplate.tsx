import { useState, useRef, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { FaPlay, FaCopy, FaCheck, FaDownload, FaSpinner, FaEraser, FaUpload } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import SourceSelector from './SourceSelector'
import CostHint from './CostHint'
import type { IconType } from 'react-icons'

export interface AnalysisOption {
  name: string
  label: string
  type: 'select' | 'text' | 'textarea' | 'checkbox'
  options?: { value: string; label: string }[]
  defaultValue?: string
  placeholder?: string
}

export interface AnalysisInputExample {
  /** 버튼 라벨 */
  label: string
  /** 버튼 클릭 시 textarea 에 채워질 값 */
  value: string
}

export interface AnalysisPageConfig {
  title: string
  icon: IconType
  iconColor: string
  description: string
  feature: string
  inputLabel?: string
  inputPlaceholder?: string
  inputLanguage?: string
  options?: AnalysisOption[]
  endpoint?: string
  allowFileUpload?: boolean
  /** 소스 선택 모드: 'java' | 'sql' | 'both' | undefined(비활성) */
  sourceMode?: 'java' | 'sql' | 'both'
  /** 옵션 버튼 아래 추가 컨텐츠 (커스텀 element) */
  extraActions?: React.ReactNode
  /**
   * v4.2.6: 입력 예시 칩 — 클릭하면 입력 textarea 가 즉시 채워짐.
   * navigator.clipboard 를 쓰지 않으므로 HTTP IP 환경에서도 안정 동작.
   */
  inputExamples?: AnalysisInputExample[]
  /** 예시 칩 좌측에 표시할 라벨 (기본: '예시:') */
  inputExamplesLabel?: string
}

export default function AnalysisPageTemplate({ config }: { config: AnalysisPageConfig }) {
  const [input, setInput] = useState('')
  const [result, setResult] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [copied, setCopied] = useState(false)
  const [optionValues, setOptionValues] = useState<Record<string, string>>(() => {
    const d: Record<string, string> = {}
    config.options?.forEach((o) => { if (o.defaultValue) d[o.name] = o.defaultValue })
    return d
  })
  const esRef = useRef<EventSource | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const toast = useToast()
  const Icon = config.icon

  const setOption = useCallback((name: string, value: string) => {
    setOptionValues((prev) => ({ ...prev, [name]: value }))
  }, [])

  const handleSourceSelect = useCallback((code: string) => {
    setInput((prev) => prev ? prev + '\n\n// ── 추가 소스 ──\n\n' + code : code)
  }, [])

  const [dragOver, setDragOver] = useState(false)

  const readFile = (file: File) => {
    const reader = new FileReader()
    reader.onload = () => { setInput(reader.result as string); toast.success(`${file.name} 로드 완료`) }
    reader.readAsText(file)
  }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) readFile(file)
    e.target.value = ''
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files?.[0]
    if (file) readFile(file)
  }

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    if (!dragOver) setDragOver(true)
  }

  const handleDragLeave = () => setDragOver(false)

  const startAnalysis = async () => {
    if (!input.trim() || streaming) return
    setResult('')
    setStreaming(true)
    try {
      // 백엔드 /stream/init은 feature, input, input2, sourceType 4개 파라미터만 받음
      const params: Record<string, string> = {
        feature: config.feature,
        input: input.trim(),
      }
      // inputLanguage → sourceType (백엔드 기대 파라미터명)
      if (config.inputLanguage) params.sourceType = config.inputLanguage
      // options를 sourceType/input2에 매핑 (config.optionMapping 존재 시 우선)
      config.options?.forEach((o) => {
        const v = optionValues[o.name]
        if (!v) return
        // 특수 옵션명 처리: sourceDb → input2, targetDb → sourceType 등
        if (o.name === 'sourceDb') params.input2 = v
        else if (o.name === 'targetDb') params.sourceType = v
        else if (o.name === 'language') params.sourceType = v
        else if (o.name === 'templateHint') params.input2 = v
        else if (o.name === 'reviewType' || o.name === 'analysisType') {
          // 리뷰/분석 유형은 feature 교체가 필요하면 별도 처리
          // 기본: sourceType에 전달
          params.sourceType = v
        } else {
          // 일반 파라미터는 input2에 JSON으로 전달 (레거시 호환)
          params[o.name] = v
        }
      })

      const res = await fetch(config.endpoint || '/stream/init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(params),
        credentials: 'include',
      })
      if (!res.ok) {
        const errText = await res.text().catch(() => '')
        toast.error(`분석 요청 실패 (${res.status})${errText ? ': ' + errText.slice(0, 100) : ''}`)
        setStreaming(false)
        return
      }

      // 백엔드는 plain text UUID 반환 (JSON이 아님)
      const sid = (await res.text()).trim()
      if (!sid) { toast.error('스트림 ID 없음'); setStreaming(false); return }

      let acc = ''
      const es = new EventSource(`/stream/${sid}`, { withCredentials: true })
      esRef.current = es
      es.onmessage = (e) => {
        if (e.data === '[DONE]' || e.data === 'done') { es.close(); esRef.current = null; setStreaming(false); return }
        acc += e.data + '\n'; setResult(acc)
      }
      es.addEventListener('done', () => { es.close(); esRef.current = null; setStreaming(false) })
      es.addEventListener('error_msg', (ev: MessageEvent) => { toast.error(ev.data); es.close(); esRef.current = null; setStreaming(false) })
      es.onerror = () => {
        // 정상 종료된 경우 acc에 내용이 있음
        es.close(); esRef.current = null; setStreaming(false)
      }
    } catch (e) {
      toast.error('분석 오류: ' + (e instanceof Error ? e.message : String(e)))
      setStreaming(false)
    }
  }

  const copyResult = () => { navigator.clipboard.writeText(result); setCopied(true); setTimeout(() => setCopied(false), 2000) }
  const exportResult = () => {
    const b = new Blob([result], { type: 'text/markdown' })
    const u = URL.createObjectURL(b); const a = document.createElement('a')
    a.href = u; a.download = `${config.feature}_${new Date().toISOString().slice(0, 10)}.md`; a.click(); URL.revokeObjectURL(u)
  }
  const clear = () => { esRef.current?.close(); esRef.current = null; setResult(''); setInput(''); setStreaming(false) }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <Icon style={{ fontSize: '22px', color: config.iconColor }} />
        <div>
          <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0 }}>{config.title}</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>{config.description}</p>
        </div>
      </div>

      <div className="analysis-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 400px), 1fr))', gap: '16px', minHeight: '60vh' }}>
        {/* 입력 패널 */}
        <div style={panelStyle}>
          <div style={panelHeader}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>{config.inputLabel || '입력'}</span>
            <div style={{ display: 'flex', gap: '5px' }}>
              {config.sourceMode && <SourceSelector mode={config.sourceMode} onSelect={handleSourceSelect} />}
              {config.allowFileUpload && (
                <>
                  <input ref={fileRef} type="file" accept=".sql,.java,.xml,.txt,.log,.json,.yaml,.yml" style={{ display: 'none' }} onChange={handleFileUpload} />
                  <button style={smallBtn} onClick={() => fileRef.current?.click()} title="파일 업로드"><FaUpload /></button>
                </>
              )}
              <button style={smallBtn} onClick={clear} title="초기화"><FaEraser /></button>
            </div>
          </div>

          {/* 옵션 — 세련된 버튼/칩 형태 */}
          {config.options && config.options.length > 0 && (
            <div style={{ padding: '8px 14px', display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
              {config.options.map((opt) => (
                <div key={opt.name}>
                  <label style={{ display: 'block', fontSize: '11px', color: 'var(--text-muted)', marginBottom: '4px', fontWeight: 600 }}>{opt.label}</label>
                  {opt.type === 'select' && opt.options ? (
                    <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
                      {opt.options.map((o) => (
                        <button key={o.value} onClick={() => setOption(opt.name, o.value)}
                          style={{
                            padding: '4px 12px', borderRadius: '16px', fontSize: '12px', cursor: 'pointer',
                            border: `1px solid ${optionValues[opt.name] === o.value ? 'var(--accent)' : 'var(--border-color)'}`,
                            background: optionValues[opt.name] === o.value ? 'var(--accent-subtle)' : 'transparent',
                            color: optionValues[opt.name] === o.value ? 'var(--accent)' : 'var(--text-sub)',
                            fontWeight: optionValues[opt.name] === o.value ? 600 : 400,
                            transition: 'all 0.15s',
                          }}>
                          {o.label}
                        </button>
                      ))}
                    </div>
                  ) : opt.type === 'checkbox' ? (
                    <input type="checkbox" checked={optionValues[opt.name] === 'true'} onChange={(e) => setOption(opt.name, String(e.target.checked))} />
                  ) : (
                    <input type="text" value={optionValues[opt.name] || ''} onChange={(e) => setOption(opt.name, e.target.value)} placeholder={opt.placeholder} style={{ fontSize: '13px', padding: '4px 10px', width: '140px' }} />
                  )}
                </div>
              ))}
            </div>
          )}

          {config.extraActions && <div style={{ padding: '4px 14px' }}>{config.extraActions}</div>}

          {/* v4.2.6: 입력 예시 칩 — 클릭 시 textarea 즉시 채움 */}
          {config.inputExamples && config.inputExamples.length > 0 && (
            <div style={{ padding: '4px 14px', display: 'flex', flexWrap: 'wrap', gap: '4px', alignItems: 'center' }}>
              <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginRight: '4px', lineHeight: '24px' }}>
                {config.inputExamplesLabel || '예시 (클릭→입력):'}
              </span>
              {config.inputExamples.map((ex) => (
                <button
                  key={ex.label}
                  type="button"
                  onClick={() => setInput(ex.value)}
                  title={ex.value}
                  style={{
                    padding: '4px 12px', borderRadius: '14px', fontSize: '11px', cursor: 'pointer',
                    border: '1px solid var(--border-color)', background: 'var(--bg-primary)',
                    color: 'var(--text-sub)', transition: 'all 0.15s',
                  }}>
                  {ex.label}
                </button>
              ))}
            </div>
          )}

          <textarea
            style={{
              flex: 1, margin: '0 14px', border: 'none', resize: 'none',
              fontFamily: "'Consolas','Monaco',monospace", fontSize: '13px',
              lineHeight: '1.6', outline: 'none', color: 'var(--text-primary)',
              background: dragOver ? 'var(--accent-subtle)' : 'transparent',
              transition: 'background 0.15s',
            }}
            value={input} onChange={(e) => setInput(e.target.value)}
            placeholder={
              config.allowFileUpload
                ? (config.inputPlaceholder || '코드를 입력하거나 파일을 드래그앤드롭 하세요...')
                : (config.inputPlaceholder || '코드 또는 SQL을 입력하세요...')
            }
            onDrop={config.allowFileUpload ? handleDrop : undefined}
            onDragOver={config.allowFileUpload ? handleDragOver : undefined}
            onDragLeave={config.allowFileUpload ? handleDragLeave : undefined}
          />

          <div style={{ padding: '10px 14px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 12 }}>
            <CostHint inputText={input} />
            <button onClick={startAnalysis} disabled={streaming || !input.trim()}
              style={{ ...analyzeBtn, opacity: streaming || !input.trim() ? 0.5 : 1 }}>
              {streaming ? <><FaSpinner className="spin" /> 분석 중...</> : <><FaPlay /> 분석 시작</>}
            </button>
          </div>
        </div>

        {/* 결과 패널 */}
        <div style={panelStyle}>
          <div style={panelHeader}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>결과 {streaming && <FaSpinner className="spin" style={{ marginLeft: '6px', fontSize: '11px' }} />}</span>
            {result && (
              <div style={{ display: 'flex', gap: '5px' }}>
                <button style={smallBtn} onClick={copyResult}>{copied ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}</button>
                <button style={smallBtn} onClick={exportResult}><FaDownload /></button>
              </div>
            )}
          </div>
          <div style={{ flex: 1, overflowY: 'auto', padding: '14px' }}>
            {result ? (
              <div className="markdown-body"><ReactMarkdown remarkPlugins={[remarkGfm]}>{result}</ReactMarkdown></div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)', fontSize: '14px' }}>
                {streaming ? '결과를 기다리는 중...' : '분석을 시작하면 결과가 여기에 표시됩니다.'}
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}

const panelStyle: React.CSSProperties = { background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', display: 'flex', flexDirection: 'column', overflow: 'hidden' }
const panelHeader: React.CSSProperties = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: '1px solid var(--border-color)' }
const smallBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
const analyzeBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 20px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '13px', fontWeight: 600 }
