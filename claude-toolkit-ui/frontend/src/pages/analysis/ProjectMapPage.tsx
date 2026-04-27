import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  FaMap, FaSync, FaChevronRight, FaArrowLeft, FaBoxes, FaHome,
} from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

/**
 * v4.5 — UX-5 전사 패키지 지도 (드릴다운)
 *
 *  Level 3 (최상위)
 *     ┌──────────┐  ┌──────────┐  ┌──────────┐
 *     │ account  │  │  sales   │  │  stock   │    ← 클릭 드릴다운
 *     └──────────┘  └──────────┘  └──────────┘
 *                        ▼
 *  Level 4 (sales 내부)
 *     ┌──────────┐  ┌──────────┐
 *     │ customer │  │  order   │    ← [←] 뒤로
 *     └──────────┘  └──────────┘
 *
 *  특정 레벨의 리프에 도달하면 "패키지 개요 열기" 버튼 → PackageOverview 로 이동
 */

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

interface OverviewResponse {
  level:        number
  prefix:       string
  packageCount: number
  packages:     PackageSummary[]
  indexerReady: boolean
}

interface SettingsResponse {
  currentLevel:  number
  currentPrefix: string
  previewLevels: Record<string, number>
  totalClasses:  number
  totalPackages: number
}

export default function ProjectMapPage() {
  const toast = useToast()
  const navigate = useNavigate()

  const [startLevel,   setStartLevel]   = useState<number>(3)
  const [targetLevel,  setTargetLevel]  = useState<number>(5)    // 리프 레벨 (패키지 개요와 연결)
  const [configPrefix, setConfigPrefix] = useState<string>('')

  const [currentLevel, setCurrentLevel] = useState<number>(3)
  // v4.5 — currentPath 를 string (dot-joined) 으로 저장해 배열 참조 불안정 → 깜빡임 이슈 해소
  const [currentPath,  setCurrentPath]  = useState<string>('')
  const [packages,     setPackages]     = useState<PackageSummary[]>([])
  const [loading,      setLoading]      = useState(false)
  // v4.5 — 초기 settings 가 완료되기 전엔 loadAtCurrent 가 fire 하지 않도록 guard
  const [settingsReady, setSettingsReady] = useState(false)

  // breadcrumb 렌더용 파생 배열 (메모이즈)
  const pathSegs = useMemo(
    () => currentPath.split('.').filter(Boolean),
    [currentPath],
  )

  // ── 설정 로드 (최초 1회) ───────────────────────────────────────────────

  const loadSettings = useCallback(async () => {
    try {
      const r = await fetch('/api/v1/package/settings', { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) {
        const s = d.data as SettingsResponse
        setTargetLevel(s.currentLevel ?? 5)
        setConfigPrefix(s.currentPrefix ?? '')
        // 시작 레벨: prefix 가 있으면 그 세그먼트 수 + 1, 아니면 L3
        const pfSegs = (s.currentPrefix || '').split('.').filter(Boolean).length
        const start = pfSegs >= 2 ? pfSegs + 1 : 3
        setStartLevel(start)
        setCurrentLevel(start)
        setCurrentPath(s.currentPrefix ?? '')  // 문자열 그대로 (참조 안정)
      }
    } catch (e) { console.warn('[ProjectMap] settings load', e) }
    finally {
      setSettingsReady(true)  // 설정 완료 후 단 1회 loadAtCurrent 허용
    }
  }, [])

  // ── 현재 레벨 패키지 로드 ──────────────────────────────────────────────

  const loadAtCurrent = useCallback(async () => {
    setLoading(true)
    try {
      const qs = new URLSearchParams({
        level:  String(currentLevel),
        prefix: currentPath,
      })
      const r = await fetch(`/api/v1/package/overview?${qs.toString()}`, { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) {
        const data = d.data as OverviewResponse
        setPackages(data.packages || [])
      } else {
        toast.error(d.error || 'overview 로드 실패')
      }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      toast.error(`로드 실패: ${msg}`)
    } finally {
      setLoading(false)
    }
  }, [currentLevel, currentPath, toast])

  useEffect(() => { loadSettings() }, [loadSettings])
  // settings 완료 후에만 loadAtCurrent — 초기 race 깜빡임 원천 차단
  useEffect(() => {
    if (!settingsReady) return
    loadAtCurrent()
  }, [settingsReady, loadAtCurrent])

  // ── 드릴다운/뒤로 ──────────────────────────────────────────────────────

  const drillDown = (pkg: PackageSummary) => {
    // 현재 레벨에 맞게 세그먼트 자름
    if (currentLevel >= targetLevel) {
      // 리프 도달 → 패키지 개요로 이동 (선택된 상태로)
      navigate(`/package-overview?pkg=${encodeURIComponent(pkg.packageName)}`)
      return
    }
    const segs = pkg.packageName.split('.')
    const newPath = segs.slice(0, currentLevel).join('.')
    if (newPath !== currentPath) setCurrentPath(newPath)
    setCurrentLevel(currentLevel + 1)
  }

  const goBack = () => {
    if (currentLevel <= startLevel) return
    setCurrentLevel(currentLevel - 1)
    const segs = pathSegs.slice(0, pathSegs.length - 1)
    const newPath = segs.join('.')
    if (newPath !== currentPath) setCurrentPath(newPath)
  }

  const goHome = () => {
    setCurrentLevel(startLevel)
    if (configPrefix !== currentPath) setCurrentPath(configPrefix)
  }

  // ── 렌더 ────────────────────────────────────────────────────────────

  const totalClasses    = packages.reduce((s, p) => s + p.classTotal, 0)
  const totalMyBatis    = packages.reduce((s, p) => s + p.mybatisCount, 0)
  const totalEndpoints  = packages.reduce((s, p) => s + p.endpointCount, 0)

  return (
    <div style={styles.page}>
      {/* 상단 */}
      <div style={styles.topBar}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <FaMap size={20} style={{ color: '#f97316' }} />
          <h2 style={{ margin: 0, fontSize: 18 }}>전사 패키지 지도</h2>
          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
            L{startLevel} 최상위 → 드릴다운 → L{targetLevel} 패키지 개요로 이동
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button style={styles.iconBtn} onClick={goHome} title="최상위로">
            <FaHome /> 홈
          </button>
          <button style={styles.iconBtn} onClick={loadAtCurrent} title="새로고침">
            <FaSync style={loading ? { animation: 'spin 1s linear infinite' } : undefined} />
          </button>
        </div>
      </div>

      {/* Breadcrumb */}
      <div style={styles.breadcrumb}>
        <button style={styles.breadcrumbBtn} onClick={goHome}>🏠 Root</button>
        {pathSegs.slice(0, currentLevel - 1).map((seg, idx) => (
          <span key={idx} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <FaChevronRight size={9} style={{ color: 'var(--text-muted)' }} />
            <span style={styles.breadcrumbSeg}>{seg}</span>
          </span>
        ))}
        <span style={{ flex: 1 }} />
        <span style={styles.levelBadge}>L{currentLevel}</span>
        {currentLevel > startLevel && (
          <button style={styles.backBtn} onClick={goBack} title="상위 레벨로">
            <FaArrowLeft size={10} /> 뒤로
          </button>
        )}
      </div>

      {/* 통계 요약 */}
      <div style={styles.summaryBar}>
        <span>📦 <strong>{packages.length}</strong>개 패키지</span>
        <span>· 🏗 클래스 <strong>{totalClasses}</strong></span>
        <span>· 📋 MyBatis <strong>{totalMyBatis}</strong></span>
        <span>· 🎯 Endpoint <strong>{totalEndpoints}</strong></span>
        {currentLevel >= targetLevel && (
          <span style={{ marginLeft: 'auto', color: '#f97316', fontWeight: 600 }}>
            🎯 리프 레벨 도달 — 클릭 시 패키지 개요로 이동
          </span>
        )}
      </div>

      {/* 본문 — 카드 그리드 */}
      <div style={styles.gridBody}>
        {loading && (
          <div style={styles.emptyHint}>로딩 중...</div>
        )}
        {!loading && packages.length === 0 && (
          <div style={styles.emptyHint}>
            이 레벨에 패키지가 없습니다. 상위로 돌아가거나 Java 인덱스를 재빌드하세요.
          </div>
        )}
        {!loading && packages.length > 0 && (
          <div style={styles.cardGrid}>
            {packages.map(p => (
              <PackageCard
                key={p.packageName}
                pkg={p}
                isLeaf={currentLevel >= targetLevel}
                onClick={() => drillDown(p)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

// ── 카드 컴포넌트 ─────────────────────────────────────────────────────

function PackageCard({ pkg, isLeaf, onClick }: {
  pkg: PackageSummary
  isLeaf: boolean
  onClick: () => void
}) {
  const activity = pkg.classTotal + pkg.mybatisCount + pkg.endpointCount * 2
  const heat = activity > 60 ? '🔴' : activity > 20 ? '🟡' : '⚪'

  return (
    <div
      style={{
        ...styles.card,
        borderLeft: isLeaf ? '4px solid #f97316' : '4px solid #06b6d4',
      }}
      onClick={onClick}
      title={isLeaf ? '클릭 → 패키지 개요 열기' : '클릭 → 드릴다운'}
    >
      <div style={styles.cardHeader}>
        <span style={{ fontSize: 20 }}>{isLeaf ? '📦' : '📁'}</span>
        <span style={styles.cardTitle}>{shortName(pkg.packageName)}</span>
        <span style={{ fontSize: 14 }} title={`활동성: ${activity}`}>{heat}</span>
      </div>
      <div style={styles.cardPath}>{pkg.packageName}</div>
      <div style={styles.cardStats}>
        <span>🏗 {pkg.classTotal}</span>
        <span>🎯 {pkg.controllerCount}</span>
        <span>⚙️ {pkg.serviceCount}</span>
        <span>🗂️ {pkg.daoCount}</span>
      </div>
      <div style={styles.cardStats}>
        <span>📋 {pkg.mybatisCount}</span>
        <span>🗄 {pkg.tableCount}</span>
        <span>🔗 {pkg.endpointCount}</span>
      </div>
      <div style={styles.cardCta}>
        {isLeaf ? <><FaBoxes size={10} /> 패키지 개요 열기</> : <>드릴다운 <FaChevronRight size={9} /></>}
      </div>
    </div>
  )
}

function shortName(fullPkg: string): string {
  const parts = fullPkg.split('.')
  return parts[parts.length - 1] || fullPkg
}

// ── 스타일 ───────────────────────────────────────────────────────────

const styles: Record<string, CSSProperties> = {
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
  breadcrumb: {
    display: 'flex', alignItems: 'center', gap: 6,
    padding: '8px 16px', background: 'var(--bg-secondary)',
    borderBottom: '1px solid var(--border-color)',
  },
  breadcrumbBtn: {
    padding: '3px 8px', fontSize: 11, fontWeight: 600,
    background: 'transparent', color: 'var(--text-primary)',
    border: '1px solid var(--border-color)', borderRadius: 4, cursor: 'pointer',
  },
  breadcrumbSeg: { fontSize: 12, fontFamily: 'monospace', color: 'var(--text-primary)' },
  levelBadge: {
    padding: '3px 10px', fontSize: 11, fontWeight: 700,
    background: '#f97316', color: '#ffffff', borderRadius: 4,
  },
  backBtn: {
    display: 'inline-flex', alignItems: 'center', gap: 4,
    padding: '3px 10px', fontSize: 11, fontWeight: 600,
    background: 'var(--bg-card)', color: 'var(--text-primary)',
    border: '1px solid var(--border-color)', borderRadius: 4, cursor: 'pointer',
  },
  summaryBar: {
    display: 'flex', gap: 12, padding: '6px 16px',
    background: 'var(--bg-card)', borderBottom: '1px solid var(--border-color)',
    fontSize: 11, color: 'var(--text-muted)',
  },
  gridBody: { flex: 1, overflowY: 'auto', padding: 16 },
  cardGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
    gap: 12,
  },
  card: {
    padding: 12, background: 'var(--bg-card)',
    border: '1px solid var(--border-color)', borderRadius: 8,
    cursor: 'pointer',
    transition: 'transform 0.05s ease, box-shadow 0.15s ease',
  },
  cardHeader: { display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 },
  cardTitle: { flex: 1, fontSize: 14, fontWeight: 700, color: 'var(--text-primary)', wordBreak: 'break-all' },
  cardPath: {
    fontSize: 10, fontFamily: 'monospace', color: 'var(--text-muted)',
    marginBottom: 8, wordBreak: 'break-all',
  },
  cardStats: {
    display: 'flex', gap: 10, fontSize: 11, color: 'var(--text-muted)',
    paddingTop: 4,
  },
  cardCta: {
    display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 4,
    marginTop: 8, paddingTop: 6, borderTop: '1px dashed var(--border-color)',
    fontSize: 11, fontWeight: 600, color: '#06b6d4',
  },
  emptyHint: {
    padding: 40, fontSize: 13, color: 'var(--text-muted)', textAlign: 'center',
  },
}
