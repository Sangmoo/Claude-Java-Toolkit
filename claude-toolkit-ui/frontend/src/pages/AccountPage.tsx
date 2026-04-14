import { useEffect, useState } from 'react'
import { FaUserEdit, FaSave } from 'react-icons/fa'
import { useToast } from '../hooks/useToast'
import { useAuthStore } from '../stores/authStore'

export default function AccountPage() {
  const user = useAuthStore((s) => s.user)
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [saving, setSaving] = useState(false)
  const toast = useToast()

  useEffect(() => {
    fetch('/account/me', { credentials: 'include' })
      .then((r) => r.ok ? r.json() : null)
      .then((d) => {
        if (d && d.success) {
          setDisplayName(d.displayName || '')
          setEmail(d.email || '')
          setPhone(d.phone || '')
        }
      })
      .catch(() => {})
  }, [])

  const save = async () => {
    setSaving(true)
    try {
      const res = await fetch('/account/save-profile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ displayName, email, phone }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (res.ok && d && d.success) toast.success('정보가 저장되었습니다.')
      else toast.error(d?.error || '저장 실패')
    } catch { toast.error('오류 발생') }
    setSaving(false)
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaUserEdit style={{ color: '#3b82f6' }} /> 내 정보 수정
      </h2>

      <div style={{ maxWidth: '500px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <Field label="사용자명 (변경 불가)">
            <input value={user?.username || ''} disabled style={{ ...inputSt, background: 'var(--bg-primary)', color: 'var(--text-muted)' }} />
          </Field>
          <Field label="역할">
            <input value={user?.role || ''} disabled style={{ ...inputSt, background: 'var(--bg-primary)', color: 'var(--text-muted)' }} />
          </Field>
          <Field label="표시 이름">
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="홍길동" style={inputSt} />
          </Field>
          <Field label="이메일">
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="user@example.com" style={inputSt} />
          </Field>
          <Field label="전화번호">
            <input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="010-1234-5678" style={inputSt} />
          </Field>
          <button onClick={save} disabled={saving} style={{
            display: 'flex', alignItems: 'center', gap: '6px', justifyContent: 'center',
            padding: '12px', borderRadius: '8px',
            background: 'var(--accent)', color: '#fff', border: 'none',
            fontSize: '14px', fontWeight: 600, cursor: 'pointer',
            opacity: saving ? 0.6 : 1,
          }}>
            <FaSave /> {saving ? '저장 중...' : '정보 저장'}
          </button>
        </div>
      </div>

      <div style={{ marginTop: '16px', padding: '14px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px', fontSize: '12px', color: 'var(--text-muted)', maxWidth: '500px' }}>
        💡 비밀번호 변경은 좌측 메뉴의 <strong>보안 설정</strong> 또는 하단 🔑 아이콘을 클릭하세요.
      </div>
    </>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '4px' }}>{label}</label>
      {children}
    </div>
  )
}

const inputSt: React.CSSProperties = { width: '100%', padding: '10px 14px', fontSize: '14px' }
