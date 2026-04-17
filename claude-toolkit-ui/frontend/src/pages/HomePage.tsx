import { useEffect, useState, useCallback, useMemo } from 'react'
import { useAuthStore } from '../stores/authStore'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'
import { formatRelative } from '../utils/date'
import {
  FaDatabase, FaFileAlt, FaComments, FaProjectDiagram,
  FaCode, FaHistory, FaChartBar, FaBug, FaUsers, FaCheckCircle, FaTimesCircle, FaClock,
  FaThLarge, FaSave, FaUndo, FaEye, FaEyeSlash, FaCog, FaTimes, FaPlus,
  FaSearch, FaTools, FaShieldAlt, FaCoins,
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

/**
 * v4.3.x — 도구 카드 카탈로그.
 *
 * 사용자가 "도구 카드 그리드" 위젯을 편집할 때 이 목록에서
 * 표시할 카드를 자유롭게 선택/제외할 수 있다.
 */
interface ToolCard {
  key: string         // 식별자 (configJson 에 저장됨)
  icon: any
  color: string
  title: string
  desc: string
  path: string
}
const ALL_TOOL_CARDS: ToolCard[] = [
  { key: 'sql-review',  icon: FaDatabase,        color: '#3b82f6', title: 'SQL 리뷰',         desc: 'SQL 쿼리 리뷰, 보안 감사, 성능 분석',     path: '/advisor' },
  { key: 'index-adv',   icon: FaSearch,          color: '#10b981', title: 'SQL 인덱스 시뮬레이션', desc: '메타데이터 기반 인덱스 활용 + DDL 추천', path: '/sql/index-advisor' },
  { key: 'chat',        icon: FaComments,        color: '#8b5cf6', title: 'AI 채팅',          desc: 'Claude와 자유롭게 대화하며 코드 질문',      path: '/chat' },
  { key: 'docgen',      icon: FaFileAlt,         color: '#10b981', title: '기술 문서',        desc: '자동 문서화, Javadoc, API 명세 생성',     path: '/docgen' },
  { key: 'pipeline',    icon: FaProjectDiagram,  color: '#8b5cf6', title: '분석 파이프라인',   desc: 'YAML 기반 다단계 분석 자동화',           path: '/pipelines' },
  { key: 'converter',   icon: FaCode,            color: '#10b981', title: '코드 변환',        desc: 'iBatis → MyBatis, Java 버전 변환',       path: '/converter' },
  { key: 'complexity',  icon: FaChartBar,        color: '#3b82f6', title: '복잡도 분석',      desc: '코드 복잡도 및 품질 메트릭 분석',         path: '/complexity' },
  { key: 'history',     icon: FaHistory,         color: '#f59e0b', title: '리뷰 이력',        desc: '분석 결과 저장, 비교, 내보내기',         path: '/history' },
  { key: 'log',         icon: FaBug,             color: '#06b6d4', title: '로그 분석',        desc: '로그 파일 분석, 보안 위협 탐지',         path: '/loganalyzer' },
  { key: 'workspace',   icon: FaTools,           color: '#f97316', title: '통합 워크스페이스',  desc: '다중 분석 동시 실행',                  path: '/workspace' },
  { key: 'cost-opt',    icon: FaCoins,           color: '#f59e0b', title: '비용 옵티마이저',   desc: '모델 추천 + 절감액 분석 (ADMIN)',       path: '/admin/cost-optimizer' },
  { key: 'security',    icon: FaShieldAlt,       color: '#ef4444', title: '데이터 마스킹',    desc: '민감정보 자동 탐지 + 마스킹 SQL',        path: '/maskgen' },
]
const DEFAULT_TOOL_KEYS = ['sql-review', 'index-adv', 'chat', 'docgen', 'pipeline', 'converter', 'complexity', 'history', 'log']

interface WidgetSpec {
  key: string
  label: string
  defaultW: number
  defaultH: number
  configurable?: boolean   // true면 위젯별 설정 가능 (도구 카드 선택 등)
}
const WIDGETS: WidgetSpec[] = [
  { key: 'hero',          label: '인사말 + 시스템 상태', defaultW: 12, defaultH: 4 },
  { key: 'tools',         label: '도구 카드 그리드',    defaultW: 12, defaultH: 8, configurable: true },
  { key: 'team-activity', label: '팀 활동 피드',       defaultW: 12, defaultH: 6 },
]

interface PersistedLayout {
  widgetKey: string
  x: number
  y: number
  w: number
  h: number
  visible: boolean
  configJson?: string
}

function defaultLayout(): PersistedLayout[] {
  let yCursor = 0
  return WIDGETS.map((w) => {
    const item: PersistedLayout = { widgetKey: w.key, x: 0, y: yCursor, w: w.defaultW, h: w.defaultH, visible: true }
    yCursor += w.defaultH
    return item
  })
}

/** configJson 안전 파싱 — 도구 위젯의 선택된 카드 키 목록 */
function parseToolKeys(configJson?: string): string[] {
  if (!configJson) return DEFAULT_TOOL_KEYS
  try {
    const parsed = JSON.parse(configJson)
    if (Array.isArray(parsed?.toolKeys)) return parsed.toolKeys
  } catch { /* ignore */ }
  return DEFAULT_TOOL_KEYS
}

export default function HomePage() {
  const user = useAuthStore((s) => s.user)
  const { data: health, get } = useApi<HealthData>({ showError: false })
  const [greeting, setGreeting] = useState('')
  const [teamActivity, setTeamActivity] = useState<TeamActivity[]>([])
  const toast = useToast()

  const [layout, setLayout] = useState<PersistedLayout[]>(defaultLayout())
  const [editMode, setEditMode] = useState(false)
  const [layoutDirty, setLayoutDirty] = useState(false)
  // v4.3.x: 위젯별 설정 모달 (현재 열린 widgetKey, null=닫힘)
  const [configWidget, setConfigWidget] = useState<string | null>(null)

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

    fetch('/api/v1/dashboard/layout', { credentials: 'include' })
      .then((r) => r.ok ? r.json() : null)
      .then((j) => {
        const saved = (j?.data ?? j) as PersistedLayout[]
        if (Array.isArray(saved) && saved.length > 0) {
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

  // v4.3.x 버그픽스: react-grid-layout 의 onLayoutChange 는 보이는 위젯만 전달함.
  //              보이지 않는(visible=false) 위젯은 prev 에서 가져와 보존해야 한다.
  //              (이전 코드는 next 만 매핑해서 숨긴 위젯이 layout 에서 사라져 다시 켤 수 없었음)
  const onLayoutChange = useCallback((next: Layout[]) => {
    setLayout((prev) => {
      const nextMap = new Map(next.map((n) => [n.i, n]))
      // 모든 prev 위젯을 순회 — 보이는 것은 next 에서, 숨긴 것은 prev 그대로
      const updated = prev.map((p) => {
        const n = nextMap.get(p.widgetKey)
        if (n) {
          // 보이는 위젯 — react-grid-layout 이 위치/크기 조정한 값 반영
          return { ...p, x: n.x, y: n.y, w: n.w, h: n.h }
        }
        // 숨긴 위젯 — prev 의 위치/크기/configJson 보존
        return p
      })
      return updated
    })
    // 실제 변경이 있었는지만 dirty 처리 (초기 fitView 호출 시 발생하는 변화 방지)
    setLayoutDirty((d) => d || next.length > 0)
  }, [])

  const toggleVisible = (key: string) => {
    setLayout((prev) => prev.map((p) => p.widgetKey === key ? { ...p, visible: !p.visible } : p))
    setLayoutDirty(true)
  }

  const updateWidgetConfig = (key: string, configJson: string) => {
    setLayout((prev) => prev.map((p) => p.widgetKey === key ? { ...p, configJson } : p))
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
  const gridLayout: Layout[] = useMemo(() =>
    layout.filter((p) => p.visible).map((p) => ({ i: p.widgetKey, x: p.x, y: p.y, w: p.w, h: p.h }))
  , [layout])

  // 도구 위젯의 현재 선택된 카드 목록
  const toolWidget = layout.find((p) => p.widgetKey === 'tools')
  const selectedToolKeys = parseToolKeys(toolWidget?.configJson)

  const renderWidget = (key: string) => {
    if (key === 'hero') return <HeroWidget greeting={greeting} username={user?.username} health={health} />
    if (key === 'tools') return <ToolsWidget selectedKeys={selectedToolKeys} />
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
          {layoutDirty && <span style={{ color: '#f59e0b' }}>· 저장되지 않은 변경</span>}
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

      {/* 편집 모드 — 위젯 가시성 토글 + 위젯별 설정 */}
      {editMode && (
        <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '12px', marginBottom: '12px' }}>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '8px' }}>
            위젯 표시 / 숨김 (드래그하여 위치 변경, 모서리로 크기 조정).
            ⚙️ 아이콘이 있는 위젯은 내부 항목을 커스터마이즈할 수 있습니다.
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {WIDGETS.map((w) => {
              const item = layout.find((p) => p.widgetKey === w.key)
              const visible = item?.visible ?? true
              return (
                <div key={w.key} style={{ display: 'flex', gap: '0' }}>
                  <button
                    onClick={() => toggleVisible(w.key)}
                    style={{
                      padding: '6px 10px', fontSize: '12px', cursor: 'pointer',
                      background: visible ? 'var(--accent-subtle, rgba(59,130,246,0.1))' : 'var(--bg-default)',
                      color: visible ? 'var(--accent)' : 'var(--text-muted)',
                      border: '1px solid var(--border-color)',
                      borderRadius: w.configurable ? '4px 0 0 4px' : '4px',
                      borderRight: w.configurable ? 'none' : '1px solid var(--border-color)',
                      display: 'flex', alignItems: 'center', gap: '6px',
                    }}>
                    {visible ? <FaEye /> : <FaEyeSlash />}
                    {w.label}
                  </button>
                  {w.configurable && (
                    <button
                      onClick={() => setConfigWidget(w.key)}
                      title="이 위젯 내부 항목 설정"
                      style={{
                        padding: '6px 10px', fontSize: '12px', cursor: 'pointer',
                        background: 'var(--bg-default)', color: 'var(--text-default)',
                        border: '1px solid var(--border-color)', borderRadius: '0 4px 4px 0',
                      }}>
                      <FaCog />
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* 위젯 그리드 */}
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

      {/* 위젯별 설정 모달 — 도구 카드 선택 */}
      {configWidget === 'tools' && (
        <ToolsConfigModal
          selectedKeys={selectedToolKeys}
          onClose={() => setConfigWidget(null)}
          onSave={(keys) => {
            updateWidgetConfig('tools', JSON.stringify({ toolKeys: keys }))
            setConfigWidget(null)
          }}
        />
      )}
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

function ToolsWidget({ selectedKeys }: { selectedKeys: string[] }) {
  const cards = ALL_TOOL_CARDS.filter((c) => selectedKeys.includes(c.key))
  if (cards.length === 0) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
        선택된 도구가 없습니다. 편집 모드에서 ⚙️ 아이콘을 눌러 카드를 추가하세요.
      </div>
    )
  }
  return (
    <div style={{ padding: '16px', height: '100%', overflowY: 'auto' }}>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
        gap: '12px',
      }}>
        {cards.map((card) => {
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

// ── 도구 카드 선택 모달 ────────────────────────────────────────────────────

function ToolsConfigModal({
  selectedKeys, onClose, onSave,
}: {
  selectedKeys: string[]
  onClose: () => void
  onSave: (keys: string[]) => void
}) {
  const [draft, setDraft] = useState<string[]>(selectedKeys)

  const toggle = (key: string) => {
    setDraft((prev) => prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key])
  }

  const move = (key: string, dir: -1 | 1) => {
    setDraft((prev) => {
      const idx = prev.indexOf(key)
      if (idx < 0) return prev
      const targetIdx = idx + dir
      if (targetIdx < 0 || targetIdx >= prev.length) return prev
      const next = [...prev]
      ;[next[idx], next[targetIdx]] = [next[targetIdx], next[idx]]
      return next
    })
  }

  const selectedCards = draft.map((k) => ALL_TOOL_CARDS.find((c) => c.key === k)).filter(Boolean) as ToolCard[]
  const availableCards = ALL_TOOL_CARDS.filter((c) => !draft.includes(c.key))

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 9999,
      }}>
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'var(--bg-primary)', border: '1px solid var(--border-color)',
          borderRadius: '12px', width: '720px', maxWidth: '95vw', maxHeight: '85vh',
          display: 'flex', flexDirection: 'column',
        }}>
        <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--border-color)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
            <FaCog /> 도구 카드 그리드 설정
          </h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', fontSize: '16px' }}>
            <FaTimes />
          </button>
        </div>

        <div style={{ padding: '16px 18px', flex: 1, overflowY: 'auto', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
          {/* 표시 중인 카드 (정렬 가능) */}
          <div>
            <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px' }}>
              📌 표시 중 ({selectedCards.length}개) — 위/아래 화살표로 순서 변경
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
              {selectedCards.map((c, i) => {
                const Icon = c.icon
                return (
                  <div key={c.key} style={{
                    display: 'flex', alignItems: 'center', gap: '6px',
                    padding: '6px 10px', borderRadius: '6px',
                    background: 'var(--bg-card)', border: `1px solid ${c.color}40`,
                    fontSize: '13px',
                  }}>
                    <Icon style={{ color: c.color, fontSize: '14px' }} />
                    <span style={{ flex: 1 }}>{c.title}</span>
                    <button onClick={() => move(c.key, -1)} disabled={i === 0}
                      style={{ background: 'none', border: 'none', cursor: i === 0 ? 'not-allowed' : 'pointer', opacity: i === 0 ? 0.3 : 1, fontSize: '11px' }}
                      title="위로">▲</button>
                    <button onClick={() => move(c.key, 1)} disabled={i === selectedCards.length - 1}
                      style={{ background: 'none', border: 'none', cursor: i === selectedCards.length - 1 ? 'not-allowed' : 'pointer', opacity: i === selectedCards.length - 1 ? 0.3 : 1, fontSize: '11px' }}
                      title="아래로">▼</button>
                    <button onClick={() => toggle(c.key)}
                      style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--red)' }}
                      title="제거">
                      <FaTimes />
                    </button>
                  </div>
                )
              })}
              {selectedCards.length === 0 && (
                <div style={{ padding: '16px', textAlign: 'center', fontSize: '12px', color: 'var(--text-muted)' }}>
                  표시 중인 카드가 없습니다.
                </div>
              )}
            </div>
          </div>

          {/* 추가 가능한 카드 */}
          <div>
            <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px' }}>
              ➕ 추가 가능 ({availableCards.length}개) — 클릭하여 추가
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
              {availableCards.map((c) => {
                const Icon = c.icon
                return (
                  <button key={c.key} onClick={() => toggle(c.key)}
                    style={{
                      display: 'flex', alignItems: 'center', gap: '6px',
                      padding: '6px 10px', borderRadius: '6px',
                      background: 'var(--bg-default)', border: '1px solid var(--border-color)',
                      cursor: 'pointer', textAlign: 'left', fontSize: '13px',
                      color: 'var(--text-default)',
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.borderColor = c.color }}
                    onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'var(--border-color)' }}
                  >
                    <FaPlus style={{ color: c.color, fontSize: '11px' }} />
                    <Icon style={{ color: c.color, fontSize: '14px' }} />
                    <span style={{ flex: 1 }}>{c.title}</span>
                  </button>
                )
              })}
              {availableCards.length === 0 && (
                <div style={{ padding: '16px', textAlign: 'center', fontSize: '12px', color: 'var(--text-muted)' }}>
                  모든 카드가 추가되었습니다.
                </div>
              )}
            </div>
          </div>
        </div>

        <div style={{ padding: '12px 18px', borderTop: '1px solid var(--border-color)', display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
          <button onClick={() => setDraft(DEFAULT_TOOL_KEYS)}
            style={{ padding: '6px 14px', fontSize: '13px', background: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer' }}>
            기본값
          </button>
          <button onClick={onClose}
            style={{ padding: '6px 14px', fontSize: '13px', background: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer' }}>
            취소
          </button>
          <button onClick={() => onSave(draft)}
            style={{ padding: '6px 14px', fontSize: '13px', background: 'var(--accent)', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600 }}>
            적용
          </button>
        </div>
      </div>
    </div>
  )
}
