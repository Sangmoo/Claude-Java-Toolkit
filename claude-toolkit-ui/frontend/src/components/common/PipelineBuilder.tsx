import { useState } from 'react'
import { FaPlus, FaTimes, FaArrowDown, FaArrowUp } from 'react-icons/fa'

/** 사용 가능한 분석 유형 */
const ANALYSIS_TYPES = [
  { id: 'CODE_REVIEW', label: '코드 리뷰', icon: '🔍', color: '#3b82f6', category: '분석' },
  { id: 'CODE_REVIEW_SECURITY', label: '보안 리뷰', icon: '🛡️', color: '#ef4444', category: '분석' },
  { id: 'REFACTOR', label: '리팩터링', icon: '🔧', color: '#8b5cf6', category: '생성' },
  { id: 'TEST_GEN', label: '테스트 생성', icon: '🧪', color: '#10b981', category: '생성' },
  { id: 'JAVADOC_GEN', label: 'Javadoc 생성', icon: '📝', color: '#06b6d4', category: '생성' },
  { id: 'DOC_GEN', label: '기술 문서', icon: '📄', color: '#10b981', category: '생성' },
  { id: 'API_SPEC', label: 'API 명세', icon: '📋', color: '#10b981', category: '생성' },
  { id: 'SQL_REVIEW', label: 'SQL 리뷰', icon: '🗄️', color: '#3b82f6', category: 'SQL' },
  { id: 'SQL_SECURITY', label: 'SQL 보안', icon: '🔐', color: '#ef4444', category: 'SQL' },
  { id: 'EXPLAIN_PLAN', label: '실행계획', icon: '📊', color: '#f59e0b', category: 'SQL' },
  { id: 'INDEX_OPT', label: '인덱스 최적화', icon: '⚡', color: '#f59e0b', category: 'SQL' },
  { id: 'COMPLEXITY', label: '복잡도 분석', icon: '📈', color: '#8b5cf6', category: '분석' },
]

interface PipelineStep {
  id: string
  analysis: string
  label: string
  useContext: boolean  // 이전 단계 결과를 참고
  condition: string    // 조건부 실행
  parallel: boolean    // 병렬 실행
}

interface PipelineBuilderProps {
  onYamlGenerate: (yaml: string) => void
  pipelineName: string
  pipelineDesc: string
  inputLanguage: string
}

export default function PipelineBuilder({ onYamlGenerate, pipelineName, pipelineDesc, inputLanguage }: PipelineBuilderProps) {
  const [steps, setSteps] = useState<PipelineStep[]>([])
  const [showPicker, setShowPicker] = useState(false)

  const addStep = (type: typeof ANALYSIS_TYPES[0]) => {
    const stepId = type.id.toLowerCase().replace(/_/g, '-') + '-' + (steps.length + 1)
    setSteps([...steps, {
      id: stepId,
      analysis: type.id,
      label: type.label,
      useContext: steps.length > 0, // 두 번째 단계부터 자동 참조
      condition: '',
      parallel: false,
    }])
    setShowPicker(false)
    generateYaml([...steps, { id: stepId, analysis: type.id, label: type.label, useContext: steps.length > 0, condition: '', parallel: false }])
  }

  const removeStep = (idx: number) => {
    const next = steps.filter((_, i) => i !== idx)
    setSteps(next)
    generateYaml(next)
  }

  const moveStep = (idx: number, dir: -1 | 1) => {
    const next = [...steps]
    const temp = next[idx]
    next[idx] = next[idx + dir]
    next[idx + dir] = temp
    setSteps(next)
    generateYaml(next)
  }

  const toggleOption = (idx: number, key: 'useContext' | 'parallel') => {
    const next = [...steps]
    next[idx] = { ...next[idx], [key]: !next[idx][key] }
    setSteps(next)
    generateYaml(next)
  }

  const generateYaml = (stepList: PipelineStep[]) => {
    const lines = [
      `id: ${pipelineName.replace(/\s+/g, '-').toLowerCase() || 'custom-pipeline'}`,
      `name: ${pipelineName || '사용자 파이프라인'}`,
      `description: ${pipelineDesc || '버튼으로 생성된 파이프라인'}`,
      `inputLanguage: ${inputLanguage}`,
      '',
      'steps:',
    ]
    stepList.forEach((step, i) => {
      lines.push(`  - id: ${step.id}`)
      lines.push(`    analysis: ${step.analysis}`)
      if (i === 0) {
        lines.push(`    input: \${pipeline.input}`)
      } else if (step.useContext) {
        lines.push(`    input: \${pipeline.input}`)
        lines.push(`    context: \${${stepList[i - 1].id}.output}`)
      } else {
        lines.push(`    input: \${pipeline.input}`)
      }
      if (step.parallel) lines.push(`    parallel: true`)
      if (step.condition) lines.push(`    condition: "${step.condition}"`)
      lines.push('')
    })
    onYamlGenerate(lines.join('\n'))
  }

  const categories = Array.from(new Set(ANALYSIS_TYPES.map((t) => t.category)))

  return (
    <div>
      <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
        파이프라인 단계 ({steps.length}개)
      </div>

      {/* 단계 목록 */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginBottom: '10px' }}>
        {steps.map((step, idx) => {
          const type = ANALYSIS_TYPES.find((t) => t.id === step.analysis)
          return (
            <div key={step.id} style={{
              display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 12px',
              background: 'var(--bg-primary)', borderRadius: '8px', border: '1px solid var(--border-color)',
            }}>
              <span style={{ fontSize: '16px' }}>{type?.icon}</span>
              <div style={{ flex: 1 }}>
                <span style={{ fontSize: '13px', fontWeight: 600 }}>{idx + 1}. {step.label}</span>
                <div style={{ display: 'flex', gap: '4px', marginTop: '3px' }}>
                  {step.useContext && idx > 0 && (
                    <span style={tagStyle}>📎 이전 결과 참조</span>
                  )}
                  {step.parallel && <span style={tagStyle}>⚡ 병렬</span>}
                </div>
              </div>
              <div style={{ display: 'flex', gap: '2px' }}>
                {idx > 0 && step.useContext !== undefined && (
                  <button onClick={() => toggleOption(idx, 'useContext')} style={miniBtn} title="이전 결과 참조 토글">
                    📎
                  </button>
                )}
                <button onClick={() => toggleOption(idx, 'parallel')} style={miniBtn} title="병렬 실행 토글">
                  ⚡
                </button>
                {idx > 0 && <button onClick={() => moveStep(idx, -1)} style={miniBtn}><FaArrowUp /></button>}
                {idx < steps.length - 1 && <button onClick={() => moveStep(idx, 1)} style={miniBtn}><FaArrowDown /></button>}
                <button onClick={() => removeStep(idx)} style={{ ...miniBtn, color: 'var(--red)' }}><FaTimes /></button>
              </div>
            </div>
          )
        })}
      </div>

      {/* 단계 추가 버튼 */}
      <button onClick={() => setShowPicker(!showPicker)} style={{
        display: 'flex', alignItems: 'center', gap: '6px', width: '100%', justifyContent: 'center',
        padding: '8px', borderRadius: '8px', fontSize: '13px', cursor: 'pointer',
        border: '2px dashed var(--border-color)', background: 'transparent', color: 'var(--text-muted)',
        transition: 'all 0.15s',
      }}>
        <FaPlus /> 분석 단계 추가
      </button>

      {/* 분석 유형 선택기 */}
      {showPicker && (
        <div style={{ marginTop: '8px', background: 'var(--bg-primary)', border: '1px solid var(--border-color)', borderRadius: '10px', padding: '12px' }}>
          {categories.map((cat) => (
            <div key={cat} style={{ marginBottom: '8px' }}>
              <div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '4px' }}>{cat}</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                {ANALYSIS_TYPES.filter((t) => t.category === cat).map((type) => (
                  <button key={type.id} onClick={() => addStep(type)} style={{
                    display: 'flex', alignItems: 'center', gap: '4px', padding: '5px 10px',
                    borderRadius: '16px', fontSize: '12px', cursor: 'pointer',
                    border: '1px solid var(--border-color)', background: 'var(--bg-secondary)',
                    color: 'var(--text-sub)', transition: 'all 0.15s',
                  }}>
                    {type.icon} {type.label}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const tagStyle: React.CSSProperties = { fontSize: '10px', padding: '1px 6px', borderRadius: '8px', background: 'var(--accent-subtle)', color: 'var(--accent)' }
const miniBtn: React.CSSProperties = { background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', padding: '2px', fontSize: '11px' }
