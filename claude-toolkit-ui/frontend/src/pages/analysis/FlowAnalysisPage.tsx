import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import ReactFlow, {
  Background, Controls, MiniMap, Handle, Position,
  type Node as RfNode, type Edge as RfEdge, type NodeProps,
} from 'reactflow'
import 'reactflow/dist/style.css'
import {
  FaPlay, FaSync, FaTimes, FaDownload, FaSitemap,
  FaDatabase, FaFileCode, FaFileAlt,
} from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

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

  const rfEdges: RfEdge[] = edges.map((e, i) => ({
    id: `e${i}-${e.from}-${e.to}`,
    source: e.from, target: e.to,
    label: e.label || '',
    animated: !!e.label && /INSERT|UPDATE|MERGE|DELETE/i.test(e.label),
    style: { stroke: '#94a3b8', strokeWidth: 1.5 },
    labelStyle: { fontSize: 10, fill: 'var(--text-muted)' },
    labelBgStyle: { fill: 'var(--bg-secondary)', fillOpacity: 0.85 },
  }))

  return { rfNodes, rfEdges }
}

// ── 페이지 본체 ────────────────────────────────────────────────────────────

export default function FlowAnalysisPage() {
  const toast = useToast()

  // 입력 폼
  const [query, setQuery]             = useState('')
  const [targetType, setTargetType]   = useState<'AUTO' | 'TABLE' | 'SP' | 'SQL_ID' | 'MIPLATFORM_XML'>('AUTO')
  const [dmlFilter, setDmlFilter]     = useState<'ALL' | 'INSERT' | 'UPDATE' | 'MERGE' | 'DELETE'>('ALL')
  const [maxBranches, setMaxBranches] = useState(3)
  const [includeDb, setIncludeDb]     = useState(true)
  const [includeUi, setIncludeUi]     = useState(true)

  // 스트림 상태
  const [streaming,  setStreaming]  = useState(false)
  const [statusText, setStatusText] = useState('')
  const [trace,      setTrace]      = useState<FlowResult | null>(null)
  const [narrative,  setNarrative]  = useState('')
  const [selected,   setSelected]   = useState<FlowNode | null>(null)

  const esRef = useRef<EventSource | null>(null)

  // SSE 종료 시 정리
  useEffect(() => () => { esRef.current?.close() }, [])

  const handleSelect = useCallback((n: FlowNode) => setSelected(n), [])

  const { rfNodes, rfEdges } = useMemo(() => {
    if (!trace || trace.nodes.length === 0) return { rfNodes: [], rfEdges: [] }
    return layoutNodes(trace.nodes, trace.edges, handleSelect)
  }, [trace, handleSelect])

  // ── 분석 시작 ────────────────────────────────────────────────────────
  const startAnalysis = async () => {
    if (!query.trim()) { toast.warning('질문을 입력해주세요.'); return }
    if (streaming) return

    setStreaming(true)
    setStatusText('연결 중...')
    setTrace(null)
    setNarrative('')
    setSelected(null)

    try {
      // Step 1: POST /flow/stream/start (세션에 요청 적재)
      const startRes = await fetch('/flow/stream/start', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          query: query.trim(),
          targetType,
          dmlFilter,
          maxBranches,
          includeDb,
          includeUi,
        }),
      })
      const startData = await startRes.json()
      if (!startData.success) {
        toast.error(startData.error || '시작 실패')
        setStreaming(false)
        setStatusText('')
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

      es.onmessage = (e: MessageEvent) => {
        const data = e.data
        if (data === '[DONE]' || data === 'done') {
          es.close(); esRef.current = null
          setStreaming(false)
          setStatusText('')
          return
        }
        if (!acc) setStatusText('')   // 첫 chunk 도착 → status 사라짐
        acc += data
        setNarrative(acc)
      }

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
      }
    } catch {
      toast.error('분석 시작 실패')
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
          <button style={styles.iconBtn} onClick={reindex} disabled={streaming} title="모든 인덱서 재빌드 (Settings 변경 후)">
            <FaSync /> 인덱스 재빌드
          </button>
          {narrative && !streaming && (
            <button style={styles.iconBtn} onClick={downloadMd} title="결과 다운로드">
              <FaDownload /> Markdown
            </button>
          )}
        </div>
      </div>

      {/* 본문 — 좌/우 분할 */}
      <div style={styles.body}>
        {/* ───── 좌측 패널 (35%) ───── */}
        <div style={styles.leftPanel}>
          {/* 입력 폼 */}
          <div style={styles.formCard}>
            <label style={styles.label}>분석 대상 질문</label>
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
              <div style={{ flex: 1 }}>
                <label style={styles.miniLabel}>DML 필터</label>
                <select
                  style={styles.select}
                  value={dmlFilter}
                  onChange={(e) => setDmlFilter(e.target.value as typeof dmlFilter)}
                  disabled={streaming}
                >
                  <option value="ALL">전체</option>
                  <option value="INSERT">INSERT 만</option>
                  <option value="UPDATE">UPDATE 만</option>
                  <option value="MERGE">MERGE 만</option>
                  <option value="DELETE">DELETE 만</option>
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

          {/* Claude narrative (markdown) */}
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
  const snippet = node.meta?.snippet
  const hasMeta = node.meta && Object.keys(node.meta).length > 0
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
          {snippet && (
            <div>
              <div style={{ ...styles.k, marginTop: 12, marginBottom: 6 }}>SQL/소스 발췌</div>
              <pre style={styles.snippet}>{snippet}</pre>
            </div>
          )}
          {!node.file && !snippet && !hasMeta && (
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
