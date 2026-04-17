import { useState, useEffect, useRef } from 'react'
import {
  FaDatabase, FaInfoCircle, FaCheckCircle, FaPlus, FaCopy, FaPlay,
  FaServer, FaExclamationTriangle, FaEraser,
} from 'react-icons/fa'
import Editor, { type OnMount } from '@monaco-editor/react'
import { useToast } from '../../hooks/useToast'
import { copyToClipboard } from '../../utils/clipboard'
import { useThemeStore } from '../../stores/themeStore'

/**
 * v4.3.x — SQL 인덱스 임팩트 시뮬레이션 (UX 개선판)
 *
 * 개선 사항:
 * - Monaco Editor 로 SQL 입력 (구문 강조 + 자동 들여쓰기)
 * - 분석 전 "어떤 DB로 조회될지" 미리 표시 (Settings vs 기본 H2 명확히 안내)
 * - 샘플 SQL 드롭다운 (즉시 체험 가능)
 * - DB 연결 실패시 명확한 사유 + Settings 페이지 링크
 * - 단축키 Ctrl+Enter 로 분석 실행
 */

interface IndexInfo {
  name: string
  columns: string[]
  nonUnique: boolean
  usableForQuery: boolean
  recommendation: string
}

interface Recommendation {
  indexName: string
  table: string
  columns: string[]
  ddl: string
  rationale: string
  priority: string
}

interface TableReport {
  table: string
  existingIndexes: IndexInfo[]
  recommendations: Recommendation[]
}

interface AdvisorResult {
  sql: string
  dbProfile: string
  detectedDbType?: string
  dbProduct?: string
  dbUrl?: string
  dbUsername?: string
  dbSource?: string                 // 'settings' / 'default' / 'default-fallback'
  settingsDbError?: string
  dbConnectionError?: string
  tables: string[]
  predicateColumns: string[]
  tableReports: TableReport[]
  summaryExistingIndexCount: number
  summaryNewRecommendCount: number
}

interface TargetDbInfo {
  hasExternal: boolean
  url?: string
  username?: string
  dbType?: string
  source?: string
  warning?: string
  error?: string
}

const SAMPLE_QUERIES: { label: string; sql: string }[] = [
  {
    label: '주문 + 사용자 JOIN (이메일/상태 필터)',
    sql: `SELECT o.id, o.created_at, u.email, u.name
FROM orders o
JOIN users u ON o.user_id = u.id
WHERE u.email = 'test@example.com'
  AND o.status = 'NEW'
ORDER BY o.created_at DESC
FETCH FIRST 50 ROWS ONLY;`,
  },
  {
    label: '단일 테이블 다중 조건',
    sql: `SELECT *
FROM products
WHERE category_id = 5
  AND price BETWEEN 1000 AND 50000
  AND stock_qty > 0;`,
  },
  {
    label: '서브쿼리 + LIKE 검색',
    sql: `SELECT id, title, author_id
FROM articles
WHERE author_id IN (SELECT id FROM authors WHERE country = 'KR')
  AND LOWER(title) LIKE '%spring%'
ORDER BY published_at DESC;`,
  },
  {
    label: '집계 쿼리 (GROUP BY + HAVING)',
    sql: `SELECT customer_id, COUNT(*) AS order_count, SUM(total_amount) AS total
FROM sales
WHERE order_date >= TRUNC(SYSDATE) - 30
GROUP BY customer_id
HAVING SUM(total_amount) > 100000;`,
  },
]

export default function IndexAdvisorPage() {
  const [sql, setSql] = useState(SAMPLE_QUERIES[0].sql)
  const [result, setResult] = useState<AdvisorResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [targetDb, setTargetDb] = useState<TargetDbInfo | null>(null)
  const editorRef = useRef<any>(null)
  const toast = useToast()
  const { theme } = useThemeStore()

  // 페이지 진입 시 대상 DB 정보 로드
  useEffect(() => {
    fetch('/api/v1/sql/index-advisor/target-db', { credentials: 'include' })
      .then((r) => r.ok ? r.json() : null)
      .then((j) => setTargetDb((j?.data ?? j) as TargetDbInfo))
      .catch(() => {})
  }, [])

  const onEditorMount: OnMount = (editor, monaco) => {
    editorRef.current = editor
    // Ctrl+Enter / Cmd+Enter 단축키
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => analyze())
  }

  const analyze = async () => {
    if (!sql.trim()) {
      toast.error('SQL을 입력해주세요')
      return
    }
    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const res = await fetch('/api/v1/sql/index-advisor', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ sql, dbProfile: 'settings' }),
      })
      const j = await res.json().catch(() => null)
      if (!res.ok || !j?.success) {
        setError(j?.error || `HTTP ${res.status}`)
        return
      }
      setResult(j.data as AdvisorResult)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  const copyDdl = async (ddl: string) => {
    const ok = await copyToClipboard(ddl)
    toast[ok ? 'success' : 'error'](ok ? 'DDL 복사됨' : '복사 실패')
  }

  const loadSample = (s: string) => {
    setSql(s)
    setResult(null)
    setError(null)
  }

  const clearAll = () => {
    setSql('')
    setResult(null)
    setError(null)
  }

  return (
    <>
      <div style={{ marginBottom: '16px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaDatabase style={{ color: '#10b981' }} /> SQL 인덱스 임팩트 시뮬레이션
        </h2>
        <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px', margin: '4px 0 0' }}>
          입력 SQL의 WHERE/JOIN 조건을 분석하여, 대상 DB의 기존 인덱스 활용 가능성과 신규 인덱스 추천 DDL을 생성합니다.
        </p>
      </div>

      {/* 대상 DB 안내 박스 */}
      <TargetDbBanner info={targetDb} />

      {/* 입력 영역 */}
      <div style={{
        background: 'var(--bg-card)', border: '1px solid var(--border-color)',
        borderRadius: '8px', padding: '12px', marginBottom: '12px',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px', flexWrap: 'wrap', gap: '8px' }}>
          <label style={{ fontSize: '13px', fontWeight: 600 }}>SQL 입력</label>
          <div style={{ display: 'flex', gap: '6px', alignItems: 'center', flexWrap: 'wrap' }}>
            <select
              onChange={(e) => {
                const idx = parseInt(e.target.value, 10)
                if (!isNaN(idx)) loadSample(SAMPLE_QUERIES[idx].sql)
                e.target.value = ''
              }}
              defaultValue=""
              style={{
                padding: '4px 10px', fontSize: '12px',
                background: 'var(--bg-default)', border: '1px solid var(--border-color)',
                borderRadius: '4px', color: 'var(--text-default)', cursor: 'pointer',
              }}>
              <option value="" disabled>📝 샘플 SQL 불러오기...</option>
              {SAMPLE_QUERIES.map((q, i) => <option key={i} value={i}>{q.label}</option>)}
            </select>
            <button onClick={clearAll}
              style={{ padding: '4px 10px', fontSize: '12px', background: 'var(--bg-default)', border: '1px solid var(--border-color)', borderRadius: '4px', color: 'var(--text-muted)', cursor: 'pointer' }}>
              <FaEraser /> 초기화
            </button>
          </div>
        </div>

        <div style={{ border: '1px solid var(--border-color)', borderRadius: '6px', overflow: 'hidden' }}>
          <Editor
            height="240px"
            language="sql"
            theme={theme === 'dark' ? 'vs-dark' : 'light'}
            value={sql}
            onChange={(v) => setSql(v ?? '')}
            onMount={onEditorMount}
            options={{
              minimap: { enabled: false },
              fontSize: 13,
              tabSize: 2,
              wordWrap: 'on',
              lineNumbers: 'on',
              scrollBeyondLastLine: false,
              automaticLayout: true,
              renderLineHighlight: 'all',
            }}
          />
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '10px', flexWrap: 'wrap', gap: '8px' }}>
          <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
            💡 단축키: <kbd style={kbdStyle}>Ctrl</kbd>+<kbd style={kbdStyle}>Enter</kbd> 로 즉시 분석
          </span>
          <button
            onClick={analyze}
            disabled={loading || !sql.trim()}
            style={{
              padding: '8px 22px', background: 'var(--accent)', color: '#fff',
              border: 'none', borderRadius: '6px', cursor: loading ? 'wait' : 'pointer',
              opacity: loading || !sql.trim() ? 0.6 : 1, fontWeight: 600,
              display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px',
            }}>
            <FaPlay /> {loading ? '분석 중...' : '인덱스 시뮬레이션 실행'}
          </button>
        </div>
      </div>

      {error && (
        <div style={{ padding: '12px', background: 'rgba(239,68,68,0.1)', color: 'var(--red, #ef4444)', borderRadius: '6px', marginBottom: '16px' }}>
          오류: {error}
        </div>
      )}

      {result && <ResultPanel result={result} onCopyDdl={copyDdl} />}
    </>
  )
}

// ── 컴포넌트 ──────────────────────────────────────────────────────────────

function TargetDbBanner({ info }: { info: TargetDbInfo | null }) {
  if (!info) return null

  const isExternal = info.hasExternal
  const isFallback = !isExternal
  const color = isExternal ? '#10b981' : '#f59e0b'
  const bg    = isExternal ? 'rgba(16,185,129,0.08)' : 'rgba(245,158,11,0.08)'

  return (
    <div style={{
      background: bg, border: `1px solid ${color}`,
      borderRadius: '8px', padding: '12px 14px', marginBottom: '12px',
      display: 'flex', gap: '10px', alignItems: 'flex-start',
    }}>
      <FaServer style={{ color, fontSize: '16px', marginTop: '2px', flexShrink: 0 }} />
      <div style={{ flex: 1, fontSize: '12px' }}>
        <div style={{ fontWeight: 600, color, marginBottom: '4px' }}>
          {isExternal ? '✅ Settings 의 외부 DB 로 메타조회됩니다' : '⚠️ 외부 DB 미설정 — 앱 내부 DB(H2)로 폴백됩니다'}
        </div>
        {info.url && (
          <div style={{ fontFamily: 'monospace', fontSize: '11px', color: 'var(--text-default)', marginBottom: '2px' }}>
            <strong>URL:</strong> <code>{info.url}</code>
            {info.dbType && <span style={{ marginLeft: 8, padding: '1px 6px', background: color + '22', color, borderRadius: '3px', fontWeight: 600 }}>{info.dbType.toUpperCase()}</span>}
          </div>
        )}
        {info.username && (
          <div style={{ fontFamily: 'monospace', fontSize: '11px', color: 'var(--text-muted)' }}>
            <strong>User:</strong> {info.username}
          </div>
        )}
        {isFallback && (
          <div style={{ marginTop: '6px', color: 'var(--text-muted)' }}>
            💡 운영 DB 의 인덱스를 보려면 <a href="/settings" style={{ color }}>⚙️ Settings → Oracle DB 설정</a> 에서 URL/계정을 입력하세요.
          </div>
        )}
        {info.error && (
          <div style={{ marginTop: '4px', color: 'var(--red, #ef4444)' }}>
            ❌ {info.error}
          </div>
        )}
      </div>
    </div>
  )
}

function ResultPanel({ result, onCopyDdl }: { result: AdvisorResult; onCopyDdl: (ddl: string) => void }) {
  return (
    <>
      {/* 분석된 DB 정보 표시 */}
      {result.dbProduct && (
        <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '10px', padding: '8px 12px', background: 'var(--bg-card)', borderRadius: '6px', border: '1px solid var(--border-color)' }}>
          <FaInfoCircle style={{ marginRight: 6 }} />
          <strong>분석 대상 DB:</strong> <code>{result.dbProduct}</code>
          {result.dbUrl && <> · <code style={{ fontSize: '11px' }}>{result.dbUrl}</code></>}
          {result.dbSource === 'settings' && <span style={{ marginLeft: 8, color: '#10b981' }}>✓ Settings 사용</span>}
          {result.dbSource === 'default-fallback' && <span style={{ marginLeft: 8, color: '#f59e0b' }}>⚠ Settings 연결 실패 → 기본 DB 폴백</span>}
          {result.dbSource === 'default' && <span style={{ marginLeft: 8, color: '#f59e0b' }}>⚠ 기본 DB(H2)</span>}
        </div>
      )}

      {result.settingsDbError && (
        <div style={{ padding: '10px 12px', background: 'rgba(245,158,11,0.1)', color: '#f59e0b', borderRadius: '6px', marginBottom: '12px', fontSize: '12px' }}>
          <FaExclamationTriangle style={{ marginRight: 6 }} />
          Settings DB 연결 실패: {result.settingsDbError}
        </div>
      )}

      {/* 요약 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '10px', marginBottom: '16px' }}>
        <SummaryBox label="대상 테이블" value={result.tables?.length ?? 0} color="#3b82f6" />
        <SummaryBox label="조건 컬럼"   value={result.predicateColumns?.length ?? 0} color="#8b5cf6" />
        <SummaryBox label="활용 가능 인덱스" value={result.summaryExistingIndexCount ?? 0} color="#10b981" />
        <SummaryBox label="신규 추천"   value={result.summaryNewRecommendCount ?? 0} color="#f59e0b" />
      </div>

      {result.dbConnectionError && (
        <div style={{ padding: '10px', background: 'rgba(245,158,11,0.1)', color: '#f59e0b', borderRadius: '6px', marginBottom: '12px', fontSize: '12px' }}>
          <FaInfoCircle style={{ marginRight: 6 }} />
          DB 메타데이터 조회 실패 — 신규 인덱스 추천만 표시됩니다. ({result.dbConnectionError})
        </div>
      )}

      {/* 테이블별 리포트 */}
      {result.tableReports?.map((tr) => (
        <div key={tr.table} style={{
          background: 'var(--bg-card)', border: '1px solid var(--border-color)',
          borderRadius: '8px', padding: '14px', marginBottom: '12px',
        }}>
          <div style={{ fontWeight: 700, fontSize: '14px', marginBottom: '10px', color: '#3b82f6' }}>
            📋 {tr.table}
          </div>

          {tr.existingIndexes?.length > 0 && (
            <div style={{ marginBottom: '12px' }}>
              <div style={{ fontSize: '12px', fontWeight: 600, marginBottom: '6px', color: 'var(--text-muted)' }}>기존 인덱스</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                {tr.existingIndexes.map((idx) => (
                  <div key={idx.name} style={{
                    padding: '6px 10px', borderRadius: '4px', fontSize: '12px',
                    background: idx.usableForQuery ? 'rgba(16,185,129,0.06)' : 'rgba(100,116,139,0.04)',
                    borderLeft: `3px solid ${idx.usableForQuery ? '#10b981' : '#94a3b8'}`,
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      {idx.usableForQuery
                        ? <FaCheckCircle style={{ color: '#10b981', fontSize: '11px' }} />
                        : <FaInfoCircle style={{ color: '#94a3b8', fontSize: '11px' }} />}
                      <code style={{ fontWeight: 600 }}>{idx.name}</code>
                      <span style={{ color: 'var(--text-muted)' }}>
                        ({(idx.columns ?? []).join(', ')}{idx.nonUnique ? '' : ', UNIQUE'})
                      </span>
                    </div>
                    <div style={{ marginTop: '2px', color: 'var(--text-muted)', paddingLeft: '17px' }}>{idx.recommendation}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tr.recommendations?.length > 0 && (
            <div>
              <div style={{ fontSize: '12px', fontWeight: 600, marginBottom: '6px', color: 'var(--text-muted)' }}>신규 인덱스 추천</div>
              {tr.recommendations.map((rec) => (
                <div key={rec.indexName} style={{
                  padding: '10px', borderRadius: '4px', marginBottom: '6px',
                  background: 'rgba(245,158,11,0.05)', borderLeft: '3px solid #f59e0b',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                    <FaPlus style={{ color: '#f59e0b', fontSize: '11px' }} />
                    <code style={{ fontWeight: 700, fontSize: '12px' }}>{rec.indexName}</code>
                    <span style={{
                      padding: '1px 6px', fontSize: '10px', borderRadius: '3px',
                      background: rec.priority === 'HIGH' ? '#ef4444' : '#3b82f6',
                      color: '#fff',
                    }}>{rec.priority}</span>
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginBottom: '6px' }}>
                    {rec.rationale}
                  </div>
                  <div style={{ position: 'relative' }}>
                    <pre style={{
                      background: 'var(--bg-default)', padding: '8px 10px', borderRadius: '4px',
                      fontSize: '12px', overflowX: 'auto', margin: 0, fontFamily: 'monospace',
                    }}>{rec.ddl}</pre>
                    <button
                      onClick={() => onCopyDdl(rec.ddl)}
                      style={{
                        position: 'absolute', top: '4px', right: '4px',
                        padding: '2px 6px', fontSize: '10px', cursor: 'pointer',
                        background: 'var(--bg-card)', border: '1px solid var(--border-color)',
                        borderRadius: '3px', color: 'var(--text-default)',
                      }}>
                      <FaCopy /> 복사
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {!tr.existingIndexes?.length && !tr.recommendations?.length && (
            <div style={{ padding: '12px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '12px' }}>
              분석된 인덱스 정보가 없습니다.
            </div>
          )}
        </div>
      ))}

      {(!result.tableReports || result.tableReports.length === 0) && (
        <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)' }}>
          조건절(WHERE/JOIN)에서 인덱스 추천 가능한 컬럼을 찾지 못했습니다.
        </div>
      )}
    </>
  )
}

function SummaryBox({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={{
      background: 'var(--bg-card)', border: '1px solid var(--border-color)',
      borderLeft: `4px solid ${color}`, borderRadius: '6px', padding: '10px 12px',
    }}>
      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{label}</div>
      <div style={{ fontSize: '20px', fontWeight: 700, color }}>{value}</div>
    </div>
  )
}

const kbdStyle: React.CSSProperties = {
  padding: '1px 5px', background: 'var(--bg-default)',
  border: '1px solid var(--border-color)', borderRadius: '3px',
  fontFamily: 'monospace', fontSize: '11px',
}
