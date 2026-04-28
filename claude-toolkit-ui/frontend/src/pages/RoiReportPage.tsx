import { useEffect, useState } from 'react'
import {
  FaChartLine, FaClock, FaComments, FaSearch, FaUser, FaUsers, FaTrophy,
} from 'react-icons/fa'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend, LineChart, Line,
} from 'recharts'
import { useApi } from '../hooks/useApi'
import { useAuthStore } from '../stores/authStore'

interface RoiData {
  totalAnalysis: number
  totalChat: number
  estimatedHoursSaved: number
}

interface FeatureCount { type: string; label: string; count: number }
interface WeekTrend   { week: string; count: number; cumulative: number; hoursSaved: number }
interface UserInsights {
  username: string
  totalAnalysis: number
  totalChat: number
  hoursSaved: number
  topFeatures: FeatureCount[]
  weeklyTrend: WeekTrend[]
}
interface TeamUser { username: string; count: number }
interface TeamInsights {
  userCount: number
  teamTotalAnalysis: number
  teamTotalHoursSaved: number
  averagePerUser: number
  topUsers: TeamUser[]
  myCount?: number
  myRank?: number
  myPercentile?: number
}

const FEATURE_COLORS = ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#06b6d4', '#ef4444']

export default function RoiReportPage() {
  const [data, setData]       = useState<RoiData | null>(null)
  const [me,   setMe]         = useState<UserInsights | null>(null)
  const [team, setTeam]       = useState<TeamInsights | null>(null)
  const api = useApi()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'

  useEffect(() => {
    api.get('/api/v1/roi-report').then((d) => { if (d) setData(d as RoiData) })
    api.get('/api/v1/insights/user').then((d) => { if (d) setMe(d as UserInsights) })
    if (isAdmin) {
      api.get('/api/v1/admin/insights/team').then((d) => { if (d) setTeam(d as TeamInsights) })
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin])

  if (!data) {
    return <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>로딩 중...</div>
  }

  const usageBarData = [
    { name: '분석', count: data.totalAnalysis, fill: '#3b82f6' },
    { name: '채팅', count: data.totalChat, fill: '#8b5cf6' },
  ]
  const pieData = [
    { name: '분석', value: data.totalAnalysis, fill: '#3b82f6' },
    { name: '채팅', value: data.totalChat, fill: '#8b5cf6' },
  ]
  const hoursSaved    = data.estimatedHoursSaved || 0
  const daysSaved     = (hoursSaved / 8).toFixed(1)
  const monetaryValue = (hoursSaved * 50000).toLocaleString()

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaChartLine style={{ color: '#f59e0b' }} /> ROI 리포트
        <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400, marginLeft: '8px' }}>
          (AI 도구 사용으로 절약된 시간 + 사용자/팀 인사이트)
        </span>
      </h2>

      {/* ── 전체 시스템 카드 4종 ─────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        <StatCard icon={<FaSearch />} iconColor="var(--blue)"
          label="총 분석 건수" value={data.totalAnalysis.toLocaleString()}
          subtitle="전체 시스템 누적" />
        <StatCard icon={<FaComments />} iconColor="var(--purple)"
          label="총 채팅 메시지" value={data.totalChat.toLocaleString()}
          subtitle="전체 시스템 누적" />
        <StatCard icon={<FaClock />} iconColor="var(--green)"
          label="절약된 시간" value={`${hoursSaved.toFixed(1)} 시간`}
          subtitle={`약 ${daysSaved}일 근무 단축`} />
        <StatCard icon={<FaChartLine />} iconColor="var(--accent)"
          label="환산 가치" value={`₩${monetaryValue}`}
          subtitle="시간당 5만원 기준" />
      </div>

      {/* 차트 2열 — 전체 사용 비교 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 400px), 1fr))', gap: '16px', marginBottom: 32 }}>
        <ChartPanel title="전체 사용 내역 비교">
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={usageBarData}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
              <XAxis dataKey="name" stroke="var(--text-muted)" fontSize={12} />
              <YAxis stroke="var(--text-muted)" fontSize={12} />
              <Tooltip contentStyle={chartTooltip} cursor={{ fill: 'rgba(148,163,184,0.1)' }} />
              <Bar dataKey="count" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartPanel>
        <ChartPanel title="전체 사용 비율">
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie data={pieData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={85}
                label={(entry) => `${entry.name} ${((entry.value / (data.totalAnalysis + data.totalChat || 1)) * 100).toFixed(0)}%`}>
                {pieData.map((entry, i) => <Cell key={i} fill={entry.fill} />)}
              </Pie>
              <Tooltip contentStyle={chartTooltip} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </ChartPanel>
      </div>

      {/* ── v4.6.x 신규: 내 활동 (사용자 인사이트) ─────────────────────── */}
      {me && (
        <SectionHeader icon={<FaUser style={{ color: '#06b6d4' }} />}
                       title="내 활동" subtitle={me.username + ' 님의 사용 통계'} />
      )}
      {me && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '12px', marginBottom: 16 }}>
          <MiniStat label="내 분석 건수"  value={me.totalAnalysis.toLocaleString()} color="#3b82f6" />
          <MiniStat label="내 채팅 세션"  value={me.totalChat.toLocaleString()}     color="#8b5cf6" />
          <MiniStat label="내 시간 절감"  value={`${me.hoursSaved.toFixed(1)} 시간`} color="#10b981" />
          <MiniStat label="기본 가정"     value="분석 30분 / 채팅 6분" color="#94a3b8" small />
        </div>
      )}
      {me && (me.topFeatures.length > 0 || me.weeklyTrend.length > 0) && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 400px), 1fr))', gap: '16px', marginBottom: 32 }}>
          {me.topFeatures.length > 0 && (
            <ChartPanel title="가장 많이 쓴 기능 Top 5">
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={me.topFeatures} layout="vertical" margin={{ left: 30 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                  <XAxis type="number" stroke="var(--text-muted)" fontSize={11} />
                  <YAxis type="category" dataKey="label" stroke="var(--text-muted)" fontSize={11} width={100} />
                  <Tooltip contentStyle={chartTooltip} cursor={{ fill: 'rgba(148,163,184,0.1)' }} />
                  <Bar dataKey="count" radius={[0, 4, 4, 0]}>
                    {me.topFeatures.map((_, i) => <Cell key={i} fill={FEATURE_COLORS[i % FEATURE_COLORS.length]} />)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </ChartPanel>
          )}
          {me.weeklyTrend.length > 0 && (
            <ChartPanel title="주간 분석 추이 (12주)">
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={me.weeklyTrend}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                  <XAxis dataKey="week" stroke="var(--text-muted)" fontSize={10}
                         tickFormatter={(w) => w.split('-')[1] || w} />
                  <YAxis yAxisId="left"  stroke="var(--text-muted)" fontSize={11} />
                  <YAxis yAxisId="right" orientation="right" stroke="var(--text-muted)" fontSize={11} />
                  <Tooltip contentStyle={chartTooltip} />
                  <Legend />
                  <Line yAxisId="left"  type="monotone" dataKey="count"
                        stroke="#3b82f6" name="주간 건수" strokeWidth={2} dot={{ r: 3 }} />
                  <Line yAxisId="right" type="monotone" dataKey="hoursSaved"
                        stroke="#10b981" name="누적 절감(시간)" strokeWidth={2} dot={false} strokeDasharray="4 4" />
                </LineChart>
              </ResponsiveContainer>
            </ChartPanel>
          )}
        </div>
      )}

      {/* ── v4.6.x 신규: 팀 비교 (ADMIN 전용) ─────────────────────── */}
      {isAdmin && team && (
        <>
          <SectionHeader icon={<FaUsers style={{ color: '#8b5cf6' }} />}
                         title="팀 비교" subtitle="ADMIN 전용 — 사용자별 활동 분포" />

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '12px', marginBottom: 16 }}>
            <MiniStat label="팀 사용자 수" value={String(team.userCount)} color="#8b5cf6" />
            <MiniStat label="팀 누적 분석" value={team.teamTotalAnalysis.toLocaleString()} color="#3b82f6" />
            <MiniStat label="팀 시간 절감" value={`${team.teamTotalHoursSaved.toFixed(1)} 시간`} color="#10b981" />
            <MiniStat label="사용자당 평균" value={team.averagePerUser.toFixed(1)} color="#f59e0b" small />
          </div>

          {(team.myRank ?? 0) > 0 && (
            <div style={percentilePanel}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 10 }}>
                <FaTrophy style={{ fontSize: 22, color: '#f59e0b' }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>내 순위</div>
                  <div style={{ fontSize: 22, fontWeight: 700 }}>
                    {team.myRank} / {team.userCount}
                    <span style={{ marginLeft: 12, fontSize: 13, color: 'var(--text-muted)', fontWeight: 400 }}>
                      상위 {100 - (team.myPercentile ?? 0)}% (백분위 {team.myPercentile})
                    </span>
                  </div>
                </div>
                <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
                  내 분석 <strong style={{ color: 'var(--text-primary)', fontSize: 17 }}>{team.myCount?.toLocaleString()}</strong>건
                </div>
              </div>
              <div style={{ background: 'var(--bg-primary)', borderRadius: 4, height: 8, overflow: 'hidden', position: 'relative' }}>
                <div style={{
                  height: '100%', width: `${team.myPercentile ?? 0}%`,
                  background: 'linear-gradient(90deg,#f59e0b,#10b981)',
                  borderRadius: 4, transition: 'width 0.4s',
                }} />
              </div>
            </div>
          )}

          {team.topUsers.length > 0 && (
            <div style={{ marginBottom: 32 }}>
              <ChartPanel title={`상위 사용자 (Top ${team.topUsers.length})`}>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={team.topUsers}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                    <XAxis dataKey="username" stroke="var(--text-muted)" fontSize={11} />
                    <YAxis stroke="var(--text-muted)" fontSize={11} />
                    <Tooltip contentStyle={chartTooltip} cursor={{ fill: 'rgba(148,163,184,0.1)' }} />
                    <Bar dataKey="count" radius={[6, 6, 0, 0]}>
                      {team.topUsers.map((_, i) => <Cell key={i} fill={FEATURE_COLORS[i % FEATURE_COLORS.length]} />)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </ChartPanel>
            </div>
          )}
        </>
      )}

      {/* 절감 시간 가정 메모 */}
      <div style={{ padding: '14px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px', fontSize: '12px', color: 'var(--text-muted)' }}>
        💡 절약 시간은 <strong>분석당 30분</strong>, <strong>채팅당 6분</strong> 기준으로 추정됩니다.
        실제 효율은 사용 방식에 따라 다를 수 있습니다.
      </div>
    </>
  )
}

// ── 보조 컴포넌트 ─────────────────────────────────────────────────────────

function StatCard({ icon, iconColor, label, value, subtitle }: {
  icon: React.ReactNode; iconColor: string; label: string; value: string; subtitle: string
}) {
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '20px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px' }}>
        <span style={{ color: iconColor, fontSize: '18px' }}>{icon}</span>
        <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 600 }}>{label}</span>
      </div>
      <div style={{ fontSize: '26px', fontWeight: 700, marginBottom: '4px' }}>{value}</div>
      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{subtitle}</div>
    </div>
  )
}

function MiniStat({ label, value, color, small }: {
  label: string; value: string; color: string; small?: boolean
}) {
  return (
    <div style={{
      background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
      borderRadius: 10, padding: 14, borderLeft: `3px solid ${color}`,
    }}>
      <div style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600, marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: small ? 13 : 20, fontWeight: 700, color: small ? 'var(--text-muted)' : 'var(--text-primary)' }}>
        {value}
      </div>
    </div>
  )
}

function ChartPanel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '18px' }}>
      <h3 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '12px' }}>{title}</h3>
      {children}
    </div>
  )
}

function SectionHeader({ icon, title, subtitle }: { icon: React.ReactNode; title: string; subtitle?: string }) {
  return (
    <h3 style={{
      fontSize: 15, fontWeight: 700, marginTop: 8, marginBottom: 12,
      display: 'flex', alignItems: 'center', gap: 8,
      paddingBottom: 6, borderBottom: '2px solid var(--border-color)',
    }}>
      {icon}
      <span>{title}</span>
      {subtitle && <span style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 400, marginLeft: 6 }}>{subtitle}</span>}
    </h3>
  )
}

const chartTooltip: React.CSSProperties = {
  background: 'var(--bg-secondary)',
  border: '1px solid var(--border-color)',
  borderRadius: 8, fontSize: 13,
}

const percentilePanel: React.CSSProperties = {
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: 12, padding: 18, marginBottom: 16,
}
