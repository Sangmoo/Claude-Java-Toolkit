import { useEffect, useState } from 'react'
import {
  FaBug, FaSync, FaCheck, FaUndo, FaTimes, FaExclamationTriangle,
  FaCheckCircle, FaTrashAlt, FaUser, FaCalendar, FaCode,
} from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import { formatRelative, formatDate } from '../../utils/date'

/**
 * v4.4.0 — 자체 구축 에러 모니터링 페이지 (ADMIN 전용).
 *
 * 백엔드 ErrorLogController + ErrorLogService 와 연동.
 * 같은 (예외클래스+메시지) 그룹은 dedupe 되어 occurrenceCount 만 증가.
 */

interface ErrorItem {
  id: number
  level: string
  exceptionClass: string
  message: string
  occurrenceCount: number
  createdAt: string
  lastOccurredAt: string
  requestPath: string | null
  requestMethod: string | null
  username: string | null
  resolved: boolean
  resolvedBy: string | null
  resolvedAt: string | null
}

interface ErrorDetail extends ErrorItem {
  stackTrace: string | null
  userAgent: string | null
  clientIp: string | null
  dedupeKey: string
}

interface ListResponse {
  items: ErrorItem[]
  totalCount: number
  unresolvedCount: number
}

const PRESETS = [
  { label: '전체', unresolvedOnly: false },
  { label: '미해결', unresolvedOnly: true },
]

export default function AdminErrorLogPage() {
  const [data, setData] = useState<ListResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [unresolvedOnly, setUnresolvedOnly] = useState(true)
  const [filter, setFilter] = useState('')
  const [detailItem, setDetailItem] = useState<ErrorDetail | null>(null)
  const [autoRefresh, setAutoRefresh] = useState(false)
  const toast = useToast()

  const load = () => {
    setLoading(true)
    fetch(`/api/v1/admin/errors?limit=200&unresolvedOnly=${unresolvedOnly}`, { credentials: 'include' })
      .then(r => r.ok ? r.json() : null)
      .then(j => setData((j?.data ?? j) as ListResponse))
      .catch(() => setData(null))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [unresolvedOnly])

  // 자동 새로고침 (30초)
  useEffect(() => {
    if (!autoRefresh) return
    const t = setInterval(load, 30000)
    return () => clearInterval(t)
  }, [autoRefresh, unresolvedOnly])

  const openDetail = async (id: number) => {
    try {
      const res = await fetch(`/api/v1/admin/errors/${id}`, { credentials: 'include' })
      const j = await res.json()
      setDetailItem((j?.data ?? j) as ErrorDetail)
    } catch (e) {
      toast.error('상세 로드 실패')
    }
  }

  const resolveItem = async (id: number, e?: React.MouseEvent) => {
    e?.stopPropagation()
    try {
      await fetch(`/api/v1/admin/errors/${id}/resolve`, { method: 'POST', credentials: 'include' })
      toast.success('해결로 표시됨')
      load()
      if (detailItem?.id === id) setDetailItem({ ...detailItem, resolved: true })
    } catch { toast.error('처리 실패') }
  }

  const unresolveItem = async (id: number, e?: React.MouseEvent) => {
    e?.stopPropagation()
    try {
      await fetch(`/api/v1/admin/errors/${id}/unresolve`, { method: 'POST', credentials: 'include' })
      toast.info('미해결로 복귀')
      load()
      if (detailItem?.id === id) setDetailItem({ ...detailItem, resolved: false })
    } catch { toast.error('처리 실패') }
  }

  const purge = async () => {
    if (!confirm('30일 이전의 해결 처리된 오류 그룹을 모두 삭제할까요?')) return
    try {
      const r = await fetch('/api/v1/admin/errors/purge?days=30', {
        method: 'DELETE', credentials: 'include',
      })
      const j = await r.json()
      toast.success(`${j?.data?.deleted || 0}개 항목 삭제됨`)
      load()
    } catch { toast.error('삭제 실패') }
  }

  const visible = (data?.items ?? []).filter(it => {
    if (!filter) return true
    const q = filter.toLowerCase()
    return (it.exceptionClass + ' ' + it.message + ' ' + (it.requestPath ?? '')).toLowerCase().includes(q)
  })

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px', marginBottom: '16px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px', margin: 0 }}>
          <FaBug style={{ color: '#ef4444' }} /> 오류 로그
          {data && (
            <>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400 }}>
                · 전체 {data.totalCount.toLocaleString()}건
              </span>
              {data.unresolvedCount > 0 && (
                <span style={{
                  fontSize: '11px', padding: '2px 8px', borderRadius: '4px',
                  background: 'rgba(239,68,68,0.15)', color: '#ef4444', fontWeight: 700,
                }}>미해결 {data.unresolvedCount.toLocaleString()}</span>
              )}
            </>
          )}
        </h2>
        <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
          <input
            type="text" value={filter} onChange={(e) => setFilter(e.target.value)}
            placeholder="검색 (예외 / 메시지 / 경로)"
            style={{ padding: '6px 10px', fontSize: '12px', minWidth: '200px',
                     background: 'var(--bg-default)', border: '1px solid var(--border-color)',
                     borderRadius: '4px', color: 'var(--text-default)' }}/>
          {PRESETS.map(p => (
            <button key={p.label} onClick={() => setUnresolvedOnly(p.unresolvedOnly)}
              style={{ padding: '6px 12px', fontSize: '12px',
                       background: unresolvedOnly === p.unresolvedOnly ? 'var(--accent)' : 'var(--bg-card)',
                       color: unresolvedOnly === p.unresolvedOnly ? '#fff' : 'var(--text-default)',
                       border: '1px solid var(--border-color)', borderRadius: '4px', cursor: 'pointer',
                       fontWeight: 600 }}>
              {p.label}
            </button>
          ))}
          <label style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '11px', color: 'var(--text-muted)' }}>
            <input type="checkbox" checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} />
            30s 자동
          </label>
          <button onClick={load} title="새로고침"
            style={{ padding: '6px 10px', fontSize: '12px', background: 'var(--bg-card)',
                     border: '1px solid var(--border-color)', borderRadius: '4px', cursor: 'pointer' }}>
            <FaSync /> 새로고침
          </button>
          <button onClick={purge} title="30일 이전 해결 항목 삭제"
            style={{ padding: '6px 10px', fontSize: '12px', background: 'var(--bg-card)',
                     border: '1px solid var(--border-color)', borderRadius: '4px', cursor: 'pointer',
                     color: 'var(--text-muted)' }}>
            <FaTrashAlt /> 정리 (30일+)
          </button>
        </div>
      </div>

      {loading && <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>로딩 중...</div>}

      {!loading && visible.length === 0 && (
        <div style={{
          padding: '40px', textAlign: 'center', color: 'var(--text-muted)',
          background: 'var(--bg-card)', border: '1px dashed var(--border-color)',
          borderRadius: '8px',
        }}>
          {unresolvedOnly ? '🎉 미해결 오류가 없습니다.' : '오류 로그가 비어있습니다.'}
        </div>
      )}

      {!loading && visible.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {visible.map(it => (
            <div key={it.id}
              onClick={() => openDetail(it.id)}
              style={{
                padding: '10px 14px', borderRadius: '8px', cursor: 'pointer',
                background: 'var(--bg-card)',
                border: `1px solid ${it.resolved ? 'var(--border-color)' : 'rgba(239,68,68,0.3)'}`,
                borderLeft: `4px solid ${it.resolved ? '#94a3b8' : '#ef4444'}`,
                transition: 'transform 0.1s',
                display: 'grid',
                gridTemplateColumns: 'auto 1fr auto',
                gap: '12px', alignItems: 'center',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateX(2px)' }}
              onMouseLeave={(e) => { e.currentTarget.style.transform = 'translateX(0)' }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '2px', minWidth: '60px' }}>
                <span style={{
                  fontSize: '14px', fontWeight: 700,
                  color: it.resolved ? 'var(--text-muted)' : '#ef4444',
                }}>{it.occurrenceCount}</span>
                <span style={{ fontSize: '9px', color: 'var(--text-muted)' }}>회 발생</span>
              </div>

              <div style={{ minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '3px' }}>
                  {it.resolved
                    ? <FaCheckCircle style={{ color: 'var(--green)', fontSize: '11px' }} />
                    : <FaExclamationTriangle style={{ color: '#ef4444', fontSize: '11px' }} />}
                  <code style={{ fontSize: '12px', fontWeight: 700, color: it.resolved ? 'var(--text-muted)' : 'var(--text-default)' }}>
                    {it.exceptionClass}
                  </code>
                  {it.requestMethod && it.requestPath && (
                    <span style={{ fontSize: '10px', padding: '1px 5px',
                                   background: 'rgba(59,130,246,0.1)', color: '#3b82f6',
                                   borderRadius: '3px', fontFamily: 'monospace' }}>
                      {it.requestMethod} {it.requestPath}
                    </span>
                  )}
                </div>
                <div style={{
                  fontSize: '12px', color: 'var(--text-sub)',
                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                  maxWidth: '700px',
                }}>{it.message}</div>
                <div style={{ display: 'flex', gap: '10px', marginTop: '4px', fontSize: '10px', color: 'var(--text-muted)' }}>
                  <span><FaCalendar style={{ marginRight: 3, fontSize: '9px' }} />{formatRelative(it.lastOccurredAt)}</span>
                  {it.username && <span><FaUser style={{ marginRight: 3, fontSize: '9px' }} />{it.username}</span>}
                </div>
              </div>

              <div style={{ display: 'flex', gap: '4px' }}>
                {it.resolved ? (
                  <button onClick={(e) => unresolveItem(it.id, e)}
                    title="다시 미해결로 표시"
                    style={{ padding: '4px 8px', fontSize: '11px', background: 'var(--bg-default)',
                             border: '1px solid var(--border-color)', borderRadius: '4px',
                             cursor: 'pointer', color: 'var(--text-muted)' }}>
                    <FaUndo /> 복귀
                  </button>
                ) : (
                  <button onClick={(e) => resolveItem(it.id, e)}
                    title="해결됨으로 표시"
                    style={{ padding: '4px 8px', fontSize: '11px', background: '#10b981',
                             border: 'none', borderRadius: '4px', cursor: 'pointer', color: '#fff',
                             fontWeight: 600 }}>
                    <FaCheck /> 해결
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {detailItem && (
        <DetailModal
          item={detailItem}
          onClose={() => setDetailItem(null)}
          onResolve={() => resolveItem(detailItem.id)}
          onUnresolve={() => unresolveItem(detailItem.id)}
        />
      )}
    </>
  )
}

function DetailModal({ item, onClose, onResolve, onUnresolve }: {
  item: ErrorDetail
  onClose: () => void
  onResolve: () => void
  onUnresolve: () => void
}) {
  return (
    <div onClick={onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 9999, padding: '20px',
      }}>
      <div onClick={(e) => e.stopPropagation()}
        style={{
          background: 'var(--bg-primary)', border: '1px solid var(--border-color)',
          borderRadius: '12px', width: '900px', maxWidth: '95vw', maxHeight: '90vh',
          display: 'flex', flexDirection: 'column',
        }}>
        <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--border-color)',
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
              {item.resolved
                ? <FaCheckCircle style={{ color: 'var(--green)' }} />
                : <FaExclamationTriangle style={{ color: '#ef4444' }} />}
              <code>{item.exceptionClass}</code>
              <span style={{
                fontSize: '11px', padding: '2px 8px', borderRadius: '4px',
                background: item.resolved ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
                color: item.resolved ? '#10b981' : '#ef4444', fontWeight: 700,
              }}>{item.resolved ? 'RESOLVED' : `${item.occurrenceCount}회 발생`}</span>
            </h3>
          </div>
          <button onClick={onClose}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', fontSize: '16px' }}>
            <FaTimes />
          </button>
        </div>

        <div style={{ padding: '14px 18px', flex: 1, overflowY: 'auto' }}>
          {/* 메시지 */}
          <Section title="📝 메시지">
            <div style={{ padding: '10px', background: 'var(--bg-card)', borderRadius: '6px',
                          fontSize: '13px', color: 'var(--text-default)', wordBreak: 'break-word' }}>
              {item.message}
            </div>
          </Section>

          {/* 메타정보 */}
          <Section title="ℹ️ 메타">
            <table style={{ width: '100%', fontSize: '12px', borderCollapse: 'collapse' }}>
              <tbody>
                <Meta k="처음 발생" v={formatDate(item.createdAt)} />
                <Meta k="마지막 발생" v={formatDate(item.lastOccurredAt) + ` (${formatRelative(item.lastOccurredAt)})`} />
                <Meta k="요청 경로" v={item.requestMethod && item.requestPath ? `${item.requestMethod} ${item.requestPath}` : '-'} />
                <Meta k="사용자" v={item.username || '익명'} />
                <Meta k="클라이언트 IP" v={item.clientIp || '-'} />
                <Meta k="User-Agent" v={item.userAgent || '-'} />
                {item.resolved && <Meta k="해결자" v={`${item.resolvedBy || '?'} (${formatDate(item.resolvedAt)})`} />}
                <Meta k="dedupeKey" v={<code style={{ fontSize: '10px' }}>{item.dedupeKey}</code>} />
              </tbody>
            </table>
          </Section>

          {/* 스택트레이스 */}
          <Section title={<><FaCode /> 스택트레이스</>}>
            <pre style={{
              padding: '12px', background: 'var(--bg-default)', borderRadius: '6px',
              fontSize: '11px', maxHeight: '400px', overflow: 'auto',
              fontFamily: 'monospace', color: 'var(--text-default)', margin: 0,
              whiteSpace: 'pre', lineHeight: 1.5,
            }}>{item.stackTrace || '(스택트레이스 없음)'}</pre>
          </Section>
        </div>

        <div style={{ padding: '12px 18px', borderTop: '1px solid var(--border-color)',
                      display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
          <button onClick={onClose}
            style={{ padding: '6px 14px', fontSize: '13px', background: 'var(--bg-card)',
                     border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer' }}>
            닫기
          </button>
          {item.resolved ? (
            <button onClick={onUnresolve}
              style={{ padding: '6px 14px', fontSize: '13px', background: 'var(--bg-card)',
                       border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer',
                       color: 'var(--text-muted)' }}>
              <FaUndo /> 미해결로 복귀
            </button>
          ) : (
            <button onClick={onResolve}
              style={{ padding: '6px 14px', fontSize: '13px', background: '#10b981',
                       border: 'none', borderRadius: '6px', cursor: 'pointer', color: '#fff',
                       fontWeight: 600 }}>
              <FaCheck /> 해결로 표시
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

function Section({ title, children }: { title: React.ReactNode; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: '14px' }}>
      <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)',
                    marginBottom: '6px', display: 'flex', alignItems: 'center', gap: '4px' }}>
        {title}
      </div>
      {children}
    </div>
  )
}

function Meta({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <tr style={{ borderBottom: '1px solid var(--border-color)' }}>
      <td style={{ padding: '6px 8px', color: 'var(--text-muted)', width: '120px', verticalAlign: 'top' }}>{k}</td>
      <td style={{ padding: '6px 8px', color: 'var(--text-default)' }}>{v}</td>
    </tr>
  )
}
