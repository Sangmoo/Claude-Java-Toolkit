import { useEffect, useState } from 'react'
import { FaServer, FaPlus, FaTrash, FaCheck, FaTimes, FaSync } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'

interface DbProfile { id: number; name: string; dbType: string; host: string; port: number; dbName: string; active: boolean }

export default function DbProfilesPage() {
  const [profiles, setProfiles] = useState<DbProfile[]>([])
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ name: '', dbType: 'oracle', host: '', port: '1521', dbName: '', username: '', password: '' })
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<string | null>(null)
  const api = useApi()
  const toast = useToast()

  const reload = () => {
    api.get('/api/v1/db-profiles').then((data) => { if (data) setProfiles(data as DbProfile[]) })
  }

  useEffect(() => { reload() }, [])

  const activate = async (id: number) => {
    await fetch(`/db-profiles/${id}/apply`, { method: 'POST', credentials: 'include' })
    setProfiles((prev) => prev.map((p) => ({ ...p, active: p.id === id })))
    toast.success('DB 프로필 활성화')
  }

  const remove = async (id: number) => {
    if (!confirm('이 프로필을 삭제하시겠습니까?')) return
    await fetch(`/db-profiles/${id}/delete`, { method: 'POST', credentials: 'include' })
    setProfiles((prev) => prev.filter((p) => p.id !== id))
    toast.success('삭제됨')
  }

  const testConnection = async () => {
    setTesting(true); setTestResult(null)
    try {
      const res = await fetch('/db-profiles/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(form),
        credentials: 'include',
      })
      const d = await res.json()
      setTestResult(d.success ? 'ok:연결 성공' : `error:${d.error || '연결 실패'}`)
    } catch { setTestResult('error:연결 실패') }
    setTesting(false)
  }

  const saveProfile = async () => {
    try {
      const res = await fetch('/db-profiles/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(form),
        credentials: 'include',
      })
      if (res.ok) {
        toast.success('프로필이 추가되었습니다.')
        setShowModal(false)
        setForm({ name: '', dbType: 'oracle', host: '', port: '1521', dbName: '', username: '', password: '' })
        reload()
      } else {
        toast.error('저장 실패')
      }
    } catch { toast.error('저장 오류') }
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaServer style={{ color: '#3b82f6' }} /> DB 프로필
        </h2>
        <button onClick={() => setShowModal(true)} style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', fontSize: '13px', cursor: 'pointer' }}>
          <FaPlus /> 프로필 추가
        </button>
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

      {/* 추가 모달 */}
      {showModal && (
        <div style={overlayStyle} onClick={() => setShowModal(false)}>
          <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: 700 }}>DB 프로필 추가</h3>
              <button onClick={() => setShowModal(false)} style={iconBtn}><FaTimes /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <Field label="프로필 이름">
                <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="예: 개발 DB" style={inputStyle} />
              </Field>
              <Field label="DB 유형">
                <div style={{ display: 'flex', gap: '4px' }}>
                  {['oracle', 'mysql', 'postgresql'].map((t) => (
                    <button key={t} onClick={() => setForm({ ...form, dbType: t, port: t === 'oracle' ? '1521' : t === 'mysql' ? '3306' : '5432' })}
                      style={{
                        padding: '5px 14px', borderRadius: '16px', fontSize: '12px', cursor: 'pointer',
                        border: `1px solid ${form.dbType === t ? 'var(--accent)' : 'var(--border-color)'}`,
                        background: form.dbType === t ? 'var(--accent-subtle)' : 'transparent',
                        color: form.dbType === t ? 'var(--accent)' : 'var(--text-sub)',
                      }}>
                      {t}
                    </button>
                  ))}
                </div>
              </Field>
              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '8px' }}>
                <Field label="호스트">
                  <input value={form.host} onChange={(e) => setForm({ ...form, host: e.target.value })} placeholder="192.168.1.100" style={inputStyle} />
                </Field>
                <Field label="포트">
                  <input value={form.port} onChange={(e) => setForm({ ...form, port: e.target.value })} style={inputStyle} />
                </Field>
              </div>
              <Field label="DB 이름 / SID">
                <input value={form.dbName} onChange={(e) => setForm({ ...form, dbName: e.target.value })} placeholder="ORCL" style={inputStyle} />
              </Field>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                <Field label="사용자명">
                  <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} style={inputStyle} />
                </Field>
                <Field label="비밀번호">
                  <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} style={inputStyle} />
                </Field>
              </div>
              {testResult && (
                <div style={{ fontSize: '12px', color: testResult.startsWith('ok') ? 'var(--green)' : 'var(--red)' }}>
                  {testResult.split(':')[1]}
                </div>
              )}
              <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                <button onClick={testConnection} disabled={testing} style={outlineBtn}><FaSync /> 연결 테스트</button>
                <button onClick={saveProfile} style={primaryBtn}>저장</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: 'block', fontSize: '11px', color: 'var(--text-muted)', marginBottom: '3px', fontWeight: 600 }}>{label}</label>{children}</div>
}

const overlayStyle: React.CSSProperties = { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500 }
const modalStyle: React.CSSProperties = { background: 'var(--bg-secondary)', borderRadius: '16px', border: '1px solid var(--border-color)', padding: '24px', width: 'min(500px, 90vw)' }
const inputStyle: React.CSSProperties = { width: '100%', padding: '8px 10px', fontSize: '13px' }
const iconBtn: React.CSSProperties = { background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '16px' }
const outlineBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '5px', padding: '8px 16px', borderRadius: '8px', fontSize: '13px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-sub)', cursor: 'pointer' }
const primaryBtn: React.CSSProperties = { flex: 1, padding: '8px 20px', borderRadius: '8px', fontSize: '13px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontWeight: 600 }
