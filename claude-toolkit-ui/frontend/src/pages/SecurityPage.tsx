import { FaShieldAlt, FaKey, FaLock } from 'react-icons/fa'
import { useToast } from '../hooks/useToast'
import { useState } from 'react'

export default function SecurityPage() {
  const [currentPw, setCurrentPw] = useState('')
  const [newPw, setNewPw] = useState('')
  const [confirmPw, setConfirmPw] = useState('')
  const toast = useToast()

  const changePw = async () => {
    if (newPw !== confirmPw) { toast.error('새 비밀번호가 일치하지 않습니다.'); return }
    if (newPw.length < 8) { toast.error('비밀번호는 8자 이상이어야 합니다.'); return }
    try {
      const res = await fetch('/account/password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ currentPassword: currentPw, newPassword: newPw }),
        credentials: 'include',
      })
      if (res.ok) { toast.success('비밀번호가 변경되었습니다.'); setCurrentPw(''); setNewPw(''); setConfirmPw('') }
      else toast.error('비밀번호 변경 실패')
    } catch { toast.error('오류 발생') }
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaShieldAlt style={{ color: '#8b5cf6' }} /> 보안 설정
      </h2>
      <div style={{ maxWidth: '500px' }}>
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px', marginBottom: '16px' }}>
          <h3 style={{ fontSize: '15px', fontWeight: 600, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}><FaKey /> 비밀번호 변경</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <input type="password" placeholder="현재 비밀번호" value={currentPw} onChange={(e) => setCurrentPw(e.target.value)} style={{ width: '100%' }} />
            <input type="password" placeholder="새 비밀번호 (8자 이상)" value={newPw} onChange={(e) => setNewPw(e.target.value)} style={{ width: '100%' }} />
            <input type="password" placeholder="새 비밀번호 확인" value={confirmPw} onChange={(e) => setConfirmPw(e.target.value)} style={{ width: '100%' }} />
            <button onClick={changePw} style={{ padding: '10px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '14px', fontWeight: 600 }}><FaLock /> 변경</button>
          </div>
        </div>
      </div>
    </>
  )
}
