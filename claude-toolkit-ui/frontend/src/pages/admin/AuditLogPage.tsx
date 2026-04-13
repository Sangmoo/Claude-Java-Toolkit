import { useEffect, useState } from 'react'
import { FaShieldAlt, FaSearch } from 'react-icons/fa'
import { useApi } from '../../hooks/useApi'

interface AuditEntry { id: number; endpoint: string; method: string; username: string; clientIp: string; statusCode: number; durationMs: number; createdAt: string }

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditEntry[]>([])
  const [filter, setFilter] = useState('')
  const api = useApi()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/admin/audit-dashboard?format=json') as AuditEntry[] | null
      if (data) setLogs(data)
    }
    load()
  }, [])

  const filtered = logs.filter((l) => {
    if (!filter) return true
    const q = filter.toLowerCase()
    return l.endpoint.toLowerCase().includes(q) || l.username?.toLowerCase().includes(q)
  })

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaShieldAlt style={{ color: '#f59e0b' }} /> 감사 로그
        </h2>
        <div style={{ position: 'relative' }}>
          <FaSearch style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)', fontSize: '13px' }} />
          <input style={{ paddingLeft: '30px', width: '220px', fontSize: '13px' }} placeholder="검색..." value={filter} onChange={(e) => setFilter(e.target.value)} />
        </div>
      </div>

      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
          <thead>
            <tr style={{ background: 'var(--bg-tertiary)' }}>
              <th style={thStyle}>시간</th><th style={thStyle}>사용자</th><th style={thStyle}>메서드</th><th style={thStyle}>엔드포인트</th><th style={thStyle}>상태</th><th style={thStyle}>IP</th><th style={thStyle}>소요(ms)</th>
            </tr>
          </thead>
          <tbody>
            {filtered.slice(0, 100).map((l) => (
              <tr key={l.id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                <td style={tdStyle}>{l.createdAt}</td>
                <td style={tdStyle}>{l.username || '-'}</td>
                <td style={tdStyle}><span style={{ padding: '1px 6px', borderRadius: '3px', background: l.method === 'GET' ? 'rgba(59,130,246,0.12)' : 'rgba(249,115,22,0.12)', color: l.method === 'GET' ? 'var(--blue)' : 'var(--accent)', fontSize: '11px' }}>{l.method}</span></td>
                <td style={{ ...tdStyle, maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{l.endpoint}</td>
                <td style={tdStyle}><span style={{ color: l.statusCode < 400 ? 'var(--green)' : 'var(--red)' }}>{l.statusCode}</span></td>
                <td style={tdStyle}>{l.clientIp}</td>
                <td style={tdStyle}>{l.durationMs}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

const thStyle: React.CSSProperties = { textAlign: 'left', padding: '8px 10px', fontWeight: 600, color: 'var(--text-muted)', whiteSpace: 'nowrap' }
const tdStyle: React.CSSProperties = { padding: '8px 10px', whiteSpace: 'nowrap' }
