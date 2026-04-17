import { useEffect, useState } from 'react'
import { FaChartLine, FaCalendarAlt } from 'react-icons/fa'
import {
  ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid,
  LineChart, Line,
} from 'recharts'

/**
 * v4.2.8 — B2: 엔드포인트 사용 통계 어드민 페이지.
 *
 * AuditLog 테이블을 집계해서:
 * - Top 엔드포인트 (호출 수)
 * - Top 사용자 (호출 수)
 * - 상태 코드 분포
 * - 일별 트렌드
 *
 * 기간 필터: 1일 / 7일 / 30일 / 90일
 */

interface EndpointRow { endpoint: string; count: number }
interface UserRow     { username: string; count: number }
interface StatusRow   { status:   number; count: number }
interface DailyRow    { date:     string; count: number }

interface StatsData {
  days:         number
  total:        number
  topEndpoints: EndpointRow[]
  topUsers:     UserRow[]
  statusCodes:  StatusRow[]
  dailyTrend:   DailyRow[]
}

const PRESET_DAYS = [
  { label: '1일',  value: 1 },
  { label: '7일',  value: 7 },
  { label: '30일', value: 30 },
  { label: '90일', value: 90 },
]

function statusColor(status: number): string {
  if (status >= 500) return '#ef4444'
  if (status >= 400) return '#f59e0b'
  if (status >= 300) return '#06b6d4'
  return '#10b981'
}

export default function AdminEndpointStatsPage() {
  const [days, setDays] = useState(7)
  const [data, setData] = useState<StatsData | null>(null)
  const [loading, setLoading] = useState(false)

  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    fetch(`/api/v1/admin/endpoint-stats?days=${days}`, { credentials: 'include' })
      .then((r) => r.ok ? r.json() : null)
      .then((j) => {
        // v4.3.x: 백엔드가 에러 시 {error: "..."} 만 반환하는 경우가 있어
        //         누락 필드를 모두 안전한 기본값으로 보강
        const raw = (j?.data ?? j ?? {}) as Partial<StatsData> & { error?: string }
        if (raw.error) {
          setError(raw.error)
          setData(null)
          return
        }
        const safe: StatsData = {
          days:         raw.days ?? days,
          total:        raw.total ?? 0,
          topEndpoints: Array.isArray(raw.topEndpoints) ? raw.topEndpoints : [],
          topUsers:     Array.isArray(raw.topUsers)     ? raw.topUsers     : [],
          statusCodes:  Array.isArray(raw.statusCodes)  ? raw.statusCodes  : [],
          dailyTrend:   Array.isArray(raw.dailyTrend)   ? raw.dailyTrend   : [],
        }
        setData(safe)
      })
      .catch((e) => {
        setError(String(e))
        setData(null)
      })
      .finally(() => setLoading(false))
  }, [days])

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px', margin: 0 }}>
          <FaChartLine style={{ color: '#3b82f6' }} /> 엔드포인트 사용 통계
        </h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <FaCalendarAlt style={{ color: 'var(--text-muted)', fontSize: '12px' }} />
          {PRESET_DAYS.map((p) => (
            <button
              key={p.value}
              onClick={() => setDays(p.value)}
              style={{
                padding: '6px 14px', borderRadius: '6px', fontSize: '12px',
                border: '1px solid var(--border-color)',
                background: days === p.value ? 'var(--accent)' : 'transparent',
                color: days === p.value ? '#fff' : 'var(--text-sub)',
                cursor: 'pointer', fontWeight: 600,
              }}>
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {loading && <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>로딩 중...</div>}

      {!loading && error && (
        <div style={{
          padding: '16px', borderRadius: '8px', marginBottom: '16px',
          background: 'rgba(239,68,68,0.1)', color: 'var(--red, #ef4444)',
          border: '1px solid var(--red, #ef4444)', fontSize: '13px',
        }}>
          ⚠️ 통계 데이터 조회 실패: {error}
          <div style={{ marginTop: '6px', fontSize: '11px', color: 'var(--text-muted)' }}>
            AuditLog 테이블이 비어있거나 DB 연결 문제일 수 있습니다. 시간이 지나도 같은 오류가 나면 관리자에게 문의하세요.
          </div>
        </div>
      )}

      {!loading && !error && data && (
        <>
          {/* 요약 */}
          <div style={summaryBar}>
            지난 <strong style={{ color: 'var(--accent)' }}>{data.days ?? days}일</strong> 동안 총{' '}
            <strong style={{ color: 'var(--accent)' }}>{(data.total ?? 0).toLocaleString()}</strong>건의 요청이 처리되었습니다.
          </div>

          {/* 일별 트렌드 */}
          <Section title="📈 일별 요청 수 추이">
            {data.dailyTrend.length === 0 ? <Empty/> : (
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={data.dailyTrend}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                  <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--text-muted)' }} />
                  <YAxis tick={{ fontSize: 11, fill: 'var(--text-muted)' }} />
                  <Tooltip contentStyle={tooltipStyle}/>
                  <Line type="monotone" dataKey="count" stroke="#3b82f6" strokeWidth={2} dot={{ r: 3 }} />
                </LineChart>
              </ResponsiveContainer>
            )}
          </Section>

          {/* Top 엔드포인트 */}
          <Section title="🎯 Top 엔드포인트 (호출 수)">
            {data.topEndpoints.length === 0 ? <Empty/> : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {data.topEndpoints.slice(0, 15).map((row, i) => {
                  const max = data.topEndpoints[0]?.count || 1
                  const pct = (row.count / max) * 100
                  return (
                    <div key={row.endpoint + i} style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '12px' }}>
                      <span style={{ width: '28px', color: 'var(--text-muted)', fontVariantNumeric: 'tabular-nums' }}>{i + 1}.</span>
                      <span style={{ flex: 1, fontFamily: 'monospace', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {row.endpoint}
                      </span>
                      <div style={{ width: '200px', height: '8px', background: 'var(--bg-primary)', borderRadius: '4px', overflow: 'hidden' }}>
                        <div style={{ width: `${pct}%`, height: '100%', background: 'var(--accent)' }} />
                      </div>
                      <strong style={{ width: '60px', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                        {(row.count ?? 0).toLocaleString()}
                      </strong>
                    </div>
                  )
                })}
              </div>
            )}
          </Section>

          {/* Top 사용자 */}
          <Section title="👥 Top 사용자 (활동량)">
            {data.topUsers.length === 0 ? <Empty/> : (
              <ResponsiveContainer width="100%" height={Math.max(160, data.topUsers.length * 28 + 40)}>
                <BarChart data={data.topUsers.slice(0, 10)} layout="vertical" margin={{ left: 50 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                  <XAxis type="number" tick={{ fontSize: 11, fill: 'var(--text-muted)' }} />
                  <YAxis type="category" dataKey="username" tick={{ fontSize: 11, fill: 'var(--text-muted)' }} width={80} />
                  <Tooltip contentStyle={tooltipStyle}/>
                  <Bar dataKey="count" fill="#8b5cf6" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </Section>

          {/* 상태 코드 분포 */}
          <Section title="🟢 상태 코드 분포">
            {data.statusCodes.length === 0 ? <Empty/> : (
              <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                {data.statusCodes.map((row) => (
                  <div key={row.status} style={{
                    padding: '10px 14px', borderRadius: '8px',
                    background: 'var(--bg-primary)',
                    border: `2px solid ${statusColor(row.status)}`,
                    minWidth: '100px',
                  }}>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginBottom: '2px' }}>
                      HTTP {row.status || '?'}
                    </div>
                    <div style={{ fontSize: '18px', fontWeight: 700, color: statusColor(row.status) }}>
                      {(row.count ?? 0).toLocaleString()}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Section>
        </>
      )}
    </>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{
      marginBottom: '20px',
      background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
      borderRadius: '12px', padding: '18px',
    }}>
      <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '14px' }}>{title}</h3>
      {children}
    </div>
  )
}

function Empty() {
  return <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '12px' }}>데이터가 없습니다.</div>
}

const summaryBar: React.CSSProperties = {
  padding: '12px 16px', marginBottom: '18px',
  background: 'var(--accent-subtle)', border: '1px solid var(--accent)',
  borderRadius: '10px', fontSize: '13px', color: 'var(--text-primary)',
}

const tooltipStyle: React.CSSProperties = {
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '6px', fontSize: '12px',
}
