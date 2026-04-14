import { useState, useRef, useEffect, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaLayerGroup, FaPlay, FaSpinner, FaCopy, FaCheck, FaDownload, FaEraser,
  FaTimes, FaCheckCircle, FaTimesCircle,
} from 'react-icons/fa'
import { useToast } from '../hooks/useToast'
import SourceSelector from '../components/common/SourceSelector'

/** 다중 선택 가능한 분석 기능 목록 */
const FEATURES = [
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
]

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
    const promises = Array.from(selected).map((featureKey) => new Promise<'completed' | 'failed'>(async (resolve) => {
      let acc = ''
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

  const copyResult = (key: string) => {
    navigator.clipboard.writeText(tasks[key]?.result || '')
    setCopiedKey(key)
    setTimeout(() => setCopiedKey(null), 2000)
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
              mode={language === 'sql' ? 'sql' : 'both'}
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
            <button onClick={exportAll} style={smallBtn}><FaDownload /> 전체 내보내기</button>
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
                    <div style={{ display: 'flex', gap: '4px' }}>
                      <button onClick={() => copyResult(task.feature)} style={miniBtn}>
                        {copiedKey === task.feature ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
                      </button>
                      <button onClick={() => exportResult(task.feature)} style={miniBtn}><FaDownload /></button>
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
const miniBtn: React.CSSProperties = { background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '2px', fontSize: '11px' }
