import { useEffect, useState } from 'react'
import { FaChartLine, FaCalendarAlt, FaTimes } from 'react-icons/fa'
import {
  PieChart, Pie, Cell, ResponsiveContainer, Tooltip, BarChart, Bar, XAxis, YAxis, CartesianGrid,
} from 'recharts'
import { formatRelative } from '../../utils/date'

/**
 * v4.2.8 — 품질 대시보드 (#8 개선 제안 반영).
 *
 * 개선 사항:
 *  - 날짜 범위 필터 (from / to + 프리셋 버튼)
 *  - 심각도/카테고리 차트 개별 클릭 → 해당 필터에 매칭되는 이력 목록이 하단에 표시
 *  - 복수 필터 조합 가능 (severity + category 동시)
 *  - 클릭한 필터는 X 로 해제
 */

interface CategoryRow { category: string; count: number }
interface HistoryRow {
  id:         number
  type:       string
  title:      string
  username:   string
  createdAt:  string
  severities: string[]
  categories: string[]
}
interface DashboardData {
  from:       string
  to:         string
  totalCount: number
  severity:   CategoryRow[]
  categories: CategoryRow[]
  histories:  HistoryRow[]
}

const SEVERITY_COLORS: Record<string, string> = {
  HIGH:   '#ef4444',
  MEDIUM: '#f59e0b',
  LOW:    '#22c55e',
}

const CATEGORY_COLORS = ['#8b5cf6', '#3b82f6', '#10b981', '#06b6d4', '#94a3b8']

// 날짜를 YYYY-MM-DD 로 포맷
function fmtDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export default function HarnessDashboardPage() {
  // 기본 기간: 최근 30일
  const today   = new Date()
  const monthAgo = new Date(Date.now() - 30 * 86400 * 1000)
  const [from, setFrom] = useState<string>(fmtDate(monthAgo))
  const [to,   setTo]   = useState<string>(fmtDate(today))
  const [data, setData] = useState<DashboardData | null>(null)
  const [loading, setLoading] = useState(false)

  // 드릴다운 필터
  const [selectedSeverity, setSelectedSeverity] = useState<string | null>(null)
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null)

  const load = async (fromDate: string, toDate: string) => {
    setLoading(true)
    try {
      const res = await fetch(`/api/v1/harness/dashboard?from=${fromDate}&to=${toDate}`, { credentials: 'include' })
      const j = await res.json()
      setData((j?.data ?? j) as DashboardData)
    } catch {
      setData(null)
    }
    setLoading(false)
  }

  useEffect(() => { load(from, to) }, [from, to])

  const applyPreset = (days: number) => {
    const end = new Date()
    const start = new Date(Date.now() - days * 86400 * 1000)
    setFrom(fmtDate(start))
    setTo(fmtDate(end))
  }

  // 필터된 이력
  const drillDownHistories = (data?.histories || []).filter((h) => {
    if (selectedSeverity && !h.severities.includes(selectedSeverity)) return false
    if (selectedCategory && !h.categories.includes(selectedCategory)) return false
    return true
  })

  const hasFilter = selectedSeverity != null || selectedCategory != null

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px', margin: 0 }}>
          <FaChartLine style={{ color: '#8b5cf6' }} /> 품질 대시보드
        </h2>
      </div>

      {/* 필터 바 */}
      <div style={filterBar}>
        <FaCalendarAlt style={{ color: 'var(--text-muted)', fontSize: '13px' }} />
        <span style={{ fontSize: '12px', color: 'var(--text-sub)' }}>기간:</span>
        <input
          type="date"
          value={from}
          max={to}
          onChange={(e) => setFrom(e.target.value)}
          style={dateInput}
        />
        <span style={{ color: 'var(--text-muted)' }}>~</span>
        <input
          type="date"
          value={to}
          min={from}
          max={fmtDate(today)}
          onChange={(e) => setTo(e.target.value)}
          style={dateInput}
        />
        <div style={{ display: 'flex', gap: '4px', marginLeft: '8px' }}>
          {[7, 30, 90].map((d) => (
            <button
              key={d}
              onClick={() => applyPreset(d)}
              style={presetBtn}>
              {d}일
            </button>
          ))}
        </div>
      </div>

      {loading && <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>로딩 중...</div>}

      {!loading && data && (
        <>
          {/* 요약 */}
          <div style={summaryBar}>
            <strong style={{ color: 'var(--accent)' }}>{data.from} ~ {data.to}</strong> 기간에{' '}
            <strong style={{ color: 'var(--accent)' }}>{data.totalCount}건</strong>의 리뷰 이력이 집계되었습니다.
          </div>

          {/* 차트 2-col */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '16px', marginBottom: '20px' }}>
            {/* 심각도 파이 */}
            <div style={cardStyle}>
              <h3 style={cardTitle}>심각도 분포 <span style={hint}>(클릭으로 필터)</span></h3>
              {data.severity.every((s) => s.count === 0) ? <Empty/> : (
                <ResponsiveContainer width="100%" height={250}>
                  <PieChart>
                    <Pie
                      data={data.severity}
                      dataKey="count"
                      nameKey="category"
                      cx="50%" cy="50%"
                      outerRadius={80}
                      onClick={(e) => {
                        const clicked = e?.category as string | undefined
                        if (clicked) setSelectedSeverity(selectedSeverity === clicked ? null : clicked)
                      }}
                      label={({ category, percent }: { category?: string; percent?: number }) =>
                        percent && percent > 0 ? `${category} ${(percent * 100).toFixed(0)}%` : ''}
                    >
                      {data.severity.map((row, i) => (
                        <Cell
                          key={i}
                          fill={SEVERITY_COLORS[row.category] || '#94a3b8'}
                          opacity={selectedSeverity == null || selectedSeverity === row.category ? 1 : 0.3}
                          style={{ cursor: 'pointer' }}
                        />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={tooltipStyle}/>
                  </PieChart>
                </ResponsiveContainer>
              )}
            </div>

            {/* 카테고리 바 */}
            <div style={cardStyle}>
              <h3 style={cardTitle}>카테고리별 이슈 <span style={hint}>(클릭으로 필터)</span></h3>
              {data.categories.every((c) => c.count === 0) ? <Empty/> : (
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={data.categories}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                    <XAxis dataKey="category" stroke="var(--text-muted)" fontSize={12} />
                    <YAxis stroke="var(--text-muted)" fontSize={12} />
                    <Tooltip contentStyle={tooltipStyle}/>
                    <Bar
                      dataKey="count"
                      radius={[4, 4, 0, 0]}
                      onClick={(e) => {
                        const clicked = e?.category as string | undefined
                        if (clicked) setSelectedCategory(selectedCategory === clicked ? null : clicked)
                      }}>
                      {data.categories.map((row, i) => (
                        <Cell
                          key={i}
                          fill={CATEGORY_COLORS[i % CATEGORY_COLORS.length]}
                          opacity={selectedCategory == null || selectedCategory === row.category ? 1 : 0.3}
                          style={{ cursor: 'pointer' }}
                        />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          {/* 드릴다운 필터 + 이력 리스트 */}
          <div style={cardStyle}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px', flexWrap: 'wrap', marginBottom: '12px' }}>
              <h3 style={cardTitle}>
                {hasFilter
                  ? `드릴다운 결과 — ${drillDownHistories.length}건`
                  : `전체 이력 — ${data.histories.length}건`}
              </h3>
              {hasFilter && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                  {selectedSeverity && (
                    <FilterChip
                      label={`심각도: ${selectedSeverity}`}
                      color={SEVERITY_COLORS[selectedSeverity] || '#94a3b8'}
                      onClear={() => setSelectedSeverity(null)}
                    />
                  )}
                  {selectedCategory && (
                    <FilterChip
                      label={`카테고리: ${selectedCategory}`}
                      color="#8b5cf6"
                      onClear={() => setSelectedCategory(null)}
                    />
                  )}
                  <button
                    onClick={() => { setSelectedSeverity(null); setSelectedCategory(null) }}
                    style={clearAllBtn}>
                    전체 해제
                  </button>
                </div>
              )}
            </div>
            {drillDownHistories.length === 0 ? (
              <div style={{ padding: '30px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                {hasFilter ? '선택한 필터에 해당하는 이력이 없습니다.' : '기간 내 이력이 없습니다.'}
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                {drillDownHistories.slice(0, 50).map((h) => (
                  <div key={h.id} style={historyRow}>
                    <span style={{ fontSize: '10px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)', flexShrink: 0 }}>
                      {h.type}
                    </span>
                    <span style={{ flex: 1, fontSize: '13px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {h.title || '(제목 없음)'}
                    </span>
                    {h.severities.map((sev) => (
                      <span key={sev} style={{
                        fontSize: '9px', padding: '1px 6px', borderRadius: '3px',
                        color: SEVERITY_COLORS[sev] || '#94a3b8',
                        border: `1px solid ${SEVERITY_COLORS[sev] || '#94a3b8'}`,
                      }}>{sev}</span>
                    ))}
                    {h.categories.map((cat) => (
                      <span key={cat} style={{
                        fontSize: '9px', padding: '1px 6px', borderRadius: '3px',
                        color: 'var(--text-muted)', border: '1px solid var(--border-color)',
                      }}>{cat}</span>
                    ))}
                    <span style={{ fontSize: '11px', color: 'var(--text-muted)', flexShrink: 0 }}>
                      @{h.username || '?'} · {formatRelative(h.createdAt)}
                    </span>
                  </div>
                ))}
                {drillDownHistories.length > 50 && (
                  <div style={{ padding: '10px', textAlign: 'center', fontSize: '11px', color: 'var(--text-muted)' }}>
                    + 최대 50건까지만 표시합니다. 더 좁은 필터를 적용하세요.
                  </div>
                )}
              </div>
            )}
          </div>
        </>
      )}
    </>
  )
}

function FilterChip({ label, color, onClear }: { label: string; color: string; onClear: () => void }) {
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: '4px',
      padding: '3px 8px', borderRadius: '12px',
      background: color + '20', border: `1px solid ${color}`,
      fontSize: '11px', fontWeight: 600, color,
    }}>
      {label}
      <FaTimes
        onClick={onClear}
        style={{ cursor: 'pointer', fontSize: '10px' }}
        title="해제"
      />
    </span>
  )
}

function Empty() {
  return <div style={{ padding: '30px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '12px' }}>데이터가 없습니다.</div>
}

const filterBar: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '8px',
  padding: '12px 16px', marginBottom: '16px',
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '10px', fontSize: '13px', flexWrap: 'wrap',
}
const dateInput: React.CSSProperties = {
  padding: '5px 10px', fontSize: '12px',
  border: '1px solid var(--border-color)', borderRadius: '6px',
  background: 'var(--bg-primary)', color: 'var(--text-primary)',
}
const presetBtn: React.CSSProperties = {
  padding: '4px 10px', borderRadius: '6px', fontSize: '11px',
  border: '1px solid var(--border-color)', background: 'transparent',
  color: 'var(--text-sub)', cursor: 'pointer',
}
const summaryBar: React.CSSProperties = {
  padding: '10px 14px', marginBottom: '16px',
  background: 'var(--accent-subtle)', border: '1px solid var(--accent)',
  borderRadius: '8px', fontSize: '12px', color: 'var(--text-primary)',
}
const cardStyle: React.CSSProperties = {
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '12px', padding: '18px', marginBottom: '16px',
}
const cardTitle: React.CSSProperties = {
  fontSize: '13px', fontWeight: 700, marginBottom: '12px',
}
const hint: React.CSSProperties = {
  fontSize: '11px', fontWeight: 400, color: 'var(--text-muted)', marginLeft: '6px',
}
const tooltipStyle: React.CSSProperties = {
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '8px', fontSize: '13px',
}
const historyRow: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '8px',
  padding: '8px 12px', borderRadius: '6px',
  background: 'var(--bg-primary)', border: '1px solid var(--border-color)',
}
const clearAllBtn: React.CSSProperties = {
  padding: '3px 10px', fontSize: '11px',
  background: 'transparent', border: '1px solid var(--border-color)',
  borderRadius: '6px', color: 'var(--text-muted)', cursor: 'pointer',
}
