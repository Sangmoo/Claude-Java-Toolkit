import { useEffect, useState, useCallback } from 'react'
import { useAuthStore } from '../stores/authStore'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'
import { formatRelative } from '../utils/date'
import {
  FaDatabase, FaFileAlt, FaComments, FaProjectDiagram,
  FaCode, FaHistory, FaChartBar, FaBug, FaUsers, FaCheckCircle, FaTimesCircle, FaClock,
  FaThLarge, FaSave, FaUndo, FaEye, FaEyeSlash,
} from 'react-icons/fa'
import GridLayout, { type Layout } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'

interface HealthData {
  status: string
  version: string
  claudeModel: string
  apiKeySet: boolean
  dbConfigured: boolean
}

interface TeamActivity {
  id:           number
  type:         string
  title:        string
  username:     string
  reviewStatus: string | null
  createdAt:    string
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

/** v4.3.0 — 위젯 카탈로그 (key, 라벨, 기본 크기) */
interface WidgetSpec {
  key: string
  label: string
  defaultW: number
  defaultH: number
}
const WIDGETS: WidgetSpec[] = [
  { key: 'hero',          label: '인사말 + 시스템 상태', defaultW: 12, defaultH: 4 },
  { key: 'tools',         label: '도구 카드 그리드',     defaultW: 12, defaultH: 8 },
  { key: 'team-activity', label: '팀 활동 피드',          defaultW: 12, defaultH: 6 },
]

interface PersistedLayout {
  widgetKey: string
  x: number
  y: number
  w: number
  h: number
  visible: boolean
}

function defaultLayout(): PersistedLayout[] {
  let yCursor = 0
  return WIDGETS.map((w) => {
    const item: PersistedLayout = { widgetKey: w.key, x: 0, y: yCursor, w: w.defaultW, h: w.defaultH, visible: true }
    yCursor += w.defaultH
    return item
  })
}

export default function HomePage() {
  const user = useAuthStore((s) => s.user)
  const { data: health, get } = useApi<HealthData>({ showError: false })
  const [greeting, setGreeting] = useState('')
  const [teamActivity, setTeamActivity] = useState<TeamActivity[]>([])
  const toast = useToast()

  // v4.3.0: 위젯 레이아웃 상태
  const [layout, setLayout] = useState<PersistedLayout[]>(defaultLayout())
  const [editMode, setEditMode] = useState(false)
  const [layoutDirty, setLayoutDirty] = useState(false)

  useEffect(() => {
    get('/api/v1/health')
    const h = new Date().getHours()
    setGreeting(h < 12 ? '좋은 아침입니다' : h < 18 ? '좋은 오후입니다' : '좋은 저녁입니다')
    fetch('/api/v1/team-activity', { credentials: 'include' })
      .then((r) => r.ok ? r.json() : null)
      .then((j) => {
        const list = (j?.data ?? j) as TeamActivity[]
        if (Array.isArray(list)) setTeamActivity(list)
      })
      .catch(() => {})

    // v4.3.0: 사용자별 저장된 레이아웃 로드 (없으면 기본값 유지)
    fetch('/api/v1/dashboard/layout', { credentials: 'include' })
      .then((r) => r.ok ? r.json() : null)
      .then((j) => {
        const saved = (j?.data ?? j) as PersistedLayout[]
        if (Array.isArray(saved) && saved.length > 0) {
          // 누락된 위젯은 기본값으로 보강
          const known = new Set(saved.map((s) => s.widgetKey))
          const merged = [...saved]
          let yCursor = Math.max(...saved.map((s) => s.y + s.h), 0)
          for (const w of WIDGETS) {
            if (!known.has(w.key)) {
              merged.push({ widgetKey: w.key, x: 0, y: yCursor, w: w.defaultW, h: w.defaultH, visible: true })
              yCursor += w.defaultH
            }
          }
          setLayout(merged)
        }
      })
      .catch(() => {})
  }, [get])

  const onLayoutChange = useCallback((next: Layout[]) => {
    setLayout((prev) => {
      // react-grid-layout 의 Layout[] 을 prev 와 매핑하여 visible 유지
      const map = new Map(prev.map((p) => [p.widgetKey, p]))
      return next.map((n) => {
        const old = map.get(n.i)
        return {
          widgetKey: n.i, x: n.x, y: n.y, w: n.w, h: n.h,
          visible: old?.visible ?? true,
        }
      })
    })
    setLayoutDirty(true)
  }, [])

  const toggleVisible = (key: string) => {
    setLayout((prev) => prev.map((p) => p.widgetKey === key ? { ...p, visible: !p.visible } : p))
    setLayoutDirty(true)
  }

  const saveLayout = async () => {
    try {
      const res = await fetch('/api/v1/dashboard/layout', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(layout),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setLayoutDirty(false)
      toast.success('대시보드 레이아웃이 저장되었습니다')
    } catch (e) {
      toast.error(`저장 실패: ${e instanceof Error ? e.message : ''}`)
    }
  }

  const resetLayout = () => {
    setLayout(defaultLayout())
    setLayoutDirty(true)
  }

  // 보이는 위젯만 react-grid-layout 의 Layout[] 형태로 변환
  const gridLayout: Layout[] = layout
    .filter((p) => p.visible)
    .map((p) => ({ i: p.widgetKey, x: p.x, y: p.y, w: p.w, h: p.h }))

  const renderWidget = (key: string) => {
    if (key === 'hero') return <HeroWidget greeting={greeting} username={user?.username} health={health} />
    if (key === 'tools') return <ToolsWidget />
    if (key === 'team-activity') return <TeamActivityWidget activities={teamActivity} />
    return null
  }

  return (
    <>
      {/* 편집 툴바 */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        marginBottom: '16px', flexWrap: 'wrap', gap: '8px',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-muted)' }}>
          <FaThLarge /> 홈 대시보드
          {editMode && <span style={{ color: 'var(--accent)', fontWeight: 600 }}>· 편집 모드</span>}
          {layoutDirty && <span style={{ color: 'var(--orange, #f59e0b)' }}>· 저장되지 않은 변경</span>}
        </div>
        <div style={{ display: 'flex', gap: '6px' }}>
          {editMode && (
            <>
              <button onClick={resetLayout}
                style={{ padding: '6px 12px', fontSize: '13px', background: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer' }}>
                <FaUndo /> 기본값
              </button>
              <button onClick={saveLayout} disabled={!layoutDirty}
                style={{ padding: '6px 12px', fontSize: '13px', background: layoutDirty ? 'var(--accent)' : 'var(--bg-card)', color: layoutDirty ? '#fff' : 'var(--text-muted)', border: '1px solid var(--border-color)', borderRadius: '6px', cursor: layoutDirty ? 'pointer' : 'not-allowed' }}>
                <FaSave /> 저장
              </button>
            </>
          )}
          <button onClick={() => setEditMode(!editMode)}
            style={{ padding: '6px 12px', fontSize: '13px', background: editMode ? 'var(--accent)' : 'var(--bg-card)', color: editMode ? '#fff' : 'var(--text-default)', border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer' }}>
            {editMode ? '편집 종료' : '대시보드 편집'}
          </button>
        </div>
      </div>

      {/* 편집 모드 — 위젯 가시성 토글 */}
      {editMode && (
        <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '12px', marginBottom: '12px' }}>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '8px' }}>위젯 표시 / 숨김 (드래그하여 위치 변경, 모서리로 크기 조정)</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {WIDGETS.map((w) => {
              const item = layout.find((p) => p.widgetKey === w.key)
              const visible = item?.visible ?? true
              return (
                <button key={w.key}
                  onClick={() => toggleVisible(w.key)}
                  style={{
                    padding: '6px 10px', fontSize: '12px', cursor: 'pointer',
                    background: visible ? 'var(--accent-subtle, rgba(59,130,246,0.1))' : 'var(--bg-default)',
                    color: visible ? 'var(--accent)' : 'var(--text-muted)',
                    border: '1px solid var(--border-color)', borderRadius: '4px',
                    display: 'flex', alignItems: 'center', gap: '6px',
                  }}>
                  {visible ? <FaEye /> : <FaEyeSlash />}
                  {w.label}
                </button>
              )
            })}
          </div>
        </div>
      )}

      {/* 위젯 그리드 — 편집 모드에서만 드래그/리사이즈 가능 */}
      <GridLayout
        className="layout"
        layout={gridLayout}
        cols={12}
        rowHeight={40}
        width={1200}
        isDraggable={editMode}
        isResizable={editMode}
        onLayoutChange={onLayoutChange}
        margin={[12, 12]}
        containerPadding={[0, 0]}
        draggableCancel="a, button, input, textarea"
      >
        {gridLayout.map((g) => (
          <div key={g.i} style={{
            background: 'var(--bg-secondary)', borderRadius: '12px',
            border: editMode ? '2px dashed var(--accent)' : '1px solid var(--border-color)',
            overflow: 'auto', padding: editMode ? '4px' : 0,
          }}>
            {renderWidget(g.i)}
          </div>
        ))}
      </GridLayout>
    </>
  )
}

// ── 위젯 컴포넌트들 ───────────────────────────────────────────────────────

function HeroWidget({ greeting, username, health }: { greeting: string; username?: string; health: HealthData | null }) {
  return (
    <div style={{
      background: 'linear-gradient(135deg, var(--bg-secondary), var(--bg-tertiary))',
      borderRadius: '12px', padding: '24px', height: '100%',
    }}>
      <h1 style={{ fontSize: '20px', fontWeight: 700, marginBottom: '6px' }}>
        {greeting}, {username ?? 'Guest'}
      </h1>
      <p style={{ color: 'var(--text-sub)', fontSize: '13px', marginBottom: '12px' }}>
        AI-powered tools for Oracle DB & Java/Spring enterprise development
      </p>
      {health && (
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', fontSize: '12px' }}>
          <span style={{ color: 'var(--green)' }}>Server: {health.status}</span>
          <span style={{ color: 'var(--text-muted)' }}>Model: {health.claudeModel}</span>
          <span style={{ color: health.apiKeySet ? 'var(--green)' : 'var(--red)' }}>
            API Key: {health.apiKeySet ? 'Set' : 'Missing'}
          </span>
        </div>
      )}
    </div>
  )
}

function ToolsWidget() {
  return (
    <div style={{ padding: '16px', height: '100%', overflowY: 'auto' }}>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
        gap: '12px',
      }}>
        {toolCards.map((card) => {
          const Icon = card.icon
          return (
            <a key={card.path} href={card.path}
              style={{
                display: 'block', background: 'var(--bg-primary)',
                border: '1px solid var(--border-color)', borderRadius: '10px',
                padding: '16px', textDecoration: 'none', color: 'inherit',
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.borderColor = card.color }}
              onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'var(--border-color)' }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                <Icon style={{ fontSize: '18px', color: card.color }} />
                <span style={{ fontSize: '14px', fontWeight: 600 }}>{card.title}</span>
              </div>
              <p style={{ fontSize: '12px', color: 'var(--text-muted)', margin: 0 }}>{card.desc}</p>
            </a>
          )
        })}
      </div>
    </div>
  )
}

function TeamActivityWidget({ activities }: { activities: TeamActivity[] }) {
  if (!activities.length) {
    return <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>최근 팀 활동이 없습니다.</div>
  }
  return (
    <div style={{ padding: '16px', height: '100%', overflowY: 'auto' }}>
      <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', display: 'flex', alignItems: 'center', gap: '6px' }}>
        <FaUsers style={{ color: '#8b5cf6' }} /> 팀 활동 피드
        <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 400 }}>— {activities.length}건</span>
      </h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {activities.slice(0, 10).map((a) => {
          const statusIcon = a.reviewStatus === 'ACCEPTED'
            ? <FaCheckCircle style={{ color: 'var(--green)', fontSize: '11px' }} />
            : a.reviewStatus === 'REJECTED'
              ? <FaTimesCircle style={{ color: 'var(--red)', fontSize: '11px' }} />
              : <FaClock style={{ color: 'var(--yellow)', fontSize: '11px' }} />
          return (
            <a key={a.id} href="/review-requests" style={{
              display: 'flex', alignItems: 'center', gap: '8px',
              padding: '6px 10px', borderRadius: '6px',
              background: 'var(--bg-primary)', border: '1px solid var(--border-color)',
              textDecoration: 'none', color: 'inherit', fontSize: '12px',
            }}>
              {statusIcon}
              <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>{a.type}</span>
              <strong style={{ color: 'var(--accent)', fontWeight: 700, fontSize: '11px' }}>@{a.username}</strong>
              <span style={{ flex: 1, color: 'var(--text-sub)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {a.title || '(제목 없음)'}
              </span>
              <span style={{ color: 'var(--text-muted)', fontSize: '11px', flexShrink: 0 }}>
                {formatRelative(a.createdAt)}
              </span>
            </a>
          )
        })}
      </div>
    </div>
  )
}
