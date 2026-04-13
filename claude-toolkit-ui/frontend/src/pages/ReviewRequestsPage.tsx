import { useEffect, useState } from 'react'
import { FaUserCheck, FaCheckCircle, FaTimesCircle, FaClock } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'

interface ReviewRequest {
  id: number
  title: string
  requesterUsername: string
  reviewerUsername: string
  status: string
  createdAt: string
  comment?: string
}

const statusBadge = (status: string) => {
  switch (status) {
    case 'PENDING': return { icon: <FaClock />, color: '#f59e0b', label: '대기 중' }
    case 'APPROVED': return { icon: <FaCheckCircle />, color: '#22c55e', label: '승인' }
    case 'REJECTED': return { icon: <FaTimesCircle />, color: '#ef4444', label: '반려' }
    default: return { icon: <FaClock />, color: '#94a3b8', label: status }
  }
}

export default function ReviewRequestsPage() {
  const [requests, setRequests] = useState<ReviewRequest[]>([])
  const [tab, setTab] = useState<'received' | 'sent'>('received')
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/api/v1/review-requests') as ReviewRequest[] | null
      if (data) setRequests(data)
    }
    load()
  }, [])

  const approve = async (id: number) => {
    await fetch(`/review-requests/${id}/approve`, { method: 'POST', credentials: 'include' })
    toast.success('승인되었습니다.')
    setRequests((prev) => prev.map((r) => r.id === id ? { ...r, status: 'APPROVED' } : r))
  }

  const reject = async (id: number) => {
    const comment = prompt('반려 사유를 입력하세요:')
    if (!comment) return
    await fetch(`/review-requests/${id}/reject`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ comment }),
      credentials: 'include',
    })
    toast.success('반려되었습니다.')
    setRequests((prev) => prev.map((r) => r.id === id ? { ...r, status: 'REJECTED' } : r))
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaUserCheck style={{ color: '#8b5cf6' }} /> 팀 리뷰 요청
      </h2>

      <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
        {(['received', 'sent'] as const).map((t) => (
          <button key={t} onClick={() => setTab(t)} style={{ padding: '6px 16px', borderRadius: '6px', border: '1px solid var(--border-color)', background: tab === t ? 'var(--accent)' : 'transparent', color: tab === t ? '#fff' : 'var(--text-sub)', cursor: 'pointer', fontSize: '13px' }}>
            {t === 'received' ? '내게 온 리뷰' : '내가 요청한 리뷰'}
          </button>
        ))}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {requests.map((r) => {
          const badge = statusBadge(r.status)
          return (
            <div key={r.id} style={{ padding: '14px 16px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '12px', color: badge.color }}>{badge.icon} {badge.label}</span>
                <span style={{ fontSize: '14px', fontWeight: 600, flex: 1 }}>{r.title}</span>
                <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{r.createdAt}</span>
              </div>
              <div style={{ fontSize: '13px', color: 'var(--text-muted)', display: 'flex', gap: '12px' }}>
                <span>요청자: {r.requesterUsername}</span>
                <span>리뷰어: {r.reviewerUsername}</span>
              </div>
              {r.status === 'PENDING' && tab === 'received' && (
                <div style={{ display: 'flex', gap: '6px', marginTop: '10px' }}>
                  <button onClick={() => approve(r.id)} style={{ padding: '5px 14px', borderRadius: '6px', background: 'var(--green)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '12px' }}>승인</button>
                  <button onClick={() => reject(r.id)} style={{ padding: '5px 14px', borderRadius: '6px', background: 'var(--red)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '12px' }}>반려</button>
                </div>
              )}
            </div>
          )
        })}
        {requests.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>리뷰 요청이 없습니다.</div>
        )}
      </div>
    </>
  )
}
