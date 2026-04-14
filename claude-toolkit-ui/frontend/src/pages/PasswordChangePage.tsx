import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { FaKey, FaShieldAlt, FaClock, FaExclamationTriangle } from 'react-icons/fa'
import { useToast } from '../hooks/useToast'

export default function PasswordChangePage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const toast = useToast()
  const expired     = params.get('expired') === 'true'
  const mustChange  = params.get('mustChange') === 'true'

  const [currentPw, setCurrentPw] = useState('')
  const [newPw, setNewPw] = useState('')
  const [confirmPw, setConfirmPw] = useState('')
  const [busy, setBusy] = useState(false)

  const change = async () => {
    if (!currentPw) { toast.error('현재 비밀번호를 입력하세요.'); return }
    if (newPw.length < 8) { toast.error('새 비밀번호는 8자 이상이어야 합니다.'); return }
    if (newPw !== confirmPw) { toast.error('새 비밀번호가 일치하지 않습니다.'); return }
    setBusy(true)
    try {
      const res = await fetch('/account/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ currentPassword: currentPw, newPassword: newPw }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (res.ok && d?.success) {
        toast.success('비밀번호가 변경되었습니다.')
        setTimeout(() => navigate('/'), 800)
      } else {
        toast.error(d?.error || '변경 실패')
      }
    } catch { toast.error('오류 발생') }
    setBusy(false)
  }

  const snooze = async () => {
    if (mustChange) { toast.error('초기 비밀번호는 반드시 변경해야 합니다.'); return }
    setBusy(true)
    try {
      const res = await fetch('/account/snooze-password', {
        method: 'POST',
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (res.ok && d?.success) {
        toast.success('다음에 변경합니다. 90일 카운트가 1일부터 다시 시작됩니다.')
        setTimeout(() => navigate('/'), 800)
      } else {
        toast.error(d?.error || '연기 실패')
      }
    } catch { toast.error('오류 발생') }
    setBusy(false)
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px', background: 'var(--bg-primary)' }}>
      <div style={{ maxWidth: '480px', width: '100%', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '16px', padding: '32px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
          <FaShieldAlt style={{ color: mustChange ? '#ef4444' : '#f59e0b', fontSize: '24px' }} />
          <h2 style={{ fontSize: '20px', fontWeight: 700, margin: 0 }}>
            {mustChange ? '초기 비밀번호 변경' : expired ? '비밀번호 변경 권고' : '비밀번호 변경'}
          </h2>
        </div>

        {(expired || mustChange) && (
          <div style={{
            display: 'flex', alignItems: 'flex-start', gap: '8px',
            padding: '12px', borderRadius: '8px', marginBottom: '20px',
            background: mustChange ? 'rgba(239,68,68,0.08)' : 'rgba(245,158,11,0.08)',
            border: `1px solid ${mustChange ? 'rgba(239,68,68,0.3)' : 'rgba(245,158,11,0.3)'}`,
            fontSize: '13px', color: 'var(--text-sub)', lineHeight: 1.6,
          }}>
            <FaExclamationTriangle style={{ color: mustChange ? '#ef4444' : '#f59e0b', marginTop: '2px', flexShrink: 0 }} />
            <div>
              {mustChange
                ? '관리자가 발급한 초기 비밀번호입니다. 보안을 위해 반드시 변경 후 서비스를 이용해주세요.'
                : '마지막 비밀번호 변경으로부터 90일이 지났습니다. 보안을 위해 비밀번호를 변경해주세요. 지금 변경이 어렵다면 "다음에 변경하기"를 선택하면 1일부터 다시 카운팅됩니다.'}
            </div>
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <div>
            <label style={labelSt}>현재 비밀번호</label>
            <input type="password" value={currentPw} onChange={(e) => setCurrentPw(e.target.value)} style={inputSt} autoFocus />
          </div>
          <div>
            <label style={labelSt}>새 비밀번호 (8자 이상)</label>
            <input type="password" value={newPw} onChange={(e) => setNewPw(e.target.value)} style={inputSt} />
          </div>
          <div>
            <label style={labelSt}>새 비밀번호 확인</label>
            <input type="password" value={confirmPw} onChange={(e) => setConfirmPw(e.target.value)} style={inputSt} />
          </div>

          <button onClick={change} disabled={busy} style={{
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
            padding: '12px', borderRadius: '8px',
            background: 'var(--accent)', color: '#fff', border: 'none',
            fontSize: '14px', fontWeight: 600, cursor: busy ? 'not-allowed' : 'pointer',
            opacity: busy ? 0.6 : 1, marginTop: '8px',
          }}>
            <FaKey /> 비밀번호 변경
          </button>

          {!mustChange && (
            <button onClick={snooze} disabled={busy} style={{
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
              padding: '10px', borderRadius: '8px',
              background: 'transparent', color: 'var(--text-sub)',
              border: '1px solid var(--border-color)',
              fontSize: '13px', cursor: busy ? 'not-allowed' : 'pointer',
              opacity: busy ? 0.6 : 1,
            }}>
              <FaClock /> 다음에 변경하기 (1일부터 다시 카운팅)
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

const labelSt: React.CSSProperties = { display: 'block', fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '4px' }
const inputSt: React.CSSProperties = { width: '100%', padding: '10px 14px', fontSize: '14px', borderRadius: '6px', border: '1px solid var(--border-color)', background: 'var(--bg-primary)', color: 'var(--text-primary)' }
