import { useEffect, useState, useCallback } from 'react'
import {
  FaUserCheck, FaCheckCircle, FaTimesCircle, FaClock, FaInbox, FaPaperPlane, FaEye,
} from 'react-icons/fa'
import { useToast } from '../hooks/useToast'
import { useAuthStore } from '../stores/authStore'

interface ReviewItem {
  id: number
  type: string
  title: string
  username: string          // 이력 작성자
  createdAt: string
  reviewStatus: string      // PENDING / ACCEPTED / REJECTED
  reviewedBy?: string
  reviewedAt?: string
  reviewNote?: string
}

export default function ReviewRequestsPage() {
  const [tab, setTab] = useState<'received' | 'sent'>('received')
  const [items, setItems] = useState<ReviewItem[]>([])
  const [loading, setLoading] = useState(false)
  const toast = useToast()
  const user = useAuthStore((s) => s.user)
  const canReview = user?.role === 'ADMIN' || user?.role === 'REVIEWER'

  const load = useCallback(async (currentTab: 'received' | 'sent') => {
    setLoading(true)
    try {
      const res = await fetch(`/api/v1/review-queue?tab=${currentTab}`, { credentials: 'include' })
      const json = await res.json()
      const data = (json.data ?? json) as ReviewItem[]
      setItems(Array.isArray(data) ? data : [])
    } catch {
      setItems([])
    }
    setLoading(false)
  }, [])

  useEffect(() => { load(tab) }, [tab, load])

  const handleReview = async (item: ReviewItem, status: 'ACCEPTED' | 'REJECTED') => {
    const actionLabel = status === 'ACCEPTED' ? '승인' : '거절'
    if (!confirm(`${actionLabel}하시겠습니까?`)) return
    try {
      const res = await fetch(`/history/${item.id}/review-status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ status }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (d?.success) {
        toast.success(`${actionLabel}되었습니다.`)
        // received 탭은 PENDING 만 보이므로 처리 후 목록에서 제거됨 → 재로드
        load(tab)
      } else {
        toast.error(d?.error || `${actionLabel} 실패`)
      }
    } catch { toast.error(`${actionLabel} 요청 실패`) }
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaUserCheck style={{ color: '#8b5cf6' }} /> 팀 리뷰 요청
      </h2>

      {/* 역할 안내 */}
      <div style={{
        marginBottom: '14px', padding: '10px 14px',
        background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
        borderRadius: '8px', fontSize: '12px', color: 'var(--text-muted)', lineHeight: 1.6,
      }}>
        {canReview ? (
          <>👤 현재 역할: <strong>{user?.role}</strong> — 다른 사용자의 검토 대기 이력을 승인·거절할 수 있습니다.</>
        ) : (
          <>👤 현재 역할: <strong>VIEWER</strong> — 본인 이력에 대한 리뷰어의 승인·거절 결과만 확인 가능합니다.</>
        )}
      </div>

      {/* 탭 */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', borderBottom: '1px solid var(--border-color)', paddingBottom: '8px' }}>
        <TabBtn active={tab === 'received'} onClick={() => setTab('received')}>
          <FaInbox /> 내게 온 리뷰 {canReview ? '(검토 대기)' : '(피드백 완료)'}
        </TabBtn>
        <TabBtn active={tab === 'sent'} onClick={() => setTab('sent')}>
          <FaPaperPlane /> 내가 요청한 리뷰
        </TabBtn>
      </div>

      {/* 목록 */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {loading && (
          <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>로딩 중...</div>
        )}
        {!loading && items.map((item) => (
          <div key={item.id} style={{ padding: '14px 16px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px', flexWrap: 'wrap' }}>
              <StatusBadge status={item.reviewStatus} />
              <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>
                {item.type}
              </span>
              <span style={{ fontSize: '14px', fontWeight: 600, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {item.title || '(제목 없음)'}
              </span>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{formatDate(item.createdAt)}</span>
            </div>
            <div style={{ fontSize: '12px', color: 'var(--text-muted)', display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
              <span>작성자: <strong style={{ color: 'var(--text-sub)' }}>{item.username || '-'}</strong></span>
              {item.reviewedBy && (
                <span>검토자: <strong style={{ color: 'var(--text-sub)' }}>{item.reviewedBy}</strong></span>
              )}
              {item.reviewedAt && <span>검토 시각: {formatDate(item.reviewedAt)}</span>}
            </div>
            {item.reviewNote && (
              <div style={{ fontSize: '12px', color: 'var(--text-sub)', fontStyle: 'italic', marginTop: '6px', padding: '6px 10px', background: 'var(--bg-primary)', borderRadius: '6px' }}>
                "{item.reviewNote}"
              </div>
            )}

            {/* 액션 영역 */}
            <div style={{ display: 'flex', gap: '6px', marginTop: '10px', alignItems: 'center' }}>
              <a href={`/history`} style={viewBtn}>
                <FaEye /> 이력 상세
              </a>
              {tab === 'received' && canReview && item.reviewStatus === 'PENDING' && (
                <>
                  <button onClick={() => handleReview(item, 'ACCEPTED')} style={acceptBtn}>
                    <FaCheckCircle /> 승인
                  </button>
                  <button onClick={() => handleReview(item, 'REJECTED')} style={rejectBtn}>
                    <FaTimesCircle /> 거절
                  </button>
                </>
              )}
              {tab === 'received' && !canReview && (
                <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                  🔒 REVIEWER/ADMIN 권한만 승인·거절 가능
                </span>
              )}
            </div>
          </div>
        ))}
        {!loading && items.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
            <FaUserCheck style={{ fontSize: '36px', opacity: 0.3, marginBottom: '12px' }} />
            <p>
              {tab === 'received'
                ? (canReview ? '현재 검토 대기 중인 리뷰가 없습니다.' : '검토 완료된 본인 이력이 없습니다.')
                : '작성한 리뷰 이력이 없습니다.'}
            </p>
          </div>
        )}
      </div>
    </>
  )
}

function TabBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick} style={{
      display: 'flex', alignItems: 'center', gap: '6px',
      padding: '8px 16px', borderRadius: '8px 8px 0 0',
      border: 'none',
      borderBottom: active ? '2px solid var(--accent)' : '2px solid transparent',
      background: active ? 'var(--bg-secondary)' : 'transparent',
      color: active ? 'var(--text-primary)' : 'var(--text-sub)',
      cursor: 'pointer', fontSize: '13px', fontWeight: active ? 700 : 400,
    }}>{children}</button>
  )
}

function StatusBadge({ status }: { status: string }) {
  const cfg = status === 'ACCEPTED'
    ? { icon: <FaCheckCircle />, color: '#10b981', bg: 'rgba(16,185,129,0.12)', label: '승인됨' }
    : status === 'REJECTED'
      ? { icon: <FaTimesCircle />, color: '#ef4444', bg: 'rgba(239,68,68,0.12)', label: '거절됨' }
      : { icon: <FaClock />, color: '#f59e0b', bg: 'rgba(245,158,11,0.12)', label: '검토 대기' }
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: '4px',
      padding: '3px 10px', borderRadius: '12px',
      fontSize: '11px', fontWeight: 700,
      color: cfg.color, background: cfg.bg, border: `1px solid ${cfg.color}`,
    }}>{cfg.icon} {cfg.label}</span>
  )
}

function formatDate(s?: string): string {
  if (!s) return ''
  try {
    const d = new Date(s)
    return d.toLocaleString('ko-KR', { year: '2-digit', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  } catch { return s }
}

const viewBtn: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '4px',
  padding: '5px 12px', borderRadius: '6px', fontSize: '11px',
  border: '1px solid var(--border-color)', background: 'transparent',
  color: 'var(--text-sub)', cursor: 'pointer', textDecoration: 'none',
}
const acceptBtn: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '4px',
  padding: '5px 14px', borderRadius: '6px', fontSize: '11px', fontWeight: 700,
  background: '#10b981', color: '#fff', border: '1px solid #10b981', cursor: 'pointer',
}
const rejectBtn: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '4px',
  padding: '5px 14px', borderRadius: '6px', fontSize: '11px', fontWeight: 700,
  background: '#ef4444', color: '#fff', border: '1px solid #ef4444', cursor: 'pointer',
}
