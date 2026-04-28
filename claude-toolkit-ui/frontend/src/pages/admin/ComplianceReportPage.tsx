import { useEffect, useState } from 'react'
import {
  FaShieldAlt, FaPlay, FaSpinner, FaDownload, FaCopy, FaCheck, FaInfoCircle, FaFileAlt,
} from 'react-icons/fa'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { useApi } from '../../hooks/useApi'
import { useToast } from '../../hooks/useToast'

interface ComplianceType {
  key: string
  label: string
  description: string
  enabled: boolean
}

interface GeneratedReport {
  id: string
  type: string
  typeLabel: string
  from: string
  to: string
  generatedAt: string
  generatedBy: string
  markdown: string
  suggestedFilename: string
}

/**
 * v4.6.x — 한국 컴플라이언스 리포트 페이지 (ADMIN 전용).
 *
 * <p>Stage 1 — 전자금융감독규정 1개 타입 + Markdown 다운로드.
 * Stage 2 에서 나머지 3종 + PDF/Excel 추가 예정.
 */
export default function ComplianceReportPage() {
  const [types, setTypes]         = useState<ComplianceType[]>([])
  const [type,  setType]          = useState<string>('fss')
  const [from,  setFrom]          = useState<string>(defaultFromDate())
  const [to,    setTo]            = useState<string>(defaultToDate())
  const [report, setReport]       = useState<GeneratedReport | null>(null)
  const [loading, setLoading]     = useState(false)
  const [copied, setCopied]       = useState(false)
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    api.get('/api/v1/admin/compliance/types').then((d) => {
      if (d) setTypes(d as ComplianceType[])
    })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const generate = async () => {
    if (!from || !to) { toast.warning('감사 기간을 입력하세요.'); return }
    if (from > to) { toast.warning('시작일이 종료일보다 늦을 수 없습니다.'); return }
    setLoading(true)
    setReport(null)
    try {
      const body = new URLSearchParams({ type, from, to })
      const res = await fetch('/api/v1/admin/compliance/generate', {
        method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
      })
      const d = await res.json()
      if (d.success && d.data) {
        setReport(d.data as GeneratedReport)
        toast.success('리포트 생성 완료')
      } else {
        toast.error(d.error || '리포트 생성 실패')
      }
    } catch (e: unknown) {
      toast.error('호출 실패: ' + (e instanceof Error ? e.message : String(e)))
    } finally {
      setLoading(false)
    }
  }

  const downloadMd = () => {
    if (!report) return
    window.open(`/api/v1/admin/compliance/${report.id}/download?format=md`, '_blank')
  }

  const copyMd = async () => {
    if (!report) return
    try {
      await navigator.clipboard.writeText(report.markdown)
      setCopied(true)
      toast.success('Markdown 복사됨')
      setTimeout(() => setCopied(false), 1500)
    } catch {
      const ta = document.createElement('textarea')
      ta.value = report.markdown; ta.style.position = 'fixed'
      document.body.appendChild(ta); ta.select()
      try { document.execCommand('copy'); toast.success('복사됨'); setCopied(true); setTimeout(() => setCopied(false), 1500) }
      catch { toast.error('복사 실패 — 수동으로 선택해 주세요.') }
      document.body.removeChild(ta)
    }
  }

  return (
    <div style={{ padding: '4px 0' }}>
      <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
        <FaShieldAlt style={{ color: '#f59e0b' }} /> 한국 컴플라이언스 리포트
        <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 400, marginLeft: 6 }}>
          ADMIN 전용 · Stage 1 — FSS / Markdown
        </span>
      </h2>
      <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 20 }}>
        review_history · audit_log 기반 자동 집계 리포트.
        법적 컴플라이언스 증빙으로 사용하기 전엔 법무 / 컴플라이언스 팀 검토를 받으세요.
      </p>

      {/* 입력 폼 */}
      <div style={{
        background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
        borderRadius: 12, padding: 18, marginBottom: 20,
      }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 14 }}>
          <Field label="리포트 타입">
            <select value={type} onChange={(e) => setType(e.target.value)} style={selectStyle}>
              {types.map((t) => (
                <option key={t.key} value={t.key} disabled={!t.enabled}>
                  {t.label}{!t.enabled && ' (Stage 2 예정)'}
                </option>
              ))}
            </select>
          </Field>
          <Field label="감사 시작일">
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} style={selectStyle} />
          </Field>
          <Field label="감사 종료일">
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} style={selectStyle} />
          </Field>
          <Field label="&nbsp;">
            <button onClick={generate} disabled={loading} style={{
              padding: '8px 16px', borderRadius: 6, border: 'none',
              background: loading ? 'var(--text-muted)' : 'var(--accent)',
              color: '#fff', fontSize: 13, fontWeight: 600,
              cursor: loading ? 'not-allowed' : 'pointer',
              display: 'inline-flex', alignItems: 'center', gap: 6, justifyContent: 'center',
            }}>
              {loading ? <><FaSpinner className="spin" /> 생성 중...</> : <><FaPlay /> 리포트 생성</>}
            </button>
          </Field>
        </div>

        {/* 선택된 타입 설명 */}
        {types.find((t) => t.key === type) && (
          <div style={{
            marginTop: 14, padding: '10px 14px',
            background: 'var(--bg-primary)', border: '1px dashed var(--border-color)',
            borderRadius: 6, fontSize: 12, color: 'var(--text-muted)',
            display: 'flex', alignItems: 'flex-start', gap: 8,
          }}>
            <FaInfoCircle style={{ color: 'var(--accent)', flexShrink: 0, marginTop: 2 }} />
            <span>{types.find((t) => t.key === type)?.description}</span>
          </div>
        )}
      </div>

      {/* 결과 */}
      {report && (
        <div style={{
          background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
          borderRadius: 12, overflow: 'hidden',
        }}>
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: '12px 16px', borderBottom: '1px solid var(--border-color)',
            background: 'var(--bg-card)',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <FaFileAlt style={{ color: '#f59e0b' }} />
              <strong style={{ fontSize: 14 }}>{report.typeLabel}</strong>
              <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                {report.from} ~ {report.to} · {report.generatedAt} · {report.generatedBy}
              </span>
            </div>
            <div style={{ display: 'flex', gap: 6 }}>
              <button onClick={copyMd} style={smallBtn}>
                {copied ? <><FaCheck style={{ color: '#10b981' }} /> 복사됨</> : <><FaCopy /> Markdown 복사</>}
              </button>
              <button onClick={downloadMd} style={smallBtn}>
                <FaDownload /> .md 다운로드
              </button>
            </div>
          </div>
          <div style={{
            padding: '20px 28px',
            maxHeight: 'calc(100vh - 360px)',
            minHeight: 360,
            overflowY: 'auto',
            background: 'var(--bg-primary)',
          }}>
            <div className="markdown-body" style={{ fontSize: 13, lineHeight: 1.75 }}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{report.markdown}</ReactMarkdown>
            </div>
          </div>
        </div>
      )}

      {!report && !loading && (
        <div style={{ textAlign: 'center', padding: 60, color: 'var(--text-muted)', fontSize: 13 }}>
          상단 폼에서 리포트 타입과 감사 기간을 선택한 뒤 *리포트 생성* 을 눌러주세요.
          <br /><br />
          <small>
            현재 활성: 전자금융감독규정 (FSS) — 나머지 3종 (개인정보보호법 / 정보통신망법 / 외부감사) 은 Stage 2 에서 추가됩니다.
          </small>
        </div>
      )}
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}
            dangerouslySetInnerHTML={{ __html: label }} />
      {children}
    </label>
  )
}

const selectStyle: React.CSSProperties = {
  padding: '8px 10px', fontSize: 13,
  borderRadius: 6, border: '1px solid var(--border-color)',
  background: 'var(--bg-card)', color: 'var(--text-primary)',
}

const smallBtn: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 5,
  padding: '5px 10px', fontSize: 11, fontWeight: 600,
  background: 'var(--bg-secondary)', color: 'var(--text-primary)',
  border: '1px solid var(--border-color)', borderRadius: 4, cursor: 'pointer',
}

function defaultFromDate(): string {
  const d = new Date()
  d.setMonth(d.getMonth() - 3)
  return d.toISOString().slice(0, 10)
}

function defaultToDate(): string {
  return new Date().toISOString().slice(0, 10)
}
