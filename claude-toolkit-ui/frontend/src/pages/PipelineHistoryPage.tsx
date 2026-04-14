import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { FaHistory, FaCheckCircle, FaTimesCircle, FaSpinner, FaEye } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'

interface Execution {
  id: number
  pipelineName: string
  status: string
  totalSteps: number
  completedSteps: number
  startedAt: string
  completedAt?: string
  username: string
}

const statusIcon = (s: string) => {
  if (s === 'COMPLETED') return <FaCheckCircle style={{ color: 'var(--green)' }} />
  if (s === 'FAILED') return <FaTimesCircle style={{ color: 'var(--red)' }} />
  if (s === 'RUNNING') return <FaSpinner className="spin" style={{ color: 'var(--accent)' }} />
  return <FaHistory style={{ color: 'var(--text-muted)' }} />
}

export default function PipelineHistoryPage() {
  const [executions, setExecutions] = useState<Execution[]>([])
  const api = useApi()

  useEffect(() => {
    api.get('/api/v1/pipelines/executions').then((data) => { if (data) setExecutions(data as Execution[]) })
  }, [])

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaHistory style={{ color: '#8b5cf6' }} /> 파이프라인 실행 이력
      </h2>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {executions.map((e) => (
          <div key={e.id} style={{
            display: 'flex', alignItems: 'center', gap: '12px',
            padding: '14px 16px', background: 'var(--bg-secondary)',
            border: '1px solid var(--border-color)', borderRadius: '10px',
          }}>
            {statusIcon(e.status)}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 600, fontSize: '14px' }}>{e.pipelineName}</div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)', display: 'flex', gap: '12px' }}>
                <span>실행자: {e.username}</span>
                <span>진행: {e.completedSteps}/{e.totalSteps}</span>
                <span>시작: {e.startedAt}</span>
                {e.completedAt && <span>완료: {e.completedAt}</span>}
              </div>
            </div>
            <Link to={`/pipelines/execution/${e.id}`} style={{
              display: 'flex', alignItems: 'center', gap: '4px',
              padding: '6px 14px', borderRadius: '6px',
              background: 'var(--accent-subtle)', color: 'var(--accent)',
              fontSize: '12px', textDecoration: 'none', fontWeight: 600,
            }}>
              <FaEye /> 상세
            </Link>
          </div>
        ))}
        {executions.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
            <FaHistory style={{ fontSize: '36px', opacity: 0.3, marginBottom: '12px' }} />
            <p>실행 이력이 없습니다.</p>
          </div>
        )}
      </div>
    </>
  )
}
