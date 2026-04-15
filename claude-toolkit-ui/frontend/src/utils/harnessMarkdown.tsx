import type { ReactNode } from 'react'
import type { Components } from 'react-markdown'

/**
 * v4.2.7 — 코드 리뷰 하네스 결과 마크다운 전처리 & 렌더 유틸.
 *
 * <p>기존에는 모두 CodeReviewPage.tsx 안에 인라인으로 정의돼 있었고,
 * 파일이 500+ 줄로 커지면서 유지보수가 어려워졌다. 순수 로직·컴포넌트만
 * 이쪽으로 분리하고 CodeReviewPage 는 상태/스트리밍 orchestration 에 집중.
 *
 * <p>중요: 이 파일에서 JSX 를 사용하므로 `.tsx` 확장자 유지. 기능 변화 없음.
 */

// ── normalizeBuilderSection ────────────────────────────────────────────────
//
// Builder 단계는 백엔드(HarnessReviewService.analyzeStream) 가 ```sql ... ```
// 펜스를 직접 래핑하지만, Claude Builder 가 지시를 어기고 자기도 펜스나 언어
// 라벨을 넣으면 이상하게 렌더된다. 이 함수는 "## 🔧 개선된 코드" 섹션만
// 범위를 좁혀 다음 3가지를 정리한다:
//
//  (a) 안쪽에 끼어든 ```lang / ``` 라인 제거 (중첩 펜스 방지)
//  (b) 펜스 바로 뒤 첫 줄이 언어 이름(sql/java) 만 있는 경우 해당 줄 제거
//      → 실제 증상: Claude 가 프롬프트 예시를 따라 `sql\nCREATE ...` 형태로
//         언어 라벨을 한 줄로 먼저 출력해서 코드 박스 상단에 "sql" 이
//         visible 라인으로 보이던 버그
//  (c) 연속 빈 줄 압축
export function normalizeBuilderSection(text: string): string {
  if (!text) return text
  const headerRe = /(## 🔧 개선된 코드\s*\n)/
  const m = text.match(headerRe)
  if (!m || m.index == null) return text
  const headerEnd = m.index + m[0].length
  // 다음 `## ` 섹션 시작 전까지가 Builder 구간
  const tail = text.substring(headerEnd)
  const nextSec = tail.search(/\n## /)
  const secLen  = nextSec === -1 ? tail.length : nextSec
  const section = tail.substring(0, secLen)
  const rest    = tail.substring(secLen)

  // section 내부 펜스 정리
  const firstOpen = section.match(/```(\w+)?\n/)
  if (!firstOpen || firstOpen.index == null) {
    // 펜스가 아예 없으면 강제로 감싸기 (Claude 가 펜스 없이 순수 코드만 뱉은 경우)
    const trimmed = section.trim()
    if (!trimmed) return text
    return text.substring(0, headerEnd) + '```\n' + trimmed + '\n```\n' + rest
  }
  const openEnd = firstOpen.index + firstOpen[0].length
  // 가장 마지막 `\n```` 를 진짜 닫는 펜스로 간주
  const lastClose = section.lastIndexOf('\n```')
  if (lastClose <= openEnd) return text
  const before  = section.substring(0, openEnd)
  let   inner   = section.substring(openEnd, lastClose)
  const closing = section.substring(lastClose)

  // (a) 안쪽 중첩 ```lang / ``` 라인 제거
  inner = inner.replace(/^```[a-zA-Z]*\s*$/gm, '')

  // (b) 펜스 바로 뒤 첫 의미있는 라인이 언어 이름(sql/java/plsql/oracle/...) 뿐이라면 제거.
  //     빈 줄을 건너뛴 뒤 첫 줄을 검사한다.
  inner = inner.replace(/^(\s*\n)*\s*(sql|java|plsql|oracle|javascript|typescript|python)\s*\n/i, '')

  // (c) 3줄 이상 공백을 2줄로 압축
  inner = inner.replace(/\n{3,}/g, '\n\n')

  const fixed = before + inner + closing
  return text.substring(0, headerEnd) + fixed + rest
}

// ── 판정 배지 컴포넌트 ─────────────────────────────────────────────────────

// React children 을 평문으로 평탄화 (판정 감지용)
function flattenChildren(nodes: ReactNode): string {
  if (nodes == null || typeof nodes === 'boolean') return ''
  if (typeof nodes === 'string') return nodes
  if (typeof nodes === 'number') return String(nodes)
  if (Array.isArray(nodes)) return nodes.map(flattenChildren).join('')
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const anyNode = nodes as any
  if (anyNode?.props?.children) return flattenChildren(anyNode.props.children)
  return ''
}

type VerdictKind = 'VERIFIED' | 'WARNINGS' | 'FAILED'
const VERDICT_COLORS: Record<VerdictKind, { color: string; bg: string; icon: string }> = {
  VERIFIED: { color: '#10b981', bg: 'rgba(16,185,129,0.12)', icon: '✅' },
  WARNINGS: { color: '#f59e0b', bg: 'rgba(245,158,11,0.12)', icon: '⚠️' },
  FAILED:   { color: '#ef4444', bg: 'rgba(239,68,68,0.12)',  icon: '❌' },
}

/**
 * ReactMarkdown 커스텀 컴포넌트: `**판정**: VERIFIED (...)` 문단을 배지 박스로 강조.
 * CodeReviewPage 에서 `components={harnessMdComponents}` 로 주입한다.
 */
export const harnessMdComponents: Components = {
  p: ({ children, ...props }) => {
    const text = flattenChildren(children as ReactNode)
    if (/판정/.test(text)) {
      const m = text.match(/\b(VERIFIED|WARNINGS|FAILED)\b/)
      if (m) {
        const c = VERDICT_COLORS[m[1] as VerdictKind]
        return (
          <div style={{
            padding: '12px 16px',
            background: c.bg,
            border: `2px solid ${c.color}`,
            borderRadius: '10px',
            fontSize: '15px',
            fontWeight: 700,
            color: c.color,
            margin: '14px 0',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            lineHeight: 1.5,
          }}>
            <span style={{ fontSize: '22px' }}>{c.icon}</span>
            <span style={{ flex: 1 }}>{children}</span>
          </div>
        )
      }
    }
    return <p {...props}>{children}</p>
  },
}
