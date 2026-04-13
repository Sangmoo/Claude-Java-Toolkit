import { useEffect, useState } from 'react'
import { FaChartBar } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'

interface UsageData {
  todayCount: number
  monthCount: number
  dailyLimit: number
  monthlyLimit: number
}

export default function UsagePage() {
  const [usage, setUsage] = useState<UsageData | null>(null)
  const api = useApi()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/usage?format=json') as UsageData | null
      if (data) setUsage(data)
    }
    load()
  }, [api])

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaChartBar style={{ color: '#f59e0b' }} /> 사용량 모니터링
      </h2>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '16px' }}>
        {usage && (
          <>
            <StatCard label="오늘 사용량" value={usage.todayCount} limit={usage.dailyLimit} color="var(--blue)" />
            <StatCard label="이번 달 사용량" value={usage.monthCount} limit={usage.monthlyLimit} color="var(--accent)" />
          </>
        )}
      </div>
      {!usage && (
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
          사용량 데이터를 불러오는 중...
        </div>
      )}
    </>
  )
}

function StatCard({ label, value, limit, color }: { label: string; value: number; limit: number; color: string }) {
  const pct = limit > 0 ? Math.min(100, Math.round((value / limit) * 100)) : 0
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '20px' }}>
      <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '8px' }}>{label}</div>
      <div style={{ fontSize: '28px', fontWeight: 700, marginBottom: '8px' }}>
        {value} <span style={{ fontSize: '14px', color: 'var(--text-muted)', fontWeight: 400 }}>/ {limit || '∞'}</span>
      </div>
      {limit > 0 && (
        <div style={{ background: 'var(--bg-primary)', borderRadius: '4px', height: '6px', overflow: 'hidden' }}>
          <div style={{ height: '100%', width: `${pct}%`, background: color, borderRadius: '4px', transition: 'width 0.5s' }} />
        </div>
      )}
    </div>
  )
}
