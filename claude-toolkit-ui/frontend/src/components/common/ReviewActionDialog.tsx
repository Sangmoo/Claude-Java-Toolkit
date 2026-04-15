import { useEffect, useRef, useState } from 'react'
import { FaCheckCircle, FaTimesCircle, FaTimes } from 'react-icons/fa'

/**
 * v4.2.7 — 리뷰 승인/거절 확정 다이얼로그.
 *
 * <p>HistoryPage 와 ReviewRequestsPage 양쪽에서 동일한 UX 로 호출되며,
 * 확정 시점에 선택적 코멘트(note) 를 포함해 콜백으로 돌려준다.
 *
 * <p>사용 방식:
 * <pre>
 *   const [dialog, setDialog] = useState&lt;{action:'ACCEPTED'|'REJECTED', item:T} | null&gt;(null)
 *   ...
 *   &lt;button onClick={() => setDialog({action:'ACCEPTED', item})}&gt;승인&lt;/button&gt;
 *   ...
 *   {dialog && (
 *     &lt;ReviewActionDialog
 *       action={dialog.action}
 *       targetTitle={dialog.item.title}
 *       onConfirm={(note) =&gt; { doReview(dialog.item, dialog.action, note); setDialog(null) }}
 *       onCancel={() =&gt; setDialog(null)}
 *     /&gt;
 *   )}
 * </pre>
 */
interface Props {
  action:       'ACCEPTED' | 'REJECTED'
  targetTitle?: string
  onConfirm:    (note: string) => void
  onCancel:     () => void
}

export default function ReviewActionDialog({ action, targetTitle, onConfirm, onCancel }: Props) {
  const [note, setNote] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const isAccept = action === 'ACCEPTED'
  const label    = isAccept ? '승인' : '거절'
  const color    = isAccept ? '#10b981' : '#ef4444'
  const bg       = isAccept ? 'rgba(16,185,129,0.12)' : 'rgba(239,68,68,0.12)'
  const Icon     = isAccept ? FaCheckCircle : FaTimesCircle

  // 포커스 + ESC 닫기 + Ctrl/Cmd+Enter 로 확정
  useEffect(() => {
    textareaRef.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { onCancel() }
      if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { onConfirm(note.trim()) }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
    // note 를 의존성에서 제외 — Ctrl+Enter 시점의 최신값은 closure 로 해결되지 않으므로 ref 대신 setState 기반 재등록을 피하고자 keydown 핸들러 내부에서 직접 읽는 건 복잡해지므로 간단히 의존성 포함
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [note])

  return (
    <div
      className="modal-overlay"
      onClick={onCancel}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 1100, padding: '20px',
      }}
    >
      <div
        className="modal-body"
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'var(--bg-secondary)', border: `2px solid ${color}`,
          borderRadius: '12px', width: 'min(520px, 94vw)',
          display: 'flex', flexDirection: 'column', overflow: 'hidden',
          boxShadow: '0 10px 50px rgba(0,0,0,0.4)',
        }}
      >
        {/* 헤더 */}
        <div style={{
          padding: '14px 18px', background: bg,
          display: 'flex', alignItems: 'center', gap: '10px',
          borderBottom: `1px solid ${color}`,
        }}>
          <Icon style={{ fontSize: '20px', color }} />
          <h3 style={{ flex: 1, fontSize: '15px', fontWeight: 700, margin: 0, color }}>
            {label}하시겠습니까?
          </h3>
          <button
            type="button"
            onClick={onCancel}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '16px', padding: '2px 6px' }}
            title="닫기 (Esc)"
          >
            <FaTimes />
          </button>
        </div>

        {/* 본문 */}
        <div style={{ padding: '16px 18px' }}>
          {targetTitle && (
            <div style={{
              padding: '8px 12px', marginBottom: '12px',
              background: 'var(--bg-primary)', borderRadius: '6px',
              fontSize: '12px', color: 'var(--text-sub)',
              borderLeft: `3px solid ${color}`,
              overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
            }}>
              <span style={{ color: 'var(--text-muted)', marginRight: '6px' }}>대상:</span>
              <strong>{targetTitle}</strong>
            </div>
          )}
          <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-sub)', marginBottom: '6px' }}>
            코멘트 <span style={{ color: 'var(--text-muted)' }}>(선택 사항 — 모든 사용자가 볼 수 있습니다)</span>
          </label>
          <textarea
            ref={textareaRef}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder={isAccept
              ? '승인 사유나 피드백을 남겨주세요... (선택)'
              : '거절 사유나 개선 요청을 남겨주세요... (선택)'}
            rows={4}
            style={{
              width: '100%', boxSizing: 'border-box',
              padding: '10px 12px', fontSize: '13px',
              border: '1px solid var(--border-color)', borderRadius: '8px',
              background: 'var(--bg-primary)', color: 'var(--text-primary)',
              resize: 'vertical', fontFamily: 'inherit', lineHeight: 1.5,
            }}
          />
          <div style={{ marginTop: '6px', fontSize: '10px', color: 'var(--text-muted)', textAlign: 'right' }}>
            Ctrl+Enter 로 확정 · Esc 로 취소
          </div>
        </div>

        {/* 푸터 */}
        <div style={{
          padding: '12px 18px', borderTop: '1px solid var(--border-color)',
          display: 'flex', gap: '8px', justifyContent: 'flex-end',
          background: 'var(--bg-primary)',
        }}>
          <button
            type="button"
            onClick={onCancel}
            style={{
              padding: '8px 18px', borderRadius: '6px', fontSize: '12px', fontWeight: 600,
              background: 'transparent', color: 'var(--text-sub)',
              border: '1px solid var(--border-color)', cursor: 'pointer',
            }}
          >
            취소
          </button>
          <button
            type="button"
            onClick={() => onConfirm(note.trim())}
            style={{
              padding: '8px 22px', borderRadius: '6px', fontSize: '12px', fontWeight: 700,
              background: color, color: '#fff', border: `1px solid ${color}`,
              cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px',
            }}
          >
            <Icon /> {label} 확정
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * v4.2.7 — 리뷰 노트 표시 카드. 승인/거절 결과와 코멘트를 "리뷰어가 남긴 피드백" 형태로
 * 사용자 친화적으로 보여준다. 모든 권한 사용자가 읽을 수 있다.
 */
export function ReviewNoteCard({
  status, reviewedBy, reviewedAt, note,
}: { status: string; reviewedBy?: string; reviewedAt?: string; note?: string }) {
  if (!reviewedBy && !note) return null
  const isAccept = status === 'ACCEPTED'
  const isReject = status === 'REJECTED'
  const color = isAccept ? '#10b981' : isReject ? '#ef4444' : '#f59e0b'
  const bg    = isAccept ? 'rgba(16,185,129,0.08)' : isReject ? 'rgba(239,68,68,0.08)' : 'rgba(245,158,11,0.08)'
  const label = isAccept ? '승인됨' : isReject ? '거절됨' : '검토 대기'
  const Icon  = isAccept ? FaCheckCircle : isReject ? FaTimesCircle : FaCheckCircle

  return (
    <div style={{
      marginTop: '10px', padding: '12px 14px',
      background: bg, border: `1px solid ${color}`, borderRadius: '8px',
      display: 'flex', gap: '10px',
    }}>
      <div style={{
        width: '32px', height: '32px', borderRadius: '50%',
        background: 'var(--bg-secondary)', color,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        flexShrink: 0, fontSize: '14px',
      }}>
        <Icon />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap',
          fontSize: '12px', marginBottom: note ? '6px' : 0,
        }}>
          <strong style={{ color, fontWeight: 700 }}>{label}</strong>
          {reviewedBy && <span style={{ color: 'var(--text-sub)' }}>— {reviewedBy}</span>}
          {reviewedAt && <span style={{ color: 'var(--text-muted)', fontSize: '11px' }}>{reviewedAt}</span>}
        </div>
        {note && (
          <div style={{
            fontSize: '13px', color: 'var(--text-primary)', lineHeight: 1.55,
            whiteSpace: 'pre-wrap', wordBreak: 'break-word',
          }}>
            {note}
          </div>
        )}
      </div>
    </div>
  )
}
