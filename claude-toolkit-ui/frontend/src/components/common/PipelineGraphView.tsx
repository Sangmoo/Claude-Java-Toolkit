import { useMemo } from 'react'
import ReactFlow, {
  Background, Controls, MiniMap, Handle, Position,
  type Node, type Edge,
} from 'reactflow'
import 'reactflow/dist/style.css'

/**
 * v4.3.0 — 파이프라인 시각화 그래프 뷰.
 *
 * <p>YAML 텍스트를 파싱하여 react-flow 그래프로 렌더링한다.
 * 단계의 분석 유형, 병렬 실행, 조건부 실행, 컨텍스트 의존성을 시각적으로 표현.
 *
 * <p>편집은 기존 PipelineBuilder(리스트 UI)에서 수행하고,
 * 이 컴포넌트는 결과 미리보기 + 의존성 검증용 read-only 뷰로 동작.
 */

interface ParsedStep {
  id: string
  analysis: string
  parallel?: boolean
  condition?: string
  context?: string
  dependsOn?: string[]
}

const NODE_COLORS: Record<string, string> = {
  CODE_REVIEW:           '#3b82f6',
  CODE_REVIEW_SECURITY:  '#ef4444',
  REFACTOR:              '#8b5cf6',
  TEST_GEN:              '#10b981',
  JAVADOC_GEN:           '#06b6d4',
  DOC_GEN:               '#10b981',
  API_SPEC:              '#10b981',
  SQL_REVIEW:            '#3b82f6',
  SQL_SECURITY:          '#ef4444',
  EXPLAIN_PLAN:          '#f59e0b',
  INDEX_OPT:             '#f59e0b',
  COMPLEXITY:            '#8b5cf6',
}

const NODE_ICONS: Record<string, string> = {
  CODE_REVIEW: '🔍', CODE_REVIEW_SECURITY: '🛡️', REFACTOR: '🔧',
  TEST_GEN: '🧪', JAVADOC_GEN: '📝', DOC_GEN: '📄', API_SPEC: '📋',
  SQL_REVIEW: '🗄️', SQL_SECURITY: '🔐', EXPLAIN_PLAN: '📊',
  INDEX_OPT: '⚡', COMPLEXITY: '📈',
}

/** 매우 단순한 YAML 파서 — steps 배열만 추출. SnakeYAML 백엔드 검증을 의존하므로
 *  여기서는 가벼운 정규식 + 라인 기반으로 충분.  */
function parseSteps(yaml: string): ParsedStep[] {
  if (!yaml) return []
  const steps: ParsedStep[] = []
  let inSteps = false
  let current: ParsedStep | null = null
  const lines = yaml.split(/\r?\n/)
  for (const raw of lines) {
    const line = raw.replace(/\t/g, '  ')
    if (/^\s*steps\s*:/.test(line)) { inSteps = true; continue }
    if (!inSteps) continue
    // 새 step 시작
    const newStep = line.match(/^\s*-\s*id\s*:\s*(.+?)\s*$/)
    if (newStep) {
      if (current) steps.push(current)
      current = { id: newStep[1].trim(), analysis: '' }
      continue
    }
    if (!current) continue
    const m = line.match(/^\s+(\w+)\s*:\s*(.+?)\s*$/)
    if (!m) continue
    const key = m[1]
    const val = m[2].replace(/^["']|["']$/g, '')
    if (key === 'analysis') current.analysis = val
    else if (key === 'parallel') current.parallel = val === 'true'
    else if (key === 'condition') current.condition = val
    else if (key === 'context') {
      current.context = val
      // ${stepId.output} 패턴에서 stepId 추출
      const dep = val.match(/\$\{([\w-]+)\.output\}/)
      if (dep) current.dependsOn = [dep[1]]
    } else if (key === 'dependsOn') {
      current.dependsOn = val.split(',').map((s) => s.trim()).filter(Boolean)
    }
  }
  if (current) steps.push(current)
  return steps
}

/** 노드/엣지 레이아웃 — 병렬 그룹은 세로로 펼침, 순차는 가로 진행 */
function layout(steps: ParsedStep[]): { nodes: Node[]; edges: Edge[] } {
  const nodes: Node[] = []
  const edges: Edge[] = []

  const COL_W = 260
  const ROW_H = 110

  // 병렬 그룹화: 연속된 parallel=true step 들을 같은 column 의 다른 row 에 배치
  let col = 0
  let row = 0
  let prevColIds: string[] = []  // 직전 컬럼의 모든 step id (자동 의존성 추론용)

  for (let i = 0; i < steps.length; i++) {
    const s = steps[i]
    const prev = steps[i - 1]
    const startsNewColumn = !s.parallel || !prev || !prev.parallel

    if (startsNewColumn && i > 0) {
      col++
      row = 0
      prevColIds = [steps[i - 1].id]
    } else if (s.parallel && prev?.parallel) {
      row++
    }

    nodes.push({
      id: s.id,
      position: { x: col * COL_W, y: row * ROW_H },
      data: {
        label: s.id,
        analysis: s.analysis,
        parallel: s.parallel,
        condition: s.condition,
      },
      type: 'analysis',
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
    })

    // 엣지 — 명시적 dependsOn 우선, 없으면 직전 컬럼의 모든 노드에서 연결
    if (s.dependsOn && s.dependsOn.length > 0) {
      for (const d of s.dependsOn) {
        edges.push({
          id: `${d}->${s.id}`,
          source: d, target: s.id,
          animated: true,
          label: s.condition ? '?' : undefined,
          style: { stroke: s.condition ? '#f59e0b' : '#94a3b8', strokeWidth: 2 },
        })
      }
    } else if (i > 0 && col > 0) {
      for (const p of prevColIds) {
        edges.push({
          id: `${p}->${s.id}-auto`,
          source: p, target: s.id,
          animated: false,
          style: { stroke: '#cbd5e1', strokeWidth: 1.5, strokeDasharray: '4 2' },
        })
      }
    }
  }

  return { nodes, edges }
}

/** 분석 노드 커스텀 컴포넌트 */
function AnalysisNode({ data }: { data: any }) {
  const color = NODE_COLORS[data.analysis] || '#64748b'
  const icon = NODE_ICONS[data.analysis] || '⚙️'
  return (
    <div style={{
      background: 'var(--bg-card, #fff)',
      border: `2px solid ${color}`, borderRadius: '8px',
      padding: '8px 12px', minWidth: '180px',
      boxShadow: '0 2px 6px rgba(0,0,0,0.08)',
      fontSize: '12px',
    }}>
      <Handle type="target" position={Position.Left} style={{ background: color }} />
      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
        <span style={{ fontSize: '18px' }}>{icon}</span>
        <strong style={{ color }}>{data.analysis}</strong>
        {data.parallel && <span style={{ fontSize: '10px', padding: '1px 5px', background: 'rgba(245,158,11,0.15)', color: '#f59e0b', borderRadius: '3px' }}>∥ 병렬</span>}
      </div>
      <div style={{ fontFamily: 'monospace', fontSize: '11px', color: 'var(--text-muted, #64748b)' }}>
        {data.label}
      </div>
      {data.condition && (
        <div style={{ fontSize: '10px', color: '#f59e0b', marginTop: '4px' }} title={data.condition}>
          if: {data.condition.length > 30 ? data.condition.slice(0, 30) + '…' : data.condition}
        </div>
      )}
      <Handle type="source" position={Position.Right} style={{ background: color }} />
    </div>
  )
}

const NODE_TYPES = { analysis: AnalysisNode }

interface Props {
  yaml: string
  height?: number
}

export default function PipelineGraphView({ yaml, height = 420 }: Props) {
  const { nodes, edges } = useMemo(() => {
    const steps = parseSteps(yaml || '')
    if (steps.length === 0) return { nodes: [], edges: [] }
    return layout(steps)
  }, [yaml])

  // 노드가 없을 때 placeholder
  if (nodes.length === 0) {
    return (
      <div style={{
        height, display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: 'var(--bg-card, #fff)', border: '1px dashed var(--border-color)',
        borderRadius: '8px', color: 'var(--text-muted)', fontSize: '13px',
      }}>
        파이프라인에 단계가 없습니다. 빌더에서 단계를 추가하면 그래프가 표시됩니다.
      </div>
    )
  }

  return (
    <div style={{ height, background: 'var(--bg-card, #fff)', border: '1px solid var(--border-color)', borderRadius: '8px' }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={NODE_TYPES}
        fitView
        proOptions={{ hideAttribution: true }}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
      >
        <Background />
        <Controls showInteractive={false} />
        <MiniMap pannable zoomable nodeColor={(n) => NODE_COLORS[n.data?.analysis] || '#64748b'} />
      </ReactFlow>
    </div>
  )
}

