import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import ReactFlow, {
  Background, Controls, MiniMap, Handle, Position, MarkerType,
  type Node as RfNode, type Edge as RfEdge, type NodeProps,
} from 'reactflow'
import 'reactflow/dist/style.css'
import {
  FaPlay, FaSync, FaTimes, FaDownload, FaSitemap,
  FaDatabase, FaFileCode, FaFileAlt, FaHistory, FaShareAlt, FaTrash,
  FaExpand, FaCopy, FaCheck, FaComments,
} from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import { useNavigate, useSearchParams } from 'react-router-dom'
import SourceSelector from '../../components/common/SourceSelector'

/**
 * v4.4.x — 데이터 흐름 분석 페이지 (Phase 3)
 *
 * <p>좌측: 입력 폼 + 진행상태 + Claude narrative (markdown stream)
 * <p>우측: ReactFlow 다이어그램 (백엔드 trace 결과 즉시 렌더, LLM narrative 도착 전부터 사용 가능)
 * <p>노드 클릭 → 우측 슬라이드아웃 패널에서 파일/SP 메타 + 스니펫 미리보기
 *
 * <p>SSE 이벤트:
 *   connected → status(여러 번) → trace(JSON) → status → message chunks → done
 */

// ── 타입 ────────────────────────────────────────────────────────────────────

type NodeType = 'ui' | 'controller' | 'service' | 'dao' | 'mybatis' | 'sp' | 'table'
type DmlKind  = 'INSERT' | 'UPDATE' | 'MERGE' | 'DELETE' | 'SELECT'

const DML_OPTIONS: { value: DmlKind; label: string; color: string }[] = [
  { value: 'INSERT', label: 'INSERT (등록)',  color: '#10b981' },
  { value: 'UPDATE', label: 'UPDATE (수정)',  color: '#f59e0b' },
  { value: 'MERGE',  label: 'MERGE (병합)',   color: '#8b5cf6' },
  { value: 'DELETE', label: 'DELETE (삭제)',  color: '#ef4444' },
  { value: 'SELECT', label: 'SELECT (조회)',  color: '#3b82f6' },
]

interface FlowNode  { id: string; type: NodeType; label: string; file?: string; line?: number; meta?: Record<string, string> }
interface FlowEdge  { from: string; to: string; label?: string }
interface FlowStep  { no: number; actor: string; what: string; file?: string; line?: number }
interface FlowResult {
  query: string
  targetType: string
  summary?: string
  mermaid?: string
  nodes: FlowNode[]
  edges: FlowEdge[]
  steps: FlowStep[]
  warnings: string[]
  stats: Record<string, any>
}

// ── 노드 타입별 색상/아이콘/순서 ───────────────────────────────────────────

const TYPE_META: Record<NodeType, { color: string; bg: string; icon: string; label: string; order: number }> = {
  ui:         { color: '#0ea5e9', bg: '#e0f2fe', icon: '🖥️', label: 'MiPlatform 화면', order: 1 },
  controller: { color: '#8b5cf6', bg: '#ede9fe', icon: '🎯', label: 'Controller',     order: 2 },
  service:    { color: '#10b981', bg: '#d1fae5', icon: '⚙️', label: 'Service',        order: 3 },
  dao:        { color: '#f59e0b', bg: '#fef3c7', icon: '🗂️', label: 'DAO',           order: 4 },
  mybatis:    { color: '#3b82f6', bg: '#dbeafe', icon: '📜', label: 'MyBatis SQL',   order: 5 },
  sp:         { color: '#ef4444', bg: '#fee2e2', icon: '🔁', label: 'Oracle SP/Trigger', order: 6 },
  table:      { color: '#6366f1', bg: '#e0e7ff', icon: '🗄️', label: 'DB 테이블',      order: 7 },
}

// ── ReactFlow 커스텀 노드 ───────────────────────────────────────────────────

function FlowDiagramNode({ data }: NodeProps<{ raw: FlowNode; onSelect: (n: FlowNode) => void }>) {
  const { raw } = data
  const meta = TYPE_META[raw.type] ?? TYPE_META.service
  return (
    <div
      onClick={() => data.onSelect(raw)}
      style={{
        background: 'var(--bg-card, #fff)',
        border: `2px solid ${meta.color}`,
        borderLeft: `8px solid ${meta.color}`,
        borderRadius: 8,
        padding: '8px 12px',
        minWidth: 200, maxWidth: 280,
        boxShadow: '0 2px 6px rgba(0,0,0,0.08)',
        cursor: 'pointer',
        fontSize: 12,
      }}
      title="클릭 → 상세 보기"
    >
      <Handle type="target" position={Position.Left} style={{ background: meta.color }} />
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
        <span style={{ fontSize: 16 }}>{meta.icon}</span>
        <strong style={{ color: meta.color, fontSize: 11, textTransform: 'uppercase' }}>{meta.label}</strong>
      </div>
      <div style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--text-primary)', wordBreak: 'break-all' }}>
        {raw.label}
      </div>
      {raw.file && (
        <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 4, wordBreak: 'break-all' }}>
          {raw.file}{raw.line ? `:${raw.line}` : ''}
        </div>
      )}
      <Handle type="source" position={Position.Right} style={{ background: meta.color }} />
    </div>
  )
}

const NODE_TYPES = { flow: FlowDiagramNode }

// ── markdown 섹션 추출 ─────────────────────────────────────────────────────
// 이모지 surrogate pair 매칭 이슈 피하려고 "##" 헤딩 라인 중 특정 문자열 포함 여부로 판단.
// (이전 버전은 [📌] 문자 클래스가 u-flag 없이 깨져서 항상 0건이었음)
function extractMarkdownSection(md: string, keywords: string[]): string {
  if (!md) return ''
  // 모든 ## 헤딩 라인을 찾고, 해당 라인의 내용에 keyword 중 하나가 포함되면 그 섹션 반환.
  const lines = md.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (!/^##\s/.test(line)) continue
    const cleaned = line.replace(/[^가-힣A-Za-z0-9]/g, '') // 한글/영문/숫자만
    if (!keywords.some((k) => cleaned.includes(k.replace(/\s+/g, '')))) continue
    // 다음 ## 헤딩 전까지 수집
    const body: string[] = []
    for (let j = i + 1; j < lines.length; j++) {
      if (/^##\s/.test(lines[j])) break
      body.push(lines[j])
    }
    return body.join('\n').trim()
  }
  return ''
}

function extractSummarySection(md: string): string {
  return extractMarkdownSection(md, ['한줄요약'])
}
function extractWarningSection(md: string): string {
  // ## ⚠ 주의/추정 또는 ## 주의사항 등
  return extractMarkdownSection(md, ['주의추정', '주의사항', '주의'])
}

// ── 엣지 DML 색상 매핑 (DML_OPTIONS 와 동일 팔레트) ────────────────────────

const DML_EDGE_COLOR: Record<string, string> = {
  INSERT: '#10b981',
  UPDATE: '#f59e0b',
  MERGE:  '#8b5cf6',
  DELETE: '#ef4444',
  SELECT: '#3b82f6',
}
const EDGE_NEUTRAL = '#94a3b8'

function getEdgeStyle(label?: string): { color: string; dml: string | null } {
  if (!label) return { color: EDGE_NEUTRAL, dml: null }
  const up = label.toUpperCase()
  // DML 키워드 우선 매칭 (writes/calls 같은 일반 라벨은 neutral)
  for (const k of Object.keys(DML_EDGE_COLOR)) {
    if (new RegExp(`\\b${k}\\b`).test(up)) return { color: DML_EDGE_COLOR[k], dml: k }
  }
  return { color: EDGE_NEUTRAL, dml: null }
}

// ── 레이아웃: type 별 column ────────────────────────────────────────────────

function layoutNodes(nodes: FlowNode[], edges: FlowEdge[], onSelect: (n: FlowNode) => void) {
  const COL_W = 320
  const ROW_H = 130
  const grouped: Record<NodeType, FlowNode[]> = {
    ui: [], controller: [], service: [], dao: [], mybatis: [], sp: [], table: [],
  }
  for (const n of nodes) (grouped[n.type] ?? grouped.service).push(n)

  const orderedTypes = (Object.keys(grouped) as NodeType[])
    .sort((a, b) => TYPE_META[a].order - TYPE_META[b].order)
    .filter((t) => grouped[t].length > 0)

  const rfNodes: RfNode[] = []
  orderedTypes.forEach((type, colIdx) => {
    grouped[type].forEach((n, rowIdx) => {
      rfNodes.push({
        id: n.id,
        type: 'flow',
        position: { x: colIdx * COL_W, y: rowIdx * ROW_H },
        data: { raw: n, onSelect },
        sourcePosition: Position.Right,
        targetPosition: Position.Left,
      })
    })
  })

  const rfEdges: RfEdge[] = edges.map((e, i) => {
    const { color, dml } = getEdgeStyle(e.label)
    return {
      id: `e${i}-${e.from}-${e.to}`,
      source: e.from, target: e.to,
      type: 'smoothstep',
      label: e.label || '',
      animated: !!dml,
      style: { stroke: color, strokeWidth: dml ? 2.2 : 1.5 },
      markerEnd: { type: MarkerType.ArrowClosed, color, width: 18, height: 18 },
      labelShowBg: true,
      labelBgPadding: [6, 3],
      labelBgBorderRadius: 4,
      labelStyle: {
        fontSize: 11,
        fontWeight: dml ? 700 : 500,
        fill: dml ? '#ffffff' : 'var(--text-primary)',
        letterSpacing: dml ? 0.4 : 0,
      },
      labelBgStyle: {
        fill: dml ? color : 'var(--bg-card, #ffffff)',
        fillOpacity: 0.95,
        stroke: color,
        strokeWidth: dml ? 0 : 1,
      },
    }
  })

  return { rfNodes, rfEdges }
}

// ── 페이지 본체 ────────────────────────────────────────────────────────────

interface HistoryItem {
  id: number; query: string; targetType: string; dmlFilters: string;
  nodesCount: number; edgesCount: number; elapsedMs: number; createdAt: string;
}

export default function FlowAnalysisPage() {
  const toast = useToast()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  // 입력 폼
  const [query, setQuery]             = useState('')
  const [targetType, setTargetType]   = useState<'AUTO' | 'TABLE' | 'SP' | 'SQL_ID' | 'MIPLATFORM_XML'>('AUTO')
  // v4.4.x — 다중 DML 선택 (SELECT 추가, 기본은 데이터 변경 4종)
  const [dmlFilters, setDmlFilters] = useState<Set<DmlKind>>(
    () => new Set<DmlKind>(['INSERT', 'UPDATE', 'MERGE', 'DELETE'])
  )
  const [maxBranches, setMaxBranches] = useState(3)
  const [includeDb, setIncludeDb]     = useState(true)
  const [includeUi, setIncludeUi]     = useState(true)

  // 스트림 상태
  const [streaming,  setStreaming]  = useState(false)
  const [statusText, setStatusText] = useState('')
  const [trace,      setTrace]      = useState<FlowResult | null>(null)
  const [narrative,  setNarrative]  = useState('')
  const [selected,   setSelected]   = useState<FlowNode | null>(null)

  // Phase 4 — 이력 + 공유
  const [historyItems, setHistoryItems] = useState<HistoryItem[]>([])
  const [showHistory,  setShowHistory]  = useState(true)
  const [savedHistoryId, setSavedHistoryId] = useState<number | null>(null)
  const [readOnly, setReadOnly] = useState(false)  // 공유 링크로 진입한 경우 입력 비활성

  // v4.5 — AI 요약 모달 (한줄요약 + 주의사항)
  const [summaryModalOpen, setSummaryModalOpen] = useState(false)
  const [summaryCopied, setSummaryCopied] = useState(false)
  const summaryText = useMemo(() => extractSummarySection(narrative), [narrative])
  const warningText = useMemo(() => extractWarningSection(narrative), [narrative])
  // 분석이 끝나면(narrative 존재 + 스트리밍 끝) 버튼 표시 — 섹션 추출 실패해도 전체 narrative 는 보여줌
  const showAiSummaryBtn = !!narrative && !streaming

  const buildCopyText = () => {
    const parts: string[] = []
    if (summaryText) parts.push('## 📌 한 줄 요약\n' + summaryText)
    if (warningText) parts.push('## ⚠ 주의사항\n' + warningText)
    if (parts.length === 0 && narrative) return narrative  // 섹션 추출 실패 시 전체 narrative
    return parts.join('\n\n')
  }

  const copySummary = async () => {
    const text = buildCopyText()
    if (!text) return
    try {
      await navigator.clipboard.writeText(text)
      setSummaryCopied(true)
      toast.success('요약이 클립보드에 복사됐습니다.')
      setTimeout(() => setSummaryCopied(false), 1500)
    } catch {
      const ta = document.createElement('textarea')
      ta.value = text; ta.style.position = 'fixed'
      document.body.appendChild(ta); ta.select()
      try { document.execCommand('copy'); toast.success('복사됨'); setSummaryCopied(true); setTimeout(() => setSummaryCopied(false), 1500) }
      catch { toast.error('복사 실패 — 수동으로 선택하세요.') }
      document.body.removeChild(ta)
    }
  }

  const esRef = useRef<EventSource | null>(null)

  // SSE 종료 시 정리
  useEffect(() => () => { esRef.current?.close() }, [])

  // v4.5 — 요약 모달: ESC 로 닫기
  useEffect(() => {
    if (!summaryModalOpen) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setSummaryModalOpen(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [summaryModalOpen])

  // ── Phase 4: 이력 목록 로드 ────────────────────────────────────────
  const loadHistory = useCallback(async () => {
    try {
      const r = await fetch('/api/v1/flow/history?limit=20', { credentials: 'include' })
      const d = await r.json()
      if (d.success) setHistoryItems(d.data || [])
    } catch { /* silent */ }
  }, [])

  useEffect(() => { loadHistory() }, [loadHistory])

  // ── 이력 항목 클릭 → trace + narrative 즉시 복원 ───────────────────
  const loadHistoryItem = async (id: number) => {
    if (streaming) return
    try {
      const r = await fetch(`/api/v1/flow/history/${id}`, { credentials: 'include' })
      const d = await r.json()
      if (!d.success) { toast.error(d.error || '이력 로드 실패'); return }
      const result: FlowResult = JSON.parse(d.data.traceJson)
      setQuery(d.data.query || '')
      setTrace(result)
      setNarrative(d.data.narrative || '')
      setSelected(null)
      setSavedHistoryId(id)
      setReadOnly(false)
      toast.info(`이력 복원: ${d.data.createdAt}`)
    } catch (e) {
      toast.error('이력 파싱 실패')
    }
  }

  const deleteHistoryItem = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation()
    if (!confirm('이 분석 이력을 삭제하시겠어요?')) return
    try {
      const r = await fetch(`/api/v1/flow/history/${id}`, { method: 'DELETE', credentials: 'include' })
      const d = await r.json()
      if (d.success) { toast.success('삭제됨'); loadHistory() }
      else toast.error(d.error || '삭제 실패')
    } catch { toast.error('삭제 호출 실패') }
  }

  // ── 공유 링크 생성 ───────────────────────────────────────────────────
  const createShare = async () => {
    if (!savedHistoryId) {
      toast.warning('저장된 이력이 없습니다. 먼저 분석을 실행하세요.')
      return
    }
    try {
      const r = await fetch(`/api/v1/flow/history/${savedHistoryId}/share`,
                            { method: 'POST', credentials: 'include' })
      const d = await r.json()
      if (!d.success) { toast.error(d.error || '공유 실패'); return }
      const fullUrl = window.location.origin + d.data.shareUrl
      try {
        await navigator.clipboard.writeText(fullUrl)
        toast.success(`공유 링크 복사됨 (${d.data.remaining})`)
      } catch {
        // HTTP 환경 fallback — execCommand
        const ta = document.createElement('textarea')
        ta.value = fullUrl; ta.style.position = 'fixed'
        document.body.appendChild(ta); ta.select()
        try { document.execCommand('copy'); toast.success('공유 링크 복사됨') }
        catch { toast.info(fullUrl) }
        document.body.removeChild(ta)
      }
    } catch { toast.error('공유 호출 실패') }
  }

  // ── ?share=token 으로 진입 → 공유 링크 복원 (read-only 모드) ──────────
  useEffect(() => {
    const token = searchParams.get('share')
    if (!token) return
    ;(async () => {
      try {
        const r = await fetch(`/api/v1/share/${token}`, { credentials: 'include' })
        const d = await r.json()
        if (!d.success) { toast.error(d.error || '공유 링크 만료/무효'); return }
        if (d.menuName !== 'flow') { toast.error('Flow Analysis 공유 링크가 아닙니다.'); return }
        const result: FlowResult = JSON.parse(d.inputText || '{}')
        setQuery(d.title?.replace(/^Flow:\s*/, '') ?? '')
        setTrace(result)
        setNarrative(d.resultText || '')
        setReadOnly(true)
        toast.info(`공유 링크 — ${d.remaining}`)
      } catch { toast.error('공유 데이터 로드 실패') }
    })()
  }, [searchParams, toast])

  const handleSelect = useCallback((n: FlowNode) => setSelected(n), [])

  const { rfNodes, rfEdges } = useMemo(() => {
    if (!trace || trace.nodes.length === 0) return { rfNodes: [], rfEdges: [] }
    return layoutNodes(trace.nodes, trace.edges, handleSelect)
  }, [trace, handleSelect])

  // ── 분석 시작 ────────────────────────────────────────────────────────
  const startAnalysis = async () => {
    if (!query.trim()) { toast.warning('질문을 입력해주세요.'); return }
    if (dmlFilters.size === 0) { toast.warning('DML 필터에서 최소 1개를 선택하세요.'); return }
    if (streaming) return

    setStreaming(true)
    setStatusText('연결 중...')
    setTrace(null)
    setNarrative('')
    setSelected(null)
    setSavedHistoryId(null)
    setReadOnly(false)
    if (searchParams.get('share')) {
      const next = new URLSearchParams(searchParams); next.delete('share')
      setSearchParams(next, { replace: true })
    }

    try {
      // Step 1: POST /flow/stream/start (세션에 요청 적재)
      const startRes = await fetch('/flow/stream/start', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          query: query.trim(),
          targetType,
          dmlFilters: Array.from(dmlFilters),  // v4.4.x — 다중 DML 배열
          maxBranches,
          includeDb,
          includeUi,
        }),
      })
      // v4.4.x — HTTP 비-200 (CSRF 403 등) 케이스 명시적으로 잡기
      if (!startRes.ok) {
        toast.error(`분석 시작 실패 (HTTP ${startRes.status} ${startRes.statusText})`)
        setStreaming(false); setStatusText('')
        return
      }
      const startData = await startRes.json().catch(() => ({ success: false, error: 'JSON 파싱 실패' }))
      if (!startData.success) {
        toast.error(startData.error || '시작 실패')
        setStreaming(false); setStatusText('')
        return
      }

      // Step 2: SSE 채널 오픈
      let acc = ''
      const es = new EventSource('/flow/stream', { withCredentials: true })
      esRef.current = es

      es.addEventListener('status', (e: MessageEvent) => {
        if (!acc) setStatusText(e.data || '')
      })

      es.addEventListener('trace', (e: MessageEvent) => {
        try {
          const result: FlowResult = JSON.parse(e.data)
          setTrace(result)
          setStatusText(`✅ 추적 완료 (노드 ${result.nodes.length}, 엣지 ${result.edges.length}) — Claude 응답 대기 중...`)
        } catch (err) {
          console.error('trace parse error', err)
        }
      })

      // 백엔드는 markdown chunk 를 unnamed(default) 이벤트로, 종료를 named "done" 이벤트로 보냄.
      // onmessage 는 unnamed 이벤트만 받으므로 종료 처리는 addEventListener('done') 에서 한다.
      es.onmessage = (e: MessageEvent) => {
        if (!acc) setStatusText('')   // 첫 chunk 도착 → status 사라짐
        acc += e.data
        setNarrative(acc)
      }

      const refreshHistoryAfterDone = async () => {
        try {
          const r = await fetch('/api/v1/flow/history?limit=20', { credentials: 'include' })
          const d = await r.json()
          if (d.success && Array.isArray(d.data)) {
            setHistoryItems(d.data)
            if (d.data.length > 0) setSavedHistoryId(d.data[0].id)
          } else if (!d.success) {
            console.warn('[Flow] history 로드 실패:', d.error)
          }
        } catch (err) {
          console.warn('[Flow] history fetch 에러:', err)
        }
      }

      es.addEventListener('done', () => {
        es.close(); esRef.current = null
        setStreaming(false)
        setStatusText('')
        refreshHistoryAfterDone()
      })

      es.addEventListener('error_msg', (e: MessageEvent) => {
        toast.error(e.data || '스트리밍 오류')
        es.close(); esRef.current = null
        setStreaming(false)
        setStatusText('')
      })

      es.onerror = () => {
        es.close(); esRef.current = null
        setStreaming(false)
        setStatusText('')
        toast.error('스트리밍 연결이 끊어졌습니다. 분석을 다시 시도해주세요.')
      }
    } catch (e: any) {
      toast.error(`분석 시작 실패: ${e?.message || '네트워크 오류'}`)
      setStreaming(false)
      setStatusText('')
    }
  }

  const cancelAnalysis = () => {
    esRef.current?.close()
    esRef.current = null
    setStreaming(false)
    setStatusText('')
  }

  // ── 인덱스 재빌드 ────────────────────────────────────────────────────
  const reindex = async () => {
    toast.info('인덱스 재빌드 시작...')
    try {
      const r = await fetch('/api/v1/flow/reindex', { method: 'POST', credentials: 'include' })
      const d = await r.json()
      if (d.success) {
        const s = d.data
        toast.success(`인덱스 완료 — MyBatis ${s.mybatisStatements} / Spring ${s.springEndpoints} / 화면 ${s.miplatformScreens} (${s.elapsedMs}ms)`)
      } else {
        toast.error(d.error || '재빌드 실패')
      }
    } catch {
      toast.error('재빌드 호출 실패')
    }
  }

  // ── narrative 다운로드 (markdown) ────────────────────────────────────
  const downloadMd = () => {
    if (!narrative) return
    const blob = new Blob([narrative], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `flow-${query.replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 40)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  // ── 렌더 ────────────────────────────────────────────────────────────

  return (
    <div style={styles.page}>
      {/* 상단 바 */}
      <div style={styles.topBar}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <FaSitemap size={20} style={{ color: '#06b6d4' }} />
          <h2 style={{ margin: 0, fontSize: 18 }}>데이터 흐름 분석</h2>
          <span style={{ fontSize: 11, color: 'var(--text-muted)', marginLeft: 8 }}>
            테이블 / SP / SQL_ID 시작점에서 MyBatis · Java · Controller · MiPlatform 까지 자동 추적
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button style={styles.iconBtn} onClick={() => setShowHistory((v) => !v)}
                  title="최근 분석 이력 보기/숨기기">
            <FaHistory /> 이력 ({historyItems.length})
          </button>
          {savedHistoryId && !streaming && (
            <button style={styles.iconBtn} onClick={createShare} title="공유 링크 생성 (7일 유효)">
              <FaShareAlt /> 공유
            </button>
          )}
          <button style={styles.iconBtn} onClick={reindex} disabled={streaming} title="모든 인덱서 재빌드 (Settings 변경 후)">
            <FaSync /> 인덱스 재빌드
          </button>
          {narrative && !streaming && (
            <button style={styles.iconBtn} onClick={downloadMd} title="결과 다운로드">
              <FaDownload /> Markdown
            </button>
          )}
          {narrative && !streaming && (
            <button
              style={styles.iconBtn}
              onClick={() => {
                const ctx = `다음 데이터 흐름 분석 결과에 대해 질문합니다.\n\n**분석 대상**: ${query}\n\n${narrative.slice(0, 3000)}`
                navigate('/chat?context=' + encodeURIComponent(ctx))
              }}
              title="이 분석 결과를 채팅으로 이어받아 질문하기"
            >
              <FaComments /> 채팅으로 이어받기
            </button>
          )}
        </div>
      </div>

      {/* 본문 — 좌/우 분할 */}
      <div style={styles.body}>
        {/* ───── 좌측 패널 (35%) ───── */}
        <div style={styles.leftPanel}>
          {/* 공유 링크 read-only 배너 */}
          {readOnly && (
            <div style={styles.shareBanner}>
              🔗 공유 링크로 진입한 read-only 보기입니다. 새 분석을 시작하려면 질문을 입력하세요.
            </div>
          )}

          {/* Phase 4 — 이력 패널 (toggle) */}
          {showHistory && historyItems.length > 0 && (
            <div style={styles.historyCard}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-muted)' }}>
                  📜 최근 분석 ({historyItems.length})
                </span>
                <button
                  style={{ ...styles.iconBtn, padding: '2px 6px', fontSize: 10 }}
                  onClick={() => setShowHistory(false)}
                ><FaTimes /></button>
              </div>
              <div style={styles.historyList}>
                {historyItems.map((h) => (
                  <div key={h.id}
                       onClick={() => loadHistoryItem(h.id)}
                       style={{
                         ...styles.historyItem,
                         background: savedHistoryId === h.id ? 'var(--bg-secondary)' : 'transparent',
                       }}
                       title="클릭하여 복원">
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={styles.historyQuery} title={h.query}>{h.query}</div>
                      <div style={styles.historyMeta}>
                        {h.targetType} · {h.dmlFilters || 'ALL'} · 노드 {h.nodesCount} · {h.createdAt}
                      </div>
                    </div>
                    <button
                      style={styles.historyDelBtn}
                      title="삭제"
                      onClick={(e) => deleteHistoryItem(h.id, e)}
                    ><FaTrash size={9} /></button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 입력 폼 */}
          <div style={styles.formCard}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <label style={styles.label}>분석 대상 질문</label>
              <SourceSelector
                mode="sql"
                dbTypes={['PROCEDURE', 'FUNCTION', 'PACKAGE', 'TRIGGER']}
                pickName
                buttonLabel="소스선택하기"
                buttonTitle="Settings에 연결된 DB 의 SP/FUNCTION/PACKAGE/TRIGGER 선택"
                onSelect={(ident) => {
                  if (streaming) return
                  // "OWNER.NAME" 형태 — SP 시작점으로 분석할 수 있도록 query 와 targetType 을 함께 세팅
                  setQuery(ident)
                  setTargetType('SP')
                }}
              />
            </div>
            <textarea
              style={styles.textarea}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder='예: "T_SHOP_INVT_SIDE 테이블에 데이터가 어떻게 들어가는지"'
              rows={3}
              disabled={streaming}
            />
            <div style={styles.formRow}>
              <div style={{ flex: 1 }}>
                <label style={styles.miniLabel}>시작점 타입</label>
                <select
                  style={styles.select}
                  value={targetType}
                  onChange={(e) => setTargetType(e.target.value as typeof targetType)}
                  disabled={streaming}
                >
                  <option value="AUTO">자동 감지</option>
                  <option value="TABLE">테이블 (T_*)</option>
                  <option value="SP">Oracle SP/FUNC</option>
                  <option value="SQL_ID">MyBatis namespace.id</option>
                  <option value="MIPLATFORM_XML">MiPlatform 화면 XML</option>
                </select>
              </div>
              <div style={{ width: 90 }}>
                <label style={styles.miniLabel}>분기수</label>
                <input
                  type="number" min={1} max={20}
                  style={styles.select}
                  value={maxBranches}
                  onChange={(e) => setMaxBranches(Math.max(1, Math.min(20, parseInt(e.target.value) || 3)))}
                  disabled={streaming}
                />
              </div>
            </div>
            <div>
              <label style={styles.miniLabel}>DML 필터 (다중 선택 가능)</label>
              <div style={styles.dmlRow}>
                {DML_OPTIONS.map((opt) => {
                  const checked = dmlFilters.has(opt.value)
                  return (
                    <label
                      key={opt.value}
                      style={{
                        ...styles.dmlChip,
                        borderColor: checked ? opt.color : 'var(--border-color)',
                        background:  checked ? `${opt.color}1a` : 'var(--bg-secondary)',
                        color:       checked ? opt.color : 'var(--text-muted)',
                        opacity:     streaming ? 0.6 : 1,
                      }}
                    >
                      <input
                        type="checkbox"
                        style={{ display: 'none' }}
                        checked={checked}
                        disabled={streaming}
                        onChange={(e) => {
                          const next = new Set(dmlFilters)
                          if (e.target.checked) next.add(opt.value)
                          else next.delete(opt.value)
                          setDmlFilters(next)
                        }}
                      />
                      {opt.label}
                    </label>
                  )
                })}
              </div>
              {dmlFilters.size === 0 && (
                <div style={{ fontSize: 10, color: '#ef4444', marginTop: 4 }}>
                  최소 1개 DML 을 선택하세요.
                </div>
              )}
            </div>
            <div style={styles.toggleRow}>
              <label style={styles.toggle}>
                <input type="checkbox" checked={includeDb} onChange={(e) => setIncludeDb(e.target.checked)} disabled={streaming} />
                <FaDatabase size={11} style={{ marginRight: 4 }} /> Oracle SP/Trigger 포함
              </label>
              <label style={styles.toggle}>
                <input type="checkbox" checked={includeUi} onChange={(e) => setIncludeUi(e.target.checked)} disabled={streaming} />
                <FaFileCode size={11} style={{ marginRight: 4 }} /> MiPlatform 화면 매칭
              </label>
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
              {!streaming ? (
                <button style={styles.runBtn} onClick={startAnalysis} disabled={!query.trim()}>
                  <FaPlay /> 분석 시작
                </button>
              ) : (
                <button style={{ ...styles.runBtn, background: '#ef4444' }} onClick={cancelAnalysis}>
                  <FaTimes /> 중단
                </button>
              )}
            </div>

            {/* v4.5 — AI 요약 버튼 (분석 완료 시에만 노출, 클릭 → 모달) */}
            {showAiSummaryBtn && (
              <button
                style={styles.aiSummaryBtn}
                onClick={() => setSummaryModalOpen(true)}
                title="AI 한 줄 요약 + 주의사항을 팝업으로 보기"
              >
                <FaExpand size={12} />
                <span style={{ flex: 1, textAlign: 'left' }}>🤖 AI 요약 보기</span>
                <span style={styles.aiSummaryBadge}>팝업</span>
              </button>
            )}
          </div>

          {/* 진행 상태 */}
          {streaming && (
            <div style={styles.statusBubble}>
              <span style={styles.spinner} />
              <span style={{ fontSize: 13 }}>{statusText || '준비 중...'}</span>
              <span style={styles.dots}>
                <span style={{ ...styles.dot, animationDelay: '0s'   }}>●</span>
                <span style={{ ...styles.dot, animationDelay: '0.2s' }}>●</span>
                <span style={{ ...styles.dot, animationDelay: '0.4s' }}>●</span>
              </span>
            </div>
          )}

          {/* 추적 통계 (trace 도착 후 즉시 표시) */}
          {trace && (
            <div style={styles.statsCard}>
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 6 }}>📊 추적 통계 (Phase 1)</div>
              <div style={styles.statsGrid}>
                <Stat label="대상" value={String(trace.stats?.targetTable ?? trace.targetType ?? '-')} />
                <Stat label="MyBatis" value={String(trace.stats?.mybatisMatches ?? 0)} />
                <Stat label="Oracle SP" value={String(trace.stats?.spMatches ?? 0)} />
                <Stat label="노드" value={String(trace.nodes.length)} />
                <Stat label="엣지" value={String(trace.edges.length)} />
                <Stat label="시간" value={`${trace.stats?.elapsedMs ?? 0}ms`} />
              </div>
              {trace.warnings && trace.warnings.length > 0 && (
                <div style={styles.warnBox}>
                  <strong>⚠ 주의 ({trace.warnings.length})</strong>
                  <ul style={{ margin: '4px 0 0 18px', padding: 0, fontSize: 11 }}>
                    {trace.warnings.slice(0, 3).map((w, i) => <li key={i}>{w}</li>)}
                    {trace.warnings.length > 3 && <li>… 외 {trace.warnings.length - 3} 건</li>}
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* Claude narrative (markdown) — 전체 내용 인라인 표시. 요약/주의는 상단 "AI 요약" 버튼으로도 접근 가능 */}
          {narrative && (
            <div style={styles.narrativeCard}>
              <div className="markdown-body">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{narrative}</ReactMarkdown>
              </div>
            </div>
          )}

          {!streaming && !trace && !narrative && (
            <div style={styles.emptyHint}>
              <FaFileAlt size={32} style={{ color: 'var(--text-muted)', opacity: 0.4 }} />
              <p style={{ margin: '8px 0 4px', fontWeight: 600 }}>데이터 흐름을 추적해보세요</p>
              <p style={{ margin: 0, fontSize: 12, color: 'var(--text-muted)' }}>
                예시:<br />
                • <code>T_SHOP_INVT_SIDE 가 어떻게 INSERT 되나</code><br />
                • <code>SP_WMS_DELV_SALE</code> (SP 시작점)<br />
                • <code>benit.jd21.model.ShopInvt.saveShopInvtRank</code> (SQL ID)
              </p>
            </div>
          )}
        </div>

        {/* ───── 우측 다이어그램 (65%) ───── */}
        <div style={styles.rightPanel}>
          {trace && trace.nodes.length > 0 ? (
            <ReactFlow
              nodes={rfNodes}
              edges={rfEdges}
              nodeTypes={NODE_TYPES}
              fitView
              proOptions={{ hideAttribution: true }}
              nodesDraggable
              nodesConnectable={false}
              minZoom={0.2}
              maxZoom={2}
            >
              <Background />
              <Controls showInteractive={false} />
              <MiniMap
                pannable zoomable
                nodeColor={(n: any) => TYPE_META[(n.data?.raw?.type as NodeType) ?? 'service']?.color ?? '#94a3b8'}
              />
            </ReactFlow>
          ) : (
            <div style={styles.diagramPlaceholder}>
              <FaSitemap size={48} style={{ color: 'var(--text-muted)', opacity: 0.3 }} />
              <p style={{ marginTop: 12, color: 'var(--text-muted)', fontSize: 13 }}>
                좌측에서 분석을 시작하면 여기에 다이어그램이 표시됩니다
              </p>
              <div style={styles.legend}>
                {(Object.entries(TYPE_META) as [NodeType, typeof TYPE_META[NodeType]][])
                  .sort(([, a], [, b]) => a.order - b.order)
                  .map(([k, m]) => (
                    <div key={k} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ width: 14, height: 14, background: m.bg, borderLeft: `4px solid ${m.color}`, borderRadius: 3 }} />
                      <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                        {m.icon} {m.label}
                      </span>
                    </div>
                  ))}
              </div>
            </div>
          )}

          {/* 노드 클릭 → 슬라이드아웃 */}
          {selected && (
            <NodeDetailDrawer node={selected} onClose={() => setSelected(null)} />
          )}
        </div>
      </div>

      {/* v4.5 — AI 요약 modal (한 줄 요약 + 주의사항) */}
      {summaryModalOpen && narrative && (
        <div style={styles.modalOverlay} onClick={() => setSummaryModalOpen(false)}>
          <div style={styles.modalDialog} onClick={(e) => e.stopPropagation()}>
            <div style={styles.modalHeader}>
              <span style={{ fontSize: 14, fontWeight: 700 }}>🤖 AI 요약</span>
              <div style={{ display: 'flex', gap: 6 }}>
                <button
                  style={summaryCopied ? styles.modalCopyBtnDone : styles.modalCopyBtn}
                  onClick={copySummary}
                >
                  {summaryCopied ? <><FaCheck size={11} /> 복사됨</> : <><FaCopy size={11} /> 전체 복사</>}
                </button>
                <button
                  style={styles.modalCloseBtn}
                  onClick={() => setSummaryModalOpen(false)}
                  title="닫기 (Esc)"
                ><FaTimes /></button>
              </div>
            </div>
            <div style={styles.modalBody}>
              {!summaryText && !warningText && (
                <div style={{ color: 'var(--text-muted)', fontSize: 12, marginBottom: 12 }}>
                  ℹ 한 줄 요약/주의 섹션 헤더를 찾지 못해 전체 응답을 표시합니다.
                </div>
              )}
              {summaryText && (
                <div style={styles.modalSection}>
                  <div style={styles.modalSectionTitle}>📌 한 줄 요약</div>
                  <div className="markdown-body">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{summaryText}</ReactMarkdown>
                  </div>
                </div>
              )}
              {warningText && (
                <div style={styles.modalSection}>
                  <div style={{ ...styles.modalSectionTitle, color: '#b45309' }}>⚠ 주의사항</div>
                  <div className="markdown-body">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{warningText}</ReactMarkdown>
                  </div>
                </div>
              )}
              {!summaryText && !warningText && (
                <div className="markdown-body">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{narrative}</ReactMarkdown>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── 통계 chip ─────────────────────────────────────────────────────────────
function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={{
      padding: '6px 8px', background: 'var(--bg-card)', borderRadius: 6,
      border: '1px solid var(--border-color)', minWidth: 0,
    }}>
      <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 600, wordBreak: 'break-all' }}>{value}</div>
    </div>
  )
}

// ── 노드 상세 슬라이드아웃 ─────────────────────────────────────────────────
function NodeDetailDrawer({ node, onClose }: { node: FlowNode; onClose: () => void }) {
  const meta = TYPE_META[node.type] ?? TYPE_META.service
  const backendSnippet = node.meta?.snippet  // MyBatis/SP 는 분석 시 이미 포함됨
  const hasMeta = node.meta && Object.keys(node.meta).length > 0

  // v4.5 — Service/Controller/DAO 는 lazy fetch 로 파일 발췌
  const [fetchedSnippet, setFetchedSnippet] = useState<string>('')
  const [fetching, setFetching] = useState(false)
  const [fetchError, setFetchError] = useState<string>('')

  useEffect(() => {
    // 백엔드 snippet 이 이미 있으면 불필요
    if (backendSnippet) return
    if (!node.file || !node.line) return
    setFetching(true); setFetchError(''); setFetchedSnippet('')
    const url = `/api/v1/flow/source?file=${encodeURIComponent(node.file)}&line=${node.line}&context=12`
    fetch(url, { credentials: 'include' })
      .then(async (r) => {
        const d = await r.json()
        if (d.success && d.data?.snippet) setFetchedSnippet(d.data.snippet)
        else setFetchError(d.error || '발췌 실패')
      })
      .catch((e) => setFetchError(String(e?.message || e)))
      .finally(() => setFetching(false))
  }, [node.file, node.line, backendSnippet])

  const displaySnippet = backendSnippet || fetchedSnippet

  return (
    <div style={styles.drawerOverlay} onClick={onClose}>
      <div style={styles.drawer} onClick={(e) => e.stopPropagation()}>
        <div style={{ ...styles.drawerHeader, borderLeft: `6px solid ${meta.color}` }}>
          <div>
            <div style={{ fontSize: 11, color: meta.color, fontWeight: 600, textTransform: 'uppercase' }}>
              {meta.icon} {meta.label}
            </div>
            <div style={{ fontFamily: 'monospace', fontSize: 14, marginTop: 2, wordBreak: 'break-all' }}>
              {node.label}
            </div>
          </div>
          <button style={styles.closeBtn} onClick={onClose}><FaTimes /></button>
        </div>
        <div style={styles.drawerBody}>
          {node.file && (
            <div style={styles.kvRow}>
              <div style={styles.k}>파일</div>
              <div style={styles.v}>
                <code style={{ wordBreak: 'break-all' }}>
                  {node.file}{node.line ? `:${node.line}` : ''}
                </code>
              </div>
            </div>
          )}
          {hasMeta && Object.entries(node.meta!).filter(([k]) => k !== 'snippet').map(([k, v]) => (
            <div key={k} style={styles.kvRow}>
              <div style={styles.k}>{k}</div>
              <div style={styles.v} title={v}>{v}</div>
            </div>
          ))}
          {fetching && (
            <div style={{ marginTop: 12, color: 'var(--text-muted)', fontSize: 12 }}>
              📄 소스 발췌 불러오는 중...
            </div>
          )}
          {fetchError && !displaySnippet && (
            <div style={{ marginTop: 12, color: '#ef4444', fontSize: 12 }}>
              ⚠ {fetchError}
            </div>
          )}
          {displaySnippet && (
            <div>
              <div style={{ ...styles.k, marginTop: 12, marginBottom: 6 }}>
                SQL/소스 발췌{!backendSnippet && node.line ? ` (라인 ${node.line} 전후)` : ''}
              </div>
              <pre style={styles.snippet}>{displaySnippet}</pre>
            </div>
          )}
          {!node.file && !displaySnippet && !hasMeta && !fetching && (
            <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>
              이 노드에 추가 메타데이터가 없습니다.
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ── 스타일 ─────────────────────────────────────────────────────────────────

const styles: Record<string, React.CSSProperties> = {
  page: { display: 'flex', flexDirection: 'column', height: 'calc(100vh - 60px)', background: 'var(--bg-primary)' },
  topBar: {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '12px 20px', borderBottom: '1px solid var(--border-color)',
    background: 'var(--bg-secondary)',
  },
  iconBtn: {
    display: 'inline-flex', alignItems: 'center', gap: 6,
    padding: '6px 12px', borderRadius: 6, fontSize: 12,
    background: 'var(--bg-card)', border: '1px solid var(--border-color)',
    color: 'var(--text-primary)', cursor: 'pointer',
  },
  body: { flex: 1, display: 'flex', overflow: 'hidden' },
  leftPanel: {
    width: '35%', minWidth: 380, maxWidth: 560,
    overflowY: 'auto', padding: 16, gap: 12, display: 'flex', flexDirection: 'column',
    borderRight: '1px solid var(--border-color)', background: 'var(--bg-primary)',
  },
  rightPanel: { flex: 1, position: 'relative', background: 'var(--bg-primary)' },

  formCard: {
    padding: 12, background: 'var(--bg-card)', borderRadius: 8,
    border: '1px solid var(--border-color)', display: 'flex', flexDirection: 'column', gap: 8,
  },
  shareBanner: {
    padding: '8px 12px', background: 'rgba(6,182,212,0.1)',
    border: '1px solid rgba(6,182,212,0.4)', borderRadius: 6,
    fontSize: 11, color: 'var(--text-primary)',
  },
  historyCard: {
    padding: 10, background: 'var(--bg-card)', borderRadius: 8,
    border: '1px solid var(--border-color)',
  },
  historyList: {
    display: 'flex', flexDirection: 'column', gap: 4,
    maxHeight: 240, overflowY: 'auto',
  },
  historyItem: {
    display: 'flex', alignItems: 'center', gap: 6, padding: '6px 8px',
    border: '1px solid var(--border-color)', borderRadius: 6,
    cursor: 'pointer', fontSize: 11, transition: 'background 0.1s',
  },
  historyQuery: {
    fontWeight: 600, color: 'var(--text-primary)', fontSize: 12,
    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
  },
  historyMeta: {
    fontSize: 10, color: 'var(--text-muted)', marginTop: 2,
  },
  historyDelBtn: {
    background: 'transparent', border: 'none', color: 'var(--text-muted)',
    cursor: 'pointer', padding: 4, opacity: 0.5,
  },
  label:     { fontSize: 12, fontWeight: 600, color: 'var(--text-primary)' },
  miniLabel: { fontSize: 10, color: 'var(--text-muted)', display: 'block', marginBottom: 3 },
  textarea: {
    resize: 'vertical', borderRadius: 6, padding: '8px 10px',
    border: '1px solid var(--border-color)', background: 'var(--bg-secondary)',
    color: 'var(--text-primary)', fontSize: 13, fontFamily: 'inherit',
  },
  select: {
    width: '100%', padding: '6px 8px', borderRadius: 6, fontSize: 12,
    border: '1px solid var(--border-color)', background: 'var(--bg-secondary)',
    color: 'var(--text-primary)',
  },
  formRow:    { display: 'flex', gap: 8 },
  toggleRow:  { display: 'flex', gap: 12, flexWrap: 'wrap' },
  dmlRow:     { display: 'flex', gap: 6, flexWrap: 'wrap' },
  dmlChip: {
    display: 'inline-flex', alignItems: 'center',
    padding: '4px 10px', borderRadius: 14, border: '1.5px solid',
    fontSize: 11, fontWeight: 600, cursor: 'pointer',
    userSelect: 'none', transition: 'all 0.12s',
  },
  toggle:     { fontSize: 11, color: 'var(--text-primary)', display: 'inline-flex', alignItems: 'center', gap: 4, cursor: 'pointer' },
  runBtn: {
    flex: 1, padding: '8px 14px', borderRadius: 6, border: 'none',
    background: 'var(--accent)', color: '#fff', fontWeight: 600, fontSize: 13,
    cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6,
  },

  statusBubble: {
    display: 'flex', alignItems: 'center', gap: 10,
    padding: '10px 14px', background: 'var(--bg-secondary)',
    border: '1px dashed var(--border-color)', borderRadius: 8,
  },
  spinner: {
    width: 14, height: 14, borderRadius: '50%',
    border: '2px solid var(--border-color)', borderTopColor: 'var(--accent)',
    animation: 'spin 0.8s linear infinite', display: 'inline-block',
  },
  dots: { display: 'inline-flex', gap: 2, fontSize: 10, color: 'var(--accent)' },
  dot:  { animation: 'blink 1.2s infinite' },

  statsCard: {
    padding: 10, background: 'var(--bg-card)', borderRadius: 8,
    border: '1px solid var(--border-color)',
  },
  statsGrid: {
    display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 6,
  },
  warnBox: {
    marginTop: 8, padding: '6px 10px', background: 'rgba(245,158,11,0.08)',
    border: '1px solid rgba(245,158,11,0.3)', borderRadius: 6, fontSize: 11,
  },
  narrativeCard: {
    padding: '12px 16px', background: 'var(--bg-card)', borderRadius: 8,
    border: '1px solid var(--border-color)', overflowX: 'auto',
  },

  // v4.5 — AI 요약 버튼 (분석 시작 버튼 아래)
  aiSummaryBtn: {
    display: 'flex', alignItems: 'center', gap: 8,
    width: '100%', marginTop: 8, padding: '10px 14px',
    fontSize: 13, fontWeight: 600, color: '#ffffff',
    background: 'linear-gradient(135deg, #06b6d4, #0ea5e9)',
    border: 'none',
    borderRadius: 6, cursor: 'pointer',
    textAlign: 'left' as const,
    boxShadow: '0 2px 6px rgba(6,182,212,0.25)',
    transition: 'transform 0.05s ease, box-shadow 0.15s ease',
  },
  aiSummaryBadge: {
    fontSize: 10, fontWeight: 600,
    padding: '2px 6px', borderRadius: 3,
    background: 'rgba(255,255,255,0.25)', color: '#ffffff',
  },
  modalSection: {
    marginBottom: 18,
  },
  modalSectionTitle: {
    fontSize: 12, fontWeight: 700, letterSpacing: 0.4,
    color: '#0e7490', textTransform: 'uppercase' as const,
    marginBottom: 6,
    paddingBottom: 4,
    borderBottom: '1px solid var(--border-color)',
  },

  // v4.5 — modal
  modalOverlay: {
    position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)',
    display: 'flex', justifyContent: 'center', alignItems: 'center',
    zIndex: 1000, padding: 24,
    animation: 'modal-overlay-in 0.15s ease-out',
  },
  modalDialog: {
    background: 'var(--bg-secondary)', borderRadius: 10,
    boxShadow: '0 10px 40px rgba(0,0,0,0.35)',
    border: '1px solid var(--border-color)',
    width: 'min(760px, 92vw)', maxHeight: '80vh',
    display: 'flex', flexDirection: 'column',
    animation: 'modal-body-in 0.18s ease-out',
  },
  modalHeader: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    padding: '12px 16px', borderBottom: '1px solid var(--border-color)',
    background: 'var(--bg-card)', borderTopLeftRadius: 10, borderTopRightRadius: 10,
  },
  modalBody: {
    flex: 1, overflowY: 'auto', padding: '16px 20px',
    fontSize: 14, lineHeight: 1.65, color: 'var(--text-primary)',
  },
  modalCopyBtn: {
    display: 'inline-flex', alignItems: 'center', gap: 5,
    padding: '5px 10px', fontSize: 11, fontWeight: 600,
    background: 'var(--bg-secondary)', color: 'var(--text-primary)',
    border: '1px solid var(--border-color)', borderRadius: 4, cursor: 'pointer',
  },
  modalCopyBtnDone: {
    display: 'inline-flex', alignItems: 'center', gap: 5,
    padding: '5px 10px', fontSize: 11, fontWeight: 600,
    background: '#10b981', color: '#ffffff',
    border: '1px solid #10b981', borderRadius: 4, cursor: 'pointer',
  },
  modalCloseBtn: {
    background: 'transparent', border: 'none', color: 'var(--text-muted)',
    cursor: 'pointer', fontSize: 14, padding: '4px 8px',
  },
  emptyHint: {
    flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
    padding: 24, textAlign: 'center', color: 'var(--text-primary)',
  },

  diagramPlaceholder: {
    height: '100%', display: 'flex', flexDirection: 'column',
    alignItems: 'center', justifyContent: 'center', padding: 24,
  },
  legend: {
    display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8,
    marginTop: 24, padding: 16,
    background: 'var(--bg-card)', borderRadius: 8, border: '1px solid var(--border-color)',
  },

  drawerOverlay: {
    position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.3)',
    display: 'flex', justifyContent: 'flex-end', zIndex: 10,
  },
  drawer: {
    width: '60%', minWidth: 360, maxWidth: 720, height: '100%',
    background: 'var(--bg-secondary)', boxShadow: '-4px 0 16px rgba(0,0,0,0.15)',
    display: 'flex', flexDirection: 'column', animation: 'slideInRight 0.18s ease-out',
  },
  drawerHeader: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
    padding: '12px 16px', borderBottom: '1px solid var(--border-color)',
    background: 'var(--bg-card)',
  },
  closeBtn: {
    background: 'transparent', border: 'none', color: 'var(--text-muted)',
    cursor: 'pointer', fontSize: 14, padding: 4,
  },
  drawerBody: { flex: 1, overflowY: 'auto', padding: 16 },
  kvRow: { display: 'flex', gap: 10, padding: '6px 0', borderBottom: '1px dashed var(--border-color)' },
  k: { width: 80, fontSize: 11, color: 'var(--text-muted)', flexShrink: 0 },
  v: { flex: 1, fontSize: 12, color: 'var(--text-primary)', wordBreak: 'break-word' },
  snippet: {
    background: 'var(--bg-card)', padding: 12, borderRadius: 6,
    border: '1px solid var(--border-color)',
    fontSize: 11, fontFamily: 'monospace', whiteSpace: 'pre-wrap', wordBreak: 'break-all',
    maxHeight: 320, overflowY: 'auto',
  },
}
