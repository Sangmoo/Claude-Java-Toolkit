import { useState, useCallback } from 'react'
import { FaSearch } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'

interface SearchResult {
  id: number
  menuName: string
  title: string
  snippet: string
  createdAt: string
}

export default function SearchPage() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchResult[]>([])
  const [searched, setSearched] = useState(false)
  const api = useApi()

  const search = useCallback(async () => {
    if (!query.trim()) return
    const data = await api.get(`/api/v1/search?q=${encodeURIComponent(query)}`) as SearchResult[] | null
    setResults(data || [])
    setSearched(true)
  }, [query, api])

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaSearch /> 검색
      </h2>

      <div style={{ display: 'flex', gap: '8px', marginBottom: '24px' }}>
        <input
          style={{ flex: 1, padding: '10px 14px', fontSize: '14px' }}
          placeholder="이력, 즐겨찾기, 분석 결과를 검색하세요..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && search()}
          autoFocus
        />
        <button
          onClick={search}
          style={{
            padding: '10px 20px', borderRadius: '8px',
            background: 'var(--accent)', color: '#fff', border: 'none',
            cursor: 'pointer', fontSize: '14px', fontWeight: 600,
          }}
        >
          <FaSearch /> 검색
        </button>
      </div>

      {results.map((r) => (
        <div key={r.id} style={{
          padding: '12px 16px', marginBottom: '8px', borderRadius: '10px',
          background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
            <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>{r.menuName}</span>
            <span style={{ fontSize: '14px', fontWeight: 600 }}>{r.title}</span>
            <span style={{ marginLeft: 'auto', fontSize: '12px', color: 'var(--text-muted)' }}>{r.createdAt}</span>
          </div>
          <p style={{ fontSize: '13px', color: 'var(--text-sub)', margin: 0 }}>{r.snippet}</p>
        </div>
      ))}

      {searched && results.length === 0 && (
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
          검색 결과가 없습니다.
        </div>
      )}
    </>
  )
}
