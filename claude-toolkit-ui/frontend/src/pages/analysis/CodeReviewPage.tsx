import { useState, useRef, useCallback, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaCodeBranch, FaPlay, FaCopy, FaCheck, FaDownload, FaSpinner, FaEraser,
  FaFolderOpen, FaDatabase, FaSearch, FaFile, FaTimes,
} from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

const TEMPLATE_HINTS = [
  { value: '', label: '균형 (기본)' },
  { value: 'performance', label: '성능 최적화' },
  { value: 'security', label: '보안 취약점' },
  { value: 'refactoring', label: '리팩터링' },
  { value: 'sql_performance', label: 'SQL 성능' },
  { value: 'readability', label: '가독성' },
]

interface FileEntry { absolutePath: string; relativePath: string; fileName: string }
interface DbObject { name: string; type: string; owner: string }

export default function CodeReviewPage() {
  const [code, setCode] = useState('')
  const [language, setLanguage] = useState('java')
  const [templateHint, setTemplateHint] = useState('')
  const [result, setResult] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [copied, setCopied] = useState(false)

  // 소스 선택 모달
  const [sourceModal, setSourceModal] = useState<'file' | 'db' | null>(null)
  const [files, setFiles] = useState<FileEntry[]>([])
  const [dbObjects, setDbObjects] = useState<DbObject[]>([])
  const [searchQ, setSearchQ] = useState('')
  const [filesLoaded, setFilesLoaded] = useState(false)
  const [dbLoaded, setDbLoaded] = useState(false)

  const esRef = useRef<EventSource | null>(null)
  const toast = useToast()

  // ── 4단계 분석 시작 ──
  const startAnalysis = async () => {
    if (!code.trim() || streaming) return
    setResult('')
    setStreaming(true)

    try {
      const body = new URLSearchParams({ code: code.trim(), language, templateHint })
      const res = await fetch('/harness/stream-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
        credentials: 'include',
      })
      const data = await res.json()
      if (!data.success || !data.streamId) {
        toast.error(data.error || '스트림 초기화 실패')
        setStreaming(false)
        return
      }

      let accumulated = ''
      const es = new EventSource(`/stream/${data.streamId}`, { withCredentials: true })
      esRef.current = es

      es.onmessage = (e) => {
        if (e.data === '[DONE]' || e.data === 'done') {
          es.close()
          esRef.current = null
          setStreaming(false)
          return
        }
        accumulated += e.data + '\n'
        setResult(accumulated)
      }

      es.addEventListener('done', () => {
        es.close()
        esRef.current = null
        setStreaming(false)
      })

      es.addEventListener('error_msg', (e: MessageEvent) => {
        toast.error(e.data || '분석 오류')
        es.close()
        esRef.current = null
        setStreaming(false)
      })

      es.onerror = () => {
        es.close()
        esRef.current = null
        setStreaming(false)
      }
    } catch {
      toast.error('분석 요청 실패')
      setStreaming(false)
    }
  }

  // ── 소스 선택: 파일 목록 ──
  const loadFiles = useCallback(async (q: string) => {
    try {
      const res = await fetch(`/harness/cache/files?q=${encodeURIComponent(q)}`, { credentials: 'include' })
      const data = await res.json()
      setFiles(data.files || [])
      setFilesLoaded(data.loaded)
    } catch { /* silent */ }
  }, [])

  const selectFile = async (f: FileEntry) => {
    try {
      const res = await fetch(`/harness/cache/file-content?path=${encodeURIComponent(f.absolutePath)}`, { credentials: 'include' })
      const data = await res.json()
      if (data.success) {
        setCode(data.content)
        setLanguage('java')
        setSourceModal(null)
        toast.success(`${f.fileName} 로드 완료`)
      } else {
        toast.error(data.error || '파일 로드 실패')
      }
    } catch { toast.error('파일 로드 실패') }
  }

  // ── 소스 선택: DB 객체 ──
  const loadDbObjects = useCallback(async (q: string) => {
    try {
      const res = await fetch(`/harness/cache/db-objects?q=${encodeURIComponent(q)}`, { credentials: 'include' })
      const data = await res.json()
      setDbObjects(data.objects || [])
      setDbLoaded(data.loaded)
    } catch { /* silent */ }
  }, [])

  const selectDbObject = async (obj: DbObject) => {
    try {
      const res = await fetch(`/harness/cache/db-source?name=${encodeURIComponent(obj.name)}&type=${encodeURIComponent(obj.type)}`, { credentials: 'include' })
      const data = await res.json()
      if (data.success) {
        setCode(data.source)
        setLanguage('sql')
        setSourceModal(null)
        toast.success(`${obj.name} (${obj.type}) 로드 완료`)
      } else {
        toast.error(data.error || 'DB 소스 로드 실패')
      }
    } catch { toast.error('DB 소스 로드 실패') }
  }

  useEffect(() => {
    if (sourceModal === 'file') loadFiles(searchQ)
    if (sourceModal === 'db') loadDbObjects(searchQ)
  }, [sourceModal, searchQ, loadFiles, loadDbObjects])

  const copyResult = () => {
    navigator.clipboard.writeText(result)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const exportResult = () => {
    const blob = new Blob([result], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `harness_review_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <FaCodeBranch style={{ fontSize: '22px', color: '#8b5cf6' }} />
        <div>
          <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0 }}>코드 리뷰 하네스</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>
            4단계 AI 파이프라인: Analyst → Builder → Reviewer → Verifier
          </p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', minHeight: '65vh' }}>
        {/* ── 좌측: 입력 ── */}
        <div style={panelStyle}>
          <div style={panelHeaderStyle}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>코드 입력</span>
            <div style={{ display: 'flex', gap: '6px' }}>
              <button style={smallBtn} onClick={() => { setSourceModal('file'); setSearchQ('') }} title="Java 파일 선택">
                <FaFolderOpen /> 파일
              </button>
              <button style={smallBtn} onClick={() => { setSourceModal('db'); setSearchQ('') }} title="DB 객체 선택">
                <FaDatabase /> DB
              </button>
              <button style={smallBtn} onClick={() => { setCode(''); setResult('') }} title="초기화">
                <FaEraser />
              </button>
            </div>
          </div>

          {/* 옵션 */}
          <div style={{ padding: '8px 14px', display: 'flex', gap: '10px', flexWrap: 'wrap', fontSize: '13px' }}>
            <div>
              <label style={{ color: 'var(--text-muted)', marginRight: '6px' }}>언어</label>
              <select value={language} onChange={(e) => setLanguage(e.target.value)} style={{ fontSize: '12px', padding: '3px 6px' }}>
                <option value="java">Java</option>
                <option value="sql">SQL / Oracle</option>
              </select>
            </div>
            <div>
              <label style={{ color: 'var(--text-muted)', marginRight: '6px' }}>포커스</label>
              <select value={templateHint} onChange={(e) => setTemplateHint(e.target.value)} style={{ fontSize: '12px', padding: '3px 6px' }}>
                {TEMPLATE_HINTS.map((h) => <option key={h.value} value={h.value}>{h.label}</option>)}
              </select>
            </div>
          </div>

          <textarea
            style={{ flex: 1, margin: '0 14px', border: 'none', resize: 'none', fontFamily: 'Consolas, Monaco, monospace', fontSize: '13px', lineHeight: '1.6', background: 'transparent', outline: 'none', color: 'var(--text-primary)' }}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="Java/SQL 코드를 입력하거나 위 '파일'/'DB' 버튼으로 소스를 선택하세요..."
          />

          <div style={{ padding: '10px 14px', display: 'flex', justifyContent: 'flex-end' }}>
            <button onClick={startAnalysis} disabled={streaming || !code.trim()} style={{ ...analyzeBtn, opacity: streaming || !code.trim() ? 0.5 : 1 }}>
              {streaming ? <><FaSpinner className="spin" /> 분석 중 (4단계)...</> : <><FaPlay /> 4단계 분석 시작</>}
            </button>
          </div>
        </div>

        {/* ── 우측: 결과 ── */}
        <div style={panelStyle}>
          <div style={panelHeaderStyle}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>
              분석 결과
              {streaming && <FaSpinner className="spin" style={{ marginLeft: '6px', fontSize: '11px' }} />}
            </span>
            {result && (
              <div style={{ display: 'flex', gap: '6px' }}>
                <button style={smallBtn} onClick={copyResult}>
                  {copied ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
                </button>
                <button style={smallBtn} onClick={exportResult}><FaDownload /></button>
              </div>
            )}
          </div>
          <div style={{ flex: 1, overflowY: 'auto', padding: '14px' }}>
            {result ? (
              <div className="markdown-body">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{result}</ReactMarkdown>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)', fontSize: '14px', flexDirection: 'column', gap: '8px' }}>
                <FaCodeBranch style={{ fontSize: '32px', opacity: 0.3 }} />
                <p>4단계 분석 결과가 여기에 표시됩니다</p>
                <p style={{ fontSize: '12px' }}>① 분석요약 → ② 개선 코드 → ③ 리뷰 → ④ 검증</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ── 소스 선택 모달 ── */}
      {sourceModal && (
        <div style={modalOverlay} onClick={() => setSourceModal(null)}>
          <div style={modalBox} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
              <h3 style={{ fontSize: '15px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
                {sourceModal === 'file' ? <><FaFolderOpen style={{ color: 'var(--accent)' }} /> Java 파일 선택</> : <><FaDatabase style={{ color: '#3b82f6' }} /> DB 객체 선택</>}
              </h3>
              <button onClick={() => setSourceModal(null)} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '16px' }}><FaTimes /></button>
            </div>

            <div style={{ position: 'relative', marginBottom: '10px' }}>
              <FaSearch style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)', fontSize: '13px' }} />
              <input
                style={{ width: '100%', paddingLeft: '30px', fontSize: '13px' }}
                placeholder={sourceModal === 'file' ? '파일명 또는 경로 검색...' : '객체명 또는 소유자 검색...'}
                value={searchQ}
                onChange={(e) => setSearchQ(e.target.value)}
                autoFocus
              />
            </div>

            <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
              {sourceModal === 'file' && (
                files.length > 0 ? files.map((f) => (
                  <div key={f.absolutePath} onClick={() => selectFile(f)} style={listItem}>
                    <FaFile style={{ color: 'var(--accent)', flexShrink: 0, fontSize: '12px' }} />
                    <div style={{ flex: 1, overflow: 'hidden' }}>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{f.fileName}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{f.relativePath}</div>
                    </div>
                  </div>
                )) : (
                  <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                    {filesLoaded ? '검색 결과가 없습니다.' : '프로젝트 스캔 경로를 Settings에서 설정해주세요.'}
                  </div>
                )
              )}
              {sourceModal === 'db' && (
                dbObjects.length > 0 ? dbObjects.map((obj) => (
                  <div key={`${obj.owner}.${obj.name}.${obj.type}`} onClick={() => selectDbObject(obj)} style={listItem}>
                    <FaDatabase style={{ color: '#3b82f6', flexShrink: 0, fontSize: '12px' }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{obj.name}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{obj.owner} · {obj.type}</div>
                    </div>
                  </div>
                )) : (
                  <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                    {dbLoaded ? '검색 결과가 없습니다.' : 'Oracle DB 연결을 Settings에서 설정해주세요.'}
                  </div>
                )
              )}
            </div>
          </div>
        </div>
      )}
    </>
  )
}

const panelStyle: React.CSSProperties = { background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', display: 'flex', flexDirection: 'column', overflow: 'hidden' }
const panelHeaderStyle: React.CSSProperties = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: '1px solid var(--border-color)' }
const smallBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
const analyzeBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 20px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '13px', fontWeight: 600 }
const modalOverlay: React.CSSProperties = { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500 }
const modalBox: React.CSSProperties = { background: 'var(--bg-secondary)', borderRadius: '16px', border: '1px solid var(--border-color)', padding: '20px', width: 'min(600px, 90vw)', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }
const listItem: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '10px', padding: '8px 12px', borderRadius: '6px', cursor: 'pointer', transition: 'background 0.15s', marginBottom: '2px' }
