import { useEffect, useState } from 'react'
import { FaMagic, FaSave, FaPlus, FaTrash, FaToggleOn, FaToggleOff } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'

interface PromptTemplate {
  id: number
  name: string
  category: string
  content: string
  active: boolean
}

export default function SettingsPromptsPage() {
  const [prompts, setPrompts] = useState<PromptTemplate[]>([])
  const [editing, setEditing] = useState<PromptTemplate | null>(null)
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/settings/prompts?format=json') as PromptTemplate[] | null
      if (data) setPrompts(data)
    }
    load()
  }, [])

  const save = async () => {
    if (!editing) return
    await fetch(`/settings/prompts/${editing.id}/save`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ content: editing.content, name: editing.name, category: editing.category }),
      credentials: 'include',
    })
    toast.success('프롬프트가 저장되었습니다.')
    setPrompts((prev) => prev.map((p) => p.id === editing.id ? editing : p))
    setEditing(null)
  }

  const toggleActive = async (p: PromptTemplate) => {
    await fetch(`/settings/prompts/${p.id}/toggle`, { method: 'POST', credentials: 'include' })
    setPrompts((prev) => prev.map((x) => x.id === p.id ? { ...x, active: !x.active } : x))
    toast.success(p.active ? '비활성화' : '활성화')
  }

  const remove = async (id: number) => {
    if (!confirm('이 프롬프트를 삭제하시겠습니까?')) return
    await fetch(`/settings/prompts/${id}/delete`, { method: 'POST', credentials: 'include' })
    setPrompts((prev) => prev.filter((p) => p.id !== id))
    toast.success('삭제됨')
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaMagic style={{ color: '#f97316' }} /> AI 프롬프트 관리
        </h2>
        <button style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', fontSize: '13px', cursor: 'pointer' }}>
          <FaPlus /> 프롬프트 추가
        </button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {prompts.map((p) => (
          <div key={p.id} style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px', overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '12px 16px' }}>
              <button onClick={() => toggleActive(p)} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '18px', color: p.active ? 'var(--green)' : 'var(--text-muted)' }}>
                {p.active ? <FaToggleOn /> : <FaToggleOff />}
              </button>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, fontSize: '14px' }}>{p.name}</div>
                <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{p.category}</div>
              </div>
              <button onClick={() => setEditing(editing?.id === p.id ? null : p)} style={{ background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 10px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }}>
                {editing?.id === p.id ? '닫기' : '편집'}
              </button>
              <button onClick={() => remove(p.id)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--red)', fontSize: '13px' }}><FaTrash /></button>
            </div>
            {editing?.id === p.id && (
              <div style={{ borderTop: '1px solid var(--border-color)', padding: '16px' }}>
                <div style={{ display: 'flex', gap: '10px', marginBottom: '10px' }}>
                  <input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} placeholder="프롬프트 이름" style={{ flex: 1, fontSize: '13px' }} />
                  <input value={editing.category} onChange={(e) => setEditing({ ...editing, category: e.target.value })} placeholder="카테고리" style={{ width: '150px', fontSize: '13px' }} />
                </div>
                <textarea
                  value={editing.content}
                  onChange={(e) => setEditing({ ...editing, content: e.target.value })}
                  style={{ width: '100%', minHeight: '200px', fontFamily: 'monospace', fontSize: '13px', marginBottom: '10px' }}
                  placeholder="프롬프트 내용..."
                />
                <button onClick={save} style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '6px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '13px' }}>
                  <FaSave /> 저장
                </button>
              </div>
            )}
          </div>
        ))}
        {prompts.length === 0 && <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>등록된 프롬프트가 없습니다.</div>}
      </div>
    </>
  )
}
