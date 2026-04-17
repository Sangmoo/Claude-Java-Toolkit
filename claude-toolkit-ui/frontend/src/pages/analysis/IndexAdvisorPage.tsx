import { useState } from 'react'
import { FaDatabase, FaInfoCircle, FaCheckCircle, FaPlus, FaCopy } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import { copyToClipboard } from '../../utils/clipboard'

/**
 * v4.3.0 — SQL 인덱스 임팩트 시뮬레이션.
 *
 * 입력 SQL 을 백엔드(IndexAdvisorService) 가 정적 파싱하여:
 * - WHERE/JOIN 컬럼 추출
 * - 대상 DB 메타데이터에서 기존 인덱스 조회
 * - 활용 가능 인덱스 + 신규 인덱스 DDL 추천
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
  dbConnectionError?: string
  tables: string[]
  predicateColumns: string[]
  tableReports: TableReport[]
  summaryExistingIndexCount: number
  summaryNewRecommendCount: number
}

const SAMPLE_SQL = `SELECT o.id, o.created_at, u.email
FROM orders o
JOIN users u ON o.user_id = u.id
WHERE u.email = 'test@example.com'
  AND o.status = 'NEW'
ORDER BY o.created_at DESC;`

export default function IndexAdvisorPage() {
  const [sql, setSql] = useState(SAMPLE_SQL)
  const [result, setResult] = useState<AdvisorResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()

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
        body: JSON.stringify({ sql, dbProfile: 'current' }),
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

  return (
    <>
      <div style={{ marginBottom: '20px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaDatabase style={{ color: '#10b981' }} /> SQL 인덱스 임팩트 시뮬레이션
        </h2>
        <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>
          입력한 SQL의 WHERE/JOIN 조건을 분석하여, 대상 DB의 기존 인덱스 활용 가능성과 신규 인덱스 추천 DDL을 생성합니다.
        </p>
      </div>

      {/* 입력 영역 */}
      <div style={{ marginBottom: '16px' }}>
        <label style={{ display: 'block', fontSize: '13px', fontWeight: 600, marginBottom: '6px' }}>
          SQL 입력
        </label>
        <textarea
          value={sql}
          onChange={(e) => setSql(e.target.value)}
          rows={10}
          style={{
            width: '100%', padding: '10px', fontFamily: 'monospace', fontSize: '13px',
            background: 'var(--bg-card)', border: '1px solid var(--border-color)',
            borderRadius: '6px', color: 'var(--text-default)', resize: 'vertical',
          }}
          placeholder="SELECT ... FROM ... WHERE ..."
        />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '8px' }}>
          <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
            Settings 의 DB 프로필을 메타데이터 조회 대상으로 사용합니다.
          </span>
          <button
            onClick={analyze}
            disabled={loading || !sql.trim()}
            style={{
              padding: '8px 18px', background: 'var(--accent)', color: '#fff',
              border: 'none', borderRadius: '6px', cursor: loading ? 'wait' : 'pointer',
              opacity: loading || !sql.trim() ? 0.6 : 1, fontWeight: 600,
            }}>
            {loading ? '분석 중...' : '인덱스 시뮬레이션 실행'}
          </button>
        </div>
      </div>

      {error && (
        <div style={{ padding: '12px', background: 'rgba(239,68,68,0.1)', color: 'var(--red)', borderRadius: '6px', marginBottom: '16px' }}>
          오류: {error}
        </div>
      )}

      {result && (
        <>
          {/* 요약 */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '10px', marginBottom: '20px' }}>
            <SummaryBox label="대상 테이블"     value={result.tables.length}             color="#3b82f6" />
            <SummaryBox label="조건 컬럼"       value={result.predicateColumns.length}    color="#8b5cf6" />
            <SummaryBox label="활용 가능 인덱스" value={result.summaryExistingIndexCount} color="#10b981" />
            <SummaryBox label="신규 추천"       value={result.summaryNewRecommendCount}  color="#f59e0b" />
          </div>

          {result.dbConnectionError && (
            <div style={{ padding: '10px', background: 'rgba(245,158,11,0.1)', color: '#f59e0b', borderRadius: '6px', marginBottom: '16px', fontSize: '12px' }}>
              <FaInfoCircle style={{ marginRight: 6 }} />
              DB 메타데이터 조회 실패 — 신규 인덱스 추천만 표시됩니다. ({result.dbConnectionError})
            </div>
          )}

          {result.dbProduct && (
            <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '12px' }}>
              감지된 DB: <code>{result.dbProduct}</code> (type: <code>{result.detectedDbType}</code>)
            </div>
          )}

          {/* 테이블별 리포트 */}
          {result.tableReports.map((tr) => (
            <div key={tr.table} style={{
              background: 'var(--bg-card)', border: '1px solid var(--border-color)',
              borderRadius: '8px', padding: '16px', marginBottom: '16px',
            }}>
              <div style={{ fontWeight: 700, fontSize: '15px', marginBottom: '12px', color: '#3b82f6' }}>
                📋 {tr.table}
              </div>

              {/* 기존 인덱스 */}
              {tr.existingIndexes.length > 0 && (
                <div style={{ marginBottom: '14px' }}>
                  <div style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px' }}>기존 인덱스</div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    {tr.existingIndexes.map((idx) => (
                      <div key={idx.name} style={{
                        padding: '8px 12px', borderRadius: '6px', fontSize: '12px',
                        background: idx.usableForQuery ? 'rgba(16,185,129,0.08)' : 'rgba(100,116,139,0.05)',
                        borderLeft: `3px solid ${idx.usableForQuery ? '#10b981' : '#94a3b8'}`,
                      }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                          {idx.usableForQuery
                            ? <FaCheckCircle style={{ color: '#10b981' }} />
                            : <FaInfoCircle style={{ color: '#94a3b8' }} />}
                          <code style={{ fontWeight: 600 }}>{idx.name}</code>
                          <span style={{ color: 'var(--text-muted)' }}>
                            ({idx.columns.join(', ')}{idx.nonUnique ? '' : ', UNIQUE'})
                          </span>
                        </div>
                        <div style={{ marginTop: '4px', color: 'var(--text-muted)' }}>{idx.recommendation}</div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* 신규 추천 */}
              {tr.recommendations.length > 0 && (
                <div>
                  <div style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px' }}>신규 인덱스 추천</div>
                  {tr.recommendations.map((rec) => (
                    <div key={rec.indexName} style={{
                      padding: '10px 12px', borderRadius: '6px', marginBottom: '8px',
                      background: 'rgba(245,158,11,0.06)', borderLeft: '3px solid #f59e0b',
                    }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '6px' }}>
                        <FaPlus style={{ color: '#f59e0b' }} />
                        <code style={{ fontWeight: 700 }}>{rec.indexName}</code>
                        <span style={{
                          padding: '1px 6px', fontSize: '10px', borderRadius: '4px',
                          background: rec.priority === 'HIGH' ? '#ef4444' : '#3b82f6',
                          color: '#fff',
                        }}>{rec.priority}</span>
                      </div>
                      <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>
                        {rec.rationale}
                      </div>
                      <div style={{ position: 'relative' }}>
                        <pre style={{
                          background: 'var(--bg-default)', padding: '10px', borderRadius: '4px',
                          fontSize: '12px', overflowX: 'auto', margin: 0,
                        }}>{rec.ddl}</pre>
                        <button
                          onClick={() => copyDdl(rec.ddl)}
                          style={{
                            position: 'absolute', top: '6px', right: '6px',
                            padding: '4px 8px', fontSize: '11px', cursor: 'pointer',
                            background: 'var(--bg-card)', border: '1px solid var(--border-color)',
                            borderRadius: '4px', color: 'var(--text-default)',
                          }}>
                          <FaCopy /> 복사
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {tr.existingIndexes.length === 0 && tr.recommendations.length === 0 && (
                <div style={{ padding: '12px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                  분석된 인덱스 정보가 없습니다.
                </div>
              )}
            </div>
          ))}

          {result.tableReports.length === 0 && (
            <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)' }}>
              조건절(WHERE/JOIN)에서 인덱스 추천 가능한 컬럼을 찾지 못했습니다.
            </div>
          )}
        </>
      )}
    </>
  )
}

function SummaryBox({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={{
      background: 'var(--bg-card)', border: '1px solid var(--border-color)',
      borderLeft: `4px solid ${color}`, borderRadius: '6px', padding: '12px',
    }}>
      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{label}</div>
      <div style={{ fontSize: '22px', fontWeight: 700, color }}>{value}</div>
    </div>
  )
}
