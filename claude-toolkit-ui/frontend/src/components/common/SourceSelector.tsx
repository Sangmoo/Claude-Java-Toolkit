import { useState, useEffect, useCallback } from 'react'
import { FaFolderOpen, FaDatabase, FaSearch, FaFile, FaTimes } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

interface FileEntry { absolutePath: string; relativePath: string; fileName: string }
interface DbObject { name: string; type: string; owner: string }

interface SourceSelectorProps {
  mode: 'java' | 'sql' | 'both'
  onSelect: (code: string, lang: 'java' | 'sql') => void
}

const DB_TYPES = ['ALL', 'PROCEDURE', 'FUNCTION', 'PACKAGE', 'TRIGGER']
const JAVA_CATEGORIES = [
  { key: 'ALL', label: '전체', pattern: null },
  { key: 'Controller', label: 'Controller', pattern: /controller/i },
  { key: 'Service', label: 'Service', pattern: /service(impl)?/i },
  { key: 'DAO', label: 'DAO', pattern: /dao|mapper/i },
  { key: 'VO', label: 'VO/DTO', pattern: /vo\.java$|dto\.java$|entity/i },
  { key: 'Config', label: 'Config', pattern: /config/i },
  { key: 'Util', label: 'Util', pattern: /util/i },
]

export default function SourceSelector({ mode, onSelect }: SourceSelectorProps) {
  const [tab, setTab] = useState<'file' | 'db'>(mode === 'sql' ? 'db' : 'file')
  const [open, setOpen] = useState(false)
  const [files, setFiles] = useState<FileEntry[]>([])
  const [dbObjects, setDbObjects] = useState<DbObject[]>([])
  const [q, setQ] = useState('')
  const [dbTypeFilter, setDbTypeFilter] = useState('ALL')
  const [javaCatFilter, setJavaCatFilter] = useState('ALL')
  const toast = useToast()

  const loadFiles = useCallback(async (kw: string) => {
    try {
      const res = await fetch(`/harness/cache/files?q=${encodeURIComponent(kw)}`, { credentials: 'include' })
      if (res.ok) { const d = await res.json(); setFiles(d.files || []) }
    } catch { /* silent */ }
  }, [])

  const loadDb = useCallback(async (kw: string, type: string) => {
    try {
      const typeParam = type === 'ALL' ? '' : `&type=${type}`
      const res = await fetch(`/harness/cache/db-objects?q=${encodeURIComponent(kw)}${typeParam}`, { credentials: 'include' })
      if (res.ok) { const d = await res.json(); setDbObjects(d.objects || []) }
    } catch { /* silent */ }
  }, [])

  useEffect(() => {
    if (!open) return
    if (tab === 'file') loadFiles(q)
    else loadDb(q, dbTypeFilter)
  }, [open, tab, q, dbTypeFilter, loadFiles, loadDb])

  // 자바 카테고리 클라이언트 필터
  const filteredFiles = (() => {
    if (javaCatFilter === 'ALL') return files
    const cat = JAVA_CATEGORIES.find((c) => c.key === javaCatFilter)
    if (!cat?.pattern) return files
    return files.filter((f) => cat.pattern!.test(f.fileName) || cat.pattern!.test(f.relativePath))
  })()

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

            {/* 필터 */}
            {tab === 'file' && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginBottom: '10px' }}>
                {JAVA_CATEGORIES.map((c) => (
                  <button key={c.key} onClick={() => setJavaCatFilter(c.key)} style={filterChip(javaCatFilter === c.key)}>
                    {c.label}
                  </button>
                ))}
              </div>
            )}
            {tab === 'db' && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginBottom: '10px' }}>
                {DB_TYPES.map((t) => (
                  <button key={t} onClick={() => setDbTypeFilter(t)} style={filterChip(dbTypeFilter === t)}>
                    {t === 'ALL' ? '전체' : t}
                  </button>
                ))}
              </div>
            )}

            {/* 목록 */}
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginBottom: '4px' }}>
              {tab === 'file' ? `${filteredFiles.length}개 파일` : `${dbObjects.length}개 객체`}
            </div>
            <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
              {tab === 'file' ? (
                filteredFiles.length > 0 ? filteredFiles.map((f) => (
                  <div key={f.absolutePath} onClick={() => selectFile(f)} style={listItem}>
                    <FaFile style={{ color: 'var(--accent)', flexShrink: 0, fontSize: '12px' }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{f.fileName}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{f.relativePath}</div>
                    </div>
                  </div>
                )) : <Empty msg={javaCatFilter !== 'ALL' ? `${javaCatFilter} 필터에 매칭되는 파일이 없습니다.` : '프로젝트 스캔 경로를 Settings에서 설정해주세요.'} />
              ) : (
                dbObjects.length > 0 ? dbObjects.map((o) => (
                  <div key={`${o.owner}.${o.name}.${o.type}`} onClick={() => selectDb(o)} style={listItem}>
                    <FaDatabase style={{ color: '#3b82f6', flexShrink: 0, fontSize: '12px' }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{o.name}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{o.owner} · {o.type}</div>
                    </div>
                  </div>
                )) : <Empty msg={dbTypeFilter !== 'ALL' ? `${dbTypeFilter} 타입 객체가 없습니다.` : 'Oracle DB를 Settings에서 설정해주세요.'} />
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

const filterChip = (active: boolean): React.CSSProperties => ({
  padding: '3px 10px', borderRadius: '12px', fontSize: '11px', cursor: 'pointer',
  border: `1px solid ${active ? 'var(--accent)' : 'var(--border-color)'}`,
  background: active ? 'var(--accent-subtle)' : 'transparent',
  color: active ? 'var(--accent)' : 'var(--text-sub)', fontWeight: active ? 600 : 400,
})

const triggerBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '5px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 10px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
const overlay: React.CSSProperties = { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500 }
const modal: React.CSSProperties = { background: 'var(--bg-secondary)', borderRadius: '16px', border: '1px solid var(--border-color)', padding: '20px', width: 'min(650px, 90vw)', maxHeight: '85vh', display: 'flex', flexDirection: 'column' }
const iconBtn: React.CSSProperties = { background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '16px' }
const listItem: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '10px', padding: '8px 12px', borderRadius: '6px', cursor: 'pointer', marginBottom: '2px' }
