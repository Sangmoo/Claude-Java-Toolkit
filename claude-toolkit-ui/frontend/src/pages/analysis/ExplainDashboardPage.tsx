import { useEffect, useState } from 'react'
import { FaChartLine } from 'react-icons/fa'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  LineChart, Line, Legend,
} from 'recharts'
import { useApi } from '../../hooks/useApi'

interface PerfEntry {
  date: string
  avgDuration: number
  queryCount: number
  slowQueries: number
}

export default function ExplainDashboardPage() {
  const [data, setData] = useState<PerfEntry[]>([])
  const api = useApi({ showError: false })

  useEffect(() => {
    const load = async () => {
      const d = await api.get('/api/v1/explain/dashboard') as PerfEntry[] | null
      if (d) setData(d)
      else {
        // 데모 데이터
        setData([
          { date: '04-07', avgDuration: 120, queryCount: 45, slowQueries: 3 },
          { date: '04-08', avgDuration: 95, queryCount: 52, slowQueries: 1 },
          { date: '04-09', avgDuration: 140, queryCount: 38, slowQueries: 5 },
          { date: '04-10', avgDuration: 88, queryCount: 60, slowQueries: 2 },
          { date: '04-11', avgDuration: 110, queryCount: 47, slowQueries: 4 },
          { date: '04-12', avgDuration: 75, queryCount: 55, slowQueries: 0 },
          { date: '04-13', avgDuration: 102, queryCount: 41, slowQueries: 3 },
        ])
      }
    }
    load()
  }, [api])

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaChartLine style={{ color: '#3b82f6' }} /> 성능 히스토리
      </h2>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '24px' }}>
        <StatCard label="평균 실행 시간" value={`${Math.round(data.reduce((s, d) => s + d.avgDuration, 0) / (data.length || 1))}ms`} color="var(--blue)" />
        <StatCard label="총 쿼리 수" value={String(data.reduce((s, d) => s + d.queryCount, 0))} color="var(--accent)" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        <ChartPanel title="일별 평균 실행 시간 (ms)">
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={data}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
              <XAxis dataKey="date" stroke="var(--text-muted)" fontSize={12} />
              <YAxis stroke="var(--text-muted)" fontSize={12} />
              <Tooltip contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px', fontSize: '13px' }} />
              <Bar dataKey="avgDuration" fill="#3b82f6" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartPanel>

        <ChartPanel title="쿼리 수 & 슬로우 쿼리">
          <ResponsiveContainer width="100%" height={250}>
            <LineChart data={data}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
              <XAxis dataKey="date" stroke="var(--text-muted)" fontSize={12} />
              <YAxis stroke="var(--text-muted)" fontSize={12} />
              <Tooltip contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px', fontSize: '13px' }} />
              <Legend />
              <Line type="monotone" dataKey="queryCount" stroke="#22c55e" name="쿼리 수" strokeWidth={2} />
              <Line type="monotone" dataKey="slowQueries" stroke="#ef4444" name="슬로우 쿼리" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </ChartPanel>
      </div>
    </>
  )
}

function StatCard({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '20px' }}>
      <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '6px' }}>{label}</div>
      <div style={{ fontSize: '28px', fontWeight: 700, color }}>{value}</div>
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
