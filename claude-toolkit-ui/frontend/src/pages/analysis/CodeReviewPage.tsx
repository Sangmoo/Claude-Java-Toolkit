import { useState, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaCodeBranch, FaPlay, FaCopy, FaCheck, FaDownload, FaSpinner, FaEraser,
} from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import SourceSelector from '../../components/common/SourceSelector'

const TEMPLATE_HINTS = [
  { value: '', label: '균형 (기본)' },
  { value: 'performance', label: '성능 최적화' },
  { value: 'security', label: '보안 취약점' },
  { value: 'refactoring', label: '리팩터링' },
  { value: 'sql_performance', label: 'SQL 성능' },
  { value: 'readability', label: '가독성' },
]

export default function CodeReviewPage() {
  const [code, setCode] = useState('')
  const [language, setLanguage] = useState('java')
  const [templateHint, setTemplateHint] = useState('')
  const [result, setResult] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [copied, setCopied] = useState(false)

  const esRef = useRef<EventSource | null>(null)
  const toast = useToast()

  const handleSourceSelect = (content: string, lang: 'java' | 'sql') => {
    setCode(content)
    setLanguage(lang)
  }

  // ── 4단계 분석 시작 ──
  const startAnalysis = async () => {
    if (!code.trim() || streaming) return
    setResult('')
    setStreaming(true)

    try {
      const body = new URLSearchParams({ code: code.trim(), language, templateHint })
      const res = await fetch('/harness/stream-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
        credentials: 'include',
      })
      const data = await res.json()
      if (!data.success || !data.streamId) {
        toast.error(data.error || '스트림 초기화 실패')
        setStreaming(false)
        return
      }

      let accumulated = ''
      const es = new EventSource(`/stream/${data.streamId}`, { withCredentials: true })
      esRef.current = es

      es.onmessage = (e) => {
        if (e.data === '[DONE]' || e.data === 'done') {
          es.close()
          esRef.current = null
          setStreaming(false)
          return
        }
        accumulated += e.data + '\n'
        setResult(accumulated)
      }

      es.addEventListener('done', () => {
        es.close()
        esRef.current = null
        setStreaming(false)
      })

      es.addEventListener('error_msg', (e: MessageEvent) => {
        toast.error(e.data || '분석 오류')
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
      toast.error('분석 요청 실패')
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
    a.download = `harness_review_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <FaCodeBranch style={{ fontSize: '22px', color: '#8b5cf6' }} />
        <div>
          <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0 }}>코드 리뷰 하네스</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>
            4단계 AI 파이프라인: Analyst → Builder → Reviewer → Verifier
          </p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', minHeight: '65vh' }}>
        {/* ── 좌측: 입력 ── */}
        <div style={panelStyle}>
          <div style={panelHeaderStyle}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>코드 입력</span>
            <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              <SourceSelector mode="both" onSelect={handleSourceSelect} />
              <button style={smallBtn} onClick={() => { setCode(''); setResult('') }} title="초기화">
                <FaEraser />
              </button>
            </div>
          </div>

          {/* 옵션 */}
          <div style={{ padding: '8px 14px', display: 'flex', gap: '10px', flexWrap: 'wrap', fontSize: '13px' }}>
            <div>
              <label style={{ color: 'var(--text-muted)', marginRight: '6px' }}>언어</label>
              <select value={language} onChange={(e) => setLanguage(e.target.value)} style={{ fontSize: '12px', padding: '3px 6px' }}>
                <option value="java">Java</option>
                <option value="sql">SQL / Oracle</option>
              </select>
            </div>
            <div>
              <label style={{ color: 'var(--text-muted)', marginRight: '6px' }}>포커스</label>
              <select value={templateHint} onChange={(e) => setTemplateHint(e.target.value)} style={{ fontSize: '12px', padding: '3px 6px' }}>
                {TEMPLATE_HINTS.map((h) => <option key={h.value} value={h.value}>{h.label}</option>)}
              </select>
            </div>
          </div>

          <textarea
            style={{ flex: 1, margin: '0 14px', border: 'none', resize: 'none', fontFamily: 'Consolas, Monaco, monospace', fontSize: '13px', lineHeight: '1.6', background: 'transparent', outline: 'none', color: 'var(--text-primary)' }}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="Java/SQL 코드를 입력하거나 위 '소스 선택' 버튼으로 파일/DB 객체를 로드하세요..."
          />

          <div style={{ padding: '10px 14px', display: 'flex', justifyContent: 'flex-end' }}>
            <button onClick={startAnalysis} disabled={streaming || !code.trim()} style={{ ...analyzeBtn, opacity: streaming || !code.trim() ? 0.5 : 1 }}>
              {streaming ? <><FaSpinner className="spin" /> 분석 중 (4단계)...</> : <><FaPlay /> 4단계 분석 시작</>}
            </button>
          </div>
        </div>

        {/* ── 우측: 결과 ── */}
        <div style={panelStyle}>
          <div style={panelHeaderStyle}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>
              분석 결과
              {streaming && <FaSpinner className="spin" style={{ marginLeft: '6px', fontSize: '11px' }} />}
            </span>
            {result && (
              <div style={{ display: 'flex', gap: '6px' }}>
                <button style={smallBtn} onClick={copyResult}>
                  {copied ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
                </button>
                <button style={smallBtn} onClick={exportResult}><FaDownload /></button>
              </div>
            )}
          </div>
          <div style={{ flex: 1, overflowY: 'auto', padding: '14px' }}>
            {result ? (
              <div className="markdown-body">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{result}</ReactMarkdown>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)', fontSize: '14px', flexDirection: 'column', gap: '8px' }}>
                <FaCodeBranch style={{ fontSize: '32px', opacity: 0.3 }} />
                <p>4단계 분석 결과가 여기에 표시됩니다</p>
                <p style={{ fontSize: '12px' }}>① 분석요약 → ② 개선 코드 → ③ 리뷰 → ④ 검증</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}

const panelStyle: React.CSSProperties = { background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', display: 'flex', flexDirection: 'column', overflow: 'hidden' }
const panelHeaderStyle: React.CSSProperties = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: '1px solid var(--border-color)' }
const smallBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
const analyzeBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 20px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '13px', fontWeight: 600 }
