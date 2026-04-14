import { useEffect, useState, useCallback } from 'react'
import { FaShieldAlt, FaSearch, FaDownload, FaChevronLeft, FaChevronRight } from 'react-icons/fa'

interface AuditEntry {
  id: number
  endpoint: string
  method: string
  username: string
  ip: string
  statusCode: number
  durationMs: number
  formattedDate: string
  actionType: string
  menuName: string
}

interface PageResp {
  content: AuditEntry[]
  page: number
  size: number
  totalPages: number
  totalElements: number
}

const PAGE_SIZE = 20

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditEntry[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [filter, setFilter] = useState('')
  const [period, setPeriod] = useState('')
  const [loading, setLoading] = useState(false)

  const load = useCallback(async (p: number) => {
    setLoading(true)
    try {
      const params = new URLSearchParams({ page: String(p), size: String(PAGE_SIZE) })
      if (filter)  params.set('user', filter)
      if (period)  params.set('period', period)
      const res = await fetch(`/security/audit-log?${params}`, { credentials: 'include' })
      if (res.ok) {
        const d: PageResp = await res.json()
        setLogs(d.content || [])
        setTotalPages(d.totalPages || 0)
        setTotalElements(d.totalElements || 0)
        setPage(d.page || 0)
      }
    } catch { /* ignore */ }
    setLoading(false)
  }, [filter, period])

  useEffect(() => { load(0) }, [load])

  const exportCsv = () => {
    // 백엔드가 BOM 포함 CSV 를 반환 → Excel 에서 한글 정상 표시
    window.location.href = '/security/audit-log/export'
  }

  // 페이지 번호 윈도우 — 현재 페이지 ±2
  const pageWindow: number[] = []
  if (totalPages > 0) {
    const start = Math.max(0, page - 2)
    const end   = Math.min(totalPages - 1, page + 2)
    for (let i = start; i <= end; i++) pageWindow.push(i)
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaShieldAlt style={{ color: '#f59e0b' }} /> 감사 로그
          <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400 }}>
            (총 {totalElements.toLocaleString()}건)
          </span>
        </h2>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
          <select value={period} onChange={(e) => setPeriod(e.target.value)} style={{ fontSize: '12px', padding: '6px 8px', borderRadius: '6px' }}>
            <option value="">전체 기간</option>
            <option value="1h">최근 1시간</option>
            <option value="today">오늘</option>
            <option value="7d">최근 7일</option>
            <option value="30d">최근 30일</option>
          </select>
          <div style={{ position: 'relative' }}>
            <FaSearch style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)', fontSize: '13px' }} />
            <input
              style={{ paddingLeft: '30px', width: '180px', fontSize: '13px', padding: '6px 8px 6px 30px', borderRadius: '6px' }}
              placeholder="사용자 검색..."
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
          <button onClick={exportCsv} style={{
            display: 'flex', alignItems: 'center', gap: '6px',
            padding: '7px 14px', borderRadius: '6px',
            background: 'var(--green)', color: '#fff', border: 'none',
            fontSize: '12px', fontWeight: 600, cursor: 'pointer',
          }}>
            <FaDownload /> 엑셀(CSV) 내려받기
          </button>
        </div>
      </div>

      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
          <thead>
            <tr style={{ background: 'var(--bg-tertiary)' }}>
              <th style={thStyle}>시간</th>
              <th style={thStyle}>사용자</th>
              <th style={thStyle}>메뉴</th>
              <th style={thStyle}>액션</th>
              <th style={thStyle}>메서드</th>
              <th style={thStyle}>엔드포인트</th>
              <th style={thStyle}>상태</th>
              <th style={thStyle}>IP</th>
              <th style={thStyle}>소요(ms)</th>
            </tr>
          </thead>
          <tbody>
            {loading && <tr><td colSpan={9} style={{ ...tdStyle, textAlign: 'center', color: 'var(--text-muted)', padding: '24px' }}>로딩 중...</td></tr>}
            {!loading && logs.length === 0 && <tr><td colSpan={9} style={{ ...tdStyle, textAlign: 'center', color: 'var(--text-muted)', padding: '24px' }}>표시할 로그가 없습니다.</td></tr>}
            {!loading && logs.map((l) => (
              <tr key={l.id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                <td style={tdStyle}>{l.formattedDate}</td>
                <td style={tdStyle}>{l.username || '-'}</td>
                <td style={tdStyle}>{l.menuName || '-'}</td>
                <td style={tdStyle}>{l.actionType || '-'}</td>
                <td style={tdStyle}>
                  <span style={{
                    padding: '1px 6px', borderRadius: '3px', fontSize: '11px',
                    background: l.method === 'GET' ? 'rgba(59,130,246,0.12)' : 'rgba(249,115,22,0.12)',
                    color: l.method === 'GET' ? 'var(--blue)' : 'var(--accent)',
                  }}>{l.method}</span>
                </td>
                <td style={{ ...tdStyle, maxWidth: '260px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{l.endpoint}</td>
                <td style={tdStyle}><span style={{ color: l.statusCode < 400 ? 'var(--green)' : 'var(--red)' }}>{l.statusCode}</span></td>
                <td style={tdStyle}>{l.ip || '-'}</td>
                <td style={tdStyle}>{l.durationMs ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '6px', marginTop: '16px', flexWrap: 'wrap' }}>
          <button onClick={() => load(0)} disabled={page === 0} style={pageBtnSt(false)}>« 처음</button>
          <button onClick={() => load(page - 1)} disabled={page === 0} style={pageBtnSt(false)}><FaChevronLeft /></button>
          {pageWindow[0] > 0 && <span style={{ color: 'var(--text-muted)' }}>...</span>}
          {pageWindow.map((n) => (
            <button key={n} onClick={() => load(n)} style={pageBtnSt(n === page)}>{n + 1}</button>
          ))}
          {pageWindow[pageWindow.length - 1] < totalPages - 1 && <span style={{ color: 'var(--text-muted)' }}>...</span>}
          <button onClick={() => load(page + 1)} disabled={page >= totalPages - 1} style={pageBtnSt(false)}><FaChevronRight /></button>
          <button onClick={() => load(totalPages - 1)} disabled={page >= totalPages - 1} style={pageBtnSt(false)}>마지막 »</button>
          <span style={{ marginLeft: '12px', fontSize: '12px', color: 'var(--text-muted)' }}>
            {page + 1} / {totalPages}
          </span>
        </div>
      )}
    </>
  )
}

const thStyle: React.CSSProperties = { textAlign: 'left', padding: '8px 10px', fontWeight: 600, color: 'var(--text-muted)', whiteSpace: 'nowrap' }
const tdStyle: React.CSSProperties = { padding: '8px 10px', whiteSpace: 'nowrap' }
const pageBtnSt = (active: boolean): React.CSSProperties => ({
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  minWidth: '32px', height: '32px', padding: '0 8px',
  borderRadius: '6px', fontSize: '12px', fontWeight: active ? 700 : 400,
  border: `1px solid ${active ? 'var(--accent)' : 'var(--border-color)'}`,
  background: active ? 'var(--accent-subtle)' : 'transparent',
  color: active ? 'var(--accent)' : 'var(--text-sub)',
  cursor: 'pointer',
})
