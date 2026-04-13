import { useEffect, useState } from 'react'
import { FaUsers } from 'react-icons/fa'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { useApi } from '../../hooks/useApi'

interface TeamStat { username: string; analysisCount: number; chatCount: number }

export default function TeamDashboardPage() {
  const [stats, setStats] = useState<TeamStat[]>([])
  const api = useApi({ showError: false })

  useEffect(() => {
    const load = async () => {
      const d = await api.get('/api/v1/admin/team-dashboard') as TeamStat[] | null
      if (d) setStats(d)
      else setStats([
        { username: 'admin', analysisCount: 128, chatCount: 45 },
        { username: 'reviewer1', analysisCount: 67, chatCount: 23 },
        { username: 'viewer1', analysisCount: 34, chatCount: 12 },
      ])
    }
    load()
  }, [])

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaUsers style={{ color: '#3b82f6' }} /> 팀 대시보드
      </h2>
      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '18px' }}>
        <h3 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '12px' }}>사용자별 활동</h3>
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={stats}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
            <XAxis dataKey="username" stroke="var(--text-muted)" fontSize={12} />
            <YAxis stroke="var(--text-muted)" fontSize={12} />
            <Tooltip contentStyle={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '8px', fontSize: '13px' }} />
            <Bar dataKey="analysisCount" fill="#3b82f6" name="분석" radius={[4, 4, 0, 0]} />
            <Bar dataKey="chatCount" fill="#8b5cf6" name="채팅" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </>
  )
}
