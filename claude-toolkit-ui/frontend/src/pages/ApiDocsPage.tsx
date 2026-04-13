import { FaPlug, FaPlay, FaCopy, FaCheck } from 'react-icons/fa'
import { useState } from 'react'
const endpoints = [
  { method: 'GET', path: '/api/v1/health', desc: '서버 상태 확인' },
  { method: 'GET', path: '/api/v1/auth/me', desc: '현재 사용자 정보' },
  { method: 'POST', path: '/api/v1/auth/login', desc: 'JSON 로그인' },
  { method: 'GET', path: '/api/v1/pipelines', desc: '파이프라인 목록' },
  { method: 'GET', path: '/api/v1/history', desc: '리뷰 이력' },
  { method: 'GET', path: '/api/v1/favorites', desc: '즐겨찾기' },
  { method: 'POST', path: '/stream/init', desc: 'SSE 스트림 초기화' },
  { method: 'GET', path: '/stream/{id}', desc: 'SSE 스트림 수신' },
  { method: 'POST', path: '/chat/send', desc: '채팅 메시지 전송' },
  { method: 'GET', path: '/chat/stream', desc: '채팅 SSE 스트림' },
]

export default function ApiDocsPage() {
  const [result, setResult] = useState('')
  const [testing, setTesting] = useState('')
  const [copied, setCopied] = useState(false)

  const testEndpoint = async (ep: typeof endpoints[0]) => {
    setTesting(ep.path)
    setResult('')
    try {
      const res = await fetch(ep.path, { credentials: 'include' })
      const text = await res.text()
      try { setResult(JSON.stringify(JSON.parse(text), null, 2)) }
      catch { setResult(text) }
    } catch (e) { setResult(`Error: ${e}`) }
    setTesting('')
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaPlug style={{ color: '#3b82f6' }} /> API Playground
      </h2>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', minHeight: '60vh' }}>
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'auto' }}>
          <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border-color)', fontSize: '13px', fontWeight: 600 }}>엔드포인트</div>
          {endpoints.map((ep) => (
            <div key={ep.path + ep.method} style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '8px 14px', borderBottom: '1px solid var(--border-color)', cursor: ep.method === 'GET' ? 'pointer' : 'default' }} onClick={() => ep.method === 'GET' && testEndpoint(ep)}>
              <span style={{ fontSize: '11px', padding: '1px 6px', borderRadius: '3px', fontWeight: 600, background: ep.method === 'GET' ? 'rgba(59,130,246,0.12)' : 'rgba(249,115,22,0.12)', color: ep.method === 'GET' ? '#3b82f6' : '#f97316' }}>{ep.method}</span>
              <code style={{ fontSize: '12px', flex: 1 }}>{ep.path}</code>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{ep.desc}</span>
              {ep.method === 'GET' && <FaPlay style={{ fontSize: '10px', color: 'var(--text-muted)' }} />}
            </div>
          ))}
        </div>
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border-color)', fontSize: '13px', fontWeight: 600, display: 'flex', justifyContent: 'space-between' }}>
            <span>응답 {testing && '(요청 중...)'}</span>
            {result && <button onClick={() => { navigator.clipboard.writeText(result); setCopied(true); setTimeout(() => setCopied(false), 2000) }} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', fontSize: '12px' }}>{copied ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}</button>}
          </div>
          <pre style={{ flex: 1, padding: '14px', fontSize: '12px', overflow: 'auto', margin: 0, whiteSpace: 'pre-wrap', color: result ? 'var(--text-primary)' : 'var(--text-muted)' }}>
            {result || 'GET 엔드포인트를 클릭하면 응답이 여기에 표시됩니다.'}
          </pre>
        </div>
      </div>
    </>
  )
}
