import { useEffect, useMemo, useState, useCallback } from 'react'
import { FaSearch, FaCompass, FaHistory, FaFilter, FaTimesCircle } from 'react-icons/fa'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import { useApi } from '../hooks/useApi'
import { menuSections, quickLinks, footerItems, type MenuItem } from '../components/layout/sidebarMenus'

interface HistoryHit {
  id: number
  type: string
  menuName: string
  title: string
  snippet: string
  matchField: string  // 'title' | 'type' | 'input' | 'output' | ''
  createdAt: string
}

interface MenuHit {
  section: string
  label: string
  path: string
  externalLink?: boolean
}

interface SearchType {
  type: string
  label: string
  count: number
}

type SortOrder = 'recent' | 'oldest' | 'relevance'

/**
 * 검색 페이지 — 메뉴 카탈로그(클라이언트 필터) + 분석 이력(백엔드 검색).
 * v4.6.x — 타입/날짜 필터 + 정렬 + 매치 위치 기반 snippet 강조.
 */
export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [query,     setQuery]     = useState(searchParams.get('q') || '')
  const [typeFilter,setTypeFilter]= useState(searchParams.get('type') || '')
  const [fromDate,  setFromDate]  = useState(searchParams.get('from') || '')
  const [toDate,    setToDate]    = useState(searchParams.get('to')   || '')
  const [sort,      setSort]      = useState<SortOrder>((searchParams.get('sort') as SortOrder) || 'recent')
  const [historyResults, setHistoryResults] = useState<HistoryHit[]>([])
  const [searchTypes,    setSearchTypes]    = useState<SearchType[]>([])
  const [searched,       setSearched]       = useState(false)
  const [showFilters,    setShowFilters]    = useState(
    !!searchParams.get('type') || !!searchParams.get('from') || !!searchParams.get('to')
      || (searchParams.get('sort') && searchParams.get('sort') !== 'recent')
  )
  const api = useApi()
  const navigate = useNavigate()

  // ── 메뉴 카탈로그 평탄화 ─────────────────────────────────────────────────
  const allMenuItems = useMemo<{ section: string; item: MenuItem }[]>(() => {
    const list: { section: string; item: MenuItem }[] = []
    quickLinks.forEach((m)   => list.push({ section: '바로가기', item: m }))
    menuSections.forEach((s) => s.items.forEach((m) => list.push({ section: s.label, item: m })))
    footerItems.forEach((m)  => list.push({ section: '기타', item: m }))
    return list
  }, [])

  const menuHits: MenuHit[] = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return []
    return allMenuItems
      .filter(({ item }) => item.label.toLowerCase().includes(q) || item.path.toLowerCase().includes(q))
      .map(({ section, item }) => ({
        section, label: item.label, path: item.path, externalLink: item.externalLink,
      }))
  }, [query, allMenuItems])

  // ── 검색 — 타입 옵션은 첫 마운트에 한 번 로드 ─────────────────────────
  useEffect(() => {
    api.get('/api/v1/search/types').then((d) => setSearchTypes((d as SearchType[] | null) || []))
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const buildSearchUrl = (q: string) => {
    const params: Record<string, string> = { q }
    if (typeFilter) params.type = typeFilter
    if (fromDate)   params.from = fromDate
    if (toDate)     params.to   = toDate
    if (sort && sort !== 'recent') params.sort = sort
    return new URLSearchParams(params).toString()
  }

  const search = useCallback(async (q: string) => {
    if (!q.trim()) { setHistoryResults([]); setSearched(false); return }
    const url = `/api/v1/search?${buildSearchUrl(q)}`
    const data = await api.get(url) as HistoryHit[] | null
    setHistoryResults(data || [])
    setSearched(true)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [api, typeFilter, fromDate, toDate, sort])

  // ?q= 으로 진입 시 자동 검색
  useEffect(() => {
    const urlQ = searchParams.get('q') || ''
    if (urlQ && urlQ !== query) setQuery(urlQ)
    if (urlQ) search(urlQ)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams])

  const onSubmit = () => {
    const q = query.trim()
    if (!q) return
    const params: Record<string, string> = { q }
    if (typeFilter) params.type = typeFilter
    if (fromDate)   params.from = fromDate
    if (toDate)     params.to   = toDate
    if (sort && sort !== 'recent') params.sort = sort
    setSearchParams(params, { replace: true })
    search(q)
  }

  const clearFilters = () => {
    setTypeFilter(''); setFromDate(''); setToDate(''); setSort('recent')
    if (query.trim()) {
      setSearchParams({ q: query.trim() }, { replace: true })
      // 즉시 재검색 — 다음 tick 까지 기다리면 stale state 사용
      setTimeout(() => search(query.trim()), 0)
    }
  }

  const navigateToMenu = (hit: MenuHit) => {
    if (hit.externalLink) window.open(hit.path, '_blank')
    else navigate(hit.path)
  }

  const filterCount = (typeFilter ? 1 : 0) + (fromDate ? 1 : 0) + (toDate ? 1 : 0) + (sort !== 'recent' ? 1 : 0)

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaSearch /> 검색
      </h2>

      <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
        <input
          style={{ flex: 1, padding: '10px 14px', fontSize: '14px' }}
          placeholder="메뉴, 이력, 즐겨찾기, 분석 결과를 검색하세요..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && onSubmit()}
          autoFocus
        />
        <button
          onClick={() => setShowFilters((v) => !v)}
          title="필터 + 정렬 토글"
          style={{
            padding: '10px 14px', borderRadius: '8px',
            background: showFilters || filterCount > 0 ? 'var(--accent-subtle)' : 'transparent',
            color: showFilters || filterCount > 0 ? 'var(--accent)' : 'var(--text-muted)',
            border: '1px solid var(--border-color)',
            cursor: 'pointer', fontSize: '13px', fontWeight: 600,
            display: 'flex', alignItems: 'center', gap: 6,
          }}
        >
          <FaFilter /> 필터 {filterCount > 0 && <span style={{
            fontSize: 10, background: 'var(--accent)', color: '#fff',
            borderRadius: 8, padding: '1px 6px',
          }}>{filterCount}</span>}
        </button>
        <button
          onClick={onSubmit}
          style={{
            padding: '10px 20px', borderRadius: '8px',
            background: 'var(--accent)', color: '#fff', border: 'none',
            cursor: 'pointer', fontSize: '14px', fontWeight: 600,
          }}
        >
          <FaSearch /> 검색
        </button>
      </div>

      {/* 필터 + 정렬 행 */}
      {showFilters && (
        <div style={{
          display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center',
          padding: '10px 14px', marginBottom: 16,
          background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
          borderRadius: 8,
        }}>
          <FilterField label="기능 타입">
            <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)} style={selectStyle}>
              <option value="">전체</option>
              {searchTypes.map((t) => (
                <option key={t.type} value={t.type}>{t.label} ({t.count})</option>
              ))}
            </select>
          </FilterField>
          <FilterField label="시작일">
            <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} style={selectStyle} />
          </FilterField>
          <FilterField label="종료일">
            <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} style={selectStyle} />
          </FilterField>
          <FilterField label="정렬">
            <select value={sort} onChange={(e) => setSort(e.target.value as SortOrder)} style={selectStyle}>
              <option value="recent">최신순</option>
              <option value="oldest">오래된순</option>
              <option value="relevance">관련도순</option>
            </select>
          </FilterField>
          {filterCount > 0 && (
            <button
              onClick={clearFilters}
              style={{
                padding: '6px 12px', borderRadius: '6px', fontSize: 12,
                background: 'transparent', color: 'var(--text-muted)',
                border: '1px solid var(--border-color)', cursor: 'pointer',
                display: 'flex', alignItems: 'center', gap: 4,
              }}
            >
              <FaTimesCircle /> 필터 초기화
            </button>
          )}
        </div>
      )}

      {/* 메뉴 카탈로그 결과 */}
      {menuHits.length > 0 && (
        <section style={{ marginBottom: 24 }}>
          <h3 style={sectionHeader}><FaCompass /> 메뉴 ({menuHits.length})</h3>
          {menuHits.map((hit) => (
            <div
              key={`${hit.section}-${hit.path}`}
              onClick={() => navigateToMenu(hit)}
              style={{
                padding: '10px 16px', marginBottom: '6px', borderRadius: '8px',
                background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
                cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10,
              }}
            >
              <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>
                {hit.section}
              </span>
              <span style={{ fontSize: '14px', fontWeight: 600 }}>
                <Highlight text={hit.label} q={query} />
              </span>
              <span style={{ marginLeft: 'auto', fontSize: '12px', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                {hit.path}
              </span>
            </div>
          ))}
        </section>
      )}

      {/* 분석 이력 결과 */}
      {historyResults.length > 0 && (
        <section>
          <h3 style={sectionHeader}>
            <FaHistory /> 이력 ({historyResults.length})
            {filterCount > 0 && (
              <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 400, marginLeft: 6 }}>
                · 필터 {filterCount}개 적용 중
              </span>
            )}
          </h3>
          {historyResults.map((r) => (
            <Link
              key={r.id}
              to={`/history?id=${r.id}`}
              style={{
                display: 'block', textDecoration: 'none',
                padding: '12px 16px', marginBottom: '8px', borderRadius: '10px',
                background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
                color: 'inherit',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>
                  {r.menuName}
                </span>
                <span style={{ fontSize: '14px', fontWeight: 600 }}>
                  <Highlight text={r.title} q={query} />
                </span>
                {r.matchField && (
                  <span style={{
                    fontSize: 10, color: 'var(--text-muted)',
                    padding: '1px 6px', border: '1px solid var(--border-color)',
                    borderRadius: 3,
                  }} title="매치된 필드">
                    {matchFieldLabel(r.matchField)}
                  </span>
                )}
                <span style={{ marginLeft: 'auto', fontSize: '12px', color: 'var(--text-muted)' }}>{r.createdAt}</span>
              </div>
              <p style={{ fontSize: '13px', color: 'var(--text-sub)', margin: 0 }}>
                <Highlight text={r.snippet} q={query} />
              </p>
            </Link>
          ))}
        </section>
      )}

      {searched && menuHits.length === 0 && historyResults.length === 0 && (
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
          검색 결과가 없습니다.
          {filterCount > 0 && (
            <div style={{ marginTop: 8, fontSize: 12 }}>
              필터를 줄여서 다시 시도해 보세요.
            </div>
          )}
        </div>
      )}
    </>
  )
}

// ── 매치 텍스트 강조 (대소문자 무시) ──────────────────────────────────────

function Highlight({ text, q }: { text: string; q: string }) {
  const trimmed = q.trim()
  if (!trimmed || !text) return <>{text}</>
  // 정규식 특수문자 escape
  const safe = trimmed.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const parts = text.split(new RegExp(`(${safe})`, 'gi'))
  return (
    <>
      {parts.map((p, i) =>
        p.toLowerCase() === trimmed.toLowerCase() ? (
          <mark key={i} style={{ background: 'rgba(245,158,11,0.35)', color: 'inherit', padding: '0 1px', borderRadius: 2 }}>
            {p}
          </mark>
        ) : <span key={i}>{p}</span>
      )}
    </>
  )
}

function matchFieldLabel(field: string): string {
  switch (field) {
    case 'title':  return '제목 일치'
    case 'type':   return '타입 일치'
    case 'input':  return '입력 일치'
    case 'output': return '출력 일치'
    default:       return ''
  }
}

function FilterField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 3, fontSize: 11, color: 'var(--text-muted)' }}>
      <span>{label}</span>
      {children}
    </label>
  )
}

const selectStyle: React.CSSProperties = {
  padding: '5px 8px', fontSize: 13,
  borderRadius: 6, border: '1px solid var(--border-color)',
  background: 'var(--bg-card)', color: 'var(--text-primary)',
  minWidth: 140,
}

const sectionHeader: React.CSSProperties = {
  fontSize: 13, fontWeight: 700, color: 'var(--text-muted)',
  margin: '0 0 10px 0', display: 'flex', alignItems: 'center', gap: 6,
  textTransform: 'uppercase', letterSpacing: 0.6,
}
