import { useEffect, useState } from 'react'
import { FaHeartbeat, FaServer, FaDatabase, FaMemory, FaStethoscope, FaSpinner } from 'react-icons/fa'
import { useApi } from '../../hooks/useApi'

interface HealthData {
  jvmHeapUsed: string; jvmHeapMax: string; heapUsagePercent: number
  uptime: string; threadCount: number; javaVersion: string; osName: string
  dbFileSize: string; diskFreeSpace: string; apiStatus: string; userCount: number
  // 자동 이관 + 런타임 전환 반영용 (v4.2.x)
  dbType?: string
  dbProduct?: string
  dbVersion?: string
  dbUrl?: string
  dbUsername?: string
  dbConnected?: boolean
  dbError?: string
  dbOverrideActive?: boolean
}

export default function AdminHealthPage() {
  const [data, setData] = useState<HealthData | null>(null)
  const [diagReport, setDiagReport] = useState<string>('')
  const [diagLoading, setDiagLoading] = useState(false)
  const api = useApi()

  const runDiagnose = async () => {
    setDiagLoading(true)
    setDiagReport('')
    try {
      const res = await fetch('/admin/health/claude-api-diagnose', { credentials: 'include' })
      const d = await res.json()
      setDiagReport(d.success ? (d.report || '(empty)') : ('오류: ' + (d.error || 'unknown')))
    } catch (e) {
      setDiagReport('요청 실패: ' + (e instanceof Error ? e.message : String(e)))
    }
    setDiagLoading(false)
  }

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
          <Stat label="유형" value={dbBadge(data)} />
          {data.dbProduct && <Stat label="제품" value={data.dbProduct} />}
          {data.dbUrl && <Stat label="URL" value={data.dbUrl} />}
          {data.dbUsername && <Stat label="계정" value={data.dbUsername} />}
          <Stat label="DB 파일/크기" value={data.dbFileSize} />
          <Stat label="디스크 여유" value={data.diskFreeSpace} />
          <Stat label="등록 사용자" value={String(data.userCount)} />
          {data.dbOverrideActive && (
            <div style={{ marginTop: '6px', padding: '6px 10px', background: 'rgba(139,92,246,0.12)', border: '1px solid rgba(139,92,246,0.3)', borderRadius: '6px', fontSize: '11px', color: '#8b5cf6' }}>
              🔁 자동 이관으로 전환된 운영 DB 사용 중
            </div>
          )}
          {data.dbConnected === false && (
            <div style={{ marginTop: '6px', padding: '6px 10px', background: 'rgba(239,68,68,0.12)', border: '1px solid rgba(239,68,68,0.3)', borderRadius: '6px', fontSize: '11px', color: 'var(--red)' }}>
              ⚠ DB 연결 실패: {data.dbError || 'unknown'}
            </div>
          )}
        </Card>
        <Card icon={<FaServer style={{ color: 'var(--text-muted)' }} />} title="시스템 정보">
          <Stat label="Java" value={data.javaVersion} />
          <Stat label="OS" value={data.osName} />
        </Card>
      </div>

      {/* Claude API 연결 진단 */}
      <div style={{ marginTop: '24px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '18px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '14px', fontWeight: 600 }}>
            <FaStethoscope style={{ color: '#8b5cf6' }} /> Claude API 연결 진단
          </div>
          <button onClick={runDiagnose} disabled={diagLoading} style={{
            display: 'flex', alignItems: 'center', gap: '6px',
            padding: '6px 14px', borderRadius: '6px',
            background: 'var(--accent)', color: '#fff', border: 'none',
            fontSize: '12px', fontWeight: 600, cursor: diagLoading ? 'not-allowed' : 'pointer',
            opacity: diagLoading ? 0.6 : 1,
          }}>
            {diagLoading ? <><FaSpinner className="spin" /> 진단 중...</> : '진단 실행'}
          </button>
        </div>
        <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '8px', lineHeight: 1.6 }}>
          TLS 핸드셰이크·DNS·프록시 경로를 종합 확인합니다. <code>handshake_failure</code> 가 나오면
          사내망 프록시(<code>CLAUDE_PROXY_HOST/PORT</code>) 또는 베이스 URL(<code>CLAUDE_BASE_URL</code>)
          설정이 필요할 수 있습니다.
        </p>
        {diagReport && (
          <pre style={{
            background: 'var(--bg-primary)', border: '1px solid var(--border-color)',
            borderRadius: '6px', padding: '12px', fontSize: '11.5px',
            fontFamily: 'Consolas, Monaco, monospace', whiteSpace: 'pre-wrap',
            maxHeight: '280px', overflowY: 'auto', margin: 0,
          }}>{diagReport}</pre>
        )}
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

function dbBadge(d: HealthData): string {
  const t = (d.dbType || 'unknown').toUpperCase()
  if (d.dbConnected === false) return `${t} (연결 실패)`
  if (d.dbVersion) return `${t} ${d.dbVersion.split(/\s+/)[0]}`
  return t
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px', gap: '8px' }}>
      <span style={{ color: 'var(--text-muted)', flexShrink: 0 }}>{label}</span>
      <span style={{ fontWeight: 500, textAlign: 'right', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={value}>{value}</span>
    </div>
  )
}
