import { useEffect, useState } from 'react'
import { useAuthStore } from '../stores/authStore'
import { useApi } from '../hooks/useApi'
import {
  FaDatabase, FaFileAlt, FaComments, FaProjectDiagram,
  FaCode, FaHistory, FaChartBar, FaBug,
} from 'react-icons/fa'

interface HealthData {
  status: string
  version: string
  claudeModel: string
  apiKeySet: boolean
  dbConfigured: boolean
}

const toolCards = [
  { icon: FaDatabase, color: '#3b82f6', title: 'SQL 리뷰', desc: 'SQL 쿼리 리뷰, 보안 감사, 성능 분석', path: '/advisor' },
  { icon: FaComments, color: '#8b5cf6', title: 'AI 채팅', desc: 'Claude와 자유롭게 대화하며 코드 질문', path: '/chat' },
  { icon: FaFileAlt, color: '#10b981', title: '기술 문서', desc: '자동 문서화, Javadoc, API 명세 생성', path: '/docgen' },
  { icon: FaProjectDiagram, color: '#8b5cf6', title: '분석 파이프라인', desc: 'YAML 기반 다단계 분석 자동화', path: '/pipelines' },
  { icon: FaCode, color: '#10b981', title: '코드 변환', desc: 'iBatis → MyBatis, Java 버전 변환', path: '/converter' },
  { icon: FaChartBar, color: '#3b82f6', title: '복잡도 분석', desc: '코드 복잡도 및 품질 메트릭 분석', path: '/complexity' },
  { icon: FaHistory, color: '#f59e0b', title: '리뷰 이력', desc: '분석 결과 저장, 비교, 내보내기', path: '/history' },
  { icon: FaBug, color: '#06b6d4', title: '로그 분석', desc: '로그 파일 분석, 보안 위협 탐지', path: '/loganalyzer' },
]

export default function HomePage() {
  const user = useAuthStore((s) => s.user)
  const { data: health, get } = useApi<HealthData>({ showError: false })
  const [greeting, setGreeting] = useState('')

  useEffect(() => {
    get('/api/v1/health')
    const h = new Date().getHours()
    setGreeting(h < 12 ? '좋은 아침입니다' : h < 18 ? '좋은 오후입니다' : '좋은 저녁입니다')
  }, [get])

  return (
    <>
      {/* Hero */}
      <div style={{
        background: 'linear-gradient(135deg, var(--bg-secondary), var(--bg-tertiary))',
        borderRadius: '16px',
        padding: '40px 32px',
        marginBottom: '28px',
        border: '1px solid var(--border-color)',
      }}>
        <h1 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '8px' }}>
          {greeting}, {user?.username ?? 'Guest'}
        </h1>
        <p style={{ color: 'var(--text-sub)', fontSize: '14px', marginBottom: '16px' }}>
          AI-powered tools for Oracle DB & Java/Spring enterprise development
        </p>
        {health && (
          <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', fontSize: '13px' }}>
            <span style={{ color: 'var(--green)' }}>
              Server: {health.status}
            </span>
            <span style={{ color: 'var(--text-muted)' }}>
              Model: {health.claudeModel}
            </span>
            <span style={{ color: health.apiKeySet ? 'var(--green)' : 'var(--red)' }}>
              API Key: {health.apiKeySet ? 'Set' : 'Missing'}
            </span>
          </div>
        )}
      </div>

      {/* Tool Cards Grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
        gap: '16px',
      }}>
        {toolCards.map((card) => {
          const Icon = card.icon
          return (
            <a
              key={card.path}
              href={card.path}
              style={{
                display: 'block',
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-color)',
                borderRadius: '12px',
                padding: '22px',
                textDecoration: 'none',
                color: 'inherit',
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = card.color
                e.currentTarget.style.transform = 'translateY(-2px)'
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = 'var(--border-color)'
                e.currentTarget.style.transform = 'none'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '10px' }}>
                <Icon style={{ fontSize: '22px', color: card.color }} />
                <span style={{ fontSize: '15px', fontWeight: 600 }}>{card.title}</span>
              </div>
              <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>
                {card.desc}
              </p>
            </a>
          )
        })}
      </div>
    </>
  )
}
