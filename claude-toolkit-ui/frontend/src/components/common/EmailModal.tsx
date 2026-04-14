import { useState } from 'react'
import { FaEnvelope, FaPaperPlane, FaTimes, FaPlus, FaTrash, FaSpinner } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

interface EmailModalProps {
  open: boolean
  onClose: () => void
  /** 기본 제목 — 분석 메뉴명 자동 prefill */
  defaultSubject?: string
  /** 발송할 본문 (마크다운/플레인 텍스트) */
  content: string
  /** 본문 미리보기에 표시할 라벨 (예: "통합 워크스페이스 결과") */
  contentLabel?: string
}

const MAX_RECIPIENTS = 10

export default function EmailModal({ open, onClose, defaultSubject = '', content, contentLabel = '결과' }: EmailModalProps) {
  const [recipients, setRecipients] = useState<string[]>([''])
  const [subject, setSubject] = useState(defaultSubject)
  const [sending, setSending] = useState(false)
  const toast = useToast()

  if (!open) return null

  const updateRecipient = (idx: number, value: string) => {
    setRecipients((prev) => prev.map((r, i) => i === idx ? value : r))
  }
  const addRecipient = () => {
    if (recipients.length >= MAX_RECIPIENTS) {
      toast.warning(`수신자는 최대 ${MAX_RECIPIENTS}명까지 추가할 수 있습니다.`)
      return
    }
    setRecipients((prev) => [...prev, ''])
  }
  const removeRecipient = (idx: number) => {
    if (recipients.length === 1) {
      setRecipients([''])
      return
    }
    setRecipients((prev) => prev.filter((_, i) => i !== idx))
  }

  const send = async () => {
    const valid = recipients.map((r) => r.trim()).filter((r) => r.length > 0)
    if (valid.length === 0) { toast.error('수신자 이메일을 1명 이상 입력해주세요.'); return }
    // 이메일 형식 가벼운 체크
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    const invalid = valid.filter((r) => !re.test(r))
    if (invalid.length > 0) { toast.error(`올바르지 않은 이메일 주소: ${invalid.join(', ')}`); return }
    if (!content || !content.trim()) { toast.error('발송할 본문이 비어 있습니다.'); return }

    setSending(true)
    try {
      const res = await fetch('/email/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          to:      valid.join(','),
          subject: subject || '(제목 없음)',
          content: content,
        }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (!res.ok || !d) {
        toast.error(`서버 오류 (HTTP ${res.status})`)
        return
      }
      if (d.success) {
        toast.success(`${d.sentCount}명에게 발송 완료`)
        onClose()
      } else {
        toast.error(d.error || '발송 실패')
      }
    } catch (e) {
      toast.error('발송 요청 실패: ' + (e instanceof Error ? e.message : String(e)))
    } finally {
      setSending(false)
    }
  }

  const preview = content.length > 600 ? content.substring(0, 600) + '\n\n... (생략됨)' : content

  return (
    <div style={overlay} onClick={onClose}>
      <div style={modal} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
          <h3 style={{ fontSize: '16px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px', margin: 0 }}>
            <FaEnvelope style={{ color: 'var(--accent)' }} /> 이메일 발송
          </h3>
          <button onClick={onClose} style={iconBtn}><FaTimes /></button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {/* 제목 */}
          <div>
            <label style={labelSt}>제목</label>
            <input
              type="text"
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              placeholder="이메일 제목"
              style={inputSt}
            />
          </div>

          {/* 수신자 목록 */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
              <label style={labelSt}>수신자 ({recipients.filter((r) => r.trim()).length}/{MAX_RECIPIENTS})</label>
              <button
                onClick={addRecipient}
                disabled={recipients.length >= MAX_RECIPIENTS}
                style={{
                  display: 'flex', alignItems: 'center', gap: '4px',
                  background: 'none', border: '1px solid var(--accent)', color: 'var(--accent)',
                  borderRadius: '6px', padding: '3px 10px', fontSize: '11px', cursor: 'pointer',
                  opacity: recipients.length >= MAX_RECIPIENTS ? 0.4 : 1,
                }}>
                <FaPlus /> 수신자 추가
              </button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', maxHeight: '240px', overflowY: 'auto', paddingRight: '4px' }}>
              {recipients.map((r, i) => (
                <div key={i} style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                  <input
                    type="email"
                    value={r}
                    onChange={(e) => updateRecipient(i, e.target.value)}
                    placeholder={`recipient${i + 1}@example.com`}
                    style={{ ...inputSt, flex: 1 }}
                  />
                  <button
                    onClick={() => removeRecipient(i)}
                    title="삭제"
                    style={{
                      background: 'none', border: '1px solid var(--border-color)', color: 'var(--red)',
                      borderRadius: '6px', padding: '6px 10px', cursor: 'pointer', fontSize: '12px',
                    }}>
                    <FaTrash />
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* 본문 미리보기 */}
          <div>
            <label style={labelSt}>{contentLabel} 미리보기 ({content.length.toLocaleString()}자)</label>
            <pre style={{
              background: 'var(--bg-primary)', border: '1px solid var(--border-color)',
              borderRadius: '6px', padding: '10px', fontSize: '11.5px', lineHeight: 1.5,
              maxHeight: '180px', overflowY: 'auto', whiteSpace: 'pre-wrap', fontFamily: 'inherit',
              margin: 0, color: 'var(--text-sub)',
            }}>{preview}</pre>
          </div>

          {/* 발송 버튼 */}
          <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '4px' }}>
            <button onClick={onClose} disabled={sending} style={{
              background: 'transparent', border: '1px solid var(--border-color)',
              color: 'var(--text-sub)', borderRadius: '8px',
              padding: '8px 18px', fontSize: '13px', cursor: 'pointer',
            }}>취소</button>
            <button onClick={send} disabled={sending} style={{
              display: 'flex', alignItems: 'center', gap: '6px',
              background: 'var(--accent)', border: 'none',
              color: '#fff', borderRadius: '8px',
              padding: '8px 20px', fontSize: '13px', fontWeight: 600,
              cursor: sending ? 'not-allowed' : 'pointer', opacity: sending ? 0.6 : 1,
            }}>
              {sending ? <><FaSpinner className="spin" /> 발송 중...</> : <><FaPaperPlane /> 발송</>}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

const overlay: React.CSSProperties = {
  position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
  display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 600,
}
const modal: React.CSSProperties = {
  background: 'var(--bg-secondary)', borderRadius: '16px',
  border: '1px solid var(--border-color)', padding: '24px',
  width: 'min(560px, 92vw)', maxHeight: '90vh', overflowY: 'auto',
}
const iconBtn: React.CSSProperties = {
  background: 'none', border: 'none', color: 'var(--text-muted)',
  cursor: 'pointer', fontSize: '16px',
}
const labelSt: React.CSSProperties = {
  display: 'block', fontSize: '11px', fontWeight: 600,
  color: 'var(--text-muted)', marginBottom: '4px',
}
const inputSt: React.CSSProperties = {
  width: '100%', padding: '8px 12px', fontSize: '13px',
  borderRadius: '6px', border: '1px solid var(--border-color)',
  background: 'var(--bg-primary)', color: 'var(--text-primary)',
}
