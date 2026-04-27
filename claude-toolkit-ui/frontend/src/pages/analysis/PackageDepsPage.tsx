import { useCallback, useEffect, useState } from 'react'
import ReactFlow, {
  Background, Controls, MiniMap, Handle, Position, MarkerType,
  type Node as RfNode, type Edge as RfEdge, type NodeProps,
} from 'reactflow'
import 'reactflow/dist/style.css'
import { FaProjectDiagram, FaSync } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

/**
 * v4.5 — 패키지 간 의존성 그래프 (Spring endpoint callee 기반 휴리스틱).
 * 전체 패키지를 노드로, 호출 관계를 엣지로 ReactFlow 로 시각화.
 */

interface DepNode  { id: string; packageName: string; classTotal: number; controllerCount: number; serviceCount: number; daoCount: number }
interface DepEdge  { id: string; source: string; target: string }
interface GraphData { nodes: DepNode[]; edges: DepEdge[]; level: number; packageCount: number; edgeCount: number }

function PackageNode({ data }: NodeProps<{ raw: DepNode }>) {
  const { raw } = data
  const short = raw.packageName.split('.').slice(-2).join('.')
  const color = raw.controllerCount > 0 ? '#8b5cf6' : raw.daoCount > 0 ? '#f59e0b' : '#10b981'
  return (
    <div style={{
      background: 'var(--bg-card, #fff)',
      border: `2px solid ${color}`,
      borderLeft: `6px solid ${color}`,
      borderRadius: 8, padding: '6px 10px', minWidth: 120, maxWidth: 180, fontSize: 11,
    }}>
      <Handle type="target" position={Position.Left}  style={{ background: color }} />
      <div style={{ fontWeight: 700, color, wordBreak: 'break-all' }}>{short}</div>
      <div style={{ color: 'var(--text-muted)', marginTop: 2 }}>
        {raw.classTotal}클래스
        {raw.controllerCount > 0 && ` · C${raw.controllerCount}`}
        {raw.daoCount > 0        && ` · D${raw.daoCount}`}
      </div>
      <Handle type="source" position={Position.Right} style={{ background: color }} />
    </div>
  )
}

const NODE_TYPES = { pkg: PackageNode }

function toRfNodes(nodes: DepNode[]): RfNode[] {
  const cols = Math.ceil(Math.sqrt(nodes.length))
  return nodes.map((n, i) => ({
    id:       n.id,
    type:     'pkg',
    position: { x: (i % cols) * 220, y: Math.floor(i / cols) * 100 },
    data:     { raw: n },
  }))
}

function toRfEdges(edges: DepEdge[]): RfEdge[] {
  return edges.map(e => ({
    id:           e.id,
    source:       e.source,
    target:       e.target,
    markerEnd:    { type: MarkerType.ArrowClosed, width: 14, height: 14, color: '#94a3b8' },
    style:        { stroke: '#94a3b8', strokeWidth: 1.5 },
    animated:     false,
  }))
}

export default function PackageDepsPage() {
  const toast = useToast()
  const [loading, setLoading] = useState(false)
  const [graph,   setGraph]   = useState<GraphData | null>(null)
  const [rfNodes, setRfNodes] = useState<RfNode[]>([])
  const [rfEdges, setRfEdges] = useState<RfEdge[]>([])

  const loadGraph = useCallback(async () => {
    setLoading(true)
    try {
      const r = await fetch('/api/v1/package/dependency-graph', { credentials: 'include' })
      const d = await r.json()
      if (d.success && d.data) {
        const g = d.data as GraphData
        setGraph(g)
        setRfNodes(toRfNodes(g.nodes))
        setRfEdges(toRfEdges(g.edges))
      } else {
        toast.error(d.error || '그래프 로드 실패')
      }
    } catch (e: unknown) {
      toast.error('그래프 호출 실패: ' + (e instanceof Error ? e.message : String(e)))
    } finally { setLoading(false) }
  }, [toast])

  useEffect(() => { loadGraph() }, [loadGraph])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* 헤더 */}
      <div style={{
        padding: '10px 16px', borderBottom: '1px solid var(--border-color)',
        display: 'flex', alignItems: 'center', gap: 10, background: 'var(--bg-card)',
      }}>
        <FaProjectDiagram size={18} style={{ color: '#06b6d4' }} />
        <h2 style={{ margin: 0, fontSize: 16 }}>패키지 의존성 그래프</h2>
        {graph && (
          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
            L{graph.level} · {graph.packageCount}개 패키지 · {graph.edgeCount}개 의존 관계
          </span>
        )}
        <div style={{ flex: 1 }} />
        <button
          onClick={loadGraph} disabled={loading}
          style={{ padding: '4px 10px', fontSize: 12, borderRadius: 6,
                   border: '1px solid var(--border-color)', background: 'var(--bg-secondary)',
                   cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', gap: 4, alignItems: 'center' }}
        >
          <FaSync style={loading ? { animation: 'spin 1s linear infinite' } : undefined} />
          {loading ? '로드 중...' : '새로고침'}
        </button>
      </div>

      {/* 안내 */}
      <div style={{ padding: '6px 16px', fontSize: 11, color: 'var(--text-muted)', background: 'var(--bg-secondary)', borderBottom: '1px solid var(--border-color)' }}>
        ⚠ Spring Controller endpoint callee 휴리스틱 기반 — 정확도가 낮을 수 있습니다. "AI 추정" 수준으로 참고하세요.
        &nbsp; 🟣 Controller 포함 &nbsp; 🟡 DAO 포함 &nbsp; 🟢 Service/Model
      </div>

      {/* 그래프 */}
      <div style={{ flex: 1, position: 'relative' }}>
        {loading && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 10, background: 'rgba(var(--bg-page-rgb, 255,255,255),0.7)', fontSize: 14, color: 'var(--text-muted)' }}>
            <FaSync style={{ animation: 'spin 1s linear infinite', marginRight: 8 }} /> 의존성 계산 중...
          </div>
        )}
        {!loading && graph?.edgeCount === 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)', fontSize: 13 }}>
            <FaProjectDiagram size={48} style={{ opacity: 0.3, marginBottom: 12 }} />
            <div>패키지 간 의존 관계가 감지되지 않았습니다.</div>
            <div style={{ fontSize: 11, marginTop: 4 }}>
              Settings → 프로젝트 스캔 경로가 설정되어 있고 Java 인덱스가 빌드됐는지 확인하세요.
            </div>
          </div>
        )}
        {rfNodes.length > 0 && (
          <ReactFlow
            nodes={rfNodes}
            edges={rfEdges}
            nodeTypes={NODE_TYPES}
            fitView
            fitViewOptions={{ padding: 0.2 }}
            minZoom={0.2}
            maxZoom={2}
          >
            <Background />
            <Controls />
            <MiniMap
              nodeColor={(n) => {
                const raw = (n.data as { raw: DepNode }).raw
                return raw.controllerCount > 0 ? '#8b5cf6' : raw.daoCount > 0 ? '#f59e0b' : '#10b981'
              }}
            />
          </ReactFlow>
        )}
      </div>
    </div>
  )
}
