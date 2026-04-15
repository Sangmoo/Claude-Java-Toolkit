import { useEffect, useState } from 'react'
import { FaUserEdit, FaSave, FaPalette, FaCheck } from 'react-icons/fa'
import { useToast } from '../hooks/useToast'
import { useAuthStore } from '../stores/authStore'
import { useThemeStore, COLOR_PRESETS, type ColorPreset } from '../stores/themeStore'

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

      {/* v4.2.8: 컬러 프리셋 선택 */}
      <ColorPresetPicker />
    </>
  )
}

/** v4.2.8 — 컬러 프리셋 선택 UI */
function ColorPresetPicker() {
  const preset    = useThemeStore((s) => s.preset)
  const setPreset = useThemeStore((s) => s.setPreset)
  const setTheme  = useThemeStore((s) => s.setTheme)
  return (
    <div style={{ marginTop: '24px', maxWidth: '680px' }}>
      <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaPalette style={{ color: 'var(--accent)' }} /> 컬러 프리셋
      </h3>
      <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '14px' }}>
        원하는 색상 조합을 선택하세요. 프리셋에 따라 권장 다크/라이트 모드가 자동 적용됩니다.
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '10px' }}>
        {COLOR_PRESETS.map((p) => {
          const isActive = preset === p.id
          const swatches = getSwatchColors(p.id)
          return (
            <button
              key={p.id}
              onClick={() => {
                setPreset(p.id as ColorPreset)
                setTheme(p.suggestedTheme)
              }}
              style={{
                display: 'flex', flexDirection: 'column', alignItems: 'stretch', gap: '6px',
                padding: '12px', borderRadius: '10px', cursor: 'pointer',
                background: 'var(--bg-secondary)',
                border: isActive ? '2px solid var(--accent)' : '1px solid var(--border-color)',
                boxShadow: isActive ? '0 0 0 3px var(--accent-subtle)' : 'none',
                transition: 'all 0.15s',
                textAlign: 'left',
              }}>
              <div style={{ display: 'flex', gap: '4px', height: '22px' }}>
                {swatches.map((c, i) => (
                  <div key={i} style={{
                    flex: 1, background: c, borderRadius: '4px',
                    border: '1px solid rgba(0,0,0,0.1)',
                  }} />
                ))}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '6px' }}>
                <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-primary)' }}>{p.name}</div>
                {isActive && <FaCheck style={{ color: 'var(--accent)', fontSize: '11px' }} />}
              </div>
              <div style={{ fontSize: '10px', color: 'var(--text-muted)' }}>{p.hint}</div>
            </button>
          )
        })}
      </div>
    </div>
  )
}

// 프리셋별 미리보기 스와치 (bg, accent, text, border 순)
function getSwatchColors(preset: ColorPreset): string[] {
  switch (preset) {
    case 'dracula':         return ['#282a36', '#bd93f9', '#f8f8f2', '#44475a']
    case 'nord':            return ['#2e3440', '#88c0d0', '#eceff4', '#4c566a']
    case 'solarized-dark':  return ['#002b36', '#b58900', '#eee8d5', '#586e75']
    case 'solarized-light': return ['#fdf6e3', '#b58900', '#073642', '#93a1a1']
    case 'github-light':    return ['#ffffff', '#0969da', '#1f2328', '#d0d7de']
    case 'monokai':         return ['#272822', '#f92672', '#f8f8f2', '#49483e']
    default:                return ['#0f172a', '#f97316', '#e2e8f0', '#334155']
  }
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
