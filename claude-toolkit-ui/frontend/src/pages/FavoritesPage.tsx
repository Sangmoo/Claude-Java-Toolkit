import { useEffect, useState, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { FaStar, FaTrash, FaChevronDown, FaChevronUp, FaCopy, FaCheck } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'

interface FavoriteItem {
  id: number
  menuName: string
  inputText: string
  resultText: string
  createdAt: string
  title?: string
}

export default function FavoritesPage() {
  const [items, setItems] = useState<FavoriteItem[]>([])
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [copiedId, setCopiedId] = useState<number | null>(null)
  const api = useApi()
  const toast = useToast()

  const load = useCallback(async () => {
    const data = await api.get('/api/v1/favorites') as FavoriteItem[] | null
    if (data) setItems(data)
  }, [])

  useEffect(() => { load() }, [load])

  const remove = async (id: number) => {
    await fetch(`/favorites/${id}/delete`, { method: 'POST', credentials: 'include' })
    setItems((prev) => prev.filter((i) => i.id !== id))
    toast.success('즐겨찾기에서 제거되었습니다.')
  }

  const copy = (item: FavoriteItem) => {
    navigator.clipboard.writeText(item.resultText || '')
    setCopiedId(item.id)
    setTimeout(() => setCopiedId(null), 2000)
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaStar style={{ color: '#f59e0b' }} /> 즐겨찾기
        <span style={{ fontSize: '13px', color: 'var(--text-muted)', fontWeight: 400 }}>({items.length}건)</span>
      </h2>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {items.map((item) => (
          <div key={item.id} style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px', overflow: 'hidden' }}>
            <div
              style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '12px 16px', cursor: 'pointer' }}
              onClick={() => setExpandedId(expandedId === item.id ? null : item.id)}
            >
              <FaStar style={{ color: '#f59e0b', flexShrink: 0 }} />
              <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>{item.menuName}</span>
              <span style={{ flex: 1, fontSize: '13px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {item.title || item.inputText?.slice(0, 60)}
              </span>
              <button style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '12px' }} onClick={(e) => { e.stopPropagation(); copy(item) }}>
                {copiedId === item.id ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
              </button>
              <button style={{ background: 'none', border: 'none', color: 'var(--red)', cursor: 'pointer', fontSize: '12px' }} onClick={(e) => { e.stopPropagation(); remove(item.id) }}><FaTrash /></button>
              {expandedId === item.id ? <FaChevronUp style={{ color: 'var(--text-muted)' }} /> : <FaChevronDown style={{ color: 'var(--text-muted)' }} />}
            </div>
            {expandedId === item.id && (
              <div style={{ borderTop: '1px solid var(--border-color)', padding: '16px' }}>
                <div className="markdown-body" style={{ fontSize: '13px' }}>
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{item.resultText || ''}</ReactMarkdown>
                </div>
              </div>
            )}
          </div>
        ))}
        {items.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
            <FaStar style={{ fontSize: '36px', opacity: 0.3, marginBottom: '12px' }} />
            <p>즐겨찾기가 없습니다.</p>
          </div>
        )}
      </div>
    </>
  )
}
