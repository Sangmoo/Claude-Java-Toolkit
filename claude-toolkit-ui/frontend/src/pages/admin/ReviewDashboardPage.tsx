import { useEffect, useState, useCallback } from 'react'
import {
  FaChartPie, FaCheckCircle, FaTimesCircle, FaClock, FaCalendarAlt,
  FaSpinner, FaSync,
} from 'react-icons/fa'
import {
  PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend,
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
  LineChart, Line,
} from 'recharts'

interface DailyTrendPoint {
  date: string
  pending: number
  accepted: number
  rejected: number
  total: number
}

interface TypeRow {
  type: string
  pending: number
  accepted: number
  rejected: number
  total: number
}

interface ReviewerRow {
  username: string
  accepted: number
  rejected: number
  total: number
}

interface DashboardData {
  totalCount: number
  pendingCount: number
  acceptedCount: number
  rejectedCount: number
  pendingPercent: number
  acceptedPercent: number
  rejectedPercent: number
  dailyTrend: DailyTrendPoint[]
  byType: TypeRow[]
  byReviewer: ReviewerRow[]
  from: string
  to: string
  days: number
}

type Preset = 1 | 3 | 7 | 30 | 'custom'

const COLOR_PENDING  = '#f59e0b'
const COLOR_ACCEPTED = '#10b981'
const COLOR_REJECTED = '#ef4444'

export default function ReviewDashboardPage() {
  const [preset, setPreset] = useState<Preset>(7)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [data, setData] = useState<DashboardData | null>(null)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (preset === 'custom') {
        if (from) params.set('from', from)
        if (to)   params.set('to', to)
      } else {
        params.set('days', String(preset))
      }
      const res = await fetch(`/api/v1/admin/review-dashboard?${params}`, { credentials: 'include' })
      const json = await res.json()
      setData((json.data ?? json) as DashboardData)
    } catch {
      setData(null)
    }
    setLoading(false)
  }, [preset, from, to])

  useEffect(() => { load() }, [load])

  const pieData = data ? [
    { name: '검토 대기', value: data.pendingCount,  color: COLOR_PENDING },
    { name: '승인됨',    value: data.acceptedCount, color: COLOR_ACCEPTED },
    { name: '거절됨',    value: data.rejectedCount, color: COLOR_REJECTED },
  ].filter((d) => d.value > 0) : []

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaChartPie style={{ color: '#8b5cf6' }} /> 리뷰 이력 대시보드
          <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400 }}>
            ({data ? `${data.from} ~ ${data.to} (${data.days}일)` : '로딩 중...'})
          </span>
        </h2>
        <button onClick={load} disabled={loading} style={refreshBtn}>
          {loading ? <FaSpinner className="spin" /> : <FaSync />} 새로고침
        </button>
      </div>

      {/* 기간 필터 */}
      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '14px 18px', marginBottom: '16px', display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
        <FaCalendarAlt style={{ color: 'var(--text-muted)' }} />
        <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>기간:</span>
        <div style={{ display: 'flex', gap: '4px' }}>
          {([1, 3, 7, 30] as const).map((d) => (
            <button key={d} onClick={() => setPreset(d)} style={chipBtn(preset === d)}>
              {d}일
            </button>
          ))}
          <button onClick={() => setPreset('custom')} style={chipBtn(preset === 'custom')}>사용자 지정</button>
        </div>
        {preset === 'custom' && (
          <>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)', marginLeft: '8px' }}>From</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} style={dateInput} />
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>To</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} style={dateInput} />
            <button onClick={load} style={{ ...refreshBtn, padding: '5px 12px' }}>적용</button>
          </>
        )}
      </div>

      {!data && !loading && (
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>데이터를 불러올 수 없습니다.</div>
      )}

      {data && (
        <>
          {/* 핵심 지표 카드 */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '14px', marginBottom: '20px' }}>
            <StatCard
              icon={<FaChartPie />}
              iconColor="#8b5cf6"
              label="총 리뷰 이력"
              value={data.totalCount.toLocaleString()}
              subtitle={`${data.days}일간`}
            />
            <StatCard
              icon={<FaClock />}
              iconColor={COLOR_PENDING}
              label="검토 대기"
              value={`${data.pendingCount.toLocaleString()}건`}
              subtitle={`${data.pendingPercent.toFixed(1)}%`}
              accent={COLOR_PENDING}
            />
            <StatCard
              icon={<FaCheckCircle />}
              iconColor={COLOR_ACCEPTED}
              label="승인됨"
              value={`${data.acceptedCount.toLocaleString()}건`}
              subtitle={`${data.acceptedPercent.toFixed(1)}%`}
              accent={COLOR_ACCEPTED}
            />
            <StatCard
              icon={<FaTimesCircle />}
              iconColor={COLOR_REJECTED}
              label="거절됨"
              value={`${data.rejectedCount.toLocaleString()}건`}
              subtitle={`${data.rejectedPercent.toFixed(1)}%`}
              accent={COLOR_REJECTED}
            />
          </div>

          {/* 차트 영역 */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 380px), 1fr))', gap: '16px', marginBottom: '20px' }}>
            <ChartPanel title="상태별 비율">
              {pieData.length === 0 ? (
                <EmptyChart msg="이 기간엔 데이터가 없습니다." />
              ) : (
                <ResponsiveContainer width="100%" height={280}>
                  <PieChart>
                    <Pie
                      data={pieData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%" cy="50%"
                      innerRadius={50}
                      outerRadius={95}
                      label={(entry) => `${entry.name} ${entry.value}`}
                    >
                      {pieData.map((entry) => <Cell key={entry.name} fill={entry.color} />)}
                    </Pie>
                    <Tooltip
                      contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px', fontSize: '12px' }}
                      formatter={(v: number, n: string) => [`${v}건`, n]}
                    />
                    <Legend wrapperStyle={{ fontSize: '12px' }} />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </ChartPanel>

            <ChartPanel title="일별 추이">
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={data.dailyTrend}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                  <XAxis dataKey="date" stroke="var(--text-muted)" fontSize={10} tickFormatter={(d) => d.slice(5)} />
                  <YAxis stroke="var(--text-muted)" fontSize={11} allowDecimals={false} />
                  <Tooltip
                    contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px', fontSize: '12px' }}
                    cursor={{ fill: 'rgba(148,163,184,0.1)' }}
                  />
                  <Legend wrapperStyle={{ fontSize: '12px' }} />
                  <Bar dataKey="accepted" stackId="s" fill={COLOR_ACCEPTED} name="승인" />
                  <Bar dataKey="rejected" stackId="s" fill={COLOR_REJECTED} name="거절" />
                  <Bar dataKey="pending"  stackId="s" fill={COLOR_PENDING}  name="대기" />
                </BarChart>
              </ResponsiveContainer>
            </ChartPanel>

            <ChartPanel title="일별 누적 라인">
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={data.dailyTrend}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                  <XAxis dataKey="date" stroke="var(--text-muted)" fontSize={10} tickFormatter={(d) => d.slice(5)} />
                  <YAxis stroke="var(--text-muted)" fontSize={11} allowDecimals={false} />
                  <Tooltip contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px', fontSize: '12px' }} />
                  <Legend wrapperStyle={{ fontSize: '12px' }} />
                  <Line type="monotone" dataKey="total"    stroke="#8b5cf6"      strokeWidth={2} name="총합" dot={false} />
                  <Line type="monotone" dataKey="accepted" stroke={COLOR_ACCEPTED} strokeWidth={2} name="승인" dot={false} />
                  <Line type="monotone" dataKey="rejected" stroke={COLOR_REJECTED} strokeWidth={2} name="거절" dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </ChartPanel>
          </div>

          {/* 분석 유형별 표 */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 480px), 1fr))', gap: '16px' }}>
            <ChartPanel title={`분석 유형별 (${data.byType.length}종)`}>
              {data.byType.length === 0 ? (
                <EmptyChart msg="데이터 없음" />
              ) : (
                <div style={{ maxHeight: '320px', overflowY: 'auto' }}>
                  <table style={tableStyle}>
                    <thead>
                      <tr>
                        <th style={thStyle}>유형</th>
                        <th style={{ ...thStyle, textAlign: 'right' }}>대기</th>
                        <th style={{ ...thStyle, textAlign: 'right' }}>승인</th>
                        <th style={{ ...thStyle, textAlign: 'right' }}>거절</th>
                        <th style={{ ...thStyle, textAlign: 'right' }}>총합</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.byType.map((r) => (
                        <tr key={r.type}>
                          <td style={tdStyle}>
                            <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>{r.type}</span>
                          </td>
                          <td style={{ ...tdStyle, textAlign: 'right', color: COLOR_PENDING }}>{r.pending}</td>
                          <td style={{ ...tdStyle, textAlign: 'right', color: COLOR_ACCEPTED }}>{r.accepted}</td>
                          <td style={{ ...tdStyle, textAlign: 'right', color: COLOR_REJECTED }}>{r.rejected}</td>
                          <td style={{ ...tdStyle, textAlign: 'right', fontWeight: 700 }}>{r.total}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </ChartPanel>

            <ChartPanel title={`리뷰어별 (${data.byReviewer.length}명)`}>
              {data.byReviewer.length === 0 ? (
                <EmptyChart msg="아직 검토한 리뷰어가 없습니다." />
              ) : (
                <div style={{ maxHeight: '320px', overflowY: 'auto' }}>
                  <table style={tableStyle}>
                    <thead>
                      <tr>
                        <th style={thStyle}>리뷰어</th>
                        <th style={{ ...thStyle, textAlign: 'right' }}>승인</th>
                        <th style={{ ...thStyle, textAlign: 'right' }}>거절</th>
                        <th style={{ ...thStyle, textAlign: 'right' }}>총합</th>
                        <th style={{ ...thStyle, textAlign: 'right' }}>승인율</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.byReviewer.map((r) => {
                        const rate = r.total > 0 ? (r.accepted * 100 / r.total) : 0
                        return (
                          <tr key={r.username}>
                            <td style={{ ...tdStyle, fontWeight: 600 }}>{r.username}</td>
                            <td style={{ ...tdStyle, textAlign: 'right', color: COLOR_ACCEPTED }}>{r.accepted}</td>
                            <td style={{ ...tdStyle, textAlign: 'right', color: COLOR_REJECTED }}>{r.rejected}</td>
                            <td style={{ ...tdStyle, textAlign: 'right', fontWeight: 700 }}>{r.total}</td>
                            <td style={{ ...tdStyle, textAlign: 'right', color: rate >= 50 ? COLOR_ACCEPTED : COLOR_REJECTED }}>
                              {rate.toFixed(1)}%
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </ChartPanel>
          </div>
        </>
      )}
    </>
  )
}

function StatCard({ icon, iconColor, label, value, subtitle, accent }: {
  icon: React.ReactNode
  iconColor: string
  label: string
  value: string
  subtitle: string
  accent?: string
}) {
  return (
    <div style={{
      background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
      borderRadius: '12px', padding: '18px',
      borderLeft: accent ? `3px solid ${accent}` : '1px solid var(--border-color)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
        <span style={{ color: iconColor, fontSize: '16px' }}>{icon}</span>
        <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>{label}</span>
      </div>
      <div style={{ fontSize: '24px', fontWeight: 700, marginBottom: '2px' }}>{value}</div>
      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{subtitle}</div>
    </div>
  )
}

function ChartPanel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '16px' }}>
      <h3 style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-muted)', marginBottom: '12px', textTransform: 'uppercase' }}>{title}</h3>
      {children}
    </div>
  )
}

function EmptyChart({ msg }: { msg: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '260px', color: 'var(--text-muted)', fontSize: '12px' }}>
      {msg}
    </div>
  )
}

const refreshBtn: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '6px',
  padding: '7px 14px', borderRadius: '6px',
  background: 'transparent', border: '1px solid var(--border-color)',
  color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px',
}
const chipBtn = (active: boolean): React.CSSProperties => ({
  padding: '5px 14px', borderRadius: '14px', fontSize: '12px', cursor: 'pointer',
  border: `1px solid ${active ? 'var(--accent)' : 'var(--border-color)'}`,
  background: active ? 'var(--accent-subtle)' : 'transparent',
  color: active ? 'var(--accent)' : 'var(--text-sub)',
  fontWeight: active ? 600 : 400,
})
const dateInput: React.CSSProperties = {
  padding: '5px 10px', fontSize: '12px',
  border: '1px solid var(--border-color)', borderRadius: '6px',
  background: 'var(--bg-primary)', color: 'var(--text-primary)',
}
const tableStyle: React.CSSProperties = { width: '100%', borderCollapse: 'collapse', fontSize: '12px' }
const thStyle: React.CSSProperties = { textAlign: 'left', padding: '6px 8px', fontWeight: 600, color: 'var(--text-muted)', borderBottom: '1px solid var(--border-color)', whiteSpace: 'nowrap' }
const tdStyle: React.CSSProperties = { padding: '6px 8px', borderBottom: '1px solid var(--border-color)', whiteSpace: 'nowrap' }
