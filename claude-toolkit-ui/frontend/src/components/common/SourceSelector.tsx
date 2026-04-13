import { useState, useEffect, useCallback } from 'react'
import { FaFolderOpen, FaDatabase, FaSearch, FaFile, FaTimes } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

interface FileEntry { absolutePath: string; relativePath: string; fileName: string }
interface DbObject { name: string; type: string; owner: string }

interface SourceSelectorProps {
  /** 'java' = Java 파일만, 'sql' = DB 객체만, 'both' = 둘 다 */
  mode: 'java' | 'sql' | 'both'
  onSelect: (code: string, lang: 'java' | 'sql') => void
}

export default function SourceSelector({ mode, onSelect }: SourceSelectorProps) {
  const [tab, setTab] = useState<'file' | 'db'>(mode === 'sql' ? 'db' : 'file')
  const [open, setOpen] = useState(false)
  const [files, setFiles] = useState<FileEntry[]>([])
  const [dbObjects, setDbObjects] = useState<DbObject[]>([])
  const [q, setQ] = useState('')
  const toast = useToast()

  const loadFiles = useCallback(async (kw: string) => {
    try {
      const res = await fetch(`/harness/cache/files?q=${encodeURIComponent(kw)}`, { credentials: 'include' })
      if (res.ok) { const d = await res.json(); setFiles(d.files || []) }
    } catch { /* silent */ }
  }, [])

  const loadDb = useCallback(async (kw: string) => {
    try {
      const res = await fetch(`/harness/cache/db-objects?q=${encodeURIComponent(kw)}`, { credentials: 'include' })
      if (res.ok) { const d = await res.json(); setDbObjects(d.objects || []) }
    } catch { /* silent */ }
  }, [])

  useEffect(() => {
    if (!open) return
    if (tab === 'file') loadFiles(q)
    else loadDb(q)
  }, [open, tab, q, loadFiles, loadDb])

  const selectFile = async (f: FileEntry) => {
    const res = await fetch(`/harness/cache/file-content?path=${encodeURIComponent(f.absolutePath)}`, { credentials: 'include' })
    const d = await res.json()
    if (d.success) { onSelect(d.content, 'java'); setOpen(false); toast.success(`${f.fileName} 로드`) }
    else toast.error(d.error || '파일 로드 실패')
  }

  const selectDb = async (obj: DbObject) => {
    const res = await fetch(`/harness/cache/db-source?name=${encodeURIComponent(obj.name)}&type=${encodeURIComponent(obj.type)}`, { credentials: 'include' })
    const d = await res.json()
    if (d.success) { onSelect(d.source, 'sql'); setOpen(false); toast.success(`${obj.name} 로드`) }
    else toast.error(d.error || 'DB 소스 로드 실패')
  }

  return (
    <>
      <button onClick={() => { setOpen(true); setQ('') }} style={triggerBtn}>
        {mode === 'sql' ? <FaDatabase /> : <FaFolderOpen />} 소스 선택
      </button>

      {open && (
        <div style={overlay} onClick={() => setOpen(false)}>
          <div style={modal} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <h3 style={{ fontSize: '15px', fontWeight: 700 }}>소스 선택하기</h3>
              <button onClick={() => setOpen(false)} style={iconBtn}><FaTimes /></button>
            </div>

            {/* 탭 */}
            {mode === 'both' && (
              <div style={{ display: 'flex', gap: '6px', marginBottom: '10px' }}>
                <TabBtn active={tab === 'file'} onClick={() => setTab('file')}><FaFolderOpen /> Java 파일</TabBtn>
                <TabBtn active={tab === 'db'} onClick={() => setTab('db')}><FaDatabase /> DB 객체</TabBtn>
              </div>
            )}

            {/* 검색 */}
            <div style={{ position: 'relative', marginBottom: '10px' }}>
              <FaSearch style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)', fontSize: '13px' }} />
              <input style={{ width: '100%', paddingLeft: '32px', fontSize: '13px' }}
                placeholder={tab === 'file' ? '파일명/경로 검색...' : '객체명/소유자 검색...'}
                value={q} onChange={(e) => setQ(e.target.value)} autoFocus />
            </div>

            {/* 목록 */}
            <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
              {tab === 'file' ? (
                files.length > 0 ? files.map((f) => (
                  <div key={f.absolutePath} onClick={() => selectFile(f)} style={listItem}>
                    <FaFile style={{ color: 'var(--accent)', flexShrink: 0, fontSize: '12px' }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{f.fileName}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{f.relativePath}</div>
                    </div>
                  </div>
                )) : <Empty msg="프로젝트 스캔 경로를 Settings에서 설정해주세요." />
              ) : (
                dbObjects.length > 0 ? dbObjects.map((o) => (
                  <div key={`${o.owner}.${o.name}.${o.type}`} onClick={() => selectDb(o)} style={listItem}>
                    <FaDatabase style={{ color: '#3b82f6', flexShrink: 0, fontSize: '12px' }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{o.name}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{o.owner} · {o.type}</div>
                    </div>
                  </div>
                )) : <Empty msg="Oracle DB를 Settings에서 설정해주세요." />
              )}
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function TabBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick} style={{
      padding: '6px 14px', borderRadius: '6px', fontSize: '13px', cursor: 'pointer',
      border: '1px solid var(--border-color)',
      background: active ? 'var(--accent)' : 'transparent',
      color: active ? '#fff' : 'var(--text-sub)',
      display: 'flex', alignItems: 'center', gap: '6px',
    }}>{children}</button>
  )
}

function Empty({ msg }: { msg: string }) {
  return <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>{msg}</div>
}

const triggerBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '5px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 10px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
const overlay: React.CSSProperties = { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500 }
const modal: React.CSSProperties = { background: 'var(--bg-secondary)', borderRadius: '16px', border: '1px solid var(--border-color)', padding: '20px', width: 'min(600px, 90vw)', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }
const iconBtn: React.CSSProperties = { background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '16px' }
const listItem: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '10px', padding: '8px 12px', borderRadius: '6px', cursor: 'pointer', marginBottom: '2px' }
