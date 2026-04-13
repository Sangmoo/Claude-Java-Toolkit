import { useEffect, useState } from 'react'
import { FaUserLock, FaToggleOn, FaToggleOff } from 'react-icons/fa'
import { useApi } from '../../hooks/useApi'
import { useToast } from '../../hooks/useToast'

interface Permission { username: string; feature: string; enabled: boolean }

export default function AdminPermissionsPage() {
  const [permissions, setPermissions] = useState<Permission[]>([])
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/admin/permissions?format=json') as Permission[] | null
      if (data) setPermissions(data)
    }
    load()
  }, [])

  const toggle = async (p: Permission) => {
    await fetch('/admin/permissions/toggle', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ username: p.username, feature: p.feature }),
      credentials: 'include',
    })
    setPermissions((prev) => prev.map((x) =>
      x.username === p.username && x.feature === p.feature ? { ...x, enabled: !x.enabled } : x
    ))
    toast.success(`${p.feature} 권한 ${p.enabled ? '비활성화' : '활성화'}`)
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaUserLock style={{ color: '#ef4444' }} /> 사용자 권한 관리
      </h2>
      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
          <thead><tr style={{ background: 'var(--bg-tertiary)' }}>
            <th style={th}>사용자</th><th style={th}>기능</th><th style={th}>상태</th><th style={th}>작업</th>
          </tr></thead>
          <tbody>{permissions.map((p, i) => (
            <tr key={i} style={{ borderBottom: '1px solid var(--border-color)' }}>
              <td style={td}>{p.username}</td>
              <td style={td}>{p.feature}</td>
              <td style={td}><span style={{ color: p.enabled ? 'var(--green)' : 'var(--red)' }}>{p.enabled ? '활성' : '비활성'}</span></td>
              <td style={td}><button onClick={() => toggle(p)} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '18px', color: p.enabled ? 'var(--green)' : 'var(--text-muted)' }}>{p.enabled ? <FaToggleOn /> : <FaToggleOff />}</button></td>
            </tr>
          ))}</tbody>
        </table>
        {permissions.length === 0 && <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>권한 데이터를 불러오는 중...</div>}
      </div>
    </>
  )
}
const th: React.CSSProperties = { textAlign: 'left', padding: '10px 14px', fontWeight: 600, color: 'var(--text-muted)' }
const td: React.CSSProperties = { padding: '10px 14px' }
