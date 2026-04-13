import { useState, useRef, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { FaPlay, FaCopy, FaCheck, FaDownload, FaSpinner, FaEraser, FaUpload } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import type { IconType } from 'react-icons'

export interface AnalysisOption {
  name: string
  label: string
  type: 'select' | 'text' | 'textarea' | 'checkbox'
  options?: { value: string; label: string }[]
  defaultValue?: string
  placeholder?: string
}

export interface AnalysisPageConfig {
  title: string
  icon: IconType
  iconColor: string
  description: string
  feature: string           // stream feature name (e.g., 'sql_review')
  inputLabel?: string
  inputPlaceholder?: string
  inputLanguage?: string    // for code input styling
  options?: AnalysisOption[]
  endpoint?: string         // custom endpoint instead of /stream/init
  allowFileUpload?: boolean // 파일 업로드 허용
}

export default function AnalysisPageTemplate({ config }: { config: AnalysisPageConfig }) {
  const [input, setInput] = useState('')
  const [result, setResult] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [copied, setCopied] = useState(false)
  const [optionValues, setOptionValues] = useState<Record<string, string>>(() => {
    const defaults: Record<string, string> = {}
    config.options?.forEach((opt) => {
      if (opt.defaultValue) defaults[opt.name] = opt.defaultValue
    })
    return defaults
  })
  const esRef = useRef<EventSource | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const toast = useToast()
  const Icon = config.icon

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      const text = reader.result as string
      setInput(text)
      toast.success(`${file.name} 로드 완료 (${(file.size / 1024).toFixed(1)}KB)`)
    }
    reader.readAsText(file)
    e.target.value = ''
  }

  const setOption = useCallback((name: string, value: string) => {
    setOptionValues((prev) => ({ ...prev, [name]: value }))
  }, [])

  const startAnalysis = async () => {
    if (!input.trim() || streaming) return

    setResult('')
    setStreaming(true)

    try {
      // Step 1: POST /stream/init
      const params: Record<string, string> = {
        feature: config.feature,
        input: input.trim(),
      }
      if (config.inputLanguage) params.language = config.inputLanguage
      config.options?.forEach((opt) => {
        if (optionValues[opt.name]) params[opt.name] = optionValues[opt.name]
      })

      const endpoint = config.endpoint || '/stream/init'
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(params),
        credentials: 'include',
      })

      if (!res.ok) {
        toast.error('분석 요청에 실패했습니다.')
        setStreaming(false)
        return
      }

      const data = await res.json()
      const streamId = data.streamId || data.id

      if (!streamId) {
        toast.error('스트림 ID를 받지 못했습니다.')
        setStreaming(false)
        return
      }

      // Step 2: SSE stream
      let accumulated = ''
      const es = new EventSource(`/stream/${streamId}`, { withCredentials: true })
      esRef.current = es

      es.onmessage = (e) => {
        const line = e.data
        if (line === '[DONE]' || line === 'done') {
          es.close()
          esRef.current = null
          setStreaming(false)
          return
        }
        accumulated += line + '\n'
        setResult(accumulated)
      }

      es.addEventListener('error_msg', (e: MessageEvent) => {
        toast.error(e.data || '분석 중 오류 발생')
        es.close()
        esRef.current = null
        setStreaming(false)
      })

      es.onerror = () => {
        es.close()
        esRef.current = null
        setStreaming(false)
      }
    } catch {
      toast.error('분석 중 오류가 발생했습니다.')
      setStreaming(false)
    }
  }

  const copyResult = () => {
    navigator.clipboard.writeText(result)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const exportResult = () => {
    const blob = new Blob([result], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${config.feature}_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  const clear = () => {
    esRef.current?.close()
    esRef.current = null
    setResult('')
    setInput('')
    setStreaming(false)
  }

  return (
    <>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '20px' }}>
        <Icon style={{ fontSize: '22px', color: config.iconColor }} />
        <div>
          <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0 }}>{config.title}</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>{config.description}</p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', minHeight: '60vh' }}>
        {/* Input Panel */}
        <div style={panelStyle}>
          <div style={panelHeaderStyle}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>{config.inputLabel || '입력'}</span>
            <div style={{ display: 'flex', gap: '6px' }}>
              {config.allowFileUpload && (
                <>
                  <input ref={fileRef} type="file" accept=".sql,.java,.xml,.txt,.log,.json,.yaml,.yml,.py,.kt" style={{ display: 'none' }} onChange={handleFileUpload} />
                  <button style={smallBtnStyle} onClick={() => fileRef.current?.click()} title="파일 업로드"><FaUpload /></button>
                </>
              )}
              <button style={smallBtnStyle} onClick={clear} title="초기화"><FaEraser /></button>
            </div>
          </div>

          {/* Options */}
          {config.options && config.options.length > 0 && (
            <div style={{ padding: '0 14px', display: 'flex', flexWrap: 'wrap', gap: '10px', marginBottom: '8px' }}>
              {config.options.map((opt) => (
                <div key={opt.name} style={{ fontSize: '13px' }}>
                  <label style={{ color: 'var(--text-muted)', marginRight: '6px' }}>{opt.label}</label>
                  {opt.type === 'select' ? (
                    <select
                      value={optionValues[opt.name] || ''}
                      onChange={(e) => setOption(opt.name, e.target.value)}
                      style={{ fontSize: '13px', padding: '3px 8px' }}
                    >
                      {opt.options?.map((o) => (
                        <option key={o.value} value={o.value}>{o.label}</option>
                      ))}
                    </select>
                  ) : opt.type === 'checkbox' ? (
                    <input
                      type="checkbox"
                      checked={optionValues[opt.name] === 'true'}
                      onChange={(e) => setOption(opt.name, String(e.target.checked))}
                    />
                  ) : (
                    <input
                      type="text"
                      value={optionValues[opt.name] || ''}
                      onChange={(e) => setOption(opt.name, e.target.value)}
                      placeholder={opt.placeholder}
                      style={{ fontSize: '13px', padding: '3px 8px', width: '120px' }}
                    />
                  )}
                </div>
              ))}
            </div>
          )}

          <textarea
            style={textareaStyle}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={config.inputPlaceholder || '코드 또는 SQL을 입력하세요...'}
          />

          <div style={{ padding: '10px 14px', display: 'flex', justifyContent: 'flex-end' }}>
            <button
              style={{ ...analyzeBtn, opacity: streaming || !input.trim() ? 0.5 : 1 }}
              onClick={startAnalysis}
              disabled={streaming || !input.trim()}
            >
              {streaming ? <><FaSpinner className="spin" /> 분석 중...</> : <><FaPlay /> 분석 시작</>}
            </button>
          </div>
        </div>

        {/* Result Panel */}
        <div style={panelStyle}>
          <div style={panelHeaderStyle}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>
              결과 {streaming && <FaSpinner className="spin" style={{ marginLeft: '6px', fontSize: '11px' }} />}
            </span>
            {result && (
              <div style={{ display: 'flex', gap: '6px' }}>
                <button style={smallBtnStyle} onClick={copyResult} title="복사">
                  {copied ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
                </button>
                <button style={smallBtnStyle} onClick={exportResult} title="내보내기">
                  <FaDownload />
                </button>
              </div>
            )}
          </div>

          <div style={{ flex: 1, overflowY: 'auto', padding: '14px' }}>
            {result ? (
              <div className="markdown-body">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{result}</ReactMarkdown>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)', fontSize: '14px' }}>
                {streaming ? '분석 결과를 기다리는 중...' : '분석을 시작하면 결과가 여기에 표시됩니다.'}
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}

const panelStyle: React.CSSProperties = {
  background: 'var(--bg-secondary)',
  border: '1px solid var(--border-color)',
  borderRadius: '12px',
  display: 'flex',
  flexDirection: 'column',
  overflow: 'hidden',
}

const panelHeaderStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  padding: '10px 14px',
  borderBottom: '1px solid var(--border-color)',
}

const textareaStyle: React.CSSProperties = {
  flex: 1,
  margin: '0 14px',
  border: 'none',
  resize: 'none',
  fontFamily: "'Consolas', 'Monaco', monospace",
  fontSize: '13px',
  lineHeight: '1.6',
  background: 'transparent',
  outline: 'none',
}

const smallBtnStyle: React.CSSProperties = {
  background: 'none',
  border: '1px solid var(--border-color)',
  borderRadius: '6px',
  padding: '4px 8px',
  color: 'var(--text-sub)',
  cursor: 'pointer',
  fontSize: '12px',
  display: 'flex',
  alignItems: 'center',
}

const analyzeBtn: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '6px',
  padding: '8px 20px',
  borderRadius: '8px',
  background: 'var(--accent)',
  color: '#fff',
  border: 'none',
  cursor: 'pointer',
  fontSize: '13px',
  fontWeight: 600,
}
