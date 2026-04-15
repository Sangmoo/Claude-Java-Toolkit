import { useState, type ReactNode } from 'react'
import { FaCopy, FaCheck } from 'react-icons/fa'
import { copyToClipboard } from '../../utils/clipboard'

/**
 * v4.2.8 — ReactMarkdown `components.code` 용 교체 컴포넌트.
 *
 * <p>분석 결과 마크다운 안의 `\`\`\`lang ... \`\`\`` 블록 우측 상단에 복사 버튼을
 * 오버레이한다. inline code (`\`foo\``) 는 기존 스타일 유지.
 *
 * <p>사용:
 * <pre>
 *   import { markdownCodeComponents } from '../common/CopyableCodeBlock'
 *   ...
 *   &lt;ReactMarkdown components={markdownCodeComponents}&gt;{text}&lt;/ReactMarkdown&gt;
 * </pre>
 *
 * <p>하네스 `harnessMdComponents` 같은 다른 오버라이드와 합치려면 `{...other, ...markdownCodeComponents}`
 * 로 스프레드.
 */

interface CodeBlockProps {
  inline?:  boolean
  className?: string
  children?:  ReactNode
}

function CodeBlock({ inline, className, children }: CodeBlockProps) {
  const [copied, setCopied] = useState(false)

  // inline code (단일 `` 로 감싼 짧은 코드) 는 기존 스타일 유지
  if (inline) {
    return <code className={className}>{children}</code>
  }

  // block code — 우측 상단에 복사 버튼 오버레이
  const text = extractText(children)
  const lang = (className || '').replace(/^language-/, '') || 'text'

  const handleCopy = async () => {
    const ok = await copyToClipboard(text)
    if (ok) {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  return (
    <div style={{ position: 'relative', margin: '8px 0' }}>
      {/* 언어 라벨 + 복사 버튼 바 */}
      <div style={{
        position: 'absolute', top: '6px', right: '6px',
        display: 'flex', alignItems: 'center', gap: '6px',
        zIndex: 1,
      }}>
        <span style={{
          fontSize: '10px', fontWeight: 600, textTransform: 'uppercase',
          color: 'var(--text-muted)',
          padding: '2px 6px', borderRadius: '4px',
          background: 'rgba(0,0,0,0.3)',
          letterSpacing: '0.05em',
        }}>
          {lang}
        </span>
        <button
          onClick={handleCopy}
          title={copied ? '복사됨' : '클립보드에 복사'}
          style={{
            display: 'flex', alignItems: 'center', gap: '4px',
            padding: '3px 8px', borderRadius: '5px',
            background: copied ? 'rgba(34,197,94,0.2)' : 'rgba(0,0,0,0.4)',
            color: copied ? 'var(--green)' : '#e2e8f0',
            border: '1px solid',
            borderColor: copied ? 'var(--green)' : 'rgba(255,255,255,0.15)',
            cursor: 'pointer',
            fontSize: '10px', fontWeight: 600,
            transition: 'all 0.15s',
          }}
        >
          {copied ? <><FaCheck /> 복사됨</> : <><FaCopy /> 복사</>}
        </button>
      </div>
      <pre style={{ margin: 0 }}><code className={className}>{children}</code></pre>
    </div>
  )
}

/** React children → plain text 추출 (중첩 span 포함) */
function extractText(nodes: ReactNode): string {
  if (nodes == null || typeof nodes === 'boolean') return ''
  if (typeof nodes === 'string') return nodes
  if (typeof nodes === 'number') return String(nodes)
  if (Array.isArray(nodes)) return nodes.map(extractText).join('')
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const anyNode = nodes as any
  if (anyNode?.props?.children) return extractText(anyNode.props.children)
  return ''
}

/**
 * ReactMarkdown 의 `components` prop 에 바로 넣을 수 있는 맵.
 * 다른 오버라이드와 합치려면 스프레드 연산자 사용.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const markdownCodeComponents: any = {
  code: CodeBlock,
}
