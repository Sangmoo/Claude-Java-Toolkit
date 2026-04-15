import { useState, useRef, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaCodeBranch, FaPlay, FaCopy, FaCheck, FaDownload, FaSpinner, FaEraser,
  FaFilePdf, FaEnvelope, FaSearch, FaWrench, FaClipboardCheck, FaShieldAlt,
  FaLayerGroup,
} from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import SourceSelector from '../../components/common/SourceSelector'
import { copyToClipboard, printAsHtml, markdownToHtml } from '../../utils/clipboard'
import EmailModal from '../../components/common/EmailModal'

const TEMPLATE_HINTS = [
  { value: '', label: '균형 (기본)' },
  { value: 'performance', label: '성능 최적화' },
  { value: 'security', label: '보안 취약점' },
  { value: 'refactoring', label: '리팩터링' },
  { value: 'sql_performance', label: 'SQL 성능' },
  { value: 'readability', label: '가독성' },
]

type StageKey = 'analyst' | 'builder' | 'reviewer' | 'verifier' | 'all'

interface StageDef {
  key: StageKey
  num: number | null   // 'all' 은 null
  title: string
  icon: React.ReactNode
  color: string
  desc: string
}

const STAGES: StageDef[] = [
  { key: 'analyst',  num: 1, title: '분석 요약', icon: <FaSearch />,         color: '#3b82f6', desc: 'Analyst — 문제점 분석' },
  { key: 'builder',  num: 2, title: '개선 코드', icon: <FaWrench />,         color: '#10b981', desc: 'Builder — 개선 코드 작성' },
  { key: 'reviewer', num: 3, title: '검토',     icon: <FaClipboardCheck />, color: '#f59e0b', desc: 'Reviewer — 변경 내역·기대 효과·품질 점수' },
  { key: 'verifier', num: 4, title: '검증',     icon: <FaShieldAlt />,      color: '#8b5cf6', desc: 'Verifier — 컴파일/문법/위험/의존성 검증' },
  { key: 'all',      num: null, title: '전체 결과', icon: <FaLayerGroup />,  color: '#ef4444', desc: '4단계 결과 통합' },
]

type StageBuffers = Record<Exclude<StageKey, 'all'>, string>

const EMPTY_BUFFERS: StageBuffers = {
  analyst: '', builder: '', reviewer: '', verifier: '',
}

export default function CodeReviewPage() {
  const [code, setCode] = useState('')
  const [language, setLanguage] = useState('java')
  const [templateHint, setTemplateHint] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [activeTab, setActiveTab] = useState<StageKey>('analyst')
  const [activeStreamingStage, setActiveStreamingStage] = useState<number>(0)
  const [stages, setStages] = useState<StageBuffers>(EMPTY_BUFFERS)
  const [copiedKey, setCopiedKey] = useState<string | null>(null)
  const [emailOpen, setEmailOpen] = useState<{ stage: StageKey } | null>(null)

  // 스트리밍 청크 파싱용 ref (state 갱신 race 방지)
  const stageRef    = useRef<number>(1)            // 현재 활성 stage (1~4)
  const buffersRef  = useRef<StageBuffers>({ ...EMPTY_BUFFERS })
  const accumRef    = useRef<string>('')           // 미파싱 청크 누적 (마커가 chunk 경계에 걸칠 수 있음)
  const esRef       = useRef<EventSource | null>(null)
  const toast       = useToast()

  // streaming 도중 활성 단계가 바뀌면 자동으로 그 탭으로 전환
  useEffect(() => {
    if (!streaming) return
    if (activeStreamingStage >= 1 && activeStreamingStage <= 4) {
      const stageKey = (['analyst', 'builder', 'reviewer', 'verifier'] as const)[activeStreamingStage - 1]
      setActiveTab(stageKey)
    }
  }, [activeStreamingStage, streaming])

  const handleSourceSelect = (content: string, lang: 'java' | 'sql') => {
    setCode(content)
    setLanguage(lang)
  }

  const stageKeyByNum = (num: number): keyof StageBuffers => {
    return (['analyst', 'builder', 'reviewer', 'verifier'] as const)[num - 1]
  }

  /**
   * 청크에서 stage 마커를 분리하여 적절한 버퍼에 저장.
   * 마커는 [[HARNESS_STAGE:N]] 형식이고 chunk 경계에 걸칠 수 있어 accumRef 로 누적.
   */
  const processChunk = (chunk: string) => {
    accumRef.current += chunk
    let buf = accumRef.current

    while (true) {
      const markerIdx = buf.indexOf('[[HARNESS_STAGE:')
      if (markerIdx === -1) {
        // 마커 없음 — 일부가 잘려있을 수 있으니 마지막 24자 정도는 남겨둠
        const safeLen = Math.max(0, buf.length - 24)
        if (safeLen > 0) {
          const text = buf.substring(0, safeLen)
          appendToCurrentStage(text)
          buf = buf.substring(safeLen)
        }
        break
      }

      // 마커 앞쪽 텍스트는 현재 stage 에 추가
      if (markerIdx > 0) {
        appendToCurrentStage(buf.substring(0, markerIdx))
      }

      // 마커 끝 ']]' 찾기
      const endIdx = buf.indexOf(']]', markerIdx)
      if (endIdx === -1) {
        // 마커가 잘림 — 다음 청크 기다림
        buf = buf.substring(markerIdx)
        break
      }

      const marker = buf.substring(markerIdx, endIdx + 2) // [[HARNESS_STAGE:N]]
      const stageNum = parseInt(marker.replace(/[^\d]/g, ''), 10)
      if (stageNum >= 1 && stageNum <= 4) {
        stageRef.current = stageNum
        setActiveStreamingStage(stageNum)
      }
      // 마커 이후로 진행 (마커 다음 \n 까지 먹어버림 — UI 에 빈 줄 안 보이게)
      let after = endIdx + 2
      if (buf.charAt(after) === '\n') after += 1
      buf = buf.substring(after)
    }

    accumRef.current = buf
  }

  const appendToCurrentStage = (text: string) => {
    if (!text) return
    const key = stageKeyByNum(stageRef.current)
    buffersRef.current = {
      ...buffersRef.current,
      [key]: buffersRef.current[key] + text,
    }
    setStages({ ...buffersRef.current })
  }

  const startAnalysis = async () => {
    if (!code.trim() || streaming) return
    // 초기화
    buffersRef.current = { ...EMPTY_BUFFERS }
    accumRef.current   = ''
    stageRef.current   = 1
    setStages(EMPTY_BUFFERS)
    setActiveStreamingStage(1)
    setActiveTab('analyst')
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

      const es = new EventSource(`/stream/${data.streamId}`, { withCredentials: true })
      esRef.current = es

      es.onmessage = (e) => {
        if (e.data === '[DONE]' || e.data === 'done') {
          es.close(); esRef.current = null; setStreaming(false); return
        }
        processChunk(e.data + '\n')
      }
      es.addEventListener('done', () => {
        // 남은 미파싱 청크 flush
        if (accumRef.current.length > 0) {
          appendToCurrentStage(accumRef.current)
          accumRef.current = ''
        }
        es.close(); esRef.current = null; setStreaming(false)
      })
      es.addEventListener('error_msg', (e: MessageEvent) => {
        toast.error(e.data || '분석 오류')
        es.close(); esRef.current = null; setStreaming(false)
      })
      es.onerror = () => { es.close(); esRef.current = null; setStreaming(false) }
    } catch {
      toast.error('분석 요청 실패')
      setStreaming(false)
    }
  }

  // ── 단계별 결과 헬퍼 ──
  const stageContent = (key: StageKey): string => {
    if (key === 'all') {
      return STAGES
        .filter((s) => s.key !== 'all')
        .map((s) => `# [${s.num}] ${s.title} (${s.desc.split('—')[0].trim()})\n\n${stages[s.key as Exclude<StageKey, 'all'>] || '_(결과 없음)_'}`)
        .join('\n\n---\n\n')
    }
    return stages[key as Exclude<StageKey, 'all'>] || ''
  }

  const stageTitle = (key: StageKey): string => {
    const s = STAGES.find((x) => x.key === key)
    return s ? (s.num != null ? `[${s.num}] ${s.title}` : s.title) : key
  }

  const copyStage = async (key: StageKey) => {
    const text = stageContent(key)
    if (!text) { toast.error('복사할 결과가 없습니다.'); return }
    const ok = await copyToClipboard(text)
    if (ok) {
      setCopiedKey(key)
      setTimeout(() => setCopiedKey((c) => c === key ? null : c), 3000)
      toast.success('복사되었습니다.')
    } else {
      toast.error('복사 실패')
    }
  }

  const downloadStage = (key: StageKey) => {
    const text = stageContent(key)
    if (!text) { toast.warning('내려받을 결과가 없습니다.'); return }
    const blob = new Blob([text], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `harness_${key}_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  const printStage = (key: StageKey) => {
    const text = stageContent(key)
    if (!text) { toast.warning('인쇄할 결과가 없습니다.'); return }
    printAsHtml(`<h1>${stageTitle(key)}</h1>` + markdownToHtml(text), `코드 리뷰 하네스 - ${stageTitle(key)}`)
  }

  const clearAll = () => {
    esRef.current?.close()
    esRef.current = null
    buffersRef.current = { ...EMPTY_BUFFERS }
    accumRef.current = ''
    stageRef.current = 1
    setStages(EMPTY_BUFFERS)
    setStreaming(false)
    setActiveStreamingStage(0)
    setCode('')
  }

  const hasAnyResult = Object.values(stages).some((v) => v.length > 0)

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

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 480px), 1fr))', gap: '16px', minHeight: '70vh' }}>
        {/* ── 좌측: 입력 ── */}
        <div style={panelStyle}>
          <div style={panelHeaderStyle}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>코드 입력</span>
            <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              <SourceSelector mode="both" onSelect={handleSourceSelect} />
              <button style={smallBtn} onClick={clearAll} title="초기화">
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
            placeholder="Java/SQL 코드를 입력하거나 위 '소스 선택' 버튼으로 파일/DB 객체를 로드하세요..."
          />

          <div style={{ padding: '10px 14px', display: 'flex', justifyContent: 'flex-end' }}>
            <button onClick={startAnalysis} disabled={streaming || !code.trim()} style={{ ...analyzeBtn, opacity: streaming || !code.trim() ? 0.5 : 1 }}>
              {streaming ? <><FaSpinner className="spin" /> 분석 중 (4단계)...</> : <><FaPlay /> 4단계 분석 시작</>}
            </button>
          </div>
        </div>

        {/* ── 우측: 결과 (탭 네비) ── */}
        <div style={panelStyle}>
          {/* 탭 네비 */}
          <div style={{ display: 'flex', borderBottom: '1px solid var(--border-color)', overflowX: 'auto', flexShrink: 0 }}>
            {STAGES.map((s) => {
              const isActive = activeTab === s.key
              const buf = s.key !== 'all' ? stages[s.key as Exclude<StageKey, 'all'>] : ''
              const hasContent = s.key === 'all' ? hasAnyResult : (buf.length > 0)
              const isStreaming = streaming && s.num === activeStreamingStage
              return (
                <button
                  key={s.key}
                  onClick={() => setActiveTab(s.key)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '6px',
                    padding: '10px 14px',
                    background: isActive ? 'var(--bg-secondary)' : 'transparent',
                    border: 'none',
                    borderBottom: isActive ? `2px solid ${s.color}` : '2px solid transparent',
                    color: isActive ? s.color : (hasContent ? 'var(--text-primary)' : 'var(--text-muted)'),
                    cursor: 'pointer', fontSize: '12px', fontWeight: isActive ? 700 : 500,
                    whiteSpace: 'nowrap', flexShrink: 0,
                  }}
                  title={s.desc}>
                  {isStreaming
                    ? <FaSpinner className="spin" style={{ color: s.color }} />
                    : <span style={{ color: hasContent ? s.color : 'var(--text-muted)' }}>{s.icon}</span>}
                  {s.num != null && <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>{s.num}.</span>}
                  <span>{s.title}</span>
                  {hasContent && s.key !== 'all' && (
                    <span style={{ fontSize: '9px', color: 'var(--text-muted)' }}>
                      ({(stages[s.key as Exclude<StageKey, 'all'>] || '').length}자)
                    </span>
                  )}
                </button>
              )
            })}
          </div>

          {/* 활성 탭 헤더 (액션 버튼) */}
          <div style={{ padding: '8px 14px', borderBottom: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
              {STAGES.find((s) => s.key === activeTab)?.desc}
            </span>
            <div style={{ display: 'flex', gap: '4px' }}>
              <button style={miniBtn} onClick={() => copyStage(activeTab)} title="복사">
                {copiedKey === activeTab
                  ? <><FaCheck style={{ color: 'var(--green)' }} /> <span style={{ color: 'var(--green)', fontWeight: 700 }}>복사됨</span></>
                  : <><FaCopy /> 복사</>}
              </button>
              <button style={miniBtn} onClick={() => downloadStage(activeTab)} title="MD"><FaDownload /> MD</button>
              <button style={miniBtn} onClick={() => printStage(activeTab)} title="PDF"><FaFilePdf /> PDF</button>
              <button style={{ ...miniBtn, color: 'var(--accent)', borderColor: 'var(--accent)' }} onClick={() => setEmailOpen({ stage: activeTab })} title="이메일">
                <FaEnvelope /> 이메일
              </button>
            </div>
          </div>

          {/* 활성 탭 본문 */}
          <div style={{ flex: 1, overflowY: 'auto', padding: '14px' }}>
            {(() => {
              const text = stageContent(activeTab)
              if (text) {
                return (
                  <div className="markdown-body">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{text}</ReactMarkdown>
                    {streaming && activeTab !== 'all' && STAGES.find((s) => s.key === activeTab)?.num === activeStreamingStage && (
                      <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', color: 'var(--accent)', fontSize: '12px', marginTop: '8px' }}>
                        <FaSpinner className="spin" /> 스트리밍 중...
                      </div>
                    )}
                  </div>
                )
              }
              if (streaming && activeTab !== 'all') {
                const stage = STAGES.find((s) => s.key === activeTab)
                if (stage && stage.num != null && stage.num > activeStreamingStage) {
                  return <EmptyState icon="⏳" msg={`이전 단계가 완료되면 ${stage.title} 단계가 시작됩니다`} />
                }
                return <EmptyState icon="🔄" msg="AI 응답 대기 중..." />
              }
              if (activeTab === 'all') {
                return <EmptyState icon="📊" msg="분석을 시작하면 4단계 결과가 통합되어 표시됩니다" />
              }
              return <EmptyState icon="📝" msg="분석 결과가 여기에 표시됩니다" />
            })()}
          </div>
        </div>
      </div>

      {/* 이메일 발송 모달 */}
      <EmailModal
        open={emailOpen !== null}
        onClose={() => setEmailOpen(null)}
        defaultSubject={
          emailOpen
            ? `[Claude Toolkit] 코드 리뷰 하네스 - ${stageTitle(emailOpen.stage)}`
            : ''
        }
        content={emailOpen ? stageContent(emailOpen.stage) : ''}
        contentLabel={emailOpen ? stageTitle(emailOpen.stage) : '결과'}
      />
    </>
  )
}

function EmptyState({ icon, msg }: { icon: string; msg: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)', fontSize: '13px', flexDirection: 'column', gap: '8px', minHeight: '300px' }}>
      <div style={{ fontSize: '32px', opacity: 0.4 }}>{icon}</div>
      <p>{msg}</p>
    </div>
  )
}

const panelStyle: React.CSSProperties = { background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', display: 'flex', flexDirection: 'column', overflow: 'hidden' }
const panelHeaderStyle: React.CSSProperties = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: '1px solid var(--border-color)' }
const smallBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
const miniBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '11px' }
const analyzeBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 20px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '13px', fontWeight: 600 }
