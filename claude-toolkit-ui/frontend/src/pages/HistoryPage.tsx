import { useEffect, useState, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaHistory, FaSearch, FaTrash, FaStar, FaCopy, FaCheck,
  FaChevronDown, FaChevronUp, FaDownload, FaCheckCircle, FaTimesCircle, FaClock,
  FaReply, FaComment, FaPaperPlane,
} from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'
import { useAuthStore } from '../stores/authStore'
import { copyToClipboard } from '../utils/clipboard'

interface HistoryItem {
  id: number
  type: string
  title?: string
  inputContent: string
  outputContent: string
  createdAt: string
  username?: string
  reviewStatus?: string
  reviewedBy?: string
  reviewedAt?: string
  reviewNote?: string
}

interface Comment {
  id: number
  parentId: number | null
  username: string
  content: string
  createdAt: string
}

export default function HistoryPage() {
  const [items, setItems] = useState<HistoryItem[]>([])
  const [filter, setFilter] = useState('')
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [copiedId, setCopiedId] = useState<number | null>(null)
  const [commentsByHistory, setCommentsByHistory] = useState<Record<number, Comment[]>>({})
  const [newComment, setNewComment] = useState<Record<number, string>>({})
  const [replyParent, setReplyParent] = useState<Record<number, number | null>>({})
  const [replyText, setReplyText] = useState<Record<number, string>>({})
  const api = useApi()
  const toast = useToast()
  const user = useAuthStore((s) => s.user)
  const canReview = user?.role === 'ADMIN' || user?.role === 'REVIEWER'

  const loadHistory = useCallback(async () => {
    const data = await api.get('/api/v1/history') as HistoryItem[] | null
    if (data) setItems(data)
  }, [])

  useEffect(() => { loadHistory() }, [loadHistory])

  const loadComments = async (historyId: number) => {
    try {
      const res = await fetch(`/history/${historyId}/comments`, { credentials: 'include' })
      if (res.ok) {
        const list: Comment[] = await res.json()
        setCommentsByHistory((prev) => ({ ...prev, [historyId]: list }))
      }
    } catch { /* ignore */ }
  }

  const toggleExpand = (id: number) => {
    if (expandedId === id) {
      setExpandedId(null)
    } else {
      setExpandedId(id)
      loadComments(id)
    }
  }

  const filtered = items.filter((item) => {
    if (!filter) return true
    const q = filter.toLowerCase()
    return (
      item.type?.toLowerCase().includes(q) ||
      item.inputContent?.toLowerCase().includes(q) ||
      item.title?.toLowerCase().includes(q)
    )
  })

  const deleteItem = async (id: number) => {
    if (!confirm('삭제하시겠습니까?')) return
    await fetch(`/history/${id}/delete`, { method: 'POST', credentials: 'include' })
    setItems((prev) => prev.filter((i) => i.id !== id))
    toast.success('삭제되었습니다.')
  }

  const addFavorite = async (item: HistoryItem) => {
    await fetch('/favorites/star', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ historyId: String(item.id) }),
      credentials: 'include',
    })
    toast.success('즐겨찾기에 추가되었습니다.')
  }

  const copyResult = async (item: HistoryItem) => {
    const ok = await copyToClipboard(item.outputContent || '')
    if (ok) {
      setCopiedId(item.id)
      setTimeout(() => setCopiedId(null), 3000)
      toast.success('복사되었습니다.')
    } else {
      toast.error('복사 실패')
    }
  }

  const exportAll = () => {
    const md = filtered.map((i) =>
      `## ${i.type} — ${i.createdAt}\n\n### 입력\n\`\`\`\n${i.inputContent}\n\`\`\`\n\n### 결과\n${i.outputContent}\n\n---\n`
    ).join('\n')
    const blob = new Blob([md], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `history_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  const reviewHistory = async (item: HistoryItem, status: 'ACCEPTED' | 'REJECTED') => {
    const action = status === 'ACCEPTED' ? '승인' : '거절'
    if (!confirm(`${action}하시겠습니까?`)) return
    try {
      const res = await fetch(`/history/${item.id}/review-status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ status }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (d?.success) {
        toast.success(`${action}되었습니다.`)
        setItems((prev) => prev.map((i) => i.id === item.id
          ? { ...i, reviewStatus: status, reviewedBy: user?.username, reviewedAt: new Date().toISOString() }
          : i))
      } else {
        toast.error(d?.error || `${action} 실패`)
      }
    } catch {
      toast.error(`${action} 요청 실패`)
    }
  }

  const postComment = async (historyId: number, content: string, parentId: number | null = null) => {
    if (!content.trim()) { toast.error('댓글 내용을 입력하세요.'); return }
    try {
      const body = new URLSearchParams({ content: content.trim() })
      if (parentId != null) body.set('parentId', String(parentId))
      const res = await fetch(`/history/${historyId}/comments`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (d?.success) {
        if (parentId != null) {
          setReplyText((prev) => ({ ...prev, [historyId]: '' }))
          setReplyParent((prev) => ({ ...prev, [historyId]: null }))
        } else {
          setNewComment((prev) => ({ ...prev, [historyId]: '' }))
        }
        loadComments(historyId)
      } else {
        toast.error(d?.error || '작성 실패')
      }
    } catch { toast.error('요청 실패') }
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaHistory style={{ color: '#f59e0b' }} /> 리뷰 이력
          <span style={{ fontSize: '13px', color: 'var(--text-muted)', fontWeight: 400 }}>({filtered.length}건)</span>
        </h2>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <div style={{ position: 'relative' }}>
            <FaSearch style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)', fontSize: '13px' }} />
            <input
              style={{ paddingLeft: '30px', width: '220px', fontSize: '13px' }}
              placeholder="검색..."
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
          <button onClick={exportAll} style={btnStyle} title="전체 내보내기"><FaDownload /> 내보내기</button>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {filtered.map((item) => {
          const comments = commentsByHistory[item.id] || []
          const topComments = comments.filter((c) => c.parentId == null)
          const repliesByParent: Record<number, Comment[]> = {}
          comments.forEach((c) => {
            if (c.parentId != null) {
              if (!repliesByParent[c.parentId]) repliesByParent[c.parentId] = []
              repliesByParent[c.parentId].push(c)
            }
          })

          return (
            <div key={item.id} style={cardStyle}>
              <div
                style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '12px 16px', cursor: 'pointer' }}
                onClick={() => toggleExpand(item.id)}
              >
                <span style={badgeStyle}>{item.type}</span>
                <ReviewStatusBadge status={item.reviewStatus} />
                <span style={{ flex: 1, fontSize: '13px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--text-sub)' }}>
                  {item.title || item.inputContent?.slice(0, 80)}
                </span>
                <span style={{ fontSize: '12px', color: 'var(--text-muted)', flexShrink: 0 }}>{formatDate(item.createdAt)}</span>
                <div style={{ display: 'flex', gap: '4px' }}>
                  <button style={iconBtnStyle} onClick={(e) => { e.stopPropagation(); copyResult(item) }}>
                    {copiedId === item.id ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
                  </button>
                  <button style={iconBtnStyle} onClick={(e) => { e.stopPropagation(); addFavorite(item) }}><FaStar /></button>
                  <button style={{ ...iconBtnStyle, color: 'var(--red)' }} onClick={(e) => { e.stopPropagation(); deleteItem(item.id) }}><FaTrash /></button>
                </div>
                {expandedId === item.id ? <FaChevronUp style={{ color: 'var(--text-muted)' }} /> : <FaChevronDown style={{ color: 'var(--text-muted)' }} />}
              </div>

              {expandedId === item.id && (
                <div style={{ borderTop: '1px solid var(--border-color)', padding: '16px' }}>
                  {/* 리뷰 승인/거절 영역 */}
                  <div style={reviewBarStyle}>
                    <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                      <ReviewStatusBadge status={item.reviewStatus} large />
                      {item.reviewedBy && (
                        <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                          {item.reviewedBy} · {formatDate(item.reviewedAt)}
                        </span>
                      )}
                      {item.reviewNote && (
                        <span style={{ fontSize: '12px', color: 'var(--text-sub)', fontStyle: 'italic' }}>"{item.reviewNote}"</span>
                      )}
                    </div>
                    {canReview ? (
                      <div style={{ display: 'flex', gap: '6px' }}>
                        <button
                          onClick={() => reviewHistory(item, 'ACCEPTED')}
                          style={{ ...actionBtnStyle, background: '#10b981', color: '#fff', borderColor: '#10b981' }}>
                          <FaCheckCircle /> 승인
                        </button>
                        <button
                          onClick={() => reviewHistory(item, 'REJECTED')}
                          style={{ ...actionBtnStyle, background: '#ef4444', color: '#fff', borderColor: '#ef4444' }}>
                          <FaTimesCircle /> 거절
                        </button>
                      </div>
                    ) : (
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                        REVIEWER/ADMIN 만 승인·거절 가능
                      </span>
                    )}
                  </div>

                  <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px', marginTop: '14px' }}>입력</h4>
                  <pre style={{ background: 'var(--bg-primary)', padding: '10px', borderRadius: '6px', fontSize: '12px', marginBottom: '12px', whiteSpace: 'pre-wrap', maxHeight: '150px', overflow: 'auto' }}>
                    {item.inputContent}
                  </pre>
                  <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>결과</h4>
                  <div className="markdown-body" style={{ fontSize: '13px' }}>
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{item.outputContent || ''}</ReactMarkdown>
                  </div>

                  {/* 댓글 섹션 */}
                  <div style={{ marginTop: '20px', borderTop: '1px solid var(--border-color)', paddingTop: '14px' }}>
                    <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '10px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <FaComment /> 댓글 ({comments.length})
                    </h4>
                    {topComments.map((c) => (
                      <div key={c.id} style={{ marginBottom: '10px' }}>
                        <CommentBox comment={c} />
                        <button
                          onClick={() => setReplyParent((prev) => ({ ...prev, [item.id]: prev[item.id] === c.id ? null : c.id }))}
                          style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: '11px', cursor: 'pointer', marginLeft: '34px', marginTop: '2px' }}>
                          <FaReply style={{ fontSize: '9px' }} /> 답글
                        </button>
                        {(repliesByParent[c.id] || []).map((r) => (
                          <div key={r.id} style={{ marginLeft: '32px', marginTop: '6px' }}>
                            <CommentBox comment={r} isReply />
                          </div>
                        ))}
                        {replyParent[item.id] === c.id && (
                          <div style={{ marginLeft: '32px', marginTop: '6px', display: 'flex', gap: '6px' }}>
                            <input
                              value={replyText[item.id] || ''}
                              onChange={(e) => setReplyText((prev) => ({ ...prev, [item.id]: e.target.value }))}
                              placeholder="답글..."
                              style={{ ...commentInputStyle, flex: 1 }}
                              onKeyDown={(e) => { if (e.key === 'Enter') postComment(item.id, replyText[item.id] || '', c.id) }}
                            />
                            <button
                              onClick={() => postComment(item.id, replyText[item.id] || '', c.id)}
                              style={commentSendStyle}><FaPaperPlane /></button>
                          </div>
                        )}
                      </div>
                    ))}
                    {topComments.length === 0 && (
                      <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'center', padding: '12px' }}>
                        첫 댓글을 남겨보세요.
                      </div>
                    )}
                    {/* 새 댓글 입력 */}
                    <div style={{ display: 'flex', gap: '6px', marginTop: '12px' }}>
                      <input
                        value={newComment[item.id] || ''}
                        onChange={(e) => setNewComment((prev) => ({ ...prev, [item.id]: e.target.value }))}
                        placeholder="댓글을 입력하세요..."
                        style={{ ...commentInputStyle, flex: 1 }}
                        onKeyDown={(e) => { if (e.key === 'Enter') postComment(item.id, newComment[item.id] || '') }}
                      />
                      <button
                        onClick={() => postComment(item.id, newComment[item.id] || '')}
                        style={commentSendStyle}><FaPaperPlane /> 작성</button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )
        })}
        {filtered.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
            <FaHistory style={{ fontSize: '36px', opacity: 0.3, marginBottom: '12px' }} />
            <p>이력이 없습니다.</p>
          </div>
        )}
      </div>
    </>
  )
}

function ReviewStatusBadge({ status, large = false }: { status?: string; large?: boolean }) {
  const s = status || 'PENDING'
  const cfg = s === 'ACCEPTED'
    ? { icon: <FaCheckCircle />, color: '#10b981', bg: 'rgba(16,185,129,0.12)', label: '승인됨' }
    : s === 'REJECTED'
      ? { icon: <FaTimesCircle />, color: '#ef4444', bg: 'rgba(239,68,68,0.12)', label: '거절됨' }
      : { icon: <FaClock />, color: '#f59e0b', bg: 'rgba(245,158,11,0.12)', label: '검토 대기' }
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: '4px',
      padding: large ? '4px 10px' : '2px 8px',
      borderRadius: '12px', fontSize: large ? '12px' : '11px', fontWeight: 600,
      color: cfg.color, background: cfg.bg, border: `1px solid ${cfg.color}`,
      flexShrink: 0,
    }}>
      {cfg.icon} {cfg.label}
    </span>
  )
}

function CommentBox({ comment, isReply = false }: { comment: Comment; isReply?: boolean }) {
  return (
    <div style={{ display: 'flex', gap: '8px', padding: '8px 10px', background: isReply ? 'var(--bg-primary)' : 'var(--bg-secondary)', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
      <div style={{ width: '28px', height: '28px', borderRadius: '50%', background: 'var(--accent-subtle)', color: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '11px', fontWeight: 700, flexShrink: 0 }}>
        {comment.username.substring(0, 2).toUpperCase()}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
          {comment.username}
          <span style={{ fontWeight: 400, color: 'var(--text-muted)', fontSize: '10px' }}>{comment.createdAt}</span>
        </div>
        <div style={{ fontSize: '12px', color: 'var(--text-sub)', marginTop: '2px', whiteSpace: 'pre-wrap' }}>{comment.content}</div>
      </div>
    </div>
  )
}

function formatDate(s?: string): string {
  if (!s) return ''
  try {
    const d = new Date(s)
    return d.toLocaleString('ko-KR', { year: '2-digit', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  } catch { return s }
}

const cardStyle: React.CSSProperties = {
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '10px', overflow: 'hidden',
}
const badgeStyle: React.CSSProperties = {
  fontSize: '11px', padding: '2px 8px', borderRadius: '4px',
  background: 'var(--accent-subtle)', color: 'var(--accent)', flexShrink: 0,
}
const iconBtnStyle: React.CSSProperties = {
  background: 'none', border: 'none', color: 'var(--text-muted)',
  cursor: 'pointer', padding: '4px', fontSize: '12px',
}
const btnStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '6px',
  padding: '6px 14px', borderRadius: '6px', fontSize: '13px',
  border: '1px solid var(--border-color)', background: 'transparent',
  color: 'var(--text-sub)', cursor: 'pointer',
}
const reviewBarStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '10px', padding: '10px 12px',
  background: 'var(--bg-primary)', border: '1px solid var(--border-color)', borderRadius: '8px',
  flexWrap: 'wrap',
}
const actionBtnStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '4px',
  padding: '6px 14px', borderRadius: '6px', fontSize: '12px',
  border: '1px solid', cursor: 'pointer', fontWeight: 600,
}
const commentInputStyle: React.CSSProperties = {
  padding: '6px 10px', fontSize: '12px',
  border: '1px solid var(--border-color)', borderRadius: '6px',
  background: 'var(--bg-primary)', color: 'var(--text-primary)',
}
const commentSendStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '4px',
  padding: '6px 12px', borderRadius: '6px', fontSize: '12px',
  border: 'none', background: 'var(--accent)', color: '#fff', cursor: 'pointer',
}
