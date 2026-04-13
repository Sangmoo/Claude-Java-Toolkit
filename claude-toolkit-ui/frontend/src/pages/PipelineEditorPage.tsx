import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import Editor from '@monaco-editor/react'
import MermaidChart from '../components/common/MermaidChart'
import {
  FaSave, FaCheckCircle, FaProjectDiagram, FaTrash,
} from 'react-icons/fa'
import { useThemeStore } from '../stores/themeStore'
import { useToast } from '../hooks/useToast'

interface PipelineData {
  id?: number
  name: string
  description: string
  yamlContent: string
  inputLanguage: string
  isBuiltin: boolean
}

const DEFAULT_YAML = `id: full-stack-review
name: 풀 스택 코드 리뷰
description: 코드 리뷰 → 리팩터링 → 테스트 생성 → 문서화 파이프라인
inputLanguage: java

steps:
  # 1단계: 코드 리뷰 (입력 코드를 분석)
  - id: review
    analysis: CODE_REVIEW
    input: \${pipeline.input}

  # 2단계: 리팩터링 (리뷰 결과를 참고하여 개선)
  - id: refactor
    analysis: REFACTOR
    input: \${pipeline.input}
    context: \${review.output}

  # 3단계: 테스트 생성 (리팩터링된 코드 기반)
  - id: test
    analysis: TEST_GEN
    input: \${refactor.output}
    context: \${review.output}

  # 4단계: 보안 감사 (조건부 — 리뷰에서 보안 이슈 발견 시)
  - id: security
    analysis: CODE_REVIEW_SECURITY
    input: \${pipeline.input}
    condition: "\${review.output}.contains('보안') || \${review.output}.contains('security')"

  # 5단계: Javadoc 생성 (병렬 실행 가능)
  - id: javadoc
    analysis: JAVADOC_GEN
    input: \${refactor.output}
    parallel: true

  # 6단계: 기술 문서 생성 (javadoc 완료 대기)
  - id: doc
    analysis: DOC_GEN
    input: \${refactor.output}
    dependsOn: "javadoc"
`

/** YAML → Mermaid flowchart 변환 */
function yamlToMermaid(yaml: string): string {
  try {
    const condRegex = /condition:/
    const steps: { id: string; analysis: string; hasCondition: boolean }[] = []

    const lines = yaml.split('\n')
    let currentStep: { id: string; analysis: string; hasCondition: boolean } | null = null

    for (const line of lines) {
      const idMatch = line.match(/^\s*- id:\s*(\S+)/)
      if (idMatch) {
        if (currentStep) steps.push(currentStep)
        currentStep = { id: idMatch[1], analysis: '', hasCondition: false }
      }
      if (currentStep) {
        const analysisMatch = line.match(/^\s*analysis:\s*(\S+)/)
        if (analysisMatch) currentStep.analysis = analysisMatch[1]
        if (condRegex.test(line)) currentStep.hasCondition = true
      }
    }
    if (currentStep) steps.push(currentStep)

    if (steps.length === 0) return ''

    let mmd = 'graph LR\n'
    steps.forEach((s, i) => {
      const shape = s.hasCondition ? `{${s.id}\\n${s.analysis}}` : `[${s.id}\\n${s.analysis}]`
      mmd += `  ${s.id}${shape}\n`
      if (i > 0) {
        mmd += `  ${steps[i - 1].id} --> ${s.id}\n`
      }
    })
    return mmd
  } catch {
    return ''
  }
}

export default function PipelineEditorPage() {
  const { id } = useParams<{ id: string }>()
  const isNew = id === 'new'
  const [data, setData] = useState<PipelineData>({
    name: '', description: '', yamlContent: DEFAULT_YAML, inputLanguage: 'java', isBuiltin: false,
  })
  const [mermaidCode, setMermaidCode] = useState('')
  const [validating, setValidating] = useState(false)
  const [validResult, setValidResult] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const theme = useThemeStore((s) => s.theme)
  const toast = useToast()
  const navigate = useNavigate()
  const debounceRef = useRef<ReturnType<typeof setTimeout>>()

  // Load existing pipeline
  useEffect(() => {
    if (!isNew && id) {
      fetch(`/api/v1/pipelines`, { credentials: 'include' })
        .then((r) => r.json())
        .then((json) => {
          const list = json.data ?? json
          const found = (Array.isArray(list) ? list : []).find((p: PipelineData) => String(p.id) === id)
          if (found) {
            setData(found)
            setMermaidCode(yamlToMermaid(found.yamlContent || ''))
          }
        })
        .catch(() => toast.error('파이프라인을 불러올 수 없습니다.'))
    }
  }, [id, isNew, toast])

  const onYamlChange = useCallback((value: string | undefined) => {
    const yaml = value ?? ''
    setData((d) => ({ ...d, yamlContent: yaml }))
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      setMermaidCode(yamlToMermaid(yaml))
    }, 500)
  }, [])

  const validate = async () => {
    setValidating(true)
    setValidResult(null)
    try {
      const body = new URLSearchParams({ yamlContent: data.yamlContent })
      const res = await fetch(`/pipelines/${id || 0}/validate`, {
        method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body, credentials: 'include',
      })
      const json = await res.json()
      if (json.valid || json.success) {
        setValidResult(`유효함 — ${json.stepCount || '?'}개 단계`)
        toast.success('YAML 유효성 검증 통과')
      } else {
        setValidResult(`오류: ${json.error || json.message || '유효하지 않은 YAML'}`)
        toast.error('YAML 유효성 검증 실패')
      }
    } catch {
      setValidResult('검증 요청 실패')
    }
    setValidating(false)
  }

  const save = async () => {
    setSaving(true)
    try {
      const endpoint = isNew ? '/pipelines' : `/pipelines/${id}/save`
      const body = new URLSearchParams({
        name: data.name,
        description: data.description,
        yamlContent: data.yamlContent,
        inputLanguage: data.inputLanguage,
      })
      const res = await fetch(endpoint, {
        method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body, credentials: 'include',
      })
      if (res.ok) {
        toast.success('파이프라인이 저장되었습니다.')
        if (isNew) navigate('/pipelines')
      } else {
        toast.error('저장 실패')
      }
    } catch {
      toast.error('저장 중 오류')
    }
    setSaving(false)
  }

  const deletePipeline = async () => {
    if (!confirm('정말 삭제하시겠습니까?')) return
    await fetch(`/pipelines/${id}/delete`, { method: 'POST', credentials: 'include' })
    toast.success('삭제되었습니다.')
    navigate('/pipelines')
  }

  return (
    <>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px', flexWrap: 'wrap' }}>
        <FaProjectDiagram style={{ fontSize: '20px', color: 'var(--purple)' }} />
        <input
          value={data.name}
          onChange={(e) => setData({ ...data, name: e.target.value })}
          placeholder="파이프라인 이름"
          disabled={data.isBuiltin}
          style={{ fontSize: '18px', fontWeight: 700, flex: 1, minWidth: '200px', background: 'transparent', border: 'none', borderBottom: '1px solid var(--border-color)', padding: '4px 0', color: 'var(--text-primary)' }}
        />
        <div style={{ display: 'flex', gap: '6px' }}>
          <button onClick={validate} disabled={validating} style={btnStyle}>
            <FaCheckCircle /> {validating ? '검증 중...' : '검증'}
          </button>
          {!data.isBuiltin && (
            <>
              <button onClick={save} disabled={saving} style={{ ...btnStyle, background: 'var(--accent)', color: '#fff', border: 'none' }}>
                <FaSave /> {saving ? '저장 중...' : '저장'}
              </button>
              {!isNew && (
                <button onClick={deletePipeline} style={{ ...btnStyle, color: 'var(--red)' }}>
                  <FaTrash /> 삭제
                </button>
              )}
            </>
          )}
        </div>
      </div>

      {validResult && (
        <div style={{ marginBottom: '12px', padding: '8px 14px', borderRadius: '8px', fontSize: '13px', background: validResult.startsWith('유효') ? 'rgba(34,197,94,0.1)' : 'rgba(239,68,68,0.1)', color: validResult.startsWith('유효') ? 'var(--green)' : 'var(--red)', border: `1px solid ${validResult.startsWith('유효') ? 'rgba(34,197,94,0.2)' : 'rgba(239,68,68,0.2)'}` }}>
          {validResult}
        </div>
      )}

      {/* Description & Language */}
      <div style={{ display: 'flex', gap: '12px', marginBottom: '12px', flexWrap: 'wrap' }}>
        <input
          value={data.description}
          onChange={(e) => setData({ ...data, description: e.target.value })}
          placeholder="파이프라인 설명"
          disabled={data.isBuiltin}
          style={{ flex: 1, fontSize: '13px' }}
        />
        <select
          value={data.inputLanguage}
          onChange={(e) => setData({ ...data, inputLanguage: e.target.value })}
          disabled={data.isBuiltin}
          style={{ fontSize: '13px', padding: '6px 10px' }}
        >
          <option value="java">Java</option>
          <option value="sql">SQL</option>
          <option value="kotlin">Kotlin</option>
          <option value="python">Python</option>
        </select>
      </div>

      {/* Editor + Mermaid */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', height: 'calc(100vh - 260px)', minHeight: '400px' }}>
        {/* Monaco Editor */}
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'hidden' }}>
          <div style={{ padding: '8px 14px', borderBottom: '1px solid var(--border-color)', fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>
            YAML 편집기 {data.isBuiltin && '(읽기 전용)'}
          </div>
          <Editor
            height="calc(100% - 36px)"
            language="yaml"
            theme={theme === 'dark' ? 'vs-dark' : 'light'}
            value={data.yamlContent}
            onChange={onYamlChange}
            options={{
              readOnly: data.isBuiltin,
              minimap: { enabled: false },
              fontSize: 13,
              tabSize: 2,
              wordWrap: 'on',
              lineNumbers: 'on',
              scrollBeyondLastLine: false,
              automaticLayout: true,
            }}
          />
        </div>

        {/* Mermaid Preview */}
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', overflow: 'hidden' }}>
          <div style={{ padding: '8px 14px', borderBottom: '1px solid var(--border-color)', fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>
            플로우차트 미리보기
          </div>
          <div style={{ height: 'calc(100% - 36px)', overflow: 'auto', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <MermaidChart chart={mermaidCode} />
          </div>
        </div>
      </div>
    </>
  )
}

const btnStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '6px',
  padding: '6px 14px', borderRadius: '8px', fontSize: '13px',
  border: '1px solid var(--border-color)', background: 'transparent',
  color: 'var(--text-sub)', cursor: 'pointer',
}
