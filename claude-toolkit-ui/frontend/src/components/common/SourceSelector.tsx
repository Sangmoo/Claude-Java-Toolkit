import { useState, useEffect, useCallback, useRef } from 'react'
import { FaFolderOpen, FaDatabase, FaSearch, FaFile, FaTimes, FaSpinner, FaSync } from 'react-icons/fa'
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
  const [fileLoaded, setFileLoaded] = useState(false)
  const [fileRefreshing, setFileRefreshing] = useState(false)
  const [fileTotal, setFileTotal] = useState(0)
  // v4.4.x — DB 캐시 상태 추가 (configured / 에러 메시지 / 새로고침 진행)
  const [dbConfigured, setDbConfigured] = useState<boolean | null>(null)
  const [dbError, setDbError]           = useState<string | null>(null)
  const [dbRefreshing, setDbRefreshing] = useState(false)
  const [dbLoaded, setDbLoaded]         = useState(false)
  const pollTimer = useRef<number | null>(null)
  const toast = useToast()

  const loadFiles = useCallback(async (kw: string) => {
    try {
      const res = await fetch(`/harness/cache/files?q=${encodeURIComponent(kw)}`, { credentials: 'include' })
      if (res.ok) {
        const d = await res.json()
        setFiles(d.files || [])
        setFileLoaded(!!d.loaded)
        setFileRefreshing(!!d.refreshing)
        setFileTotal(d.totalCount || 0)
      }
    } catch { /* silent */ }
  }, [])

  const loadDb = useCallback(async (kw: string, type: string) => {
    try {
      const typeParam = type === 'ALL' ? '' : `&type=${type}`
      const res = await fetch(`/harness/cache/db-objects?q=${encodeURIComponent(kw)}${typeParam}`, { credentials: 'include' })
      if (res.ok) {
        const d = await res.json()
        setDbObjects(d.objects || [])
        // v4.4.x — 백엔드가 보내주는 상태로 정확한 메시지 표시
        setDbConfigured(typeof d.configured === 'boolean' ? d.configured : null)
        setDbError(d.dbError || null)
        setDbRefreshing(!!d.refreshing)
        setDbLoaded(!!d.loaded)
      }
    } catch { /* silent */ }
  }, [])

  // DB 캐시 강제 재빌드 (관리자가 Settings 변경 후 또는 fail 후 수동 재시도)
  const triggerDbRefresh = async () => {
    setDbRefreshing(true); setDbError(null)
    try {
      await fetch('/harness/cache/refresh', { method: 'POST', credentials: 'include' })
      toast.info('DB 캐시 재로드 중...')
    } catch { toast.error('재로드 호출 실패') }
    setTimeout(() => loadDb(q, dbTypeFilter), 800)
    setTimeout(() => loadDb(q, dbTypeFilter), 3000)  // 한 번 더 (DB 응답 시간 확보)
  }

  // 부팅 직후엔 백엔드가 백그라운드 스캔 중일 수 있음 — refreshing/!loaded 면
  // 1.5초마다 자동 폴링해서 완료되면 즉시 갱신
  useEffect(() => {
    if (!open) return
    // 현재 탭에 따라 어떤 캐시를 폴링할지 결정
    const isFileTab = tab === 'file'
    const settled = isFileTab
      ? (fileLoaded && !fileRefreshing)
      : (dbLoaded && !dbRefreshing)
    if (settled) {
      if (pollTimer.current) { window.clearInterval(pollTimer.current); pollTimer.current = null }
      return
    }
    if (pollTimer.current) return
    pollTimer.current = window.setInterval(() => {
      if (isFileTab) loadFiles(q)
      else           loadDb(q, dbTypeFilter)
    }, 1500)
    return () => {
      if (pollTimer.current) { window.clearInterval(pollTimer.current); pollTimer.current = null }
    }
  }, [open, tab, fileLoaded, fileRefreshing, dbLoaded, dbRefreshing, loadFiles, loadDb, q, dbTypeFilter])

  useEffect(() => {
    if (!open) return
    if (tab === 'file') loadFiles(q)
    else loadDb(q, dbTypeFilter)
  }, [open, tab, q, dbTypeFilter, loadFiles, loadDb])

  const triggerRefresh = async () => {
    setFileRefreshing(true)
    try {
      await fetch('/harness/cache/refresh', { method: 'POST', credentials: 'include' })
    } catch { /* noop */ }
    setTimeout(() => loadFiles(q), 500)
  }

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
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginBottom: '4px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span>
                {tab === 'file'
                  ? `${filteredFiles.length}개 파일${fileTotal > 0 ? ` (전체 캐시 ${fileTotal})` : ''}`
                  : `${dbObjects.length}개 객체`}
              </span>
              {tab === 'file' && (
                <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  {fileRefreshing && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', color: 'var(--blue)' }}><FaSpinner className="spin" /> 스캔 중...</span>}
                  <button onClick={triggerRefresh} title="캐시 새로고침" style={{ background: 'none', border: '1px solid var(--border-color)', borderRadius: '4px', padding: '2px 6px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '10px', display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                    <FaSync /> 새로고침
                  </button>
                </span>
              )}
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
                )) : (
                  fileRefreshing || !fileLoaded
                    ? <ScanProgress />
                    : <Empty msg={javaCatFilter !== 'ALL'
                        ? `${javaCatFilter} 필터에 매칭되는 파일이 없습니다.`
                        : '프로젝트 스캔 경로를 Settings에서 설정하거나 새로고침 버튼을 눌러주세요.'} />
                )
              ) : (
                dbObjects.length > 0 ? dbObjects.map((o) => (
                  <div key={`${o.owner}.${o.name}.${o.type}`} onClick={() => selectDb(o)} style={listItem}>
                    <FaDatabase style={{ color: '#3b82f6', flexShrink: 0, fontSize: '12px' }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{o.name}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{o.owner} · {o.type}</div>
                    </div>
                  </div>
                )) : (
                  // v4.4.x — 정확한 사유별 메시지 + 재시도 버튼
                  dbRefreshing || !dbLoaded ? <ScanProgress /> :
                  dbConfigured === false ? (
                    <Empty msg="Oracle DB 가 Settings 에 입력되지 않았습니다. Settings → Oracle DB 항목을 채워주세요." />
                  ) : dbError ? (
                    <div style={{ padding: '20px', textAlign: 'center', fontSize: '12px', color: 'var(--text-muted)' }}>
                      <div style={{ color: '#ef4444', marginBottom: '8px' }}>⚠ DB 연결 실패</div>
                      <div style={{ marginBottom: '10px', fontFamily: 'monospace', fontSize: '11px' }}>{dbError}</div>
                      <button onClick={triggerDbRefresh} style={{ padding: '4px 10px', borderRadius: '4px', border: '1px solid var(--border-color)', cursor: 'pointer' }}>
                        <FaSync /> 재시도
                      </button>
                    </div>
                  ) : dbTypeFilter !== 'ALL' ? (
                    <Empty msg={`${dbTypeFilter} 타입 객체가 없습니다.`} />
                  ) : (
                    <div style={{ padding: '20px', textAlign: 'center', fontSize: '12px', color: 'var(--text-muted)' }}>
                      <div style={{ marginBottom: '10px' }}>이 스키마에 PROCEDURE / FUNCTION / PACKAGE / TRIGGER 가 없습니다.</div>
                      <button onClick={triggerDbRefresh} style={{ padding: '4px 10px', borderRadius: '4px', border: '1px solid var(--border-color)', cursor: 'pointer' }}>
                        <FaSync /> 다시 불러오기
                      </button>
                    </div>
                  )
                )
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

function ScanProgress() {
  return (
    <div style={{ padding: '32px 24px', textAlign: 'center', color: 'var(--blue)', fontSize: '13px' }}>
      <div style={{ fontSize: '24px', marginBottom: '10px' }}>
        <FaSpinner className="spin" />
      </div>
      <div style={{ fontWeight: 600, marginBottom: '4px' }}>프로젝트 스캔 중...</div>
      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
        WAS 시작 직후엔 백그라운드에서 Java 파일을 인덱싱합니다.<br/>
        완료되면 자동으로 표시됩니다 (보통 수 초 ~ 수십 초).
      </div>
    </div>
  )
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
