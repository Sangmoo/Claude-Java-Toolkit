import { useEffect, useState } from 'react'
import { FaSlidersH, FaSave } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'

interface Prompt {
  id: number
  name: string
  content: string
  active: boolean
}

export default function PromptsPage() {
  const [prompts, setPrompts] = useState<Prompt[]>([])
  const [editing, setEditing] = useState<Prompt | null>(null)
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/prompts?format=json') as Prompt[] | null
      if (data) setPrompts(data)
    }
    load()
  }, [api])

  const save = async () => {
    if (!editing) return
    await fetch(`/prompts/${editing.id}/save`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ content: editing.content }),
      credentials: 'include',
    })
    toast.success('프롬프트가 저장되었습니다.')
    setPrompts((prev) => prev.map((p) => p.id === editing.id ? editing : p))
    setEditing(null)
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaSlidersH style={{ color: '#f97316' }} /> 프롬프트 템플릿
      </h2>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {prompts.map((p) => (
          <div key={p.id} style={{ padding: '14px 16px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: editing?.id === p.id ? '12px' : '0' }}>
              <span style={{ fontSize: '14px', fontWeight: 600, flex: 1 }}>{p.name}</span>
              <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: p.active ? 'rgba(34,197,94,0.12)' : 'rgba(148,163,184,0.12)', color: p.active ? 'var(--green)' : 'var(--text-muted)' }}>
                {p.active ? '활성' : '비활성'}
              </span>
              <button onClick={() => setEditing(editing?.id === p.id ? null : p)} style={{ background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 10px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }}>
                {editing?.id === p.id ? '닫기' : '편집'}
              </button>
            </div>
            {editing?.id === p.id && (
              <div>
                <textarea
                  value={editing.content}
                  onChange={(e) => setEditing({ ...editing, content: e.target.value })}
                  style={{ width: '100%', minHeight: '150px', fontFamily: 'monospace', fontSize: '13px', marginBottom: '8px' }}
                />
                <button onClick={save} style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '6px 16px', borderRadius: '6px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '13px' }}>
                  <FaSave /> 저장
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </>
  )
}
