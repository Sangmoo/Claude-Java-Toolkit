import { useEffect, useState } from 'react'
import { FaChartBar, FaClock, FaCalendarDay, FaCalendarAlt, FaBolt } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'

interface UsageData {
  todayCount: number
  monthCount: number
  dailyLimit: number
  monthlyLimit: number
  rateLimitPerMinute: number
  rateLimitPerHour: number
}

export default function UsagePage() {
  const [usage, setUsage] = useState<UsageData | null>(null)
  const api = useApi()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/api/v1/usage') as UsageData | null
      if (data) setUsage(data)
    }
    load()
    const interval = setInterval(load, 30000) // 30초마다 갱신
    return () => clearInterval(interval)
  }, [])

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaChartBar style={{ color: '#f59e0b' }} /> 사용량 모니터링
        <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400, marginLeft: '8px' }}>
          (30초마다 자동 갱신)
        </span>
      </h2>

      {usage ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '16px' }}>
          <StatCard
            icon={<FaCalendarDay />}
            iconColor="var(--blue)"
            label="오늘 사용량"
            value={usage.todayCount}
            limit={usage.dailyLimit}
            unit="회/일"
          />
          <StatCard
            icon={<FaCalendarAlt />}
            iconColor="var(--accent)"
            label="이번 달 사용량"
            value={usage.monthCount}
            limit={usage.monthlyLimit}
            unit="회/월"
          />
          <StatCard
            icon={<FaClock />}
            iconColor="var(--purple)"
            label="시간당 제한"
            value={0}
            limit={usage.rateLimitPerHour}
            unit="회/시"
            subtitle="최근 1시간 내 호출 수"
          />
          <StatCard
            icon={<FaBolt />}
            iconColor="var(--green)"
            label="분당 제한"
            value={0}
            limit={usage.rateLimitPerMinute}
            unit="회/분"
            subtitle="최근 1분 내 호출 수"
          />
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
          사용량 데이터를 불러오는 중...
        </div>
      )}

      <div style={{ marginTop: '24px', padding: '14px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px', fontSize: '12px', color: 'var(--text-muted)' }}>
        💡 일일/월간 한도는 DB에 영속화되어 서버 재시작 후에도 유지됩니다.
        한도가 0이면 무제한입니다. 한도 변경은 관리자에게 문의하세요.
      </div>
    </>
  )
}

function StatCard({ icon, iconColor, label, value, limit, unit, subtitle }: {
  icon: React.ReactNode
  iconColor: string
  label: string
  value: number
  limit: number
  unit: string
  subtitle?: string
}) {
  const pct = limit > 0 ? Math.min(100, Math.round((value / limit) * 100)) : 0
  const color = pct >= 90 ? 'var(--red)' : pct >= 70 ? 'var(--yellow)' : iconColor
  const unlimited = limit <= 0

  return (
    <div style={{
      background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
      borderRadius: '12px', padding: '20px',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
        <span style={{ color: iconColor, fontSize: '16px' }}>{icon}</span>
        <span style={{ fontSize: '13px', color: 'var(--text-muted)', fontWeight: 600 }}>{label}</span>
      </div>

      <div style={{ display: 'flex', alignItems: 'baseline', gap: '4px', marginBottom: '8px' }}>
        <span style={{ fontSize: '28px', fontWeight: 700 }}>{value.toLocaleString()}</span>
        <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
          / {unlimited ? '∞' : limit.toLocaleString()} {unit}
        </span>
      </div>

      {!unlimited && limit > 0 && (
        <>
          <div style={{ background: 'var(--bg-primary)', borderRadius: '4px', height: '6px', overflow: 'hidden' }}>
            <div style={{
              height: '100%', width: `${pct}%`, background: color, borderRadius: '4px',
              transition: 'width 0.5s',
            }} />
          </div>
          <div style={{ marginTop: '4px', fontSize: '11px', color: 'var(--text-muted)' }}>
            {pct}% 사용 {pct >= 90 && <span style={{ color: 'var(--red)' }}>⚠️ 곧 한도 도달</span>}
          </div>
        </>
      )}

      {unlimited && (
        <div style={{ fontSize: '11px', color: 'var(--green)', marginTop: '4px' }}>♾️ 무제한</div>
      )}

      {subtitle && (
        <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', fontStyle: 'italic' }}>{subtitle}</div>
      )}
    </div>
  )
}
