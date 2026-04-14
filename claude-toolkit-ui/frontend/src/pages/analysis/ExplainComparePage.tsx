import { useState, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { FaColumns, FaPlay, FaSpinner, FaCopy, FaCheck, FaArrowRight } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import SourceSelector from '../../components/common/SourceSelector'

export default function ExplainComparePage() {
  const [sqlBefore, setSqlBefore] = useState('')
  const [sqlAfter, setSqlAfter] = useState('')
  const [result, setResult] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [costBefore, setCostBefore] = useState<number | null>(null)
  const [costAfter, setCostAfter] = useState<number | null>(null)
  const [copied, setCopied] = useState(false)
  const esRef = useRef<EventSource | null>(null)
  const toast = useToast()

  const compare = async () => {
    if (!sqlBefore.trim() || !sqlAfter.trim()) { toast.error('전/후 SQL 모두 입력해주세요.'); return }
    setResult('')
    setCostBefore(null)
    setCostAfter(null)
    setStreaming(true)

    // /explain/compare는 Thymeleaf view 반환이므로, /stream/init을 통한 AI 분석만 사용
    try {
      const input = `## BEFORE SQL\n\`\`\`sql\n${sqlBefore}\n\`\`\`\n\n## AFTER SQL\n\`\`\`sql\n${sqlAfter}\n\`\`\``
      const res = await fetch('/stream/init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ feature: 'explain_plan', input }),
        credentials: 'include',
      })
      const data = await res.json()
      const sid = data.streamId || data.id
      if (!sid) { toast.error('스트림 초기화 실패'); setStreaming(false); return }

      let acc = ''
      const es = new EventSource(`/stream/${sid}`, { withCredentials: true })
      esRef.current = es
      es.onmessage = (e) => {
        if (e.data === '[DONE]' || e.data === 'done') { es.close(); esRef.current = null; setStreaming(false); return }
        acc += e.data + '\n'; setResult(acc)
      }
      es.addEventListener('error_msg', (ev: MessageEvent) => { toast.error(ev.data); es.close(); esRef.current = null; setStreaming(false) })
      es.onerror = () => { es.close(); esRef.current = null; setStreaming(false) }
    } catch { toast.error('비교 요청 실패'); setStreaming(false) }
  }

  const copyResult = () => {
    navigator.clipboard.writeText(result)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <FaColumns style={{ fontSize: '22px', color: '#3b82f6' }} />
        <div>
          <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0 }}>실행계획 비교</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>
            전/후 SQL 실행계획을 나란히 비교하고 AI 분석
          </p>
        </div>
      </div>

      {/* 좌우 SQL 입력 */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr', gap: '12px', alignItems: 'stretch', marginBottom: '16px' }}>
        {/* BEFORE */}
        <div style={panelStyle}>
          <div style={panelHeader}>
            <span style={{ fontWeight: 600, fontSize: '13px', color: 'var(--red)' }}>BEFORE (변경 전)</span>
            <SourceSelector mode="sql" onSelect={(code) => setSqlBefore(code)} />
          </div>
          <textarea
            value={sqlBefore}
            onChange={(e) => setSqlBefore(e.target.value)}
            placeholder="변경 전 SQL을 입력하세요..."
            style={textareaStyle}
          />
          {costBefore !== null && (
            <div style={{ padding: '8px 14px', fontSize: '12px', color: 'var(--text-muted)', borderTop: '1px solid var(--border-color)' }}>
              COST: <strong style={{ color: 'var(--text-primary)' }}>{costBefore}</strong>
            </div>
          )}
        </div>

        {/* 화살표 */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{
            width: '40px', height: '40px', borderRadius: '50%',
            background: 'var(--accent-subtle)', display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'var(--accent)', fontSize: '14px',
          }}>
            <FaArrowRight />
          </div>
        </div>

        {/* AFTER */}
        <div style={panelStyle}>
          <div style={panelHeader}>
            <span style={{ fontWeight: 600, fontSize: '13px', color: 'var(--green)' }}>AFTER (변경 후)</span>
            <SourceSelector mode="sql" onSelect={(code) => setSqlAfter(code)} />
          </div>
          <textarea
            value={sqlAfter}
            onChange={(e) => setSqlAfter(e.target.value)}
            placeholder="변경 후 SQL을 입력하세요..."
            style={textareaStyle}
          />
          {costAfter !== null && (
            <div style={{ padding: '8px 14px', fontSize: '12px', color: 'var(--text-muted)', borderTop: '1px solid var(--border-color)' }}>
              COST: <strong style={{ color: 'var(--text-primary)' }}>{costAfter}</strong>
            </div>
          )}
        </div>
      </div>

      {/* 비교 버튼 */}
      <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '16px' }}>
        <button onClick={compare} disabled={streaming || !sqlBefore.trim() || !sqlAfter.trim()}
          style={{
            display: 'flex', alignItems: 'center', gap: '6px',
            padding: '10px 28px', borderRadius: '10px',
            background: 'var(--accent)', color: '#fff', border: 'none',
            cursor: 'pointer', fontSize: '14px', fontWeight: 600,
            opacity: streaming || !sqlBefore.trim() || !sqlAfter.trim() ? 0.5 : 1,
          }}>
          {streaming ? <><FaSpinner className="spin" /> 비교 분석 중...</> : <><FaPlay /> 실행계획 비교</>}
        </button>
      </div>

      {/* 결과 */}
      {(result || streaming) && (
        <div style={{ ...panelStyle, minHeight: '300px' }}>
          <div style={panelHeader}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>
              비교 분석 결과 {streaming && <FaSpinner className="spin" style={{ marginLeft: '6px', fontSize: '11px' }} />}
            </span>
            {result && (
              <button onClick={copyResult} style={smallBtn}>
                {copied ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
              </button>
            )}
          </div>
          <div style={{ padding: '14px', flex: 1, overflowY: 'auto' }}>
            {result ? (
              <div className="markdown-body"><ReactMarkdown remarkPlugins={[remarkGfm]}>{result}</ReactMarkdown></div>
            ) : (
              <div style={{ color: 'var(--text-muted)', fontSize: '13px', textAlign: 'center', padding: '40px' }}>
                분석 결과를 기다리는 중...
              </div>
            )}
          </div>
        </div>
      )}
    </>
  )
}

const panelStyle: React.CSSProperties = { background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', display: 'flex', flexDirection: 'column', overflow: 'hidden', minHeight: '300px' }
const panelHeader: React.CSSProperties = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: '1px solid var(--border-color)' }
const textareaStyle: React.CSSProperties = { flex: 1, margin: '0 14px', border: 'none', resize: 'none', fontFamily: "'Consolas','Monaco',monospace", fontSize: '13px', lineHeight: '1.6', background: 'transparent', outline: 'none', color: 'var(--text-primary)', minHeight: '250px' }
const smallBtn: React.CSSProperties = { background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
