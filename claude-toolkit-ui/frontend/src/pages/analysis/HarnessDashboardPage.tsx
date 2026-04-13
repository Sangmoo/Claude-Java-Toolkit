import { useEffect, useState } from 'react'
import { FaChartLine } from 'react-icons/fa'
import {
  PieChart, Pie, Cell, ResponsiveContainer, Tooltip, BarChart, Bar, XAxis, YAxis, CartesianGrid,
} from 'recharts'
import { useApi } from '../../hooks/useApi'

interface QualityData {
  category: string
  count: number
}

const COLORS = ['#22c55e', '#f59e0b', '#ef4444', '#3b82f6', '#8b5cf6']

export default function HarnessDashboardPage() {
  const [severity, setSeverity] = useState<QualityData[]>([])
  const [categories, setCategories] = useState<QualityData[]>([])
  const api = useApi({ showError: false })

  useEffect(() => {
    const load = async () => {
      const d = await api.get('/api/v1/harness/dashboard')
      if (d) {
        // parse response
      } else {
        // 데모 데이터
        setSeverity([
          { category: 'LOW', count: 42 },
          { category: 'MEDIUM', count: 18 },
          { category: 'HIGH', count: 7 },
        ])
        setCategories([
          { category: '설계', count: 15 },
          { category: '성능', count: 22 },
          { category: '보안', count: 7 },
          { category: '코딩 스타일', count: 23 },
        ])
      }
    }
    load()
  }, [api])

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaChartLine style={{ color: '#8b5cf6' }} /> 품질 대시보드
      </h2>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '18px' }}>
          <h3 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '12px' }}>심각도 분포</h3>
          <ResponsiveContainer width="100%" height={250}>
            <PieChart>
              <Pie data={severity} dataKey="count" nameKey="category" cx="50%" cy="50%" outerRadius={80} label={({ category, percent }) => `${category} ${(percent * 100).toFixed(0)}%`}>
                {severity.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Pie>
              <Tooltip contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px', fontSize: '13px' }} />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '18px' }}>
          <h3 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '12px' }}>카테고리별 이슈</h3>
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={categories}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
              <XAxis dataKey="category" stroke="var(--text-muted)" fontSize={12} />
              <YAxis stroke="var(--text-muted)" fontSize={12} />
              <Tooltip contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px', fontSize: '13px' }} />
              <Bar dataKey="count" fill="#8b5cf6" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </>
  )
}
