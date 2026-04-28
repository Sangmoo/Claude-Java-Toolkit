import { useEffect, useState } from 'react'
import { FaBolt, FaSearch, FaDatabase, FaFileCode, FaGlobe, FaTimes, FaCopy, FaCheck } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import SourceSelector from '../../components/common/SourceSelector'

/**
 * v4.5+ — 테이블 변경 영향 분석 (Impact Analysis).
 * 단계: TABLE → MyBatis Statements → Java Files → Controller Endpoints
 * (MiPlatform 단계는 사용자 요청으로 UI 에서 제외)
 *
 * Java 파일 행 클릭 시 모달로 전체 소스 표시 + 클립보드 복사 지원.
 */

interface Statement { fullId: string; dml: string; file: string; line: number }
interface Endpoint  { url: string; httpMethod: string; className: string; methodName: string; file: string; line: number }
interface ImpactData {
  table: string; dml: string
  statements: Statement[]; javaFiles: string[]; endpoints: Endpoint[]; screens: string[]
  counts: { statements: number; javaFiles: number; endpoints: number; screens: number }
}

interface FileViewerData { file: string; name: string; size: number; content: string; encoding?: string }

const DML_OPTIONS = ['ALL', 'SELECT', 'INSERT', 'UPDATE', 'MERGE', 'DELETE']

export default function ImpactAnalysisPage() {
  const toast = useToast()
  const [table,   setTable]   = useState('')
  const [dml,     setDml]     = useState('ALL')
  const [loading, setLoading] = useState(false)
  const [result,  setResult]  = useState<ImpactData | null>(null)

  // 파일 뷰어 모달 상태
  const [openFile, setOpenFile]     = useState<string | null>(null)
  const [fileData, setFileData]     = useState<FileViewerData | null>(null)
  const [fileLoading, setFileLoading] = useState(false)
  const [fileError, setFileError]   = useState<string>('')
  const [copied, setCopied]         = useState(false)

  const analyze = async () => {
    if (!table.trim()) { toast.warning('테이블명을 입력하세요.'); return }
    setLoading(true)
    try {
      const qs = new URLSearchParams({ table: table.trim().toUpperCase(), dml })
      const r = await fetch(`/api/v1/flow/impact?${qs}`, { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) setResult(d.data as ImpactData)
      else toast.error(d.error || '분석 실패')
    } catch (e: unknown) {
      toast.error('호출 실패: ' + (e instanceof Error ? e.message : String(e)))
    } finally { setLoading(false) }
  }

  // 파일 클릭 → /api/v1/flow/file 로 전체 내용 fetch
  useEffect(() => {
    if (!openFile) { setFileData(null); setFileError(''); return }
    setFileLoading(true); setFileError(''); setFileData(null); setCopied(false)
    const qs = new URLSearchParams({ path: openFile })
    fetch(`/api/v1/flow/file?${qs}`, { credentials: 'include' })
      .then(async (r) => {
        const d = await r.json()
        if (d.success && d.data) setFileData(d.data as FileViewerData)
        else setFileError(d.error || '파일 조회 실패')
      })
      .catch((e) => setFileError(e instanceof Error ? e.message : String(e)))
      .finally(() => setFileLoading(false))
  }, [openFile])

  // ESC 로 모달 닫기
  useEffect(() => {
    if (!openFile) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpenFile(null) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [openFile])

  const copyContent = async () => {
    if (!fileData?.content) return
    try {
      await navigator.clipboard.writeText(fileData.content)
      setCopied(true)
      toast.success('파일 내용을 클립보드에 복사했습니다.')
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // HTTP/insecure context 폴백
      const ta = document.createElement('textarea')
      ta.value = fileData.content; ta.style.position = 'fixed'
      document.body.appendChild(ta); ta.select()
      try {
        document.execCommand('copy')
        toast.success('복사됨')
        setCopied(true); setTimeout(() => setCopied(false), 1500)
      } catch { toast.error('복사 실패 — 수동으로 선택해 주세요.') }
      document.body.removeChild(ta)
    }
  }

  return (
    <div style={{ padding: 20, maxWidth: 1000, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20 }}>
        <FaBolt size={22} style={{ color: '#f59e0b' }} />
        <h2 style={{ margin: 0, fontSize: 18 }}>테이블 변경 영향 분석</h2>
        <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
          TABLE → MyBatis → Java → Controller 역추적
        </span>
      </div>

      {/* 입력 폼 */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 20, flexWrap: 'wrap' }}>
        <input
          type="text"
          value={table}
          onChange={e => setTable(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && analyze()}
          placeholder="테이블명 (예: T_SHOP_INVT_SIDE)"
          style={{
            flex: 1, minWidth: 240, padding: '8px 12px', fontSize: 13,
            borderRadius: 6, border: '1px solid var(--border-color)',
            background: 'var(--bg-card)', color: 'var(--text-primary)', fontFamily: 'monospace',
          }}
        />
        <SourceSelector
          mode="sql"
          dbTypes={['TABLE']}
          pickName
          buttonLabel="소스선택하기"
          buttonTitle="Settings에 연결된 DB 의 테이블만 선택"
          onSelect={(name) => {
            const tableName = name.includes('.') ? name.split('.').slice(-1)[0] : name
            setTable(tableName.toUpperCase())
          }}
        />
        <select
          value={dml} onChange={e => setDml(e.target.value)}
          style={{ padding: '8px 10px', fontSize: 13, borderRadius: 6, border: '1px solid var(--border-color)', background: 'var(--bg-card)', color: 'var(--text-primary)' }}
        >
          {DML_OPTIONS.map(o => <option key={o} value={o}>{o}</option>)}
        </select>
        <button
          onClick={analyze} disabled={loading}
          style={{ padding: '8px 18px', fontSize: 13, fontWeight: 600, borderRadius: 6, border: 'none', background: '#f59e0b', color: '#fff', cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}
        >
          <FaSearch size={12} /> {loading ? '분석 중...' : '분석'}
        </button>
      </div>

      {/* 결과 */}
      {result && (
        <div>
          {/* 요약 카운트 — MiPlatform 카드 제거, 3 컬럼 */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginBottom: 20 }}>
            {[
              { label: 'MyBatis 구문', count: result.counts.statements, icon: <FaDatabase />,  color: '#3b82f6' },
              { label: 'Java 파일',   count: result.counts.javaFiles,   icon: <FaFileCode />,  color: '#10b981' },
              { label: 'Controller', count: result.counts.endpoints,    icon: <FaGlobe />,     color: '#8b5cf6' },
            ].map(item => (
              <div key={item.label} style={{ padding: 14, background: 'var(--bg-card)', borderRadius: 8, border: '1px solid var(--border-color)', borderLeft: `4px solid ${item.color}`, textAlign: 'center' }}>
                <div style={{ fontSize: 20, color: item.color, marginBottom: 4 }}>{item.icon}</div>
                <div style={{ fontSize: 24, fontWeight: 700, color: item.color }}>{item.count}</div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{item.label}</div>
              </div>
            ))}
          </div>

          {/* MyBatis 구문 */}
          {result.statements.length > 0 && (
            <Section title={`📋 MyBatis 구문 (${result.statements.length})`}>
              {result.statements.map(s => (
                <div
                  key={s.fullId}
                  onClick={() => s.file && setOpenFile(s.file)}
                  style={{ ...rowStyle, cursor: s.file ? 'pointer' : 'default' }}
                  title={s.file ? '클릭 → 파일 내용 보기' : ''}
                >
                  <span style={{ fontFamily: 'monospace', fontSize: 11, color: '#3b82f6' }}>[{s.dml}]</span>
                  <span style={{ fontFamily: 'monospace', fontSize: 11, flex: 1 }}>{s.fullId}</span>
                  {s.file && <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>{s.file}:{s.line}</span>}
                </div>
              ))}
            </Section>
          )}

          {/* Java 파일 — 클릭하면 모달 열림 */}
          {result.javaFiles.length > 0 && (
            <Section title={`☕ Java 파일 (${result.javaFiles.length}) — 클릭하여 내용 보기`}>
              {result.javaFiles.map(f => (
                <div
                  key={f}
                  onClick={() => setOpenFile(f)}
                  style={{ ...rowStyle, cursor: 'pointer' }}
                  title="클릭 → 파일 내용 보기"
                  onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = 'var(--bg-secondary)' }}
                  onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = 'transparent' }}
                >
                  <FaFileCode size={11} style={{ color: '#10b981', flexShrink: 0 }} />
                  <span style={{ fontFamily: 'monospace', fontSize: 11 }}>{f}</span>
                </div>
              ))}
            </Section>
          )}

          {/* Controller 엔드포인트 */}
          {result.endpoints.length > 0 && (
            <Section title={`🎯 Controller 엔드포인트 (${result.endpoints.length})`}>
              {result.endpoints.map((ep, i) => (
                <div
                  key={i}
                  onClick={() => ep.file && setOpenFile(ep.file)}
                  style={{ ...rowStyle, cursor: ep.file ? 'pointer' : 'default' }}
                  title={ep.file ? '클릭 → 파일 내용 보기' : ''}
                >
                  <span style={{ fontFamily: 'monospace', fontSize: 11, color: '#8b5cf6', minWidth: 70 }}>{ep.httpMethod}</span>
                  <span style={{ fontFamily: 'monospace', fontSize: 11, color: '#8b5cf6', flex: 1 }}>{ep.url}</span>
                  <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>{ep.className}.{ep.methodName}</span>
                </div>
              ))}
            </Section>
          )}

          {result.counts.statements === 0 && (
            <div style={{ padding: 20, textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
              테이블 <strong>{result.table}</strong> 에 대한 MyBatis 구문이 인덱스에서 감지되지 않았습니다.
              <br/><small>Java 인덱스 재빌드 후 다시 시도하거나, 테이블명이 정확한지 확인하세요.</small>
            </div>
          )}
        </div>
      )}

      {/* 파일 뷰어 모달 */}
      {openFile && (
        <div style={modalOverlay} onClick={() => setOpenFile(null)}>
          <div style={modalDialog} onClick={(e) => e.stopPropagation()}>
            <div style={modalHeader}>
              <div style={{ minWidth: 0, flex: 1 }}>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 2 }}>
                  파일 보기
                  {fileData && (
                    <>
                      <span style={{ marginLeft: 8 }}>· {(fileData.size / 1024).toFixed(1)} KB</span>
                      {fileData.encoding && (
                        <span style={{ marginLeft: 8 }}>· 인코딩 {fileData.encoding}</span>
                      )}
                    </>
                  )}
                </div>
                <div style={{ fontSize: 13, fontWeight: 600, fontFamily: 'monospace', wordBreak: 'break-all' }}>
                  {fileData?.name || openFile.split('/').pop()}
                </div>
                <div style={{ fontSize: 10, color: 'var(--text-muted)', fontFamily: 'monospace', wordBreak: 'break-all', marginTop: 2 }}>
                  {openFile}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
                <button
                  onClick={copyContent}
                  disabled={!fileData?.content}
                  style={copied ? modalCopyBtnDone : modalCopyBtn}
                  title="파일 내용을 클립보드에 복사"
                >
                  {copied ? <><FaCheck size={11} /> 복사됨</> : <><FaCopy size={11} /> 전체 복사</>}
                </button>
                <button
                  onClick={() => setOpenFile(null)}
                  style={modalCloseBtn}
                  title="닫기 (Esc)"
                ><FaTimes /></button>
              </div>
            </div>
            <div style={modalBody}>
              {fileLoading && (
                <div style={{ padding: 24, textAlign: 'center', color: 'var(--text-muted)' }}>
                  파일 불러오는 중...
                </div>
              )}
              {fileError && !fileLoading && (
                <div style={{ padding: 24, textAlign: 'center', color: '#ef4444', fontSize: 13 }}>
                  ⚠ {fileError}
                </div>
              )}
              {fileData && !fileLoading && (
                <pre style={fileContentStyle}>{fileData.content}</pre>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 16, borderRadius: 8, border: '1px solid var(--border-color)', overflow: 'hidden' }}>
      <div style={{ padding: '8px 14px', background: 'var(--bg-secondary)', fontWeight: 600, fontSize: 13, borderBottom: '1px solid var(--border-color)' }}>
        {title}
      </div>
      <div style={{ padding: '6px 0' }}>{children}</div>
    </div>
  )
}

const rowStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 8, padding: '4px 14px',
  borderBottom: '1px solid var(--border-color)', fontSize: 12,
  transition: 'background 0.1s',
}

// ── 모달 스타일 ─────────────────────────────────────────────────────────────

const modalOverlay: React.CSSProperties = {
  position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)',
  display: 'flex', justifyContent: 'center', alignItems: 'center',
  zIndex: 1000, padding: 24,
}

const modalDialog: React.CSSProperties = {
  background: 'var(--bg-secondary)', borderRadius: 10,
  boxShadow: '0 10px 40px rgba(0,0,0,0.35)',
  border: '1px solid var(--border-color)',
  width: 'min(960px, 96vw)', maxHeight: '88vh',
  display: 'flex', flexDirection: 'column',
}

const modalHeader: React.CSSProperties = {
  display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12,
  padding: '12px 16px', borderBottom: '1px solid var(--border-color)',
  background: 'var(--bg-card)', borderTopLeftRadius: 10, borderTopRightRadius: 10,
}

const modalBody: React.CSSProperties = {
  flex: 1, overflowY: 'auto', padding: 0,
}

const fileContentStyle: React.CSSProperties = {
  margin: 0, padding: '14px 18px',
  background: 'var(--bg-card)',
  fontSize: 12, fontFamily: 'monospace',
  whiteSpace: 'pre', overflow: 'auto',
  color: 'var(--text-primary)',
  minHeight: 240,
}

const modalCopyBtn: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 5,
  padding: '5px 10px', fontSize: 11, fontWeight: 600,
  background: 'var(--bg-secondary)', color: 'var(--text-primary)',
  border: '1px solid var(--border-color)', borderRadius: 4, cursor: 'pointer',
}

const modalCopyBtnDone: React.CSSProperties = {
  ...modalCopyBtn,
  background: '#10b981', color: '#ffffff', borderColor: '#10b981',
}

const modalCloseBtn: React.CSSProperties = {
  background: 'transparent', border: 'none', color: 'var(--text-muted)',
  cursor: 'pointer', fontSize: 14, padding: '4px 8px',
}
