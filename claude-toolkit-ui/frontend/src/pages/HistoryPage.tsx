import { useEffect, useState, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaHistory, FaSearch, FaTrash, FaStar, FaCopy, FaCheck,
  FaChevronDown, FaChevronUp, FaDownload,
} from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'

interface HistoryItem {
  id: number
  menuName: string
  inputText: string
  resultText: string
  createdAt: string
  title?: string
}

export default function HistoryPage() {
  const [items, setItems] = useState<HistoryItem[]>([])
  const [filter, setFilter] = useState('')
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [copiedId, setCopiedId] = useState<number | null>(null)
  const api = useApi()
  const toast = useToast()

  const loadHistory = useCallback(async () => {
    const data = await api.get('/api/v1/history') as HistoryItem[] | null
    if (data) setItems(data)
  }, [])

  useEffect(() => { loadHistory() }, [loadHistory])

  const filtered = items.filter((item) => {
    if (!filter) return true
    const q = filter.toLowerCase()
    return (
      item.menuName?.toLowerCase().includes(q) ||
      item.inputText?.toLowerCase().includes(q) ||
      item.title?.toLowerCase().includes(q)
    )
  })

  const deleteItem = async (id: number) => {
    await fetch(`/history/${id}/delete`, { method: 'POST', credentials: 'include' })
    setItems((prev) => prev.filter((i) => i.id !== id))
    toast.success('삭제되었습니다.')
  }

  const addFavorite = async (item: HistoryItem) => {
    await fetch('/favorites/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ historyId: String(item.id) }),
      credentials: 'include',
    })
    toast.success('즐겨찾기에 추가되었습니다.')
  }

  const copyResult = (item: HistoryItem) => {
    navigator.clipboard.writeText(item.resultText || '')
    setCopiedId(item.id)
    setTimeout(() => setCopiedId(null), 2000)
  }

  const exportAll = () => {
    const md = filtered.map((i) =>
      `## ${i.menuName} — ${i.createdAt}\n\n### 입력\n\`\`\`\n${i.inputText}\n\`\`\`\n\n### 결과\n${i.resultText}\n\n---\n`
    ).join('\n')
    const blob = new Blob([md], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `history_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaHistory style={{ color: '#f59e0b' }} /> 리뷰 이력
          <span style={{ fontSize: '13px', color: 'var(--text-muted)', fontWeight: 400 }}>({filtered.length}건)</span>
        </h2>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <div style={{ position: 'relative' }}>
            <FaSearch style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)', fontSize: '13px' }} />
            <input
              style={{ paddingLeft: '30px', width: '220px', fontSize: '13px' }}
              placeholder="검색..."
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
          <button onClick={exportAll} style={btnStyle} title="전체 내보내기"><FaDownload /> 내보내기</button>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {filtered.map((item) => (
          <div key={item.id} style={cardStyle}>
            <div
              style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '12px 16px', cursor: 'pointer' }}
              onClick={() => setExpandedId(expandedId === item.id ? null : item.id)}
            >
              <span style={badgeStyle}>{item.menuName}</span>
              <span style={{ flex: 1, fontSize: '13px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--text-sub)' }}>
                {item.title || item.inputText?.slice(0, 80)}
              </span>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)', flexShrink: 0 }}>{item.createdAt}</span>
              <div style={{ display: 'flex', gap: '4px' }}>
                <button style={iconBtnStyle} onClick={(e) => { e.stopPropagation(); copyResult(item) }}>
                  {copiedId === item.id ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
                </button>
                <button style={iconBtnStyle} onClick={(e) => { e.stopPropagation(); addFavorite(item) }}><FaStar /></button>
                <button style={{ ...iconBtnStyle, color: 'var(--red)' }} onClick={(e) => { e.stopPropagation(); deleteItem(item.id) }}><FaTrash /></button>
              </div>
              {expandedId === item.id ? <FaChevronUp style={{ color: 'var(--text-muted)' }} /> : <FaChevronDown style={{ color: 'var(--text-muted)' }} />}
            </div>
            {expandedId === item.id && (
              <div style={{ borderTop: '1px solid var(--border-color)', padding: '16px' }}>
                <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>입력</h4>
                <pre style={{ background: 'var(--bg-primary)', padding: '10px', borderRadius: '6px', fontSize: '12px', marginBottom: '12px', whiteSpace: 'pre-wrap', maxHeight: '150px', overflow: 'auto' }}>
                  {item.inputText}
                </pre>
                <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>결과</h4>
                <div className="markdown-body" style={{ fontSize: '13px' }}>
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{item.resultText || ''}</ReactMarkdown>
                </div>
              </div>
            )}
          </div>
        ))}
        {filtered.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
            <FaHistory style={{ fontSize: '36px', opacity: 0.3, marginBottom: '12px' }} />
            <p>이력이 없습니다.</p>
          </div>
        )}
      </div>
    </>
  )
}

const cardStyle: React.CSSProperties = {
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '10px', overflow: 'hidden',
}
const badgeStyle: React.CSSProperties = {
  fontSize: '11px', padding: '2px 8px', borderRadius: '4px',
  background: 'var(--accent-subtle)', color: 'var(--accent)', flexShrink: 0,
}
const iconBtnStyle: React.CSSProperties = {
  background: 'none', border: 'none', color: 'var(--text-muted)',
  cursor: 'pointer', padding: '4px', fontSize: '12px',
}
const btnStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '6px',
  padding: '6px 14px', borderRadius: '6px', fontSize: '13px',
  border: '1px solid var(--border-color)', background: 'transparent',
  color: 'var(--text-sub)', cursor: 'pointer',
}
