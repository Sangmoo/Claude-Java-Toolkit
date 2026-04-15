import { useEffect, useState, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaStar, FaTrash, FaChevronDown, FaChevronUp, FaCopy, FaCheck,
  FaSearch, FaDownload, FaFileExport,
} from 'react-icons/fa'
import { useToast } from '../hooks/useToast'
import { markdownCodeComponents } from '../components/common/CopyableCodeBlock'

// v4.2.7: 백엔드 Favorite 엔티티의 실제 직렬화 필드명과 일치시킴.
// 기존 interface 는 menuName/inputText/resultText 로 잘못 매핑되어
// 즐겨찾기 카드를 펼쳤을 때 본문이 항상 비어 보이던 버그의 원인이었다.
interface FavoriteItem {
  id:             number
  type:           string   // 예: 'SQL_REVIEW'
  typeLabel?:     string   // 백엔드 getTypeLabel() 파생
  title?:         string
  tag?:           string
  inputContent:   string
  outputContent:  string
  createdAt:      string
}

export default function FavoritesPage() {
  const [items, setItems]             = useState<FavoriteItem[]>([])
  const [expandedId, setExpandedId]   = useState<number | null>(null)
  const [copiedId, setCopiedId]       = useState<number | null>(null)
  // v4.2.7: HistoryPage 와 동일한 검색/다중 선택 패턴 이식
  const [filter, setFilter]           = useState('')
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  // v4.2.7: 페이지네이션
  const PAGE_SIZE = 50
  const [currentPage, setCurrentPage] = useState(0)
  const [hasMore, setHasMore]         = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const toast = useToast()

  const load = useCallback(async () => {
    try {
      const res = await fetch(`/api/v1/favorites?page=0&size=${PAGE_SIZE}`, { credentials: 'include' })
      if (!res.ok) return
      const json = await res.json()
      const data = (json.data ?? json) as FavoriteItem[]
      setItems(Array.isArray(data) ? data : [])
      setCurrentPage(0)
      setHasMore((res.headers.get('X-Has-More') || 'false') === 'true')
    } catch { /* silent */ }
  }, [])

  const loadMore = async () => {
    if (loadingMore || !hasMore) return
    setLoadingMore(true)
    try {
      const nextPage = currentPage + 1
      const res = await fetch(`/api/v1/favorites?page=${nextPage}&size=${PAGE_SIZE}`, { credentials: 'include' })
      if (!res.ok) return
      const json = await res.json()
      const data = (json.data ?? json) as FavoriteItem[]
      if (Array.isArray(data) && data.length > 0) {
        setItems((prev) => [...prev, ...data])
        setCurrentPage(nextPage)
      }
      setHasMore((res.headers.get('X-Has-More') || 'false') === 'true')
    } catch { /* silent */ }
    finally { setLoadingMore(false) }
  }

  useEffect(() => { load() }, [load])

  // 검색 필터 — title / inputContent / typeLabel 매칭
  const filtered = items.filter((item) => {
    if (!filter) return true
    const q = filter.toLowerCase()
    return (
      (item.title         || '').toLowerCase().includes(q) ||
      (item.inputContent  || '').toLowerCase().includes(q) ||
      (item.typeLabel     || '').toLowerCase().includes(q) ||
      (item.type          || '').toLowerCase().includes(q)
    )
  })

  const remove = async (id: number) => {
    // v4.2.7: 백엔드가 JSON + 소유자 체크 응답을 돌려줌
    try {
      const res = await fetch(`/favorites/${id}/delete`, { method: 'POST', credentials: 'include' })
      const d   = await res.json().catch(() => null)
      if (res.ok && d?.success) {
        setItems((prev) => prev.filter((i) => i.id !== id))
        setSelectedIds((prev) => {
          const next = new Set(prev)
          next.delete(id)
          return next
        })
        toast.success('즐겨찾기에서 제거되었습니다.')
      } else {
        toast.error(d?.error || '즐겨찾기 제거 실패')
      }
    } catch {
      toast.error('요청 실패')
    }
  }

  const copy = (item: FavoriteItem) => {
    navigator.clipboard.writeText(item.outputContent || '')
    setCopiedId(item.id)
    setTimeout(() => setCopiedId(null), 2000)
  }

  // ── 다중 선택 ───────────────────────────────────────────────────
  const toggleSelect = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id); else next.add(id)
      return next
    })
  }
  const toggleSelectAll = () => {
    if (selectedIds.size === filtered.length && filtered.length > 0) {
      setSelectedIds(new Set())
    } else {
      setSelectedIds(new Set(filtered.map((i) => i.id)))
    }
  }

  // 선택 있으면 선택분만, 없으면 필터 결과 전체를 .md 로 내보냄
  const exportSelected = () => {
    const target = selectedIds.size > 0
      ? filtered.filter((i) => selectedIds.has(i.id))
      : filtered
    if (target.length === 0) { toast.error('내보낼 즐겨찾기가 없습니다.'); return }
    const md = target.map((i) =>
      `## ${i.typeLabel || i.type} — ${i.createdAt}\n\n### 제목\n${i.title || '(제목 없음)'}\n\n### 입력\n\`\`\`\n${i.inputContent}\n\`\`\`\n\n### 결과\n${i.outputContent}\n\n---\n`
    ).join('\n')
    const blob = new Blob([md], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const suffix = selectedIds.size > 0 ? `selected_${target.length}` : 'all'
    a.download = `favorites_${suffix}_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  // 선택 일괄 삭제 — 백엔드 개별 엔드포인트 병렬 호출 (소유자 체크는 백엔드가 각각 수행)
  const deleteSelected = async () => {
    if (selectedIds.size === 0) return
    if (!confirm(`선택한 ${selectedIds.size}건의 즐겨찾기를 삭제하시겠습니까?`)) return
    const ids = Array.from(selectedIds)
    const results = await Promise.all(ids.map(async (id) => {
      try {
        const res = await fetch(`/favorites/${id}/delete`, { method: 'POST', credentials: 'include' })
        const d   = await res.json().catch(() => null)
        return { id, ok: !!(res.ok && d?.success), error: d?.error as string | undefined }
      } catch {
        return { id, ok: false, error: '요청 실패' }
      }
    }))
    const okIds = new Set(results.filter((r) => r.ok).map((r) => r.id))
    const failCount = results.length - okIds.size
    setItems((prev) => prev.filter((i) => !okIds.has(i.id)))
    setSelectedIds((prev) => {
      const next = new Set(prev)
      okIds.forEach((id) => next.delete(id))
      return next
    })
    if (okIds.size > 0) toast.success(`${okIds.size}건 삭제되었습니다.`)
    if (failCount > 0) toast.error(`${failCount}건 삭제 실패`)
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaStar style={{ color: '#f59e0b' }} /> 즐겨찾기
          <span style={{ fontSize: '13px', color: 'var(--text-muted)', fontWeight: 400 }}>({filtered.length}건)</span>
          {selectedIds.size > 0 && (
            <span style={{ fontSize: '12px', color: 'var(--accent)', fontWeight: 600 }}>
              · {selectedIds.size}건 선택됨
            </span>
          )}
        </h2>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ position: 'relative' }}>
            <FaSearch style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)', fontSize: '13px' }} />
            <input
              style={{ paddingLeft: '30px', width: '220px', fontSize: '13px' }}
              placeholder="검색..."
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-sub)', cursor: filtered.length ? 'pointer' : 'not-allowed', userSelect: 'none' }}>
            <input
              type="checkbox"
              checked={filtered.length > 0 && selectedIds.size === filtered.length}
              ref={(el) => { if (el) el.indeterminate = selectedIds.size > 0 && selectedIds.size < filtered.length }}
              onChange={toggleSelectAll}
              disabled={filtered.length === 0}
            />
            전체선택
          </label>
          {selectedIds.size > 0 && (
            <button onClick={() => setSelectedIds(new Set())} style={{ ...btnStyle, color: 'var(--text-muted)' }} title="선택 해제">
              해제
            </button>
          )}
          {selectedIds.size > 0 && (
            <button
              onClick={deleteSelected}
              style={{ ...btnStyle, background: 'var(--red)', color: '#fff', borderColor: 'var(--red)' }}
              title={`선택 ${selectedIds.size}건 삭제`}>
              <FaTrash /> 선택 {selectedIds.size}건 삭제
            </button>
          )}
          <button
            onClick={exportSelected}
            style={selectedIds.size > 0 ? { ...btnStyle, background: 'var(--accent)', color: '#fff', borderColor: 'var(--accent)' } : btnStyle}
            title={selectedIds.size > 0 ? `선택 ${selectedIds.size}건 내보내기` : '전체 내보내기'}>
            {selectedIds.size > 0 ? <FaFileExport /> : <FaDownload />}
            {selectedIds.size > 0 ? ` 선택 ${selectedIds.size}건 내보내기` : ' 내보내기'}
          </button>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {filtered.map((item) => {
          const isSelected = selectedIds.has(item.id)
          return (
            <div
              key={item.id}
              style={isSelected
                ? { background: 'var(--bg-secondary)', border: '1px solid var(--accent)', boxShadow: '0 0 0 1px var(--accent)', borderRadius: '10px', overflow: 'hidden' }
                : { background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px', overflow: 'hidden' }}
            >
              <div
                style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '12px 16px', cursor: 'pointer' }}
                onClick={() => setExpandedId(expandedId === item.id ? null : item.id)}
              >
                {/* 다중 선택 체크박스 */}
                <input
                  type="checkbox"
                  checked={isSelected}
                  onChange={() => toggleSelect(item.id)}
                  onClick={(e) => e.stopPropagation()}
                  style={{ flexShrink: 0, cursor: 'pointer' }}
                  title="내보내기/삭제 선택"
                />
                <FaStar style={{ color: '#f59e0b', flexShrink: 0 }} />
                <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>{item.typeLabel || item.type}</span>
                <span style={{ flex: 1, fontSize: '13px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {item.title || item.inputContent?.slice(0, 60)}
                </span>
                <button style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '12px' }} onClick={(e) => { e.stopPropagation(); copy(item) }}>
                  {copiedId === item.id ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
                </button>
                <button style={{ background: 'none', border: 'none', color: 'var(--red)', cursor: 'pointer', fontSize: '12px' }} onClick={(e) => { e.stopPropagation(); remove(item.id) }}><FaTrash /></button>
                {expandedId === item.id ? <FaChevronUp style={{ color: 'var(--text-muted)' }} /> : <FaChevronDown style={{ color: 'var(--text-muted)' }} />}
              </div>
              {expandedId === item.id && (
                <div style={{ borderTop: '1px solid var(--border-color)', padding: '16px' }}>
                  {item.inputContent && (
                    <>
                      <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>입력</h4>
                      <pre style={{ background: 'var(--bg-primary)', padding: '10px', borderRadius: '6px', fontSize: '12px', marginBottom: '12px', whiteSpace: 'pre-wrap', maxHeight: '200px', overflow: 'auto' }}>
                        {item.inputContent}
                      </pre>
                    </>
                  )}
                  <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>결과</h4>
                  <div className="markdown-body" style={{ fontSize: '13px' }}>
                    <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownCodeComponents}>{item.outputContent || '_(내용 없음)_'}</ReactMarkdown>
                  </div>
                </div>
              )}
            </div>
          )
        })}
        {filtered.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
            <FaStar style={{ fontSize: '36px', opacity: 0.3, marginBottom: '12px' }} />
            <p>{filter ? '검색 결과가 없습니다.' : '즐겨찾기가 없습니다.'}</p>
          </div>
        )}
        {/* v4.2.7: 더 보기 — 서버에 다음 페이지가 남아 있을 때만 노출 */}
        {hasMore && (
          <div style={{ textAlign: 'center', padding: '12px' }}>
            <button
              onClick={loadMore}
              disabled={loadingMore}
              style={{
                ...btnStyle,
                padding: '8px 24px',
                opacity: loadingMore ? 0.6 : 1,
                cursor: loadingMore ? 'wait' : 'pointer',
              }}>
              {loadingMore ? '불러오는 중...' : `더 보기 (+${PAGE_SIZE})`}
            </button>
          </div>
        )}
      </div>
    </>
  )
}

const btnStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '6px',
  padding: '6px 14px', borderRadius: '6px', fontSize: '13px',
  border: '1px solid var(--border-color)', background: 'transparent',
  color: 'var(--text-sub)', cursor: 'pointer',
}
