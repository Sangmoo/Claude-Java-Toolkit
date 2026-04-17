import { useState, useRef, useEffect, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaLayerGroup, FaPlay, FaSpinner, FaCopy, FaCheck, FaDownload, FaEraser,
  FaTimes, FaCheckCircle, FaTimesCircle, FaFilePdf, FaEnvelope,
} from 'react-icons/fa'
import { copyToClipboard, printAsHtml, markdownToHtml } from '../utils/clipboard'
import EmailModal from '../components/common/EmailModal'
import { useToast } from '../hooks/useToast'
import SourceSelector from '../components/common/SourceSelector'

/**
 * 다중 선택 가능한 분석 기능 목록.
 *
 * v4.3.0: nonStreaming=true 인 기능은 SSE 스트림 대신 별도 REST 엔드포인트를
 * 호출하고 결과를 마크다운으로 포맷하여 같은 result 영역에 표시한다.
 */
interface FeatureDef {
  key: string
  label: string
  icon: string
  color: string
  lang: 'java' | 'sql'
  nonStreaming?: true
}
const FEATURES: FeatureDef[] = [
  { key: 'code_review', label: '코드 리뷰', icon: '🔍', color: '#3b82f6', lang: 'java' },
  { key: 'code_review_security', label: '보안 감사', icon: '🛡️', color: '#ef4444', lang: 'java' },
  { key: 'refactor_gen', label: '리팩터링', icon: '🔧', color: '#8b5cf6', lang: 'java' },
  { key: 'test_gen', label: '테스트 생성', icon: '🧪', color: '#10b981', lang: 'java' },
  { key: 'javadoc_gen', label: 'Javadoc', icon: '📝', color: '#06b6d4', lang: 'java' },
  { key: 'doc_gen', label: '기술 문서', icon: '📄', color: '#10b981', lang: 'java' },
  { key: 'api_spec', label: 'API 명세', icon: '📋', color: '#10b981', lang: 'java' },
  { key: 'harness_review', label: '4단계 하네스', icon: '⚡', color: '#f97316', lang: 'java' },
  { key: 'sql_review', label: 'SQL 리뷰', icon: '🗄️', color: '#3b82f6', lang: 'sql' },
  { key: 'sql_security', label: 'SQL 보안', icon: '🔐', color: '#ef4444', lang: 'sql' },
  { key: 'explain_plan', label: '실행계획', icon: '📊', color: '#f59e0b', lang: 'sql' },
  { key: 'index_opt', label: '인덱스 최적화', icon: '⚡', color: '#f59e0b', lang: 'sql' },
  // v4.3.0: AI 호출 없이 정적 분석 + DB 메타데이터로 즉시 결과 반환
  { key: 'index_advisor', label: '인덱스 시뮬레이션', icon: '📊', color: '#10b981', lang: 'sql', nonStreaming: true },
]

/** v4.3.0: index_advisor REST 응답을 마크다운 포맷으로 변환 */
function formatIndexAdvisorResult(data: any): string {
  if (!data) return '(결과 없음)'
  const lines: string[] = []
  lines.push(`### 분석 요약`)
  lines.push(`- 대상 테이블: **${data.tables?.length || 0}** | 조건 컬럼: **${data.predicateColumns?.length || 0}**`)
  lines.push(`- 활용 가능 인덱스: **${data.summaryExistingIndexCount || 0}** | 신규 추천: **${data.summaryNewRecommendCount || 0}**`)
  if (data.dbProduct) lines.push(`- 감지된 DB: \`${data.dbProduct}\` (${data.detectedDbType})`)
  if (data.dbConnectionError) lines.push(`- ⚠️ DB 메타조회 실패: ${data.dbConnectionError}`)
  lines.push('')

  for (const tr of (data.tableReports || [])) {
    lines.push(`### 📋 ${tr.table}`)
    if (tr.existingIndexes?.length) {
      lines.push(`**기존 인덱스**`)
      for (const idx of tr.existingIndexes) {
        const flag = idx.usableForQuery ? '✅' : 'ℹ️'
        lines.push(`- ${flag} \`${idx.name}\` (${(idx.columns || []).join(', ')}) — ${idx.recommendation}`)
      }
    }
    if (tr.recommendations?.length) {
      lines.push(`**신규 추천**`)
      for (const rec of tr.recommendations) {
        lines.push(`- **${rec.indexName}** (${rec.priority}) — ${rec.rationale}`)
        lines.push('  ```sql')
        lines.push('  ' + rec.ddl)
        lines.push('  ```')
      }
    }
    if (!tr.existingIndexes?.length && !tr.recommendations?.length) {
      lines.push(`_분석된 인덱스 정보가 없습니다._`)
    }
    lines.push('')
  }
  return lines.join('\n')
}

interface RunningTask {
  feature: string
  label: string
  icon: string
  color: string
  result: string
  status: 'running' | 'completed' | 'failed'
  error?: string
}

export default function WorkspacePage() {
  const [input, setInput] = useState('')
  const [language, setLanguage] = useState<'java' | 'sql'>('java')
  const [selected, setSelected] = useState<Set<string>>(new Set(['code_review']))
  const [tasks, setTasks] = useState<Record<string, RunningTask>>({})
  const [running, setRunning] = useState(false)
  const [copiedKey, setCopiedKey] = useState<string | null>(null)
  const [emailOpen, setEmailOpen] = useState<{ key: string | 'all' } | null>(null)
  const sourcesRef = useRef<Record<string, EventSource>>({})
  const toast = useToast()

  // 언어에 따라 필터링된 기능 목록
  const availableFeatures = FEATURES.filter((f) => f.lang === language)

  // 언어 변경 시 선택 초기화 (다른 언어 기능이 섞이지 않도록)
  useEffect(() => {
    setSelected((prev) => {
      const next = new Set<string>()
      prev.forEach((k) => {
        if (availableFeatures.find((f) => f.key === k)) next.add(k)
      })
      return next
    })
  }, [language])

  const toggleFeature = (key: string) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  const selectAll = () => setSelected(new Set(availableFeatures.map((f) => f.key)))
  const clearAll = () => setSelected(new Set())

  const closeAll = useCallback(() => {
    Object.values(sourcesRef.current).forEach((es) => es.close())
    sourcesRef.current = {}
  }, [])

  useEffect(() => closeAll, [closeAll])

  const startAnalysis = async () => {
    if (!input.trim()) { toast.error('코드를 입력해주세요.'); return }
    if (selected.size === 0) { toast.error('최소 1개 이상의 기능을 선택해주세요.'); return }

    closeAll()
    setRunning(true)

    // 각 선택된 기능에 대한 초기 태스크 생성
    const initialTasks: Record<string, RunningTask> = {}
    selected.forEach((key) => {
      const feature = FEATURES.find((f) => f.key === key)!
      initialTasks[key] = {
        feature: key,
        label: feature.label,
        icon: feature.icon,
        color: feature.color,
        result: '',
        status: 'running',
      }
    })
    setTasks(initialTasks)

    // 병렬 실행: 각 기능마다 별도의 /stream/init → /stream/{id}
    // v4.3.0: nonStreaming 기능은 별도 REST 호출 + 결과 마크다운 포맷
    const promises = Array.from(selected).map((featureKey) => new Promise<'completed' | 'failed'>(async (resolve) => {
      let acc = ''
      const featureDef = FEATURES.find((f) => f.key === featureKey)

      // v4.3.0: 비스트리밍 기능 (REST + 즉시 결과)
      if (featureDef?.nonStreaming && featureKey === 'index_advisor') {
        try {
          const res = await fetch('/api/v1/sql/index-advisor', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ sql: input.trim(), dbProfile: 'current' }),
          })
          const j = await res.json().catch(() => null)
          if (!res.ok || !j?.success) {
            const msg = j?.error || `HTTP ${res.status}`
            setTasks((prev) => ({
              ...prev,
              [featureKey]: { ...prev[featureKey], status: 'failed', error: msg, result: '' },
            }))
            resolve('failed')
            return
          }
          const md = formatIndexAdvisorResult(j.data)
          setTasks((prev) => ({
            ...prev,
            [featureKey]: { ...prev[featureKey], status: 'completed', result: md },
          }))
          resolve('completed')
        } catch (err) {
          setTasks((prev) => ({
            ...prev,
            [featureKey]: { ...prev[featureKey], status: 'failed', error: err instanceof Error ? err.message : '오류', result: '' },
          }))
          resolve('failed')
        }
        return
      }

      try {
        const res = await fetch('/stream/init', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({
            feature: featureKey,
            input: input.trim(),
            sourceType: language === 'java' ? 'java' : 'sql',
          }),
          credentials: 'include',
        })
        if (!res.ok) {
          const errText = await res.text().catch(() => '')
          throw new Error(`HTTP ${res.status}${errText ? ': ' + errText.slice(0, 80) : ''}`)
        }
        // 백엔드는 plain text UUID 반환 (JSON 아님)
        const sid = (await res.text()).trim()
        if (!sid) throw new Error('스트림 ID 없음')

        let finished = false
        const es = new EventSource(`/stream/${sid}`, { withCredentials: true })
        sourcesRef.current[featureKey] = es

        const finish = (status: 'completed' | 'failed', error?: string) => {
          if (finished) return
          finished = true
          es.close()
          delete sourcesRef.current[featureKey]
          setTasks((prev) => ({ ...prev, [featureKey]: { ...prev[featureKey], status, error, result: acc } }))
          resolve(status)
        }

        es.onmessage = (e) => {
          if (e.data === '[DONE]' || e.data === 'done') { finish('completed'); return }
          acc += e.data + '\n'
          setTasks((prev) => ({ ...prev, [featureKey]: { ...prev[featureKey], result: acc } }))
        }
        es.addEventListener('done', () => finish('completed'))
        es.addEventListener('error_msg', (ev: MessageEvent) => finish('failed', ev.data || '분석 중 오류'))
        es.onerror = () => {
          if (finished) return
          // 결과가 있으면 성공, 없으면 실패
          if (acc.trim().length > 0) finish('completed')
          else finish('failed', 'SSE 연결 오류')
        }
      } catch (err) {
        setTasks((prev) => ({
          ...prev,
          [featureKey]: { ...prev[featureKey], status: 'failed', error: err instanceof Error ? err.message : '오류', result: acc },
        }))
        resolve('failed')
      }
    }))

    const results = await Promise.all(promises)
    setRunning(false)

    // ⭐ 실제 결과 상태 기반으로 토스트 메시지 결정
    const successCount = results.filter((r) => r === 'completed').length
    const failCount = results.filter((r) => r === 'failed').length
    if (failCount === 0 && successCount > 0) {
      toast.success(`모든 분석이 성공했습니다. (${successCount}개)`)
    } else if (successCount === 0) {
      toast.error(`모든 분석이 실패했습니다. (${failCount}개)`)
    } else {
      toast.warning(`분석 완료 — 성공 ${successCount}개, 실패 ${failCount}개`)
    }
  }

  const copyResult = async (key: string) => {
    const text = tasks[key]?.result || ''
    if (!text) { toast.error('복사할 결과가 없습니다.'); return }
    const ok = await copyToClipboard(text)
    if (ok) {
      setCopiedKey(key)
      setTimeout(() => setCopiedKey((cur) => cur === key ? null : cur), 3000)
      toast.success(`${tasks[key]?.label || '결과'} 가 클립보드에 복사되었습니다.`)
    } else {
      toast.error('복사 실패 — 브라우저 권한을 확인해주세요.')
    }
  }

  const exportResult = (key: string) => {
    const task = tasks[key]
    if (!task) return
    const blob = new Blob([task.result], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `workspace_${key}_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  const exportAll = () => {
    const md = Object.values(tasks).map((t) =>
      `# ${t.icon} ${t.label}\n\n${t.result}\n\n---\n`
    ).join('\n')
    const blob = new Blob([md], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `workspace_all_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  /** 단일 결과를 PDF 인쇄 다이얼로그로 (사용자가 'PDF 로 저장' 선택) */
  const printResult = (key: string) => {
    const task = tasks[key]
    if (!task || !task.result) return
    const html = `<h1>${task.icon} ${task.label}</h1>` + markdownToHtml(task.result)
    printAsHtml(html, `${task.label} - 분석 결과`)
  }

  /** 전체 결과를 한 문서로 PDF 인쇄 */
  const printAll = () => {
    const html = Object.values(tasks)
      .filter((t) => t.result)
      .map((t) => `<h1>${t.icon} ${t.label}</h1>` + markdownToHtml(t.result) + '<hr/>')
      .join('\n')
    if (!html) { toast.warning('인쇄할 결과가 없습니다.'); return }
    printAsHtml(html, `통합 워크스페이스 분석 결과`)
  }

  return (
    <>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <FaLayerGroup style={{ fontSize: '22px', color: '#f97316' }} />
        <div>
          <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0 }}>통합 워크스페이스</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>
            원하는 분석을 다중 선택하여 <strong>병렬 실행</strong>하고 결과를 한 화면에서 확인
          </p>
        </div>
      </div>

      {/* 입력 영역 */}
      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '16px', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px', flexWrap: 'wrap', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>언어:</span>
            <div style={{ display: 'flex', gap: '4px' }}>
              {(['java', 'sql'] as const).map((lang) => (
                <button key={lang} onClick={() => setLanguage(lang)} style={chipBtn(language === lang)}>
                  {lang === 'java' ? 'Java' : 'SQL/Oracle'}
                </button>
              ))}
            </div>
          </div>
          <div style={{ display: 'flex', gap: '6px' }}>
            <SourceSelector
              mode="both"
              onSelect={(code, lang) => { setInput(code); if (lang === 'sql') setLanguage('sql'); else setLanguage('java') }}
            />
            <button onClick={() => setInput('')} style={smallBtn}><FaEraser /> 초기화</button>
          </div>
        </div>
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={language === 'java' ? 'Java 코드를 입력하세요...' : 'SQL을 입력하세요...'}
          style={{
            width: '100%', minHeight: '180px', resize: 'vertical',
            fontFamily: "'Consolas','Monaco',monospace", fontSize: '13px', lineHeight: '1.6',
          }}
        />
      </div>

      {/* 기능 다중 선택 */}
      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '16px', marginBottom: '16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
          <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
            분석 기능 선택 ({selected.size}개)
          </span>
          <div style={{ display: 'flex', gap: '4px' }}>
            <button onClick={selectAll} style={smallBtn}>전체 선택</button>
            <button onClick={clearAll} style={smallBtn}>전체 해제</button>
          </div>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
          {availableFeatures.map((f) => {
            const active = selected.has(f.key)
            return (
              <button key={f.key} onClick={() => toggleFeature(f.key)} style={{
                display: 'flex', alignItems: 'center', gap: '6px',
                padding: '8px 14px', borderRadius: '20px', fontSize: '13px', cursor: 'pointer',
                border: `2px solid ${active ? f.color : 'var(--border-color)'}`,
                background: active ? `${f.color}1a` : 'transparent',
                color: active ? f.color : 'var(--text-sub)',
                fontWeight: active ? 600 : 400,
                transition: 'all 0.15s',
              }}>
                <span style={{ fontSize: '16px' }}>{f.icon}</span>
                {f.label}
                {active && <FaCheck style={{ fontSize: '10px' }} />}
              </button>
            )
          })}
        </div>
      </div>

      {/* 실행 버튼 */}
      <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '20px' }}>
        <button onClick={startAnalysis} disabled={running || selected.size === 0 || !input.trim()}
          style={{
            display: 'flex', alignItems: 'center', gap: '8px',
            padding: '12px 32px', borderRadius: '12px',
            background: 'var(--accent)', color: '#fff', border: 'none',
            cursor: 'pointer', fontSize: '15px', fontWeight: 700,
            opacity: running || selected.size === 0 || !input.trim() ? 0.5 : 1,
          }}>
          {running ? <><FaSpinner className="spin" /> 병렬 분석 중 ({Object.values(tasks).filter((t) => t.status === 'running').length}개)...</> : <><FaPlay /> {selected.size}개 기능 병렬 실행</>}
        </button>
      </div>

      {/* 결과 영역 */}
      {Object.keys(tasks).length > 0 && (
        <>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
            <h3 style={{ fontSize: '14px', fontWeight: 700 }}>
              분석 결과 ({Object.values(tasks).filter((t) => t.status === 'completed').length}/{Object.keys(tasks).length})
            </h3>
            <div style={{ display: 'flex', gap: '6px' }}>
              <button onClick={exportAll} style={smallBtn} title="전체 결과를 .md 파일로 내려받기"><FaDownload /> 전체 MD</button>
              <button onClick={printAll} style={smallBtn} title="전체 결과를 PDF 로 인쇄/저장"><FaFilePdf /> 전체 PDF</button>
              <button onClick={() => setEmailOpen({ key: 'all' })} style={{ ...smallBtn, color: 'var(--accent)', borderColor: 'var(--accent)' }} title="전체 결과를 이메일로 발송"><FaEnvelope /> 전체 이메일</button>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 450px), 1fr))', gap: '12px' }}>
            {Object.values(tasks).map((task) => (
              <div key={task.feature} style={{
                background: 'var(--bg-secondary)',
                border: `2px solid ${task.status === 'completed' ? 'rgba(34,197,94,0.3)' : task.status === 'failed' ? 'rgba(239,68,68,0.3)' : 'var(--border-color)'}`,
                borderRadius: '12px', overflow: 'hidden', display: 'flex', flexDirection: 'column',
              }}>
                <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border-color)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ fontSize: '18px' }}>{task.icon}</span>
                  <span style={{ fontWeight: 700, fontSize: '13px', flex: 1 }}>{task.label}</span>
                  {task.status === 'running' && <FaSpinner className="spin" style={{ color: 'var(--accent)', fontSize: '12px' }} />}
                  {task.status === 'completed' && <FaCheckCircle style={{ color: 'var(--green)', fontSize: '12px' }} />}
                  {task.status === 'failed' && <FaTimesCircle style={{ color: 'var(--red)', fontSize: '12px' }} />}
                  {task.result && (
                    <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
                      <button
                        onClick={() => copyResult(task.feature)}
                        style={copiedKey === task.feature ? copiedBtn : miniBtn}
                        title="결과 복사">
                        {copiedKey === task.feature
                          ? <><FaCheck style={{ color: 'var(--green)' }} /> <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--green)' }}>복사됨</span></>
                          : <><FaCopy /> <span style={{ fontSize: '11px' }}>복사</span></>}
                      </button>
                      <button onClick={() => exportResult(task.feature)} style={miniBtn} title="MD 내려받기"><FaDownload /></button>
                      <button onClick={() => printResult(task.feature)} style={miniBtn} title="PDF 인쇄/저장"><FaFilePdf /></button>
                      <button onClick={() => setEmailOpen({ key: task.feature })} style={miniBtn} title="이메일 발송"><FaEnvelope /></button>
                    </div>
                  )}
                </div>
                <div style={{ padding: '14px', flex: 1, maxHeight: '500px', overflowY: 'auto' }}>
                  {task.status === 'failed' ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--red)', fontSize: '13px' }}>
                      <FaTimes /> {task.error || '분석 실패'}
                    </div>
                  ) : task.result ? (
                    <div className="markdown-body" style={{ fontSize: '13px' }}>
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>{task.result}</ReactMarkdown>
                    </div>
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-muted)', fontSize: '13px' }}>
                      <FaSpinner className="spin" /> 분석 대기 중...
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {/* 이메일 발송 모달 */}
      <EmailModal
        open={emailOpen !== null}
        onClose={() => setEmailOpen(null)}
        defaultSubject={
          emailOpen?.key === 'all'
            ? '[Claude Toolkit] 통합 워크스페이스 분석 결과'
            : emailOpen ? `[Claude Toolkit] ${tasks[emailOpen.key]?.label || '워크스페이스'} 결과` : ''
        }
        content={
          emailOpen?.key === 'all'
            ? Object.values(tasks).map((t) => `# ${t.icon} ${t.label}\n\n${t.result}\n\n---\n`).join('\n')
            : emailOpen ? (tasks[emailOpen.key]?.result || '') : ''
        }
        contentLabel={emailOpen?.key === 'all' ? '전체 워크스페이스 결과' : '분석 결과'}
      />
    </>
  )
}

const chipBtn = (active: boolean): React.CSSProperties => ({
  padding: '5px 14px', borderRadius: '16px', fontSize: '12px', cursor: 'pointer',
  border: `1px solid ${active ? 'var(--accent)' : 'var(--border-color)'}`,
  background: active ? 'var(--accent-subtle)' : 'transparent',
  color: active ? 'var(--accent)' : 'var(--text-sub)', fontWeight: active ? 600 : 400,
})
const smallBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 10px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
const miniBtn: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '4px',
  background: 'none', border: '1px solid var(--border-color)',
  color: 'var(--text-sub)', cursor: 'pointer',
  padding: '4px 8px', borderRadius: '6px', fontSize: '11px',
}
const copiedBtn: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '4px',
  background: 'rgba(34,197,94,0.12)', border: '1px solid rgba(34,197,94,0.4)',
  color: 'var(--green)', cursor: 'pointer',
  padding: '4px 8px', borderRadius: '6px', fontSize: '11px', fontWeight: 700,
}
