import { useEffect, useState, useCallback, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaUserCheck, FaCheckCircle, FaTimesCircle, FaClock, FaInbox, FaPaperPlane, FaEye, FaTimes,
} from 'react-icons/fa'
import { useToast } from '../hooks/useToast'
import { useAuthStore } from '../stores/authStore'
import { formatDate } from '../utils/date'
import ReviewActionDialog, { ReviewNoteCard } from '../components/common/ReviewActionDialog'
import { markdownCodeComponents } from '../components/common/CopyableCodeBlock'

// v4.2.7: 이력 상세 모달에 쓰는 fetch 응답 타입.
// ReviewHistoryController.detail() 이 반환하는 필드 집합과 맞춤.
interface HistoryDetail {
  id:       string
  type:     string
  typeCode: string
  title:    string
  date:     string
  input:    string
  output:   string
}

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
  // v4.2.7: 알림 링크(/review-requests?historyId=N)로 진입시 해당 카드로 스크롤 + 하이라이트
  const [searchParams] = useSearchParams()
  const targetHistoryId = (() => {
    const raw = searchParams.get('historyId')
    const n = raw != null ? parseInt(raw, 10) : NaN
    return Number.isFinite(n) ? n : null
  })()
  const [highlightId, setHighlightId] = useState<number | null>(null)
  const cardRefs = useRef<Record<number, HTMLDivElement | null>>({})
  // v4.2.7: 이력 상세 모달 — 리뷰어가 승인/거절 전에 VIEWER 가 생성한 이력 본문을
  // 볼 수 있도록 팀 리뷰 요청 페이지 내에서 팝업으로 연다.
  const [detailItem, setDetailItem]       = useState<ReviewItem | null>(null)
  const [detailData, setDetailData]       = useState<HistoryDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  // v4.2.7: 승인/거절 확정 다이얼로그 (코멘트 입력 포함)
  const [reviewDialog, setReviewDialog] = useState<{ item: ReviewItem; action: 'ACCEPTED' | 'REJECTED' } | null>(null)

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

  // v4.2.7: 목록 로드 후 targetHistoryId 가 있으면 해당 카드로 스크롤 + 3초 하이라이트
  // 대상 이력이 다른 탭에 있을 수 있으므로 현재 탭에서 찾지 못하면 반대 탭으로 전환한다.
  const triedOtherTab = useRef(false)
  useEffect(() => {
    if (targetHistoryId == null || loading) return
    const found = items.find((i) => i.id === targetHistoryId)
    if (!found) {
      if (!triedOtherTab.current) {
        triedOtherTab.current = true
        setTab((t) => (t === 'received' ? 'sent' : 'received'))
      }
      return
    }
    // 카드 DOM 이 렌더된 뒤에 스크롤
    requestAnimationFrame(() => {
      const el = cardRefs.current[targetHistoryId]
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' })
        setHighlightId(targetHistoryId)
        // 3초 뒤 하이라이트 제거
        setTimeout(() => setHighlightId((h) => (h === targetHistoryId ? null : h)), 3000)
      }
    })
  }, [targetHistoryId, items, loading])

  // v4.2.7: 버튼 클릭 → 다이얼로그 오픈 (코멘트 입력 가능)
  const openReviewDialog = (item: ReviewItem, action: 'ACCEPTED' | 'REJECTED') => {
    setReviewDialog({ item, action })
  }
  // 다이얼로그 확정 시 실제 API 호출
  const submitReview = async (item: ReviewItem, status: 'ACCEPTED' | 'REJECTED', note: string) => {
    const actionLabel = status === 'ACCEPTED' ? '승인' : '거절'
    try {
      const res = await fetch(`/history/${item.id}/review-status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ status, note }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (d?.success) {
        toast.success(`${actionLabel}되었습니다.`)
        load(tab)
        // 상세 모달/다이얼로그 모두 닫기
        setDetailItem(null); setDetailData(null)
      } else {
        toast.error(d?.error || `${actionLabel} 실패`)
      }
    } catch {
      toast.error(`${actionLabel} 요청 실패`)
    } finally {
      setReviewDialog(null)
    }
  }

  // v4.2.7: "이력 상세" 클릭 → 모달 오픈 + /history/{id}/detail 로드
  const openDetail = async (item: ReviewItem) => {
    setDetailItem(item)
    setDetailData(null)
    setDetailLoading(true)
    try {
      const res = await fetch(`/history/${item.id}/detail`, { credentials: 'include' })
      if (!res.ok) {
        toast.error('이력을 불러올 수 없습니다.')
        setDetailLoading(false)
        return
      }
      const data = await res.json() as HistoryDetail & { error?: string }
      if (data.error) {
        toast.error(data.error)
      } else {
        setDetailData(data)
      }
    } catch {
      toast.error('이력 상세 요청 실패')
    } finally {
      setDetailLoading(false)
    }
  }
  const closeDetail = () => { setDetailItem(null); setDetailData(null); setDetailLoading(false) }

  // ESC 키로 모달 닫기
  useEffect(() => {
    if (!detailItem) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') closeDetail() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [detailItem])

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
          <div
            key={item.id}
            ref={(el) => { cardRefs.current[item.id] = el }}
            style={{
              padding: '14px 16px',
              background: highlightId === item.id ? 'rgba(139,92,246,0.12)' : 'var(--bg-secondary)',
              border: highlightId === item.id ? '2px solid #8b5cf6' : '1px solid var(--border-color)',
              borderRadius: '10px',
              boxShadow: highlightId === item.id ? '0 0 0 4px rgba(139,92,246,0.15)' : 'none',
              transition: 'background 0.3s, border 0.3s, box-shadow 0.3s',
            }}
          >
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
            {/* v4.2.7: 리뷰 코멘트 친화적 카드 표시 (모든 사용자 열람) */}
            <ReviewNoteCard
              status={item.reviewStatus}
              reviewedBy={item.reviewedBy}
              reviewedAt={item.reviewedAt ? formatDate(item.reviewedAt) : undefined}
              note={item.reviewNote}
            />

            {/* 액션 영역 */}
            <div style={{ display: 'flex', gap: '6px', marginTop: '10px', alignItems: 'center' }}>
              <button onClick={() => openDetail(item)} style={viewBtn} type="button">
                <FaEye /> 이력 상세
              </button>
              {tab === 'received' && canReview && item.reviewStatus === 'PENDING' && (
                <>
                  <button onClick={() => openReviewDialog(item, 'ACCEPTED')} style={acceptBtn}>
                    <FaCheckCircle /> 승인
                  </button>
                  <button onClick={() => openReviewDialog(item, 'REJECTED')} style={rejectBtn}>
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

      {/* v4.2.7: 이력 상세 모달 — VIEWER 가 작성한 이력 본문을 팀 리뷰 요청 페이지에서 직접 확인 */}
      {detailItem && (
        <div
          className="modal-overlay"
          onClick={closeDetail}
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            zIndex: 1000, padding: '20px',
          }}
        >
          <div
            className="modal-body"
            onClick={(e) => e.stopPropagation()}
            style={{
              background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
              borderRadius: '12px', width: 'min(920px, 96vw)', maxHeight: '90vh',
              display: 'flex', flexDirection: 'column', overflow: 'hidden',
              boxShadow: '0 10px 50px rgba(0,0,0,0.4)',
            }}
          >
            {/* 모달 헤더 */}
            <div style={{
              padding: '14px 18px', borderBottom: '1px solid var(--border-color)',
              display: 'flex', alignItems: 'center', gap: '12px', flexShrink: 0,
            }}>
              <StatusBadge status={detailItem.reviewStatus} />
              <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>
                {detailItem.type}
              </span>
              <h3 style={{ flex: 1, fontSize: '15px', fontWeight: 700, margin: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {detailItem.title || '(제목 없음)'}
              </h3>
              <button
                onClick={closeDetail}
                style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '18px', padding: '4px 8px' }}
                title="닫기 (Esc)"
                type="button"
              >
                <FaTimes />
              </button>
            </div>

            {/* 메타 정보 */}
            <div style={{
              padding: '8px 18px', borderBottom: '1px solid var(--border-color)',
              fontSize: '12px', color: 'var(--text-muted)',
              display: 'flex', gap: '14px', flexWrap: 'wrap', flexShrink: 0,
            }}>
              <span>작성자: <strong style={{ color: 'var(--text-sub)' }}>{detailItem.username || '-'}</strong></span>
              <span>작성 시각: {formatDate(detailItem.createdAt)}</span>
              {detailItem.reviewedBy && <span>검토자: <strong style={{ color: 'var(--text-sub)' }}>{detailItem.reviewedBy}</strong></span>}
              {detailItem.reviewedAt && <span>검토 시각: {formatDate(detailItem.reviewedAt)}</span>}
            </div>

            {/* 본문 */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '18px', minHeight: 0 }}>
              {detailLoading ? (
                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>로딩 중...</div>
              ) : detailData ? (
                <>
                  {/* v4.2.7: 이미 검토된 이력이라면 최상단에 리뷰어의 코멘트 카드 먼저 노출 */}
                  <ReviewNoteCard
                    status={detailItem.reviewStatus}
                    reviewedBy={detailItem.reviewedBy}
                    reviewedAt={detailItem.reviewedAt ? formatDate(detailItem.reviewedAt) : undefined}
                    note={detailItem.reviewNote}
                  />
                  <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px', marginTop: '14px' }}>입력</h4>
                  <pre style={{
                    background: 'var(--bg-primary)', padding: '12px', borderRadius: '8px',
                    fontSize: '12px', marginBottom: '14px', whiteSpace: 'pre-wrap',
                    maxHeight: '260px', overflow: 'auto',
                  }}>
                    {detailData.input}
                  </pre>
                  <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>분석 결과</h4>
                  <div className="markdown-body" style={{ fontSize: '13px' }}>
                    <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownCodeComponents}>{detailData.output || ''}</ReactMarkdown>
                  </div>
                </>
              ) : (
                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>이력 내용을 불러올 수 없습니다.</div>
              )}
            </div>

            {/* 푸터 — 승인/거절 */}
            {canReview && detailItem.reviewStatus === 'PENDING' && (
              <div style={{
                padding: '12px 18px', borderTop: '1px solid var(--border-color)',
                display: 'flex', gap: '8px', justifyContent: 'flex-end', flexShrink: 0,
              }}>
                <button onClick={() => openReviewDialog(detailItem, 'ACCEPTED')} style={acceptBtn} type="button">
                  <FaCheckCircle /> 승인
                </button>
                <button onClick={() => openReviewDialog(detailItem, 'REJECTED')} style={rejectBtn} type="button">
                  <FaTimesCircle /> 거절
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* v4.2.7: 승인/거절 확정 다이얼로그 (코멘트 입력) */}
      {reviewDialog && (
        <ReviewActionDialog
          action={reviewDialog.action}
          targetTitle={reviewDialog.item.title || '(제목 없음)'}
          onConfirm={(note) => submitReview(reviewDialog.item, reviewDialog.action, note)}
          onCancel={() => setReviewDialog(null)}
        />
      )}
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

// formatDate 는 utils/date.ts 로 이동 (v4.2.7)

const viewBtn: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: '4px',
  padding: '5px 12px', borderRadius: '6px', fontSize: '11px',
  border: '1px solid var(--border-color)', background: 'transparent',
  color: 'var(--text-sub)', cursor: 'pointer', textDecoration: 'none',
  fontFamily: 'inherit',
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
