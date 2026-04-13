import { useEffect, useState } from 'react'
import { FaServer, FaPlus, FaTrash, FaCheck } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'

interface DbProfile { id: number; name: string; dbType: string; host: string; port: number; dbName: string; active: boolean }

export default function DbProfilesPage() {
  const [profiles, setProfiles] = useState<DbProfile[]>([])
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/api/v1/prompts') as DbProfile[] | null
      if (data) setProfiles(data)
    }
    load()
  }, [])

  const activate = async (id: number) => {
    await fetch(`/db-profiles/${id}/activate`, { method: 'POST', credentials: 'include' })
    setProfiles((prev) => prev.map((p) => ({ ...p, active: p.id === id })))
    toast.success('DB 프로필 활성화')
  }

  const remove = async (id: number) => {
    await fetch(`/db-profiles/${id}/delete`, { method: 'POST', credentials: 'include' })
    setProfiles((prev) => prev.filter((p) => p.id !== id))
    toast.success('삭제됨')
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaServer style={{ color: '#3b82f6' }} /> DB 프로필
        </h2>
        <button style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', fontSize: '13px', cursor: 'pointer' }}><FaPlus /> 프로필 추가</button>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {profiles.map((p) => (
          <div key={p.id} style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '14px 16px', background: 'var(--bg-secondary)', border: `1px solid ${p.active ? 'var(--green)' : 'var(--border-color)'}`, borderRadius: '10px' }}>
            <FaServer style={{ color: p.active ? 'var(--green)' : 'var(--text-muted)', fontSize: '18px' }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600, fontSize: '14px' }}>{p.name} {p.active && <span style={{ fontSize: '11px', color: 'var(--green)' }}>(활성)</span>}</div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{p.dbType} · {p.host}:{p.port}/{p.dbName}</div>
            </div>
            {!p.active && <button onClick={() => activate(p.id)} style={{ background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 10px', fontSize: '12px', cursor: 'pointer', color: 'var(--green)' }}><FaCheck /> 활성화</button>}
            <button onClick={() => remove(p.id)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--red)', fontSize: '14px' }}><FaTrash /></button>
          </div>
        ))}
        {profiles.length === 0 && <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>등록된 DB 프로필이 없습니다.</div>}
      </div>
    </>
  )
}
