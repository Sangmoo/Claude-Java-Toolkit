import { useState } from 'react'
import { FaBolt, FaSearch, FaDatabase, FaFileCode, FaGlobe, FaDesktop } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

/**
 * v4.5 — SP 호출 흐름 역추적 (SP Flow 분석).
 * SP 이름 → MyBatis 구문(snippet 검색) → Java Files → Controller → MiPlatform 화면
 */

interface Statement { fullId: string; dml: string; file: string; line: number; snippet: string }
interface Endpoint  { url: string; httpMethod: string; className: string; methodName: string; file: string; line: number }
interface SpImpactData {
  sp: string
  statements: Statement[]; javaFiles: string[]; endpoints: Endpoint[]; screens: string[]
  counts: { statements: number; javaFiles: number; endpoints: number; screens: number }
}

export default function SpImpactPage() {
  const toast = useToast()
  const [sp,      setSp]      = useState('')
  const [loading, setLoading] = useState(false)
  const [result,  setResult]  = useState<SpImpactData | null>(null)

  const analyze = async () => {
    if (!sp.trim()) { toast.warning('SP 이름을 입력하세요.'); return }
    setLoading(true)
    try {
      const qs = new URLSearchParams({ sp: sp.trim().toUpperCase() })
      const r = await fetch(`/api/v1/flow/sp-impact?${qs}`, { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) setResult(d.data as SpImpactData)
      else toast.error(d.error || '분석 실패')
    } catch (e: unknown) {
      toast.error('호출 실패: ' + (e instanceof Error ? e.message : String(e)))
    } finally { setLoading(false) }
  }

  return (
    <div style={{ padding: 20, maxWidth: 1000, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20 }}>
        <FaBolt size={22} style={{ color: '#8b5cf6' }} />
        <h2 style={{ margin: 0, fontSize: 18 }}>SP 흐름 분석</h2>
        <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
          SP 이름 → MyBatis 구문 → Java → Controller → MiPlatform 화면 역추적
        </span>
      </div>

      {/* 입력 폼 */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 20, flexWrap: 'wrap' }}>
        <input
          type="text"
          value={sp}
          onChange={e => setSp(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && analyze()}
          placeholder="SP 이름 (예: SP_GET_SHOP_LIST)"
          style={{
            flex: 1, minWidth: 240, padding: '8px 12px', fontSize: 13,
            borderRadius: 6, border: '1px solid var(--border-color)',
            background: 'var(--bg-card)', color: 'var(--text-primary)', fontFamily: 'monospace',
          }}
        />
        <button
          onClick={analyze} disabled={loading}
          style={{ padding: '8px 18px', fontSize: 13, fontWeight: 600, borderRadius: 6, border: 'none', background: '#8b5cf6', color: '#fff', cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}
        >
          <FaSearch size={12} /> {loading ? '분석 중...' : '분석'}
        </button>
      </div>

      {/* 결과 */}
      {result && (
        <div>
          {/* 요약 카운트 */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, marginBottom: 20 }}>
            {[
              { label: 'MyBatis 구문', count: result.counts.statements, icon: <FaDatabase />,  color: '#3b82f6' },
              { label: 'Java 파일',   count: result.counts.javaFiles,   icon: <FaFileCode />,  color: '#10b981' },
              { label: 'Controller', count: result.counts.endpoints,    icon: <FaGlobe />,     color: '#8b5cf6' },
              { label: 'MiPlatform', count: result.counts.screens,      icon: <FaDesktop />,   color: '#f97316' },
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
                <div key={s.fullId} style={{ ...rowStyle, flexDirection: 'column', alignItems: 'flex-start', gap: 2 }}>
                  <div style={{ display: 'flex', gap: 8, width: '100%' }}>
                    <span style={{ fontFamily: 'monospace', fontSize: 11, color: '#3b82f6' }}>[{s.dml}]</span>
                    <span style={{ fontFamily: 'monospace', fontSize: 11, flex: 1 }}>{s.fullId}</span>
                    {s.file && <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>{s.file}:{s.line}</span>}
                  </div>
                  {s.snippet && (
                    <div style={{ fontFamily: 'monospace', fontSize: 10, color: 'var(--text-muted)', background: 'var(--bg-secondary)', padding: '2px 6px', borderRadius: 4, maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {s.snippet}
                    </div>
                  )}
                </div>
              ))}
            </Section>
          )}

          {/* Java 파일 */}
          {result.javaFiles.length > 0 && (
            <Section title={`☕ Java 파일 (${result.javaFiles.length})`}>
              {result.javaFiles.map(f => (
                <div key={f} style={rowStyle}>
                  <span style={{ fontFamily: 'monospace', fontSize: 11 }}>{f}</span>
                </div>
              ))}
            </Section>
          )}

          {/* Controller 엔드포인트 */}
          {result.endpoints.length > 0 && (
            <Section title={`🎯 Controller 엔드포인트 (${result.endpoints.length})`}>
              {result.endpoints.map((ep, i) => (
                <div key={i} style={rowStyle}>
                  <span style={{ fontFamily: 'monospace', fontSize: 11, color: '#8b5cf6', minWidth: 70 }}>{ep.httpMethod}</span>
                  <span style={{ fontFamily: 'monospace', fontSize: 11, color: '#8b5cf6', flex: 1 }}>{ep.url}</span>
                  <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>{ep.className}.{ep.methodName}</span>
                </div>
              ))}
            </Section>
          )}

          {/* MiPlatform 화면 */}
          {result.screens.length > 0 && (
            <Section title={`🖥️ MiPlatform 화면 (${result.screens.length})`}>
              {result.screens.map(s => (
                <div key={s} style={rowStyle}>
                  <span style={{ fontFamily: 'monospace', fontSize: 11 }}>{s}</span>
                </div>
              ))}
            </Section>
          )}

          {result.counts.statements === 0 && (
            <div style={{ padding: 20, textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
              SP <strong>{result.sp}</strong>를 호출하는 MyBatis 구문이 인덱스에서 감지되지 않았습니다.
              <br/><small>Java 인덱스 재빌드 후 다시 시도하거나, SP 이름이 정확한지 확인하세요.</small>
            </div>
          )}
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
}
