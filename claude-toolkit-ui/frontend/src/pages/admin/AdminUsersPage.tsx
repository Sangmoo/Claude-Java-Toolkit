import { useEffect, useState, useCallback } from 'react'
import { FaUsersCog, FaEdit, FaPlus, FaTimes, FaKey, FaSave } from 'react-icons/fa'
import { useApi } from '../../hooks/useApi'
import { useToast } from '../../hooks/useToast'

interface User {
  id: number
  username: string
  displayName?: string
  email?: string
  phone?: string
  role: string
  enabled: boolean
  personalApiKey?: string
  rateLimitPerMinute?: number
  rateLimitPerHour?: number
  dailyApiLimit?: number
  monthlyApiLimit?: number
  lastLoginAt?: string
}

export default function AdminUsersPage() {
  const [users, setUsers] = useState<User[]>([])
  const [editing, setEditing] = useState<User | null>(null)
  const [creating, setCreating] = useState(false)
  const [pwModal, setPwModal] = useState<User | null>(null)
  const [newPw, setNewPw] = useState('')
  const api = useApi()
  const toast = useToast()

  const reload = useCallback(() => {
    api.get('/api/v1/admin/users').then((data) => { if (data) setUsers(data as User[]) })
  }, [])

  useEffect(() => { reload() }, [])

  const toggleEnabled = async (u: User) => {
    await fetch(`/admin/users/${u.id}/toggle`, { method: 'POST', credentials: 'include' })
    setUsers((prev) => prev.map((x) => x.id === u.id ? { ...x, enabled: !x.enabled } : x))
    toast.success(u.enabled ? '비활성화됨' : '활성화됨')
  }

  const saveEdit = async () => {
    if (!editing) return
    try {
      const params = new URLSearchParams({
        displayName: editing.displayName || '',
        email: editing.email || '',
        phone: editing.phone || '',
        role: editing.role,
        personalApiKey: editing.personalApiKey || '',
        rateLimitPerMinute: String(editing.rateLimitPerMinute || 0),
        rateLimitPerHour: String(editing.rateLimitPerHour || 0),
        dailyApiLimit: String(editing.dailyApiLimit || 0),
        monthlyApiLimit: String(editing.monthlyApiLimit || 0),
      })
      const res = await fetch(`/admin/users/${editing.id}/update`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params,
        credentials: 'include',
      })
      if (res.ok) {
        toast.success('저장되었습니다.')
        setEditing(null)
        reload()
      } else toast.error('저장 실패')
    } catch { toast.error('오류') }
  }

  const changePassword = async () => {
    if (!pwModal || newPw.length < 8) { toast.error('8자 이상 입력'); return }
    try {
      const res = await fetch(`/admin/users/${pwModal.id}/change-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ newPassword: newPw }),
        credentials: 'include',
      })
      if (res.ok) {
        toast.success('비밀번호 변경 완료')
        setPwModal(null); setNewPw('')
      } else toast.error('변경 실패')
    } catch { toast.error('오류') }
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaUsersCog style={{ color: '#ef4444' }} /> 사용자 관리
        </h2>
        <button onClick={() => setCreating(true)} style={primaryBtn}><FaPlus /> 사용자 추가</button>
      </div>

      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
          <thead>
            <tr style={{ background: 'var(--bg-tertiary)' }}>
              <th style={thStyle}>ID</th>
              <th style={thStyle}>아이디</th>
              <th style={thStyle}>사용자명</th>
              <th style={thStyle}>이메일</th>
              <th style={thStyle}>전화번호</th>
              <th style={thStyle}>역할</th>
              <th style={thStyle}>상태</th>
              <th style={thStyle}>일일/월간 한도</th>
              <th style={thStyle}>작업</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                <td style={tdStyle}>{u.id}</td>
                <td style={{ ...tdStyle, fontWeight: 600 }}>{u.username}</td>
                <td style={tdStyle}>{u.displayName || '-'}</td>
                <td style={tdStyle}>{u.email || '-'}</td>
                <td style={tdStyle}>{u.phone || '-'}</td>
                <td style={tdStyle}>
                  <span style={{
                    padding: '2px 8px', borderRadius: '10px', fontSize: '11px', fontWeight: 600,
                    background: u.role === 'ADMIN' ? 'rgba(239,68,68,0.12)' : u.role === 'REVIEWER' ? 'rgba(139,92,246,0.12)' : 'var(--accent-subtle)',
                    color: u.role === 'ADMIN' ? 'var(--red)' : u.role === 'REVIEWER' ? 'var(--purple)' : 'var(--accent)',
                  }}>
                    {u.role}
                  </span>
                </td>
                <td style={tdStyle}>
                  <button onClick={() => toggleEnabled(u)} style={{
                    padding: '2px 10px', borderRadius: '10px', fontSize: '11px', cursor: 'pointer', border: 'none',
                    background: u.enabled ? 'rgba(34,197,94,0.12)' : 'rgba(148,163,184,0.12)',
                    color: u.enabled ? 'var(--green)' : 'var(--text-muted)', fontWeight: 600,
                  }}>
                    {u.enabled ? '활성' : '비활성'}
                  </button>
                </td>
                <td style={tdStyle}>{u.dailyApiLimit || '∞'} / {u.monthlyApiLimit || '∞'}</td>
                <td style={tdStyle}>
                  <div style={{ display: 'flex', gap: '4px' }}>
                    <button onClick={() => setEditing({ ...u })} style={miniBtn} title="정보 수정"><FaEdit /></button>
                    <button onClick={() => setPwModal(u)} style={miniBtn} title="비밀번호 변경"><FaKey /></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 정보 수정 모달 */}
      {editing && (
        <Modal title={`사용자 정보 수정 - ${editing.username}`} onClose={() => setEditing(null)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <Field label="사용자명"><input value={editing.displayName || ''} onChange={(e) => setEditing({ ...editing, displayName: e.target.value })} style={inputSt} /></Field>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
              <Field label="이메일"><input type="email" value={editing.email || ''} onChange={(e) => setEditing({ ...editing, email: e.target.value })} style={inputSt} /></Field>
              <Field label="전화번호"><input value={editing.phone || ''} onChange={(e) => setEditing({ ...editing, phone: e.target.value })} style={inputSt} /></Field>
            </div>
            <Field label="역할">
              <div style={{ display: 'flex', gap: '4px' }}>
                {['ADMIN', 'REVIEWER', 'VIEWER'].map((r) => (
                  <button key={r} onClick={() => setEditing({ ...editing, role: r })} style={chipBtn(editing.role === r)}>{r}</button>
                ))}
              </div>
            </Field>
            <Field label="개인 API 키 (선택)">
              <input type="password" placeholder="sk-ant-... (변경 시만 입력)" value={editing.personalApiKey || ''} onChange={(e) => setEditing({ ...editing, personalApiKey: e.target.value })} style={inputSt} />
            </Field>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
              <Field label="분당 요청 (0=무제한)"><input type="number" value={editing.rateLimitPerMinute || 0} onChange={(e) => setEditing({ ...editing, rateLimitPerMinute: parseInt(e.target.value) || 0 })} style={inputSt} /></Field>
              <Field label="시간당 요청"><input type="number" value={editing.rateLimitPerHour || 0} onChange={(e) => setEditing({ ...editing, rateLimitPerHour: parseInt(e.target.value) || 0 })} style={inputSt} /></Field>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
              <Field label="일일 API 한도"><input type="number" value={editing.dailyApiLimit || 0} onChange={(e) => setEditing({ ...editing, dailyApiLimit: parseInt(e.target.value) || 0 })} style={inputSt} /></Field>
              <Field label="월간 API 한도"><input type="number" value={editing.monthlyApiLimit || 0} onChange={(e) => setEditing({ ...editing, monthlyApiLimit: parseInt(e.target.value) || 0 })} style={inputSt} /></Field>
            </div>
            <button onClick={saveEdit} style={{ ...primaryBtn, marginTop: '8px', justifyContent: 'center' }}><FaSave /> 저장</button>
          </div>
        </Modal>
      )}

      {/* 비밀번호 변경 모달 */}
      {pwModal && (
        <Modal title={`비밀번호 변경 - ${pwModal.username}`} onClose={() => { setPwModal(null); setNewPw('') }}>
          <Field label="새 비밀번호 (8자 이상)">
            <input type="password" value={newPw} onChange={(e) => setNewPw(e.target.value)} style={inputSt} autoFocus />
          </Field>
          <button onClick={changePassword} style={{ ...primaryBtn, marginTop: '12px', justifyContent: 'center', width: '100%' }}><FaKey /> 변경</button>
        </Modal>
      )}

      {/* 신규 사용자 모달 */}
      {creating && <CreateUserModal onClose={() => setCreating(false)} onCreated={() => { setCreating(false); reload() }} />}
    </>
  )
}

function CreateUserModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('VIEWER')
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const toast = useToast()

  const create = async () => {
    if (!username || !password) { toast.error('아이디와 비밀번호 필수'); return }
    try {
      const res = await fetch('/admin/users/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ username, password, role, displayName, email, phone }),
        credentials: 'include',
      })
      const d = await res.json()
      if (d.success) { toast.success('사용자 추가됨'); onCreated() }
      else toast.error(d.error || '추가 실패')
    } catch { toast.error('오류') }
  }

  return (
    <Modal title="사용자 추가" onClose={onClose}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <Field label="아이디 *"><input value={username} onChange={(e) => setUsername(e.target.value)} style={inputSt} autoFocus /></Field>
        <Field label="비밀번호 *"><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} style={inputSt} /></Field>
        <Field label="사용자명"><input value={displayName} onChange={(e) => setDisplayName(e.target.value)} style={inputSt} /></Field>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
          <Field label="이메일"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} style={inputSt} /></Field>
          <Field label="전화번호"><input value={phone} onChange={(e) => setPhone(e.target.value)} style={inputSt} /></Field>
        </div>
        <Field label="역할">
          <div style={{ display: 'flex', gap: '4px' }}>
            {['ADMIN', 'REVIEWER', 'VIEWER'].map((r) => (
              <button key={r} onClick={() => setRole(r)} style={chipBtn(role === r)}>{r}</button>
            ))}
          </div>
        </Field>
        <button onClick={create} style={{ ...primaryBtn, marginTop: '8px', justifyContent: 'center' }}><FaPlus /> 추가</button>
      </div>
    </Modal>
  )
}

function Modal({ title, children, onClose }: { title: string; children: React.ReactNode; onClose: () => void }) {
  return (
    <div style={overlayStyle} onClick={onClose}>
      <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{title}</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', fontSize: '16px' }}><FaTimes /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: 'block', fontSize: '11px', color: 'var(--text-muted)', marginBottom: '3px', fontWeight: 600 }}>{label}</label>{children}</div>
}

const chipBtn = (active: boolean): React.CSSProperties => ({
  padding: '5px 14px', borderRadius: '16px', fontSize: '12px', cursor: 'pointer',
  border: `1px solid ${active ? 'var(--accent)' : 'var(--border-color)'}`,
  background: active ? 'var(--accent-subtle)' : 'transparent',
  color: active ? 'var(--accent)' : 'var(--text-sub)', fontWeight: active ? 600 : 400,
})

const thStyle: React.CSSProperties = { textAlign: 'left', padding: '10px 14px', fontWeight: 600, color: 'var(--text-muted)', fontSize: '12px' }
const tdStyle: React.CSSProperties = { padding: '10px 14px' }
const miniBtn: React.CSSProperties = { background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
const primaryBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', fontSize: '13px', cursor: 'pointer', fontWeight: 600 }
const overlayStyle: React.CSSProperties = { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500 }
const modalStyle: React.CSSProperties = { background: 'var(--bg-secondary)', borderRadius: '16px', border: '1px solid var(--border-color)', padding: '24px', width: 'min(550px, 90vw)', maxHeight: '85vh', overflowY: 'auto' }
const inputSt: React.CSSProperties = { width: '100%', padding: '8px 10px', fontSize: '13px' }
