import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { FaShareAlt, FaCopy, FaCheck } from 'react-icons/fa'

interface SharedResult { menuName: string; inputText: string; resultText: string; sharedAt: string }

export default function ShareViewPage() {
  const { token } = useParams<{ token: string }>()
  const [data, setData] = useState<SharedResult | null>(null)
  const [error, setError] = useState(false)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    fetch(`/share/${token}`, { headers: { 'Accept': 'application/json' } })
      .then((r) => { if (!r.ok) throw new Error(); return r.json() })
      .then((d) => setData(d.data ?? d))
      .catch(() => setError(true))
  }, [token])

  if (error) return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-primary)', color: 'var(--text-muted)' }}>
      <div style={{ textAlign: 'center' }}>
        <FaShareAlt style={{ fontSize: '40px', opacity: 0.3, marginBottom: '12px' }} />
        <p>공유 링크가 만료되었거나 존재하지 않습니다.</p>
      </div>
    </div>
  )

  if (!data) return <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>로딩 중...</div>

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', padding: '24px' }}>
      <div style={{ maxWidth: '800px', margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '20px' }}>
          <FaShareAlt style={{ color: 'var(--accent)' }} />
          <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--accent-subtle)', color: 'var(--accent)' }}>{data.menuName}</span>
          <span style={{ fontSize: '12px', color: 'var(--text-muted)', marginLeft: 'auto' }}>{data.sharedAt}</span>
          <button onClick={() => { navigator.clipboard.writeText(data.resultText); setCopied(true); setTimeout(() => setCopied(false), 2000) }} style={{ background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 10px', cursor: 'pointer', fontSize: '12px', color: 'var(--text-sub)' }}>
            {copied ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />} 복사
          </button>
        </div>
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px' }}>
          <div className="markdown-body"><ReactMarkdown remarkPlugins={[remarkGfm]}>{data.resultText}</ReactMarkdown></div>
        </div>
      </div>
    </div>
  )
}
