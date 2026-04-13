import { Link } from 'react-router-dom'
import { FaExclamationTriangle } from 'react-icons/fa'

export default function NotFoundPage() {
  return (
    <div style={{ minHeight: '60vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '16px', color: 'var(--text-muted)' }}>
      <FaExclamationTriangle style={{ fontSize: '48px', color: 'var(--yellow)', opacity: 0.5 }} />
      <h2 style={{ fontSize: '20px', fontWeight: 700, color: 'var(--text-primary)' }}>404 — 페이지를 찾을 수 없습니다</h2>
      <p style={{ fontSize: '14px' }}>요청하신 페이지가 존재하지 않거나 이동되었습니다.</p>
      <Link to="/" style={{ padding: '8px 20px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', textDecoration: 'none', fontSize: '14px', fontWeight: 600 }}>홈으로 돌아가기</Link>
    </div>
  )
}
