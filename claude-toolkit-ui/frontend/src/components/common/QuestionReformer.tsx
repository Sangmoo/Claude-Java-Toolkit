import { useState } from 'react'
import { FaMagic, FaTimes, FaSpinner } from 'react-icons/fa'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/**
 * v4.2.8 — D3: 질문 리포머 버튼 + 결과 모달.
 *
 * <p>분석 페이지의 입력 textarea 옆에 배치하면 된다. 버튼 클릭시 현재 textarea 내용을
 * Claude 에게 보내 "어느 관점으로 해석할지" 명료화 옵션을 받는다. 결과는 모달로
 * 표시되고, 사용자는 제안을 읽고 직접 원문을 편집하거나 제안을 원문에 append 할 수 있다.
 *
 * <p>사용:
 * <pre>
 *   &lt;QuestionReformer
 *     value={input}
 *     onUpdate={(next) => setInput(next)}
 *   /&gt;
 * </pre>
 */
interface Props {
  value:    string
  onUpdate: (next: string) => void
  /** 버튼 스타일 오버라이드 */
  style?:   React.CSSProperties
}

export default function QuestionReformer({ value, onUpdate, style }: Props) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [suggestions, setSuggestions] = useState('')
  const [error, setError] = useState<string | null>(null)

  const runRefine = async () => {
    if (!value.trim()) return
    setOpen(true)
    setLoading(true)
    setError(null)
    setSuggestions('')
    try {
      const res = await fetch('/api/v1/reformer/refine', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ input: value }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (res.ok && d?.success && d.suggestions) {
        setSuggestions(d.suggestions)
      } else {
        setError(d?.error || '질문 리포머 호출 실패')
      }
    } catch {
      setError('요청 실패')
    }
    setLoading(false)
  }

  const applyAppend = () => {
    if (!suggestions) return
    // 원문 끝에 구분선 + 제안을 append
    const next = value.trim() + '\n\n---\n' + suggestions.trim()
    onUpdate(next)
    setOpen(false)
  }

  return (
    <>
      <button
        type="button"
        onClick={runRefine}
        disabled={!value.trim()}
        title="Claude 가 질문을 검토하고 더 구체적인 관점을 제안합니다"
        style={{
          display: 'inline-flex', alignItems: 'center', gap: '5px',
          padding: '6px 12px', borderRadius: '6px',
          background: 'var(--accent-subtle)', color: 'var(--accent)',
          border: '1px solid var(--accent)',
          fontSize: '12px', fontWeight: 600,
          cursor: value.trim() ? 'pointer' : 'not-allowed',
          opacity: value.trim() ? 1 : 0.4,
          ...style,
        }}>
        <FaMagic /> 질문 다듬기
      </button>

      {open && (
        <div className="modal-overlay" onClick={() => setOpen(false)} style={overlay}>
          <div className="modal-body" onClick={(e) => e.stopPropagation()} style={modalBody}>
            <div style={header}>
              <FaMagic style={{ color: 'var(--accent)' }} />
              <h3 style={{ flex: 1, margin: 0, fontSize: '14px', fontWeight: 700 }}>
                질문 리포머 — Claude 가 제안합니다
              </h3>
              <button onClick={() => setOpen(false)} style={closeBtn} title="닫기 (Esc)">
                <FaTimes />
              </button>
            </div>
            <div style={bodyStyle}>
              {loading && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '20px', color: 'var(--text-muted)' }}>
                  <FaSpinner className="spin" /> Claude 가 질문을 검토 중입니다...
                </div>
              )}
              {error && (
                <div style={{ padding: '16px', color: 'var(--red)', fontSize: '13px' }}>❌ {error}</div>
              )}
              {!loading && !error && suggestions && (
                <div className="markdown-body" style={{ fontSize: '13px' }}>
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{suggestions}</ReactMarkdown>
                </div>
              )}
            </div>
            {!loading && !error && suggestions && (
              <div style={footer}>
                <button onClick={() => setOpen(false)} style={secondaryBtn}>닫기</button>
                <button onClick={applyAppend} style={primaryBtn}>제안을 원문에 추가</button>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  )
}

const overlay: React.CSSProperties = {
  position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  zIndex: 1050, padding: '20px',
}
const modalBody: React.CSSProperties = {
  background: 'var(--bg-secondary)', border: '2px solid var(--accent)',
  borderRadius: '12px', width: 'min(560px, 94vw)', maxHeight: '80vh',
  display: 'flex', flexDirection: 'column', overflow: 'hidden',
  boxShadow: '0 12px 40px rgba(0,0,0,0.4)',
}
const header: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '10px',
  padding: '12px 16px', borderBottom: '1px solid var(--border-color)',
  background: 'var(--accent-subtle)',
}
const closeBtn: React.CSSProperties = {
  background: 'none', border: 'none', color: 'var(--text-muted)',
  cursor: 'pointer', fontSize: '16px', padding: '4px 8px',
}
const bodyStyle: React.CSSProperties = {
  flex: 1, overflowY: 'auto', padding: '16px',
}
const footer: React.CSSProperties = {
  padding: '10px 16px', borderTop: '1px solid var(--border-color)',
  display: 'flex', gap: '8px', justifyContent: 'flex-end',
  background: 'var(--bg-primary)',
}
const secondaryBtn: React.CSSProperties = {
  padding: '6px 14px', borderRadius: '6px', fontSize: '12px',
  background: 'transparent', color: 'var(--text-sub)',
  border: '1px solid var(--border-color)', cursor: 'pointer',
}
const primaryBtn: React.CSSProperties = {
  padding: '6px 16px', borderRadius: '6px', fontSize: '12px', fontWeight: 700,
  background: 'var(--accent)', color: '#fff',
  border: '1px solid var(--accent)', cursor: 'pointer',
}
