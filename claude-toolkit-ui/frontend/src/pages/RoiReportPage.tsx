import { useEffect, useState } from 'react'
import { FaChartLine, FaClock, FaComments, FaSearch } from 'react-icons/fa'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend,
} from 'recharts'
import { useApi } from '../hooks/useApi'

interface RoiData {
  totalAnalysis: number
  totalChat: number
  estimatedHoursSaved: number
}

export default function RoiReportPage() {
  const [data, setData] = useState<RoiData | null>(null)
  const api = useApi()

  useEffect(() => {
    api.get('/api/v1/roi-report').then((d) => { if (d) setData(d as RoiData) })
  }, [])

  if (!data) {
    return <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>로딩 중...</div>
  }

  // 차트 데이터 구성
  const usageBarData = [
    { name: '분석', count: data.totalAnalysis, fill: '#3b82f6' },
    { name: '채팅', count: data.totalChat, fill: '#8b5cf6' },
  ]
  const pieData = [
    { name: '분석', value: data.totalAnalysis, fill: '#3b82f6' },
    { name: '채팅', value: data.totalChat, fill: '#8b5cf6' },
  ]
  const hoursSaved = data.estimatedHoursSaved || 0
  const daysSaved = (hoursSaved / 8).toFixed(1)
  const monetaryValue = (hoursSaved * 50000).toLocaleString() // 시간당 5만원 가정

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaChartLine style={{ color: '#f59e0b' }} /> ROI 리포트
        <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400, marginLeft: '8px' }}>
          (AI 도구 사용으로 절약된 시간 분석)
        </span>
      </h2>

      {/* 핵심 지표 카드 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        <StatCard
          icon={<FaSearch />}
          iconColor="var(--blue)"
          label="총 분석 건수"
          value={data.totalAnalysis.toLocaleString()}
          subtitle="코드/SQL 리뷰, 문서 생성 등"
        />
        <StatCard
          icon={<FaComments />}
          iconColor="var(--purple)"
          label="총 채팅 메시지"
          value={data.totalChat.toLocaleString()}
          subtitle="AI 채팅 대화"
        />
        <StatCard
          icon={<FaClock />}
          iconColor="var(--green)"
          label="절약된 시간"
          value={`${hoursSaved.toFixed(1)} 시간`}
          subtitle={`약 ${daysSaved}일 근무 단축`}
        />
        <StatCard
          icon={<FaChartLine />}
          iconColor="var(--accent)"
          label="환산 가치"
          value={`₩${monetaryValue}`}
          subtitle="시간당 5만원 기준"
        />
      </div>

      {/* 차트 2열 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 400px), 1fr))', gap: '16px' }}>
        <ChartPanel title="사용 내역 비교">
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={usageBarData}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
              <XAxis dataKey="name" stroke="var(--text-muted)" fontSize={12} />
              <YAxis stroke="var(--text-muted)" fontSize={12} />
              <Tooltip
                contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px', fontSize: '13px' }}
                cursor={{ fill: 'rgba(148,163,184,0.1)' }}
              />
              <Bar dataKey="count" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartPanel>

        <ChartPanel title="사용 비율">
          <ResponsiveContainer width="100%" height={280}>
            <PieChart>
              <Pie
                data={pieData}
                dataKey="value"
                nameKey="name"
                cx="50%"
                cy="50%"
                outerRadius={90}
                label={(entry) => `${entry.name} ${((entry.value / (data.totalAnalysis + data.totalChat || 1)) * 100).toFixed(0)}%`}
              >
                {pieData.map((entry, i) => <Cell key={i} fill={entry.fill} />)}
              </Pie>
              <Tooltip
                contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px', fontSize: '13px' }}
              />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </ChartPanel>
      </div>

      <div style={{ marginTop: '20px', padding: '14px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px', fontSize: '12px', color: 'var(--text-muted)' }}>
        💡 절약 시간은 <strong>분석당 30분</strong>, <strong>채팅당 6분</strong>을 기준으로 추정됩니다.
        실제 효율은 사용 방식에 따라 다를 수 있습니다.
      </div>
    </>
  )
}

function StatCard({ icon, iconColor, label, value, subtitle }: {
  icon: React.ReactNode
  iconColor: string
  label: string
  value: string
  subtitle: string
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

function ChartPanel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '18px' }}>
      <h3 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '12px' }}>{title}</h3>
      {children}
    </div>
  )
}
