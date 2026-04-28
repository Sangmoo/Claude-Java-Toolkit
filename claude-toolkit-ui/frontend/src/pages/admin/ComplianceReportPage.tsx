import { useEffect, useState } from 'react'
import {
  FaShieldAlt, FaPlay, FaSpinner, FaDownload, FaCopy, FaCheck, FaInfoCircle, FaFileAlt,
  FaFileExcel, FaPrint, FaRobot,
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
  hasExecutiveSummary: boolean
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
  const [withExecSummary, setWithExecSummary] = useState(false)
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
      const body = new URLSearchParams({ type, from, to, executiveSummary: String(withExecSummary) })
      const res = await fetch('/api/v1/admin/compliance/generate', {
        method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
      })
      const d = await res.json()
      if (d.success && d.data) {
        setReport(d.data as GeneratedReport)
        toast.success(
          (d.data as GeneratedReport).hasExecutiveSummary
            ? '리포트 + 경영진 요약 생성 완료'
            : '리포트 생성 완료'
        )
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

  const downloadXlsx = () => {
    if (!report) return
    window.open(`/api/v1/admin/compliance/${report.id}/download?format=xlsx`, '_blank')
  }

  /**
   * Stage 3 — 인쇄용 보기 + 브라우저 네이티브 PDF 저장.
   *
   * 별도 PDF 라이브러리 없이 새 창에서 격식 있는 HTML 로 렌더링하고,
   * 자동으로 print 다이얼로그 띄움 → 사용자가 "PDF 로 저장" 선택.
   * Korean 글꼴은 브라우저 글꼴 스택을 그대로 사용해 깨짐 0.
   */
  const openPrintView = () => {
    if (!report) return
    const win = window.open('', '_blank', 'width=900,height=1200')
    if (!win) { toast.error('팝업이 차단되었습니다 — 브라우저 설정에서 허용하세요.'); return }
    // markdown 을 HTML 로 클라이언트 사이드 변환 — DOM 에 ReactMarkdown 띄우고 그 outerHTML 추출
    // 간단 처리: ReactMarkdown 결과를 직접 못 쓰니 markdown raw + 인쇄용 CSS 로 충분
    const html = buildPrintHtml(report)
    win.document.open()
    win.document.write(html)
    win.document.close()
    // 0.5s 후 자동 인쇄
    setTimeout(() => { try { win.focus(); win.print() } catch {} }, 500)
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
          ADMIN 전용 · 4종 (FSS / PIPA / 정보통신망법 / 외부감사 종합)
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

        {/* AI 경영진 요약 옵션 (Stage 3) */}
        <label style={{
          marginTop: 12, display: 'flex', alignItems: 'flex-start', gap: 8,
          padding: '10px 14px',
          background: withExecSummary ? 'var(--accent-subtle)' : 'transparent',
          border: '1px dashed var(--accent)',
          borderRadius: 6, fontSize: 12, cursor: 'pointer',
          transition: 'background 0.12s',
        }}>
          <input type="checkbox" checked={withExecSummary} onChange={(e) => setWithExecSummary(e.target.checked)}
                 style={{ marginTop: 3 }} disabled={loading} />
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: 5 }}>
              <FaRobot style={{ color: 'var(--accent)' }} /> AI 경영진 요약 추가 (선택)
            </div>
            <div style={{ marginTop: 3, color: 'var(--text-muted)' }}>
              Claude API 가 점검 데이터를 보고 3-5문장 한국어 요약을 생성해 리포트 상단에 삽입합니다.
              <strong style={{ color: 'var(--accent)', marginLeft: 4 }}>토큰 비용 발생</strong>.
            </div>
          </div>
        </label>
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
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {report.hasExecutiveSummary && (
                <span style={{
                  display: 'inline-flex', alignItems: 'center', gap: 4,
                  fontSize: 10, padding: '3px 8px', borderRadius: 10,
                  background: 'var(--accent-subtle)', color: 'var(--accent)', fontWeight: 600,
                }}>
                  <FaRobot size={9} /> AI 요약 포함
                </span>
              )}
              <button onClick={copyMd} style={smallBtn}>
                {copied ? <><FaCheck style={{ color: '#10b981' }} /> 복사됨</> : <><FaCopy /> Markdown 복사</>}
              </button>
              <button onClick={downloadMd} style={smallBtn}>
                <FaDownload /> .md
              </button>
              <button onClick={downloadXlsx} style={smallBtn} title="Apache POI 기반 4시트 워크북 (요약/보안/인증/활동)">
                <FaFileExcel style={{ color: '#10b981' }} /> .xlsx
              </button>
              <button onClick={openPrintView} style={smallBtn} title="새 창에서 인쇄용 보기 → 브라우저로 PDF 저장">
                <FaPrint /> 인쇄 / PDF
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
            4종 활성: 전자금융감독규정 / 개인정보보호법 / 정보통신망법 / 외부감사 종합.
            <br />PDF · Excel 다운로드는 Stage 3 에서 추가됩니다.
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

// ── Print HTML 빌더 (Stage 3) ────────────────────────────────────────────
//
// 별도 라이브러리 없이 markdown 을 인쇄용 HTML 로 변환. 지원 토큰:
//  - 헤더 #/##/###/####
//  - 표 | a | b |
//  - 글머리 - / 체크리스트 - [ ]
//  - **bold**, *italic*, `code`, > quote, ---
function buildPrintHtml(report: GeneratedReport): string {
  const safeTitle = escapeHtml(`${report.typeLabel} (${report.from} ~ ${report.to})`)
  const body = mdToHtml(report.markdown)
  return `<!doctype html><html lang="ko"><head><meta charset="UTF-8">
<title>${safeTitle}</title>
<style>
  @page { size: A4; margin: 22mm 18mm; }
  * { box-sizing: border-box; }
  body { font-family: 'Noto Sans KR','Apple SD Gothic Neo','Malgun Gothic','맑은 고딕',sans-serif;
         font-size: 11.5pt; line-height: 1.65; color: #111; margin: 0; padding: 0 8mm; }
  h1 { font-size: 22pt; margin: 0 0 12pt; padding-bottom: 8pt; border-bottom: 2pt solid #f59e0b; }
  h2 { font-size: 15pt; margin: 22pt 0 8pt; padding-bottom: 4pt; border-bottom: 0.6pt solid #d1d5db; }
  h3 { font-size: 12.5pt; margin: 14pt 0 6pt; color: #334155; }
  p, li { margin: 0 0 6pt; }
  ul, ol { padding-left: 22pt; }
  table { width: 100%; border-collapse: collapse; margin: 6pt 0 12pt; font-size: 10.5pt; page-break-inside: avoid; }
  th, td { border: 0.5pt solid #cbd5e1; padding: 5pt 8pt; text-align: left; vertical-align: top; }
  th { background: #f1f5f9; font-weight: 700; }
  code { background: #f1f5f9; padding: 1pt 4pt; border-radius: 2pt; font-family: 'Consolas','Monaco',monospace; font-size: 10pt; }
  blockquote { border-left: 3pt solid #cbd5e1; margin: 6pt 0; padding: 4pt 10pt; color: #475569; background: #f8fafc; }
  hr { border: none; border-top: 0.5pt solid #cbd5e1; margin: 14pt 0; }
  strong { font-weight: 700; color: #0f172a; }
  .meta-bar { font-size: 9pt; color: #64748b; margin-bottom: 14pt; }
  .footer-note { margin-top: 24pt; padding-top: 8pt; border-top: 0.5pt dashed #cbd5e1;
                 font-size: 9pt; color: #64748b; }
  /* 인쇄 시 컨트롤 안 보이게 */
  @media print { .no-print { display: none !important; } }
  .no-print { position: fixed; top: 8pt; right: 12pt; padding: 5pt 12pt;
              background: #f59e0b; color: #fff; border: none; border-radius: 4pt;
              font-size: 9pt; font-weight: 700; cursor: pointer; }
</style></head><body>
<button class="no-print" onclick="window.print()">📄 PDF 로 저장 / 인쇄</button>
<div class="meta-bar">
  Claude Java Toolkit · 컴플라이언스 리포트 · ${escapeHtml(report.generatedAt)} · ${escapeHtml(report.generatedBy)}
</div>
${body}
<div class="footer-note">
  본 리포트는 자동 집계 결과입니다. 법적 컴플라이언스 증빙으로 사용하기 전 법무·컴플라이언스 팀 검토를 받으시기 바랍니다.
</div>
</body></html>`
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

/** 최소 markdown → HTML 변환기 (Stage 3 인쇄용) */
function mdToHtml(md: string): string {
  const out: string[] = []
  const lines = md.split('\n')
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    // 표 — 헤더 다음 |---|---| 라인이 오는 경우
    if (line.includes('|') && i + 1 < lines.length && /^\s*\|?\s*-{2,}/.test(lines[i + 1])) {
      const tableLines: string[] = [line, lines[i + 1]]
      let j = i + 2
      while (j < lines.length && lines[j].includes('|') && lines[j].trim() !== '') {
        tableLines.push(lines[j]); j++
      }
      out.push(renderTable(tableLines))
      i = j
      continue
    }
    // 헤더
    const h = /^(#{1,6})\s+(.+)$/.exec(line)
    if (h) {
      out.push(`<h${h[1].length}>${inlineMd(h[2])}</h${h[1].length}>`)
      i++; continue
    }
    // 수평선
    if (/^---+\s*$/.test(line)) { out.push('<hr>'); i++; continue }
    // 인용
    if (/^>\s+/.test(line)) {
      const block: string[] = []
      while (i < lines.length && /^>\s*/.test(lines[i])) {
        block.push(lines[i].replace(/^>\s?/, ''))
        i++
      }
      out.push(`<blockquote>${inlineMd(block.join(' '))}</blockquote>`)
      continue
    }
    // 글머리 / 체크박스 리스트
    if (/^\s*-\s+/.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^\s*-\s+/.test(lines[i])) {
        const raw = lines[i].replace(/^\s*-\s+/, '')
        const cb = /^\[([ xX])\]\s+(.*)$/.exec(raw)
        if (cb) {
          const checked = cb[1].toLowerCase() === 'x' ? 'checked' : ''
          items.push(`<li><input type="checkbox" disabled ${checked}> ${inlineMd(cb[2])}</li>`)
        } else {
          items.push(`<li>${inlineMd(raw)}</li>`)
        }
        i++
      }
      out.push(`<ul>${items.join('')}</ul>`)
      continue
    }
    // 빈 줄
    if (line.trim() === '') { out.push(''); i++; continue }
    // 일반 단락
    const para: string[] = [line]
    i++
    while (i < lines.length && lines[i].trim() !== ''
            && !/^#{1,6}\s/.test(lines[i])
            && !lines[i].includes('|')
            && !/^---+\s*$/.test(lines[i])
            && !/^>\s+/.test(lines[i])
            && !/^\s*-\s+/.test(lines[i])) {
      para.push(lines[i]); i++
    }
    out.push(`<p>${inlineMd(para.join(' '))}</p>`)
  }
  return out.join('\n')
}

function renderTable(lines: string[]): string {
  if (lines.length < 2) return ''
  const split = (l: string) => l.replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map((c) => c.trim())
  const headers = split(lines[0])
  const rows = lines.slice(2).map(split)
  const th = headers.map((h) => `<th>${inlineMd(h)}</th>`).join('')
  const tr = rows.map((r) => `<tr>${r.map((c) => `<td>${inlineMd(c)}</td>`).join('')}</tr>`).join('')
  return `<table><thead><tr>${th}</tr></thead><tbody>${tr}</tbody></table>`
}

function inlineMd(s: string): string {
  // 순서: 코드 → 굵은 → 이탤릭 → escape (간단 휴리스틱)
  let out = escapeHtml(s)
  // 백슬래시 escape `|` 복원 — 템플릿이 표 셀 내 |를 \| 로 escape 했음.
  // 정규식 literal 의 backslash 처리는 까다로워서 split/join 으로 안전 처리.
  out = out.split('\\|').join('|')
  out = out.replace(/`([^`]+)`/g, '<code>$1</code>')
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  out = out.replace(/(?<!\*)\*([^*]+)\*(?!\*)/g, '<em>$1</em>')
  return out
}
