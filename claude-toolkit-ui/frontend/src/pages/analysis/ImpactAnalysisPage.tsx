import { useState } from 'react'
import { FaBolt, FaSearch, FaDatabase, FaFileCode, FaGlobe, FaDesktop } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

/**
 * v4.5 — 테이블 변경 영향 분석 (Impact Analysis).
 * TABLE → MyBatis Statements → Java Files → Controller Endpoints → MiPlatform Screens
 */

interface Statement { fullId: string; dml: string; file: string; line: number }
interface Endpoint  { url: string; httpMethod: string; className: string; methodName: string; file: string; line: number }
interface ImpactData {
  table: string; dml: string
  statements: Statement[]; javaFiles: string[]; endpoints: Endpoint[]; screens: string[]
  counts: { statements: number; javaFiles: number; endpoints: number; screens: number }
}

const DML_OPTIONS = ['ALL', 'SELECT', 'INSERT', 'UPDATE', 'MERGE', 'DELETE']

export default function ImpactAnalysisPage() {
  const toast = useToast()
  const [table,   setTable]   = useState('')
  const [dml,     setDml]     = useState('ALL')
  const [loading, setLoading] = useState(false)
  const [result,  setResult]  = useState<ImpactData | null>(null)

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

  return (
    <div style={{ padding: 20, maxWidth: 1000, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20 }}>
        <FaBolt size={22} style={{ color: '#f59e0b' }} />
        <h2 style={{ margin: 0, fontSize: 18 }}>테이블 변경 영향 분석</h2>
        <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
          TABLE → MyBatis → Java → Controller → MiPlatform 화면 역추적
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
                <div key={s.fullId} style={rowStyle}>
                  <span style={{ fontFamily: 'monospace', fontSize: 11, color: '#3b82f6' }}>[{s.dml}]</span>
                  <span style={{ fontFamily: 'monospace', fontSize: 11, flex: 1 }}>{s.fullId}</span>
                  {s.file && <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>{s.file}:{s.line}</span>}
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
              테이블 <strong>{result.table}</strong> 에 대한 MyBatis 구문이 인덱스에서 감지되지 않았습니다.
              <br/><small>Java 인덱스 재빌드 후 다시 시도하거나, 테이블명이 정확한지 확인하세요.</small>
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
