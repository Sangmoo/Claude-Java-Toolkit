import { useEffect, useRef, useState } from 'react'
import mermaid from 'mermaid'
import { useThemeStore } from '../../stores/themeStore'

interface MermaidChartProps {
  chart: string
  className?: string
}

let mermaidInitialized = false

export default function MermaidChart({ chart, className }: MermaidChartProps) {
  const ref = useRef<HTMLDivElement>(null)
  const [svg, setSvg] = useState('')
  const [error, setError] = useState<string | null>(null)
  const theme = useThemeStore((s) => s.theme)

  useEffect(() => {
    if (!mermaidInitialized) {
      mermaid.initialize({
        startOnLoad: false,
        theme: theme === 'dark' ? 'dark' : 'default',
        securityLevel: 'loose',
        flowchart: { htmlLabels: true, curve: 'basis' },
      })
      mermaidInitialized = true
    }
  }, [theme])

  useEffect(() => {
    if (!chart.trim()) {
      setSvg('')
      setError(null)
      return
    }

    const render = async () => {
      try {
        const id = `mermaid-${Date.now()}`
        const { svg: rendered } = await mermaid.render(id, chart.trim())
        setSvg(rendered)
        setError(null)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Mermaid 렌더링 실패')
        setSvg('')
      }
    }
    render()
  }, [chart])

  if (error) {
    return (
      <div style={{
        padding: '12px', borderRadius: '8px',
        background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)',
        fontSize: '12px', color: 'var(--red)',
      }}>
        플로우차트 오류: {error}
      </div>
    )
  }

  if (!svg) {
    return (
      <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
        YAML을 입력하면 플로우차트가 표시됩니다.
      </div>
    )
  }

  return (
    <div
      ref={ref}
      className={className}
      dangerouslySetInnerHTML={{ __html: svg }}
      style={{
        display: 'flex', justifyContent: 'center', padding: '16px',
        overflow: 'auto', maxHeight: '400px',
      }}
    />
  )
}
