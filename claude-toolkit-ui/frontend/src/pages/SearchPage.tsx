import { useEffect, useMemo, useState, useCallback } from 'react'
import { FaSearch, FaCompass, FaHistory } from 'react-icons/fa'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import { useApi } from '../hooks/useApi'
import { menuSections, quickLinks, footerItems, type MenuItem } from '../components/layout/sidebarMenus'

interface HistoryHit {
  id: number
  menuName: string
  title: string
  snippet: string
  createdAt: string
}

interface MenuHit {
  section: string
  label: string
  path: string
  externalLink?: boolean
}

/**
 * 검색 페이지 — 두 종류 결과 통합:
 *  1) 메뉴 카탈로그 (sidebarMenus.ts) — 클라이언트 필터, 즉시 반응
 *  2) 분석 이력 (review_history) — 백엔드 /api/v1/search 호출
 * 외부에서 ?q=... URL 파라미터로 진입하면 자동으로 검색이 실행됨 (TopBar 검색창 연동).
 */
export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const initialQ = searchParams.get('q') || ''
  const [query, setQuery] = useState(initialQ)
  const [historyResults, setHistoryResults] = useState<HistoryHit[]>([])
  const [searched, setSearched] = useState(false)
  const api = useApi()
  const navigate = useNavigate()

  // 사이드바 전체 항목을 (section, item) flat 리스트로 변환 — useMemo 로 재계산 캐시
  const allMenuItems = useMemo<{ section: string; item: MenuItem }[]>(() => {
    const list: { section: string; item: MenuItem }[] = []
    quickLinks.forEach((m) => list.push({ section: '바로가기', item: m }))
    menuSections.forEach((s) => s.items.forEach((m) => list.push({ section: s.label, item: m })))
    footerItems.forEach((m) => list.push({ section: '기타', item: m }))
    return list
  }, [])

  // 메뉴 카탈로그 클라이언트 필터 (label 부분일치, 대소문자 무시)
  const menuHits: MenuHit[] = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return []
    return allMenuItems
      .filter(({ item }) => item.label.toLowerCase().includes(q) || item.path.toLowerCase().includes(q))
      .map(({ section, item }) => ({
        section, label: item.label, path: item.path, externalLink: item.externalLink,
      }))
  }, [query, allMenuItems])

  const search = useCallback(async (q: string) => {
    if (!q.trim()) { setHistoryResults([]); setSearched(false); return }
    const data = await api.get(`/api/v1/search?q=${encodeURIComponent(q)}`) as HistoryHit[] | null
    setHistoryResults(data || [])
    setSearched(true)
  }, [api])

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
    setSearchParams({ q }, { replace: true })  // URL 동기화 — 새로고침/공유에 안전
    search(q)
  }

  const navigateToMenu = (hit: MenuHit) => {
    if (hit.externalLink) window.open(hit.path, '_blank')
    else navigate(hit.path)
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaSearch /> 검색
      </h2>

      <div style={{ display: 'flex', gap: '8px', marginBottom: '24px' }}>
        <input
          style={{ flex: 1, padding: '10px 14px', fontSize: '14px' }}
          placeholder="메뉴, 이력, 즐겨찾기, 분석 결과를 검색하세요..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && onSubmit()}
          autoFocus
        />
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

      {/* 메뉴 카탈로그 결과 */}
      {menuHits.length > 0 && (
        <section style={{ marginBottom: 24 }}>
          <h3 style={sectionHeader}>
            <FaCompass /> 메뉴 ({menuHits.length})
          </h3>
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
              <span style={{ fontSize: '14px', fontWeight: 600 }}>{hit.label}</span>
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
                <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>{r.menuName}</span>
                <span style={{ fontSize: '14px', fontWeight: 600 }}>{r.title}</span>
                <span style={{ marginLeft: 'auto', fontSize: '12px', color: 'var(--text-muted)' }}>{r.createdAt}</span>
              </div>
              <p style={{ fontSize: '13px', color: 'var(--text-sub)', margin: 0 }}>{r.snippet}</p>
            </Link>
          ))}
        </section>
      )}

      {searched && menuHits.length === 0 && historyResults.length === 0 && (
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
          검색 결과가 없습니다.
        </div>
      )}
    </>
  )
}

const sectionHeader: React.CSSProperties = {
  fontSize: 13, fontWeight: 700, color: 'var(--text-muted)',
  margin: '0 0 10px 0', display: 'flex', alignItems: 'center', gap: 6,
  textTransform: 'uppercase', letterSpacing: 0.6,
}
