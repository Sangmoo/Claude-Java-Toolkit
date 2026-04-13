import { useEffect, useState } from 'react'
import { FaUsersCog, FaEdit, FaPlus } from 'react-icons/fa'
import { useApi } from '../../hooks/useApi'
import { useToast } from '../../hooks/useToast'

interface User { id: number; username: string; role: string; enabled: boolean; dailyApiLimit: number; monthlyApiLimit: number }

export default function AdminUsersPage() {
  const [users, setUsers] = useState<User[]>([])
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/admin/users?format=json') as User[] | null
      if (data) setUsers(data)
    }
    load()
  }, [api])

  const toggleEnabled = async (u: User) => {
    await fetch(`/admin/users/${u.id}/toggle`, { method: 'POST', credentials: 'include' })
    setUsers((prev) => prev.map((x) => x.id === u.id ? { ...x, enabled: !x.enabled } : x))
    toast.success(u.enabled ? '비활성화됨' : '활성화됨')
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaUsersCog style={{ color: '#ef4444' }} /> 사용자 관리
        </h2>
        <button style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', fontSize: '13px', cursor: 'pointer' }}>
          <FaPlus /> 사용자 추가
        </button>
      </div>

      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
          <thead>
            <tr style={{ background: 'var(--bg-tertiary)' }}>
              <th style={thStyle}>사용자명</th>
              <th style={thStyle}>역할</th>
              <th style={thStyle}>상태</th>
              <th style={thStyle}>일일 한도</th>
              <th style={thStyle}>월간 한도</th>
              <th style={thStyle}>작업</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                <td style={tdStyle}>{u.username}</td>
                <td style={tdStyle}><span style={{ padding: '2px 8px', borderRadius: '4px', background: u.role === 'ADMIN' ? 'rgba(239,68,68,0.12)' : 'var(--accent-subtle)', color: u.role === 'ADMIN' ? 'var(--red)' : 'var(--accent)', fontSize: '11px' }}>{u.role}</span></td>
                <td style={tdStyle}><span style={{ color: u.enabled ? 'var(--green)' : 'var(--red)' }}>{u.enabled ? '활성' : '비활성'}</span></td>
                <td style={tdStyle}>{u.dailyApiLimit || '∞'}</td>
                <td style={tdStyle}>{u.monthlyApiLimit || '∞'}</td>
                <td style={tdStyle}>
                  <div style={{ display: 'flex', gap: '4px' }}>
                    <button style={iconBtn}><FaEdit /></button>
                    <button style={{ ...iconBtn, color: u.enabled ? 'var(--red)' : 'var(--green)' }} onClick={() => toggleEnabled(u)}>{u.enabled ? '비활성화' : '활성화'}</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

const thStyle: React.CSSProperties = { textAlign: 'left', padding: '10px 14px', fontWeight: 600, color: 'var(--text-muted)' }
const tdStyle: React.CSSProperties = { padding: '10px 14px' }
const iconBtn: React.CSSProperties = { background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
