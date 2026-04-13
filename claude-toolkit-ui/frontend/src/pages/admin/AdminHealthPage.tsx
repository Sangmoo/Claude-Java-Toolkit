import { useEffect, useState } from 'react'
import { FaHeartbeat, FaServer, FaDatabase, FaMemory } from 'react-icons/fa'
import { useApi } from '../../hooks/useApi'

interface HealthData {
  jvmHeapUsed: string; jvmHeapMax: string; heapUsagePercent: number
  uptime: string; threadCount: number; javaVersion: string; osName: string
  dbFileSize: string; diskFreeSpace: string; apiStatus: string; userCount: number
}

export default function AdminHealthPage() {
  const [data, setData] = useState<HealthData | null>(null)
  const api = useApi()

  useEffect(() => {
    const load = async () => {
      const d = await api.get('/api/v1/admin/health/data') as HealthData | null
      if (d) setData(d)
    }
    load()
    const interval = setInterval(load, 30000)
    return () => clearInterval(interval)
  }, [])

  if (!data) return <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>로딩 중...</div>

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaHeartbeat style={{ color: '#ef4444' }} /> 시스템 헬스
      </h2>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '16px' }}>
        <Card icon={<FaServer style={{ color: 'var(--green)' }} />} title="서버 상태">
          <Stat label="업타임" value={data.uptime} />
          <Stat label="스레드" value={String(data.threadCount)} />
          <Stat label="API 상태" value={data.apiStatus} />
        </Card>
        <Card icon={<FaMemory style={{ color: 'var(--blue)' }} />} title="JVM 메모리">
          <Stat label="사용" value={data.jvmHeapUsed} />
          <Stat label="최대" value={data.jvmHeapMax} />
          <div style={{ background: 'var(--bg-primary)', borderRadius: '4px', height: '6px', marginTop: '8px', overflow: 'hidden' }}>
            <div style={{ height: '100%', width: `${data.heapUsagePercent}%`, background: data.heapUsagePercent > 80 ? 'var(--red)' : 'var(--blue)', borderRadius: '4px' }} />
          </div>
        </Card>
        <Card icon={<FaDatabase style={{ color: '#f59e0b' }} />} title="데이터베이스">
          <Stat label="DB 파일" value={data.dbFileSize} />
          <Stat label="디스크 여유" value={data.diskFreeSpace} />
          <Stat label="등록 사용자" value={String(data.userCount)} />
        </Card>
        <Card icon={<FaServer style={{ color: 'var(--text-muted)' }} />} title="시스템 정보">
          <Stat label="Java" value={data.javaVersion} />
          <Stat label="OS" value={data.osName} />
        </Card>
      </div>
    </>
  )
}

function Card({ icon, title, children }: { icon: React.ReactNode; title: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '18px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px', fontSize: '14px', fontWeight: 600 }}>{icon} {title}</div>
      {children}
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px' }}>
      <span style={{ color: 'var(--text-muted)' }}>{label}</span>
      <span style={{ fontWeight: 500 }}>{value}</span>
    </div>
  )
}
