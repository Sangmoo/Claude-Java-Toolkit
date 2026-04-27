import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react'
import ReactFlow, {
  Background, Controls, MiniMap, Handle, Position, MarkerType,
  type Node as RfNode, type Edge as RfEdge, type NodeProps,
} from 'reactflow'
import 'reactflow/dist/style.css'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaBoxes, FaCog, FaSync, FaTimes,
  FaSearch, FaChevronRight, FaProjectDiagram, FaStream, FaBookOpen, FaCopy, FaCheck,
} from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import MermaidChart from '../../components/common/MermaidChart'

/**
 * v4.5 — 패키지 개요 페이지 (Hybrid UX)
 *
 *  [📊 요약]  [🔗 ERD]  [🌊 풀 흐름도]  [📜 스토리]  ← 4탭 모두 활성
 *  ┌──────────┬──────────────────────┬──────────────┐
 *  │ 좌 트리   │ 중앙 — 탭별 컨텐츠     │ 우 — 상세 drawer│
 *  └──────────┴──────────────────────┴──────────────┘
 */

// ── 타입 ─────────────────────────────────────────────────────────────────

interface PackageSummary {
  packageName:     string
  classTotal:      number
  controllerCount: number
  serviceCount:    number
  daoCount:        number
  modelCount:      number
  otherCount:      number
  mybatisCount:    number
  tableCount:      number
  endpointCount:   number
}

interface JavaClassInfo {
  packageName: string
  className:   string
  relPath:     string
  type:        string
}

interface MyBatisStatement {
  namespace: string
  id:        string
  fullId:    string
  dml:       string
  file:      string
  line:      number
  tables?:   string[]
  snippet?:  string
}

interface ControllerEndpoint {
  url:        string
  httpMethod: string
  className:  string
  methodName: string
  file:       string
  line?:      number
  callees?:   string[]
}

interface PackageDetail {
  packageName:      string
  level:            number
  classTotal:       number
  controllerCount:  number
  serviceCount:     number
  daoCount:         number
  modelCount:       number
  mybatisCount:     number
  tableCount:       number
  endpointCount:    number
  classes:          JavaClassInfo[]
  mybatisStatements: MyBatisStatement[]
  endpoints:        ControllerEndpoint[]
  tables:           string[]
  externalDependencies: string[]
}

type DetailItem =
  | { kind: 'class';    data: JavaClassInfo }
  | { kind: 'mybatis';  data: MyBatisStatement }
  | { kind: 'endpoint'; data: ControllerEndpoint }

type TabKey = 'summary' | 'erd' | 'flow' | 'story'

// ── ERD 탭 응답 ────────────────────────────────────────────────────────

interface ErdColumn { name: string; dataType: string; nullable: boolean; pk: boolean }
interface ErdTable  { name: string; comment?: string; columns: ErdColumn[] }
interface ErdResponse {
  mermaid: string
  tableCount: number
  foreignKeyCount: number
  tables: ErdTable[]
  hitCounts: Record<string, number>
  warnings: string[]
  options: { columnDetail: boolean; prefixGrouping: boolean; heatmap: boolean }
}

// ── Story 탭 응답 ──────────────────────────────────────────────────────

interface StoryResponse {
  packageName: string
  markdown:    string
  error?:      string | null
  fromCache:   boolean
  cacheAgeMs:  number
  elapsedMs:   number
}

// ── Flow 탭 응답 ───────────────────────────────────────────────────────

interface FlowNode  {
  id: string; type: string; label: string
  file?: string; line?: number
  meta?: Record<string, string>
}
interface FlowEdge  { from: string; to: string; label?: string }
interface FlowResponse {
  packageName:     string
  nodes:           FlowNode[]
  edges:           FlowEdge[]
  analyzedTables:  string[]
  tablesTruncated: boolean
  nodesByType:     Record<string, number>
  warnings:        string[]
  fromCache:       boolean
  cacheAgeMs:      number
  elapsedMs:       number
}

// ── 노드 타입 시각 메타 ──────────────────────────────────────────────────

type FlowNodeType = 'ui' | 'controller' | 'service' | 'dao' | 'mybatis' | 'sp' | 'table'
const FLOW_TYPE_META: Record<FlowNodeType, { color: string; bg: string; icon: string; label: string; order: number }> = {
  ui:         { color: '#0ea5e9', bg: '#e0f2fe', icon: '🖥️', label: 'MiPlatform 화면', order: 1 },
  controller: { color: '#8b5cf6', bg: '#ede9fe', icon: '🎯', label: 'Controller',       order: 2 },
  service:    { color: '#10b981', bg: '#d1fae5', icon: '⚙️', label: 'Service',          order: 3 },
  dao:        { color: '#f59e0b', bg: '#fef3c7', icon: '🗂️', label: 'DAO',              order: 4 },
  mybatis:    { color: '#3b82f6', bg: '#dbeafe', icon: '📜', label: 'MyBatis SQL',      order: 5 },
  sp:         { color: '#ef4444', bg: '#fee2e2', icon: '🔁', label: 'Oracle SP',        order: 6 },
  table:      { color: '#6366f1', bg: '#e0e7ff', icon: '🗄️', label: 'DB 테이블',        order: 7 },
}

const DML_EDGE_COLOR: Record<string, string> = {
  INSERT: '#10b981', UPDATE: '#f59e0b', MERGE: '#8b5cf6', DELETE: '#ef4444', SELECT: '#3b82f6',
}
function getFlowEdgeStyle(label?: string): { color: string; dml: string | null } {
  if (!label) return { color: '#94a3b8', dml: null }
  const up = label.toUpperCase()
  for (const k of Object.keys(DML_EDGE_COLOR)) {
    if (new RegExp(`\\b${k}\\b`).test(up)) return { color: DML_EDGE_COLOR[k], dml: k }
  }
  return { color: '#94a3b8', dml: null }
}

// ── 레이어 타입별 색/라벨 ────────────────────────────────────────────────

const LAYER_META: Record<string, { color: string; label: string; icon: string }> = {
  controller: { color: '#8b5cf6', label: 'Controller', icon: '🎯' },
  service:    { color: '#10b981', label: 'Service',    icon: '⚙️' },
  dao:        { color: '#f59e0b', label: 'DAO/Mapper', icon: '🗂️' },
  model:      { color: '#6366f1', label: 'Model/DTO',  icon: '📦' },
  util:       { color: '#64748b', label: 'Util',       icon: '🔧' },
  config:     { color: '#0ea5e9', label: 'Config',     icon: '⚙️' },
  exception:  { color: '#ef4444', label: 'Exception',  icon: '⚠️' },
  other:      { color: '#94a3b8', label: 'Other',      icon: '📄' },
}

// ── 페이지 본체 ──────────────────────────────────────────────────────────

export default function PackageOverviewPage() {
  const toast = useToast()

  // URL 쿼리로 들어온 경우 (`?pkg=xxx`) — ProjectMap 에서 연결됨
  const initialPkg = typeof window !== 'undefined'
    ? new URLSearchParams(window.location.search).get('pkg')
    : null

  // 설정 상태
  const [level, setLevel]     = useState<number>(5)
  const [prefix, setPrefix]   = useState<string>('')
  const [settingsOpen, setSettingsOpen] = useState(false)

  // 데이터 상태
  const [packages, setPackages] = useState<PackageSummary[]>([])
  const [loading,  setLoading]  = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [indexerReady, setIndexerReady] = useState(true)
  const [lastScanMs, setLastScanMs] = useState<number>(0)

  // 선택 상태
  const [selectedPkg, setSelectedPkg] = useState<string | null>(null)
  const [detail, setDetail] = useState<PackageDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  // Drawer
  const [drawerItem, setDrawerItem] = useState<DetailItem | null>(null)

  // 탭
  const [activeTab, setActiveTab] = useState<TabKey>('summary')

  // 좌 트리 필터
  const [searchKw, setSearchKw] = useState('')

  // ── 초기 로드 ──────────────────────────────────────────────────────────

  const loadSettings = useCallback(async () => {
    try {
      const r = await fetch('/api/v1/package/settings', { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) {
        setLevel(d.data.currentLevel ?? 5)
        setPrefix(d.data.currentPrefix ?? '')
        setIndexerReady(!!d.data.indexerReady)
        setLastScanMs(d.data.lastScanMs ?? 0)
      }
    } catch (e) { console.warn('[Package] settings load failed', e) }
  }, [])

  const loadOverview = useCallback(async (lv: number, pf: string) => {
    setLoading(true)
    try {
      const url = `/api/v1/package/overview?level=${lv}&prefix=${encodeURIComponent(pf || '')}`
      const r = await fetch(url, { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) {
        setPackages(d.data.packages || [])
        setIndexerReady(!!d.data.indexerReady)
      } else {
        toast.error(d.error || 'overview 로드 실패')
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      toast.error(`overview 호출 실패: ${msg}`)
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => { loadSettings() }, [loadSettings])
  useEffect(() => { if (level) loadOverview(level, prefix) }, [level, prefix, loadOverview])

  // ?pkg=xxx 로 들어오면 overview 로드 직후 자동 선택 (1회만)
  useEffect(() => {
    if (!initialPkg || packages.length === 0) return
    if (selectedPkg === initialPkg) return
    if (packages.find(p => p.packageName === initialPkg)) {
      selectPackage(initialPkg)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialPkg, packages])

  // ── 상세 로드 ──────────────────────────────────────────────────────────

  const loadDetail = useCallback(async (pkg: string) => {
    setDetailLoading(true); setDetail(null); setDrawerItem(null)
    try {
      const url = `/api/v1/package/detail?name=${encodeURIComponent(pkg)}&level=${level}`
      const r = await fetch(url, { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data?.detail) setDetail(d.data.detail)
      else toast.error(d.error || '상세 로드 실패')
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      toast.error(`상세 호출 실패: ${msg}`)
    } finally { setDetailLoading(false) }
  }, [level, toast])

  const selectPackage = (pkg: string) => {
    setSelectedPkg(pkg)
    loadDetail(pkg)
  }

  // ── 인덱스 재빌드 ──────────────────────────────────────────────────────

  const refreshIndex = async () => {
    setRefreshing(true)
    try {
      const r = await fetch('/api/v1/package/refresh', { method: 'POST', credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) {
        toast.success(`재빌드 완료 — 클래스 ${d.data.javaClasses} / 패키지 ${d.data.javaPackages} (${d.data.elapsedMs}ms)`)
        loadSettings()
        loadOverview(level, prefix)
      } else toast.error(d.error || '재빌드 실패')
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      toast.error(`재빌드 호출 실패: ${msg}`)
    } finally { setRefreshing(false) }
  }

  // ── 좌 트리 필터링된 목록 ──────────────────────────────────────────────

  const filteredPackages = useMemo(() => {
    const kw = searchKw.trim().toLowerCase()
    if (!kw) return packages
    return packages.filter(p => p.packageName.toLowerCase().includes(kw))
  }, [packages, searchKw])

  // ── 렌더 ───────────────────────────────────────────────────────────────

  return (
    <div style={styles.page}>
      {/* 상단 바 */}
      <div style={styles.topBar}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <FaBoxes size={20} style={{ color: '#06b6d4' }} />
          <h2 style={{ margin: 0, fontSize: 18 }}>패키지 개요</h2>
          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
            Java 패키지 단위 요약 · ERD · 흐름도 (Level {level}{prefix ? ` · prefix "${prefix}"` : ''})
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {lastScanMs > 0 && (
            <span style={{ fontSize: 11, color: 'var(--text-muted)' }}
                  title={new Date(lastScanMs).toISOString()}>
              마지막 인덱스: {new Date(lastScanMs).toLocaleString('ko-KR')}
            </span>
          )}
          <button style={styles.iconBtn} onClick={() => setSettingsOpen(true)} title="패키지 레벨/prefix 설정">
            <FaCog /> 설정
          </button>
          <button style={styles.iconBtn} onClick={refreshIndex} disabled={refreshing}
                  title="Java 파일 인덱서 재빌드">
            <FaSync style={refreshing ? { animation: 'spin 1s linear infinite' } : undefined} />
            {refreshing ? ' 재빌드 중...' : ' 재빌드'}
          </button>
        </div>
      </div>

      {/* 탭 — Week 4: 전체 활성 (summary + erd + flow + story) */}
      <div style={styles.tabBar}>
        {(['summary','erd','flow','story'] as TabKey[]).map(tab => {
          const disabled = false
          const label = tab === 'summary' ? '📊 요약'
                     : tab === 'erd'     ? '🔗 ERD'
                     : tab === 'flow'    ? '🌊 풀 흐름도'
                     :                      '📜 스토리'
          return (
            <button
              key={tab}
              style={{
                ...styles.tabBtn,
                ...(activeTab === tab ? styles.tabBtnActive : {}),
                ...(disabled ? styles.tabBtnDisabled : {}),
              }}
              onClick={() => !disabled && setActiveTab(tab)}
              disabled={disabled}
              title={disabled ? '다음 주 릴리스 예정' : ''}
            >{label}{disabled ? ' (준비중)' : ''}</button>
          )
        })}
      </div>

      {/* 본문 */}
      <div style={styles.body}>
        {/* 좌 트리 */}
        <div style={styles.leftPanel}>
          <div style={styles.searchBox}>
            <FaSearch size={11} style={{ color: 'var(--text-muted)' }} />
            <input
              type="text"
              placeholder="패키지 검색..."
              value={searchKw}
              onChange={e => setSearchKw(e.target.value)}
              style={styles.searchInput}
            />
          </div>
          <div style={styles.treeHeader}>
            {loading ? '로딩...' : `패키지 ${filteredPackages.length}개`}
          </div>
          <div style={styles.treeList}>
            {filteredPackages.map(p => (
              <div
                key={p.packageName}
                style={{
                  ...styles.treeItem,
                  background: selectedPkg === p.packageName ? 'var(--bg-secondary)' : 'transparent',
                  borderLeft: selectedPkg === p.packageName ? '3px solid #06b6d4' : '3px solid transparent',
                }}
                onClick={() => selectPackage(p.packageName)}
              >
                <div style={styles.treeItemName}>{p.packageName}</div>
                <div style={styles.treeItemMeta}>
                  📦 {p.classTotal} · 📋 {p.mybatisCount} · 🎯 {p.endpointCount}
                </div>
              </div>
            ))}
            {!loading && filteredPackages.length === 0 && (
              <div style={styles.emptyHint}>
                {indexerReady
                  ? (searchKw ? '검색 결과 없음' : '패키지 없음 — scanPath 확인 후 [재빌드]')
                  : 'Java 파일 인덱스가 아직 준비되지 않았습니다. [재빌드] 버튼으로 시작하세요.'}
              </div>
            )}
          </div>
        </div>

        {/* 중앙 본문 */}
        <div style={styles.centerPanel}>
          {activeTab === 'summary' && (
            <SummaryTabContent
              packages={packages}
              selectedPkg={selectedPkg}
              detail={detail}
              detailLoading={detailLoading}
              onOpen={setDrawerItem}
              onSelectPackage={selectPackage}
            />
          )}
          {activeTab === 'erd' && (
            <ErdTabContent
              selectedPkg={selectedPkg}
              level={level}
            />
          )}
          {activeTab === 'flow' && (
            <FlowTabContent
              selectedPkg={selectedPkg}
              level={level}
            />
          )}
          {activeTab === 'story' && (
            <StoryTabContent
              selectedPkg={selectedPkg}
              level={level}
            />
          )}
        </div>

        {/* 우 drawer */}
        {drawerItem && (
          <DetailDrawer item={drawerItem} onClose={() => setDrawerItem(null)} />
        )}
      </div>

      {/* 설정 모달 */}
      {settingsOpen && (
        <PackageSettingsModal
          currentLevel={level}
          currentPrefix={prefix}
          onSave={(lv, pf) => {
            setLevel(lv); setPrefix(pf); setSettingsOpen(false)
          }}
          onClose={() => setSettingsOpen(false)}
        />
      )}
    </div>
  )
}

// ── 📊 요약 탭 콘텐츠 ────────────────────────────────────────────────────

function SummaryTabContent({
  packages, selectedPkg, detail, detailLoading, onOpen, onSelectPackage,
}: {
  packages: PackageSummary[]
  selectedPkg: string | null
  detail: PackageDetail | null
  detailLoading: boolean
  onOpen: (i: DetailItem) => void
  onSelectPackage: (pkg: string) => void
}) {
  if (!selectedPkg) {
    return (
      <div style={{ padding: 20 }}>
        <h3 style={{ margin: '0 0 12px 0', fontSize: 14 }}>📊 프로젝트 전체 요약</h3>
        <div style={styles.statsGrid}>
          <Stat label="패키지" value={String(packages.length)} />
          <Stat label="총 클래스" value={String(packages.reduce((s, p) => s + p.classTotal, 0))} />
          <Stat label="Controller" value={String(packages.reduce((s, p) => s + p.controllerCount, 0))} />
          <Stat label="Service" value={String(packages.reduce((s, p) => s + p.serviceCount, 0))} />
          <Stat label="DAO" value={String(packages.reduce((s, p) => s + p.daoCount, 0))} />
          <Stat label="MyBatis" value={String(packages.reduce((s, p) => s + p.mybatisCount, 0))} />
        </div>
        <h4 style={{ fontSize: 13, marginTop: 20, marginBottom: 8 }}>🏆 활동성 Top 패키지 (클릭해서 상세)</h4>
        <div style={styles.topList}>
          {packages.slice(0, 12).map(p => (
            <div key={p.packageName} style={styles.topItem}
                 onClick={() => onSelectPackage(p.packageName)}>
              <div style={{ flex: 1, fontFamily: 'monospace', fontSize: 12 }}>{p.packageName}</div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                📦 {p.classTotal} · 📋 {p.mybatisCount} · 🎯 {p.endpointCount}
              </div>
              <FaChevronRight size={10} style={{ color: 'var(--text-muted)' }} />
            </div>
          ))}
        </div>
      </div>
    )
  }

  if (detailLoading || !detail) {
    return <div style={{ padding: 20, color: 'var(--text-muted)' }}>상세 불러오는 중...</div>
  }

  return (
    <div style={{ padding: 16, overflowY: 'auto' }}>
      <h3 style={{ margin: '0 0 10px 0', fontSize: 14, fontFamily: 'monospace' }}>
        📦 {detail.packageName}
      </h3>

      {/* 통계 카드 */}
      <div style={styles.statsGrid}>
        <Stat label="클래스" value={String(detail.classTotal)} />
        <Stat label="Controller" value={String(detail.controllerCount)} color={LAYER_META.controller.color} />
        <Stat label="Service" value={String(detail.serviceCount)} color={LAYER_META.service.color} />
        <Stat label="DAO" value={String(detail.daoCount)} color={LAYER_META.dao.color} />
        <Stat label="Model" value={String(detail.modelCount)} color={LAYER_META.model.color} />
        <Stat label="MyBatis" value={String(detail.mybatisCount)} />
        <Stat label="테이블" value={String(detail.tableCount)} />
        <Stat label="Endpoint" value={String(detail.endpointCount)} />
      </div>

      {/* 테이블 목록 */}
      {detail.tables.length > 0 && (
        <Section title={`🗄 연관 테이블 (${detail.tables.length})`}>
          <div style={styles.chipRow}>
            {detail.tables.map(t => (
              <span key={t} style={styles.chip}>{t}</span>
            ))}
          </div>
        </Section>
      )}

      {/* Controller */}
      {detail.endpoints.length > 0 && (
        <Section title={`🎯 Controller Endpoint (${detail.endpoints.length})`}>
          {detail.endpoints.slice(0, 20).map((ep, i) => (
            <Row key={i}
                 onClick={() => onOpen({ kind: 'endpoint', data: ep })}>
              <span style={styles.httpBadge}>{ep.httpMethod}</span>
              <span style={styles.rowMono}>{ep.url}</span>
              <span style={styles.rowHint}>— {ep.className}.{ep.methodName}</span>
            </Row>
          ))}
          {detail.endpoints.length > 20 && <div style={styles.moreHint}>... 외 {detail.endpoints.length - 20}건</div>}
        </Section>
      )}

      {/* MyBatis */}
      {detail.mybatisStatements.length > 0 && (
        <Section title={`📋 MyBatis Statement (${detail.mybatisStatements.length})`}>
          {detail.mybatisStatements.slice(0, 20).map((st, i) => (
            <Row key={i}
                 onClick={() => onOpen({ kind: 'mybatis', data: st })}>
              <span style={{ ...styles.dmlBadge, background: dmlColor(st.dml) }}>{st.dml}</span>
              <span style={styles.rowMono}>{st.fullId}</span>
              {st.tables && st.tables.length > 0 && (
                <span style={styles.rowHint}>→ {st.tables.join(', ')}</span>
              )}
            </Row>
          ))}
          {detail.mybatisStatements.length > 20 && <div style={styles.moreHint}>... 외 {detail.mybatisStatements.length - 20}건</div>}
        </Section>
      )}

      {/* Class 레이어별 */}
      <Section title={`📦 클래스 (${detail.classes.length})`}>
        {(['controller','service','dao','model','util','config','exception','other'] as const).map(type => {
          const list = detail.classes.filter(c => c.type === type)
          if (list.length === 0) return null
          const meta = LAYER_META[type]
          return (
            <div key={type} style={{ marginBottom: 8 }}>
              <div style={{ ...styles.layerHeader, color: meta.color }}>
                {meta.icon} {meta.label} ({list.length})
              </div>
              {list.slice(0, 15).map((c, i) => (
                <Row key={i} onClick={() => onOpen({ kind: 'class', data: c })}>
                  <span style={styles.rowMono}>{c.className}</span>
                  <span style={styles.rowHint}>{c.relPath}</span>
                </Row>
              ))}
              {list.length > 15 && <div style={styles.moreHint}>... 외 {list.length - 15}건</div>}
            </div>
          )
        })}
      </Section>

      {/* 외부 의존 */}
      {detail.externalDependencies.length > 0 && (
        <Section title={`🔗 외부 패키지 의존 (${detail.externalDependencies.length})`}>
          <div style={styles.chipRow}>
            {detail.externalDependencies.map(d => (
              <span key={d} style={{ ...styles.chip, fontFamily: 'monospace' }}>{d}</span>
            ))}
          </div>
        </Section>
      )}
    </div>
  )
}

// ── 🔗 ERD 탭 콘텐츠 ─────────────────────────────────────────────────────

function ErdTabContent({ selectedPkg, level }: { selectedPkg: string | null; level: number }) {
  const toast = useToast()
  const [loading, setLoading] = useState(false)
  const [erd, setErd] = useState<ErdResponse | null>(null)

  // 옵션 (사용자가 토글 가능)
  const [columnDetail,   setColumnDetail]   = useState(false)
  const [prefixGrouping, setPrefixGrouping] = useState(true)
  const [heatmap,        setHeatmap]        = useState(true)

  const loadErd = useCallback(async () => {
    if (!selectedPkg) return
    setLoading(true)
    try {
      const qs = new URLSearchParams({
        name:           selectedPkg,
        level:          String(level),
        columnDetail:   String(columnDetail),
        prefixGrouping: String(prefixGrouping),
        heatmap:        String(heatmap),
      })
      const r = await fetch(`/api/v1/package/erd?${qs.toString()}`, { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) setErd(d.data as ErdResponse)
      else toast.error(d.error || 'ERD 로드 실패')
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      toast.error(`ERD 호출 실패: ${msg}`)
    } finally { setLoading(false) }
  }, [selectedPkg, level, columnDetail, prefixGrouping, heatmap, toast])

  useEffect(() => { loadErd() }, [loadErd])

  if (!selectedPkg) {
    return (
      <div style={styles.comingSoonBox}>
        <FaProjectDiagram size={48} style={{ color: 'var(--text-muted)', opacity: 0.4 }} />
        <p style={{ fontSize: 13, fontWeight: 600, marginTop: 10 }}>
          좌측에서 패키지를 선택하면 연관 테이블의 ERD가 표시됩니다
        </p>
      </div>
    )
  }

  const hitEntries: [string, number][] = erd
    ? Object.entries(erd.hitCounts).sort((a, b) => b[1] - a[1])
    : []
  const maxHit = hitEntries.length ? hitEntries[0][1] : 0

  return (
    <div style={{ padding: 16, overflowY: 'auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <h3 style={{ margin: 0, fontSize: 14, fontFamily: 'monospace' }}>
          🔗 {selectedPkg} — ERD
        </h3>
        <div style={{ display: 'flex', gap: 6 }}>
          <ToggleChip label="컬럼 상세"    active={columnDetail}   onChange={setColumnDetail} />
          <ToggleChip label="Prefix 그룹"  active={prefixGrouping} onChange={setPrefixGrouping} />
          <ToggleChip label="빈도 히트맵"  active={heatmap}        onChange={setHeatmap} />
        </div>
      </div>

      {loading && (
        <div style={{ padding: 30, textAlign: 'center', color: 'var(--text-muted)' }}>
          Oracle 메타데이터 조회 중...
        </div>
      )}

      {!loading && erd && (
        <>
          {/* 통계 */}
          <div style={styles.statsGrid}>
            <Stat label="테이블"   value={String(erd.tableCount)} />
            <Stat label="FK 관계"  value={String(erd.foreignKeyCount)} />
            <Stat label="컬럼 합"  value={String(erd.tables.reduce((s, t) => s + (t.columns?.length ?? 0), 0))} />
            <Stat label="Hotspot" value={maxHit ? String(maxHit) : '-'} />
          </div>

          {/* 경고 */}
          {erd.warnings && erd.warnings.length > 0 && (
            <div style={styles.warnBox}>
              <strong>⚠ 주의</strong>
              <ul style={{ margin: '4px 0 0 18px', padding: 0, fontSize: 11 }}>
                {erd.warnings.map((w, i) => <li key={i}>{w}</li>)}
              </ul>
            </div>
          )}

          {/* Mermaid ERD */}
          {erd.mermaid && erd.tableCount > 0 && (
            <Section title={`📐 Mermaid ERD`}>
              <div style={styles.mermaidBox}>
                <MermaidChart chart={erd.mermaid} />
              </div>
              <details style={{ marginTop: 6 }}>
                <summary style={{ fontSize: 11, color: 'var(--text-muted)', cursor: 'pointer' }}>
                  📋 Mermaid 원본 소스 보기
                </summary>
                <pre style={styles.snippet}>{erd.mermaid}</pre>
              </details>
            </Section>
          )}

          {/* 빈도 히트맵 테이블 */}
          {heatmap && hitEntries.length > 0 && (
            <Section title={`🌡 접근 빈도 히트맵 (MyBatis 기준)`}>
              <div style={styles.heatTableWrap}>
                <table style={styles.heatTable}>
                  <thead>
                    <tr>
                      <th style={styles.heatTh}>테이블</th>
                      <th style={{ ...styles.heatTh, width: 70, textAlign: 'center' }}>Hits</th>
                      <th style={styles.heatTh}>강도</th>
                    </tr>
                  </thead>
                  <tbody>
                    {hitEntries.map(([tName, hit]) => {
                      const r = maxHit > 0 ? hit / maxHit : 0
                      const color = r >= 0.66 ? '#ef4444' : r >= 0.33 ? '#f59e0b' : '#94a3b8'
                      return (
                        <tr key={tName}>
                          <td style={{ ...styles.heatTd, fontFamily: 'monospace' }}>{tName}</td>
                          <td style={{ ...styles.heatTd, textAlign: 'center', fontWeight: 600 }}>{hit}</td>
                          <td style={styles.heatTd}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                              <div style={{
                                width: `${Math.max(12, r * 100)}%`, height: 10,
                                background: color, borderRadius: 2,
                              }}/>
                              <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                                {(r * 100).toFixed(0)}%
                              </span>
                            </div>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </Section>
          )}

          {/* 테이블 상세 리스트 (컬럼 상세 OFF 시 요약용) */}
          {!columnDetail && erd.tables.length > 0 && (
            <Section title={`📑 테이블 요약 (${erd.tables.length})`}>
              {erd.tables.slice(0, 30).map(t => (
                <div key={t.name} style={styles.row}>
                  <span style={styles.rowMono}>{t.name}</span>
                  <span style={styles.rowHint}>{t.columns?.length ?? 0} cols</span>
                  {t.comment && <span style={styles.rowHint}>— {t.comment}</span>}
                </div>
              ))}
              {erd.tables.length > 30 && (
                <div style={styles.moreHint}>... 외 {erd.tables.length - 30}개</div>
              )}
            </Section>
          )}
        </>
      )}

      {!loading && erd && erd.tableCount === 0 && (
        <div style={styles.emptyHint}>
          이 패키지가 참조하는 테이블을 찾지 못했습니다.
          MyBatis 인덱스가 아직 준비되지 않았거나, 패키지에 DAO가 없는 경우일 수 있습니다.
        </div>
      )}
    </div>
  )
}

function ToggleChip({ label, active, onChange }: {
  label: string; active: boolean; onChange: (v: boolean) => void
}) {
  return (
    <button
      style={{
        padding: '3px 10px', fontSize: 11, fontWeight: 600,
        background: active ? '#06b6d4' : 'var(--bg-card)',
        color: active ? '#ffffff' : 'var(--text-primary)',
        border: `1px solid ${active ? '#06b6d4' : 'var(--border-color)'}`,
        borderRadius: 12, cursor: 'pointer',
      }}
      onClick={() => onChange(!active)}
    >{label}</button>
  )
}

// ── 🌊 풀 흐름도 탭 콘텐츠 ────────────────────────────────────────────────

function FlowDiagramNode({ data }: NodeProps<{ raw: FlowNode; onSelect: (n: FlowNode) => void }>) {
  const { raw } = data
  const meta = FLOW_TYPE_META[(raw.type as FlowNodeType)] ?? FLOW_TYPE_META.service
  return (
    <div
      onClick={() => data.onSelect(raw)}
      style={{
        background: 'var(--bg-card, #fff)',
        border: `2px solid ${meta.color}`,
        borderLeft: `8px solid ${meta.color}`,
        borderRadius: 8,
        padding: '6px 10px',
        minWidth: 180, maxWidth: 260,
        boxShadow: '0 2px 6px rgba(0,0,0,0.08)',
        cursor: 'pointer',
        fontSize: 11,
      }}
      title="클릭 → 상세"
    >
      <Handle type="target" position={Position.Left} style={{ background: meta.color }} />
      <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginBottom: 2 }}>
        <span style={{ fontSize: 14 }}>{meta.icon}</span>
        <strong style={{ color: meta.color, fontSize: 10, textTransform: 'uppercase' }}>
          {meta.label}
        </strong>
      </div>
      <div style={{ fontFamily: 'monospace', color: 'var(--text-primary)', wordBreak: 'break-all' }}>
        {raw.label}
      </div>
      <Handle type="source" position={Position.Right} style={{ background: meta.color }} />
    </div>
  )
}

const FLOW_NODE_TYPES = { flow: FlowDiagramNode }

function FlowTabContent({ selectedPkg, level }: { selectedPkg: string | null; level: number }) {
  const toast = useToast()
  const [loading, setLoading]   = useState(false)
  const [flow, setFlow]         = useState<FlowResponse | null>(null)
  const [selected, setSelected] = useState<FlowNode | null>(null)

  // 타입 필터
  const [visibleTypes, setVisibleTypes] = useState<Set<FlowNodeType>>(
    () => new Set<FlowNodeType>(['ui', 'controller', 'service', 'dao', 'mybatis', 'sp', 'table'])
  )

  const loadFlow = useCallback(async (fresh: boolean) => {
    if (!selectedPkg) return
    setLoading(true); setFlow(null); setSelected(null)
    try {
      const qs = new URLSearchParams({
        name:  selectedPkg,
        level: String(level),
        fresh: String(fresh),
      })
      const r = await fetch(`/api/v1/package/flow?${qs.toString()}`, { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) setFlow(d.data as FlowResponse)
      else toast.error(d.error || 'Flow 로드 실패')
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      toast.error(`Flow 호출 실패: ${msg}`)
    } finally { setLoading(false) }
  }, [selectedPkg, level, toast])

  useEffect(() => { loadFlow(false) }, [loadFlow])

  // ReactFlow 레이아웃: type 별 column
  const { rfNodes, rfEdges } = useMemo(() => {
    if (!flow || flow.nodes.length === 0) return { rfNodes: [] as RfNode[], rfEdges: [] as RfEdge[] }

    const COL_W = 300, ROW_H = 90
    const grouped: Record<FlowNodeType, FlowNode[]> = {
      ui: [], controller: [], service: [], dao: [], mybatis: [], sp: [], table: [],
    }
    for (const n of flow.nodes) {
      const t = (n.type as FlowNodeType)
      if (grouped[t]) grouped[t].push(n)
      else grouped.service.push(n)
    }
    const orderedTypes = (Object.keys(grouped) as FlowNodeType[])
      .sort((a, b) => FLOW_TYPE_META[a].order - FLOW_TYPE_META[b].order)
      .filter(t => grouped[t].length > 0 && visibleTypes.has(t))

    const visibleNodeIds = new Set<string>()
    const nodes: RfNode[] = []
    orderedTypes.forEach((type, colIdx) => {
      grouped[type].forEach((n, rowIdx) => {
        visibleNodeIds.add(n.id)
        nodes.push({
          id: n.id, type: 'flow',
          position: { x: colIdx * COL_W, y: rowIdx * ROW_H },
          data: { raw: n, onSelect: setSelected },
          sourcePosition: Position.Right,
          targetPosition: Position.Left,
        })
      })
    })

    const edges: RfEdge[] = flow.edges
      .filter(e => visibleNodeIds.has(e.from) && visibleNodeIds.has(e.to))
      .map((e, i) => {
        const { color, dml } = getFlowEdgeStyle(e.label)
        return {
          id: `e${i}-${e.from}-${e.to}`,
          source: e.from, target: e.to,
          type: 'smoothstep',
          label: e.label || '',
          animated: !!dml,
          style: { stroke: color, strokeWidth: dml ? 2 : 1.5 },
          markerEnd: { type: MarkerType.ArrowClosed, color, width: 16, height: 16 },
          labelShowBg: true,
          labelBgPadding: [5, 2] as [number, number],
          labelBgBorderRadius: 3,
          labelStyle: {
            fontSize: 10, fontWeight: dml ? 700 : 500,
            fill: dml ? '#ffffff' : 'var(--text-primary)',
          },
          labelBgStyle: {
            fill: dml ? color : 'var(--bg-card, #ffffff)',
            fillOpacity: 0.95, stroke: color, strokeWidth: dml ? 0 : 1,
          },
        }
      })
    return { rfNodes: nodes, rfEdges: edges }
  }, [flow, visibleTypes])

  if (!selectedPkg) {
    return (
      <div style={styles.comingSoonBox}>
        <FaStream size={48} style={{ color: 'var(--text-muted)', opacity: 0.4 }} />
        <p style={{ fontSize: 13, fontWeight: 600, marginTop: 10 }}>
          좌측에서 패키지를 선택하면 풀 흐름도가 표시됩니다
        </p>
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
      {/* 상단 툴바 */}
      <div style={{
        padding: '8px 16px', borderBottom: '1px solid var(--border-color)',
        display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap',
        background: 'var(--bg-card)',
      }}>
        <span style={{ fontSize: 12, fontWeight: 600 }}>🌊 풀 흐름도</span>
        {flow && (
          <>
            <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
              · 테이블 {flow.analyzedTables.length}{flow.tablesTruncated ? '(truncated)' : ''}
              · 노드 {flow.nodes.length}
              · 엣지 {flow.edges.length}
              · {flow.fromCache ? `캐시 (${Math.round(flow.cacheAgeMs / 1000)}s ago)` : `${flow.elapsedMs}ms`}
            </span>
          </>
        )}
        <div style={{ flex: 1 }} />
        {(['ui','controller','service','dao','mybatis','sp','table'] as FlowNodeType[]).map(t => {
          const meta = FLOW_TYPE_META[t]
          const count = flow?.nodesByType?.[t] ?? 0
          if (count === 0) return null
          const active = visibleTypes.has(t)
          return (
            <button key={t}
              style={{
                padding: '3px 8px', fontSize: 10, fontWeight: 600,
                background: active ? meta.color : 'var(--bg-secondary)',
                color: active ? '#ffffff' : 'var(--text-muted)',
                border: `1px solid ${active ? meta.color : 'var(--border-color)'}`,
                borderRadius: 12, cursor: 'pointer',
              }}
              onClick={() => {
                const next = new Set(visibleTypes)
                if (active) next.delete(t); else next.add(t)
                setVisibleTypes(next)
              }}
            >{meta.icon} {meta.label} ({count})</button>
          )
        })}
        <button style={styles.iconBtn} onClick={() => loadFlow(true)} disabled={loading}>
          <FaSync /> {loading ? '분석중...' : '재분석'}
        </button>
      </div>

      {/* 경고 */}
      {flow?.warnings && flow.warnings.length > 0 && (
        <div style={{ ...styles.warnBox, margin: '8px 16px 0' }}>
          <strong>⚠ 주의 ({flow.warnings.length})</strong>
          <ul style={{ margin: '4px 0 0 18px', padding: 0, fontSize: 11 }}>
            {flow.warnings.slice(0, 5).map((w, i) => <li key={i}>{w}</li>)}
            {flow.warnings.length > 5 && <li>... 외 {flow.warnings.length - 5}건</li>}
          </ul>
        </div>
      )}

      {/* 다이어그램 본문 */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', position: 'relative' }}>
        {loading && (
          <div style={{
            position: 'absolute', inset: 0, display: 'flex',
            alignItems: 'center', justifyContent: 'center',
            background: 'rgba(0,0,0,0.35)', zIndex: 5,
            flexDirection: 'column', gap: 8, color: '#fff',
          }}>
            <FaSync style={{ animation: 'spin 1s linear infinite', fontSize: 24 }} />
            <div style={{ fontSize: 12 }}>
              연관 테이블 전체 분석 중... (패키지당 수 초~수 십 초)
            </div>
          </div>
        )}
        {flow && flow.nodes.length > 0 ? (
          <div style={{ flex: 1 }}>
            <ReactFlow
              nodes={rfNodes}
              edges={rfEdges}
              nodeTypes={FLOW_NODE_TYPES}
              fitView
              proOptions={{ hideAttribution: true }}
              nodesDraggable
              nodesConnectable={false}
              minZoom={0.15}
              maxZoom={2}
            >
              <Background />
              <Controls showInteractive={false} />
              <MiniMap
                pannable zoomable
                nodeColor={(n: RfNode) => {
                  const raw = (n.data as { raw?: FlowNode })?.raw
                  const t = (raw?.type as FlowNodeType) ?? 'service'
                  return FLOW_TYPE_META[t]?.color ?? '#94a3b8'
                }}
              />
            </ReactFlow>
          </div>
        ) : (
          !loading && (
            <div style={styles.emptyHint}>
              {flow ? '표시할 노드가 없습니다.' : '분석 결과가 없습니다.'}
            </div>
          )
        )}

        {/* 노드 선택 drawer */}
        {selected && (
          <div style={{
            width: 360, borderLeft: '1px solid var(--border-color)',
            background: 'var(--bg-secondary)', display: 'flex', flexDirection: 'column',
          }}>
            <div style={styles.drawerHeader}>
              <div>
                <div style={{ fontSize: 10, textTransform: 'uppercase',
                              color: FLOW_TYPE_META[(selected.type as FlowNodeType)]?.color ?? 'var(--text-muted)' }}>
                  {FLOW_TYPE_META[(selected.type as FlowNodeType)]?.icon} {FLOW_TYPE_META[(selected.type as FlowNodeType)]?.label ?? selected.type}
                </div>
                <div style={{ fontFamily: 'monospace', fontSize: 13, marginTop: 2, wordBreak: 'break-all' }}>
                  {selected.label}
                </div>
              </div>
              <button style={styles.closeBtn} onClick={() => setSelected(null)}><FaTimes /></button>
            </div>
            <div style={styles.drawerBody}>
              {selected.file && <Kv k="파일" v={`${selected.file}${selected.line ? `:${selected.line}` : ''}`} />}
              {selected.meta && Object.entries(selected.meta)
                .filter(([k]) => k !== 'snippet')
                .map(([k, v]) => <Kv key={k} k={k} v={String(v)} />)}
              {selected.meta?.snippet && (
                <div>
                  <div style={{ ...styles.k, marginTop: 10, marginBottom: 4 }}>SQL/소스 발췌</div>
                  <pre style={styles.snippet}>{selected.meta.snippet}</pre>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

// ── 📜 스토리 탭 콘텐츠 ──────────────────────────────────────────────────

function StoryTabContent({ selectedPkg, level }: { selectedPkg: string | null; level: number }) {
  const toast = useToast()
  const [loading, setLoading] = useState(false)
  const [story,   setStory]   = useState<StoryResponse | null>(null)
  const [copied,  setCopied]  = useState(false)

  const loadStory = useCallback(async (fresh: boolean) => {
    if (!selectedPkg) return
    setLoading(true); setStory(null); setCopied(false)
    try {
      const qs = new URLSearchParams({
        name:  selectedPkg,
        level: String(level),
        fresh: String(fresh),
      })
      const r = await fetch(`/api/v1/package/story?${qs.toString()}`, { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) setStory(d.data as StoryResponse)
      else toast.error(d.error || '스토리 로드 실패')
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      toast.error(`스토리 호출 실패: ${msg}`)
    } finally { setLoading(false) }
  }, [selectedPkg, level, toast])

  useEffect(() => { loadStory(false) }, [loadStory])

  const copyMarkdown = async () => {
    if (!story?.markdown) return
    try {
      await navigator.clipboard.writeText(story.markdown)
      setCopied(true); toast.success('복사됨')
      setTimeout(() => setCopied(false), 1500)
    } catch {
      const ta = document.createElement('textarea')
      ta.value = story.markdown; ta.style.position = 'fixed'
      document.body.appendChild(ta); ta.select()
      try { document.execCommand('copy'); toast.success('복사됨'); setCopied(true); setTimeout(() => setCopied(false), 1500) }
      catch { toast.error('복사 실패') }
      document.body.removeChild(ta)
    }
  }

  if (!selectedPkg) {
    return (
      <div style={styles.comingSoonBox}>
        <FaBookOpen size={48} style={{ color: 'var(--text-muted)', opacity: 0.4 }} />
        <p style={{ fontSize: 13, fontWeight: 600, marginTop: 10 }}>
          좌측에서 패키지를 선택하면 AI가 스토리로 설명합니다
        </p>
        <p style={{ fontSize: 11, color: 'var(--text-muted)' }}>
          신입 친화적 어조 · 업무 맥락 추정 · 6개 섹션 자동 생성
        </p>
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
      {/* 툴바 */}
      <div style={{
        padding: '8px 16px', borderBottom: '1px solid var(--border-color)',
        display: 'flex', alignItems: 'center', gap: 8,
        background: 'var(--bg-card)',
      }}>
        <FaBookOpen size={14} />
        <span style={{ fontSize: 12, fontWeight: 600 }}>📜 AI 스토리</span>
        <span style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'monospace' }}>
          {selectedPkg}
        </span>
        {story && (
          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
            · {story.fromCache ? `캐시 (${Math.round(story.cacheAgeMs / 1000)}s ago)` : `${story.elapsedMs}ms`}
          </span>
        )}
        <div style={{ flex: 1 }} />
        {story?.markdown && (
          <button
            style={copied ? styles.btnPrimary : styles.iconBtn}
            onClick={copyMarkdown}
          >
            {copied ? <><FaCheck size={11} /> 복사됨</> : <><FaCopy size={11} /> 복사</>}
          </button>
        )}
        <button style={styles.iconBtn} onClick={() => loadStory(true)} disabled={loading}>
          <FaSync style={loading ? { animation: 'spin 1s linear infinite' } : undefined} />
          {loading ? ' 생성중...' : ' 재생성'}
        </button>
      </div>

      {/* 본문 */}
      <div style={{ flex: 1, overflowY: 'auto', padding: 20 }}>
        {loading && (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center',
                        gap: 10, padding: 40, color: 'var(--text-muted)' }}>
            <FaSync style={{ animation: 'spin 1s linear infinite', fontSize: 24 }} />
            <div style={{ fontSize: 13 }}>
              AI가 이 패키지의 스토리를 작성 중입니다...
            </div>
            <div style={{ fontSize: 11 }}>
              (보통 10~30초 소요)
            </div>
          </div>
        )}

        {!loading && story && story.markdown && (
          <div className="markdown-body" style={{ maxWidth: 820 }}>
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{story.markdown}</ReactMarkdown>
          </div>
        )}

        {!loading && story && story.error && (
          <div style={{ ...styles.warnBox, color: '#b91c1c' }}>
            <strong>⚠ 생성 실패</strong>
            <div style={{ marginTop: 4 }}>{story.error}</div>
          </div>
        )}
      </div>
    </div>
  )
}

// ── 세부 컴포넌트 ────────────────────────────────────────────────────────

function Stat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div style={{
      padding: '6px 8px', background: 'var(--bg-card)', borderRadius: 6,
      border: '1px solid var(--border-color)',
      borderLeft: color ? `3px solid ${color}` : '1px solid var(--border-color)',
      minWidth: 0,
    }}>
      <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 600 }}>{value}</div>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginTop: 12 }}>
      <div style={styles.sectionTitle}>{title}</div>
      <div>{children}</div>
    </div>
  )
}

function Row({ children, onClick }: { children: React.ReactNode; onClick?: () => void }) {
  return (
    <div style={styles.row} onClick={onClick}>
      {children}
    </div>
  )
}

function dmlColor(dml: string): string {
  switch (dml) {
    case 'INSERT': return '#10b981'
    case 'UPDATE': return '#f59e0b'
    case 'DELETE': return '#ef4444'
    case 'MERGE':  return '#8b5cf6'
    case 'SELECT': return '#3b82f6'
    default: return '#94a3b8'
  }
}

// ── 상세 Drawer ──────────────────────────────────────────────────────────

function DetailDrawer({ item, onClose }: { item: DetailItem; onClose: () => void }) {
  return (
    <div style={styles.drawer}>
      <div style={styles.drawerHeader}>
        <div>
          <div style={{ fontSize: 10, textTransform: 'uppercase', color: 'var(--text-muted)' }}>
            {item.kind === 'class' ? '클래스' : item.kind === 'mybatis' ? 'MyBatis' : 'Endpoint'}
          </div>
          <div style={{ fontFamily: 'monospace', fontSize: 14, wordBreak: 'break-all', marginTop: 2 }}>
            {item.kind === 'class' && item.data.className}
            {item.kind === 'mybatis' && item.data.fullId}
            {item.kind === 'endpoint' && `${item.data.httpMethod} ${item.data.url}`}
          </div>
        </div>
        <button style={styles.closeBtn} onClick={onClose}><FaTimes /></button>
      </div>
      <div style={styles.drawerBody}>
        {item.kind === 'class' && (
          <>
            <Kv k="패키지" v={item.data.packageName} />
            <Kv k="타입"   v={LAYER_META[item.data.type]?.label ?? item.data.type} />
            <Kv k="파일"   v={item.data.relPath} />
          </>
        )}
        {item.kind === 'mybatis' && (
          <>
            <Kv k="DML" v={item.data.dml} />
            <Kv k="파일" v={`${item.data.file}:${item.data.line}`} />
            {item.data.tables && item.data.tables.length > 0 && (
              <Kv k="테이블" v={item.data.tables.join(', ')} />
            )}
            {item.data.snippet && (
              <div>
                <div style={{ ...styles.k, marginTop: 10, marginBottom: 4 }}>SQL 발췌</div>
                <pre style={styles.snippet}>{item.data.snippet}</pre>
              </div>
            )}
          </>
        )}
        {item.kind === 'endpoint' && (
          <>
            <Kv k="URL"    v={`${item.data.httpMethod} ${item.data.url}`} />
            <Kv k="클래스" v={`${item.data.className}.${item.data.methodName}`} />
            <Kv k="파일"   v={`${item.data.file}${item.data.line ? `:${item.data.line}` : ''}`} />
            {item.data.callees && item.data.callees.length > 0 && (
              <Kv k="호출 메서드" v={item.data.callees.slice(0, 15).join(', ')} />
            )}
          </>
        )}
      </div>
    </div>
  )
}

function Kv({ k, v }: { k: string; v: string }) {
  return (
    <div style={styles.kvRow}>
      <div style={styles.k}>{k}</div>
      <div style={styles.v}><code style={{ wordBreak: 'break-all' }}>{v}</code></div>
    </div>
  )
}

// ── 설정 모달 ────────────────────────────────────────────────────────────

function PackageSettingsModal({
  currentLevel, currentPrefix, onSave, onClose,
}: {
  currentLevel: number
  currentPrefix: string
  onSave: (level: number, prefix: string) => void
  onClose: () => void
}) {
  const toast = useToast()
  const [level, setLevel]   = useState(currentLevel)
  const [prefix, setPrefix] = useState(currentPrefix)
  const [preview, setPreview] = useState<{
    levels: Record<string, number>
    samples: string[]
    totalClasses: number
    totalPackages: number
  }>({ levels: {}, samples: [], totalClasses: 0, totalPackages: 0 })
  const [saving, setSaving] = useState(false)

  // 디바운스 미리보기
  useEffect(() => {
    const t = setTimeout(async () => {
      try {
        const url = `/api/v1/package/settings?prefix=${encodeURIComponent(prefix)}`
        const r = await fetch(url, { credentials: 'include' })
        const d = await r.json()
        if (d.success && d.data) {
          setPreview({
            levels:        d.data.previewLevels || {},
            samples:       d.data.samplePackages || [],
            totalClasses:  d.data.totalClasses || 0,
            totalPackages: d.data.totalPackages || 0,
          })
        }
      } catch { /* silent */ }
    }, 200)
    return () => clearTimeout(t)
  }, [prefix])

  // 레벨이 바뀔 때 샘플도 다시 (prefix 미변경 시에도)
  useEffect(() => {
    // 샘플은 서버의 currentLevel 기준이라 프론트에서 별도 샘플 계산은 생략
  }, [level])

  const save = async () => {
    setSaving(true)
    try {
      const r = await fetch('/api/v1/package/settings', {
        method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ level, prefix }),
      })
      const d = await r.json()
      if (d.success) {
        toast.success(`저장됨 — 레벨 ${d.data.level}`)
        onSave(d.data.level, d.data.prefix || '')
      } else toast.error(d.error || '저장 실패')
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      toast.error(`저장 호출 실패: ${msg}`)
    } finally { setSaving(false) }
  }

  return (
    <div style={styles.modalOverlay} onClick={onClose}>
      <div style={styles.modalDialog} onClick={e => e.stopPropagation()}>
        <div style={styles.modalHeader}>
          <span style={{ fontSize: 14, fontWeight: 700 }}>⚙ 패키지 그룹핑 설정</span>
          <button style={styles.closeBtn} onClick={onClose}><FaTimes /></button>
        </div>
        <div style={styles.modalBody}>
          <div style={{ marginBottom: 12 }}>
            <label style={styles.label}>패키지 레벨 (Java 패키지 깊이)</label>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 6 }}>
              <span style={styles.miniLabel}>L2</span>
              <input
                type="range" min={2} max={9} step={1}
                value={level} onChange={e => setLevel(parseInt(e.target.value))}
                style={{ flex: 1 }}
              />
              <span style={styles.miniLabel}>L9</span>
              <span style={styles.levelBadge}>L{level}</span>
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 6 }}>
              예: <code>io.github.claudetoolkit.ui.flow.indexer</code> →
              {' '}L4면 <code>io.github.claudetoolkit.ui</code>,
              {' '}L5면 <code>io.github.claudetoolkit.ui.flow</code>
            </div>
          </div>

          <div style={{ marginBottom: 12 }}>
            <label style={styles.label}>Prefix 필터 (이 문자열로 시작하는 패키지만 표시, 비우면 전체)</label>
            <input
              type="text"
              placeholder="예: com.mycompany.erp"
              value={prefix}
              onChange={e => setPrefix(e.target.value)}
              style={styles.textInput}
            />
          </div>

          <div style={styles.previewBox}>
            <div style={{ fontSize: 11, fontWeight: 700, marginBottom: 6 }}>
              📊 미리보기 (현재 인덱스 — 총 클래스 {preview.totalClasses}, 전체 패키지 {preview.totalPackages})
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6 }}>
              {[2,3,4,5,6,7,8,9].map(lv => (
                <div key={lv}
                     style={{
                       padding: '4px 6px', borderRadius: 4, textAlign: 'center',
                       background: level === lv ? 'rgba(6,182,212,0.15)' : 'var(--bg-card)',
                       border: level === lv ? '1px solid #06b6d4' : '1px solid var(--border-color)',
                       cursor: 'pointer',
                     }}
                     onClick={() => setLevel(lv)}>
                  <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>L{lv}</div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {preview.levels[String(lv)] ?? '-'}개
                  </div>
                </div>
              ))}
            </div>
            {preview.samples.length > 0 && (
              <div style={{ marginTop: 8 }}>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>
                  현재 설정 샘플 패키지:
                </div>
                <div style={{ fontFamily: 'monospace', fontSize: 11, color: 'var(--text-primary)', lineHeight: 1.5 }}>
                  {preview.samples.slice(0, 6).map(s => (
                    <div key={s}>· {s}</div>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div style={{ marginTop: 12, fontSize: 11, color: 'var(--text-muted)' }}>
            💡 ERP 프로젝트는 보통 L4~L5가 "업무 모듈" 단위입니다.
          </div>
        </div>
        <div style={styles.modalFooter}>
          <button style={styles.btnGhost} onClick={onClose}>취소</button>
          <button style={styles.btnPrimary} onClick={save} disabled={saving}>
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── 스타일 ───────────────────────────────────────────────────────────────

const styles: Record<string, CSSProperties> = {
  // v4.5 — 부모 .main-content 가 padding 24 를 가지고 있어 height:100% 가 먹지 않음.
  // FlowAnalysisPage 처럼 100vh 기준으로 탑바(60) 제외.
  page: { display: 'flex', flexDirection: 'column', height: 'calc(100vh - 60px)', overflow: 'hidden' },
  topBar: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    padding: '10px 16px', borderBottom: '1px solid var(--border-color)',
    background: 'var(--bg-card)',
  },
  iconBtn: {
    display: 'inline-flex', alignItems: 'center', gap: 5,
    padding: '5px 10px', fontSize: 11, fontWeight: 600,
    background: 'var(--bg-secondary)', color: 'var(--text-primary)',
    border: '1px solid var(--border-color)', borderRadius: 4, cursor: 'pointer',
  },
  tabBar: {
    display: 'flex', gap: 4, padding: '6px 16px 0',
    borderBottom: '1px solid var(--border-color)', background: 'var(--bg-card)',
  },
  tabBtn: {
    padding: '6px 12px', fontSize: 12, fontWeight: 600,
    background: 'transparent', color: 'var(--text-muted)',
    border: 'none', borderBottom: '2px solid transparent', cursor: 'pointer',
  },
  tabBtnActive: {
    color: '#06b6d4', borderBottom: '2px solid #06b6d4',
  },
  tabBtnDisabled: {
    opacity: 0.5, cursor: 'not-allowed',
  },
  body: {
    flex: 1, display: 'flex', overflow: 'hidden',
  },
  leftPanel: {
    width: '22%', minWidth: 220, maxWidth: 320,
    borderRight: '1px solid var(--border-color)',
    display: 'flex', flexDirection: 'column', background: 'var(--bg-secondary)',
  },
  searchBox: {
    display: 'flex', alignItems: 'center', gap: 6, padding: '8px 10px',
    borderBottom: '1px solid var(--border-color)',
  },
  searchInput: {
    flex: 1, padding: '4px 6px', fontSize: 12,
    background: 'var(--bg-card)', color: 'var(--text-primary)',
    border: '1px solid var(--border-color)', borderRadius: 4,
  },
  treeHeader: {
    padding: '6px 12px', fontSize: 10, color: 'var(--text-muted)',
    borderBottom: '1px solid var(--border-color)', fontWeight: 600,
  },
  treeList: { flex: 1, overflowY: 'auto' },
  treeItem: {
    padding: '6px 10px 6px 8px', cursor: 'pointer',
    borderBottom: '1px dashed var(--border-color)',
  },
  treeItemName: { fontSize: 11, fontFamily: 'monospace', color: 'var(--text-primary)', wordBreak: 'break-all' },
  treeItemMeta: { fontSize: 10, color: 'var(--text-muted)', marginTop: 2 },
  emptyHint: {
    padding: 20, fontSize: 12, color: 'var(--text-muted)', textAlign: 'center',
  },
  centerPanel: {
    flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column',
  },
  comingSoonBox: {
    flex: 1, display: 'flex', flexDirection: 'column',
    alignItems: 'center', justifyContent: 'center', padding: 40,
  },
  statsGrid: {
    display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6,
  },
  topList: { display: 'flex', flexDirection: 'column', gap: 4 },
  topItem: {
    display: 'flex', alignItems: 'center', gap: 8,
    padding: '6px 10px', background: 'var(--bg-card)',
    border: '1px solid var(--border-color)', borderRadius: 4,
    cursor: 'pointer',
  },
  sectionTitle: {
    fontSize: 11, fontWeight: 700, letterSpacing: 0.3,
    color: 'var(--text-muted)', textTransform: 'uppercase',
    marginBottom: 6, paddingBottom: 3,
    borderBottom: '1px solid var(--border-color)',
  },
  row: {
    display: 'flex', alignItems: 'center', gap: 8,
    padding: '4px 8px', borderBottom: '1px dashed var(--border-color)',
    cursor: 'pointer', fontSize: 12,
  },
  rowMono: { fontFamily: 'monospace', color: 'var(--text-primary)' },
  rowHint: { color: 'var(--text-muted)', fontSize: 11 },
  moreHint: { fontSize: 11, color: 'var(--text-muted)', textAlign: 'center', padding: '4px 0' },
  httpBadge: {
    display: 'inline-block', padding: '1px 5px', fontSize: 10, fontWeight: 700,
    background: '#3b82f6', color: '#ffffff', borderRadius: 3, minWidth: 40, textAlign: 'center',
  },
  dmlBadge: {
    display: 'inline-block', padding: '1px 5px', fontSize: 10, fontWeight: 700,
    color: '#ffffff', borderRadius: 3, minWidth: 50, textAlign: 'center',
  },
  layerHeader: {
    fontSize: 11, fontWeight: 700, padding: '4px 0', textTransform: 'uppercase',
  },
  chipRow: { display: 'flex', flexWrap: 'wrap', gap: 4 },
  chip: {
    display: 'inline-block', padding: '2px 8px', fontSize: 11,
    background: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: 12,
    color: 'var(--text-primary)',
  },
  drawer: {
    width: 360, minWidth: 280, borderLeft: '1px solid var(--border-color)',
    display: 'flex', flexDirection: 'column', background: 'var(--bg-secondary)',
  },
  drawerHeader: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
    padding: '12px 16px', borderBottom: '1px solid var(--border-color)',
    background: 'var(--bg-card)',
  },
  closeBtn: {
    background: 'transparent', border: 'none', color: 'var(--text-muted)',
    cursor: 'pointer', fontSize: 14, padding: 4,
  },
  drawerBody: { flex: 1, overflowY: 'auto', padding: 16 },
  kvRow: { display: 'flex', gap: 10, padding: '6px 0', borderBottom: '1px dashed var(--border-color)' },
  k: { width: 80, fontSize: 11, color: 'var(--text-muted)', flexShrink: 0 },
  v: { flex: 1, fontSize: 12, color: 'var(--text-primary)', wordBreak: 'break-word' },
  snippet: {
    background: 'var(--bg-card)', padding: 12, borderRadius: 6,
    border: '1px solid var(--border-color)',
    fontSize: 11, fontFamily: 'monospace', whiteSpace: 'pre-wrap', wordBreak: 'break-all',
    maxHeight: 320, overflowY: 'auto',
  },

  // ERD 탭
  warnBox: {
    marginTop: 10, padding: '6px 10px', background: 'rgba(245,158,11,0.08)',
    border: '1px solid rgba(245,158,11,0.3)', borderRadius: 6, fontSize: 11,
  },
  mermaidBox: {
    background: 'var(--bg-card)', border: '1px solid var(--border-color)',
    borderRadius: 6, padding: 8, overflow: 'auto',
  },
  heatTableWrap: {
    background: 'var(--bg-card)', border: '1px solid var(--border-color)',
    borderRadius: 6, overflow: 'hidden',
  },
  heatTable: {
    width: '100%', borderCollapse: 'collapse', fontSize: 12,
  },
  heatTh: {
    textAlign: 'left', padding: '6px 10px', fontSize: 11, fontWeight: 600,
    color: 'var(--text-muted)', borderBottom: '1px solid var(--border-color)',
    background: 'var(--bg-secondary)',
  },
  heatTd: {
    padding: '5px 10px', borderBottom: '1px dashed var(--border-color)',
    fontSize: 12,
  },

  // 설정 모달
  modalOverlay: {
    position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)',
    display: 'flex', justifyContent: 'center', alignItems: 'center',
    zIndex: 1000, padding: 24,
    animation: 'modal-overlay-in 0.15s ease-out',
  },
  modalDialog: {
    background: 'var(--bg-secondary)', borderRadius: 10,
    boxShadow: '0 10px 40px rgba(0,0,0,0.35)',
    border: '1px solid var(--border-color)',
    width: 'min(640px, 92vw)', maxHeight: '80vh',
    display: 'flex', flexDirection: 'column',
    animation: 'modal-body-in 0.18s ease-out',
  },
  modalHeader: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    padding: '12px 16px', borderBottom: '1px solid var(--border-color)',
    background: 'var(--bg-card)', borderTopLeftRadius: 10, borderTopRightRadius: 10,
  },
  modalBody: { flex: 1, overflowY: 'auto', padding: '16px 20px' },
  modalFooter: {
    display: 'flex', justifyContent: 'flex-end', gap: 8,
    padding: '10px 16px', borderTop: '1px solid var(--border-color)',
    background: 'var(--bg-card)',
  },
  label: { fontSize: 12, fontWeight: 600, color: 'var(--text-primary)' },
  miniLabel: { fontSize: 10, color: 'var(--text-muted)' },
  textInput: {
    width: '100%', marginTop: 6, padding: '6px 8px', fontSize: 12,
    background: 'var(--bg-card)', color: 'var(--text-primary)',
    border: '1px solid var(--border-color)', borderRadius: 4,
  },
  levelBadge: {
    display: 'inline-block', padding: '2px 10px', fontSize: 12, fontWeight: 700,
    background: '#06b6d4', color: '#ffffff', borderRadius: 4, minWidth: 32, textAlign: 'center',
  },
  previewBox: {
    padding: 10, background: 'var(--bg-card)',
    border: '1px solid var(--border-color)', borderRadius: 6,
  },
  btnGhost: {
    padding: '6px 14px', fontSize: 12,
    background: 'transparent', color: 'var(--text-primary)',
    border: '1px solid var(--border-color)', borderRadius: 4, cursor: 'pointer',
  },
  btnPrimary: {
    padding: '6px 14px', fontSize: 12, fontWeight: 600,
    background: '#06b6d4', color: '#ffffff',
    border: 'none', borderRadius: 4, cursor: 'pointer',
  },
}
