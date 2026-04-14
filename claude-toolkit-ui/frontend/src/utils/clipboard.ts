/**
 * 안전한 클립보드 복사 — secure context(HTTPS/localhost) 가 아닐 때
 * navigator.clipboard 가 undefined 이므로 execCommand fallback 으로
 * textarea 를 통한 복사를 수행한다.
 *
 * 사내망 HTTP IP 접속(예: http://192.168.x.x:8027) 환경에서도 동작.
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  // 1) 최신 API — secure context 에서만 동작
  if (typeof navigator !== 'undefined' && navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // fallback 으로 진행
    }
  }

  // 2) 레거시 fallback — execCommand('copy')
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    // 화면 밖 위치 + 키보드 포커스 방지
    ta.style.position = 'fixed'
    ta.style.top = '0'
    ta.style.left = '0'
    ta.style.width = '1px'
    ta.style.height = '1px'
    ta.style.padding = '0'
    ta.style.border = 'none'
    ta.style.outline = 'none'
    ta.style.boxShadow = 'none'
    ta.style.background = 'transparent'
    ta.setAttribute('readonly', '')
    document.body.appendChild(ta)
    ta.select()
    ta.setSelectionRange(0, text.length)
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}

/**
 * 마크다운 문자열을 받아 새 창에서 인쇄(저장) 다이얼로그를 띄운다.
 * 사용자가 "PDF 로 저장" 을 선택하면 PDF 가 생성된다.
 * 외부 라이브러리 없이 브라우저 기본 인쇄 엔진만 사용.
 */
export function printAsHtml(htmlBody: string, title: string): void {
  const win = window.open('', '_blank', 'width=820,height=900')
  if (!win) return
  win.document.write(`<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>${escapeHtml(title)}</title>
<style>
  @page { size: A4; margin: 18mm; }
  body { font-family: -apple-system, "Segoe UI", "Malgun Gothic", "Apple SD Gothic Neo", sans-serif; line-height: 1.65; color: #1f2937; max-width: 760px; margin: 0 auto; padding: 24px; font-size: 12.5pt; }
  h1, h2, h3, h4 { color: #111827; margin-top: 1.4em; margin-bottom: 0.6em; }
  h1 { font-size: 22pt; border-bottom: 2px solid #e5e7eb; padding-bottom: 8px; }
  h2 { font-size: 17pt; border-bottom: 1px solid #e5e7eb; padding-bottom: 4px; }
  h3 { font-size: 14pt; }
  pre { background: #f3f4f6; border: 1px solid #e5e7eb; border-radius: 6px; padding: 12px; overflow-x: auto; font-size: 10.5pt; line-height: 1.45; page-break-inside: avoid; }
  code { background: #f3f4f6; padding: 1px 5px; border-radius: 4px; font-family: Consolas, Monaco, monospace; font-size: 10.5pt; }
  pre code { background: transparent; padding: 0; }
  table { border-collapse: collapse; width: 100%; margin: 12px 0; }
  th, td { border: 1px solid #d1d5db; padding: 6px 10px; text-align: left; }
  th { background: #f9fafb; font-weight: 600; }
  blockquote { border-left: 3px solid #d1d5db; margin-left: 0; padding-left: 12px; color: #6b7280; }
  hr { border: none; border-top: 1px solid #e5e7eb; margin: 24px 0; }
  ul, ol { padding-left: 22px; }
  .meta { color: #6b7280; font-size: 10pt; margin-bottom: 18px; }
  @media print { body { padding: 0; } }
</style>
</head>
<body>
<div class="meta">생성일: ${new Date().toLocaleString('ko-KR')}</div>
${htmlBody}
<script>
  window.addEventListener('load', function() {
    setTimeout(function() { window.print(); }, 200);
  });
</script>
</body>
</html>`)
  win.document.close()
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * 마크다운 문자열을 매우 단순한 HTML 로 변환 (PDF 인쇄용).
 * react-markdown 을 print 컨텍스트에서 사용하기 어려워 별도 변환기 사용.
 * 코드 블록, 헤더, 목록, 강조, 인라인 코드, 테이블, 수평선 정도만 지원.
 */
export function markdownToHtml(md: string): string {
  if (!md) return ''
  let html = escapeHtml(md)

  // 코드 블록 ``` ... ```
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_m, _lang, code) => {
    return `<pre><code>${code}</code></pre>`
  })

  // 헤더 (### → h3, ## → h2, # → h1)
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>')

  // 수평선
  html = html.replace(/^---+$/gm, '<hr/>')

  // 인라인 코드 `code`
  html = html.replace(/`([^`\n]+)`/g, '<code>$1</code>')

  // 굵게 **text**
  html = html.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')

  // 기울임 *text*
  html = html.replace(/(?<![*])\*([^*\n]+)\*(?![*])/g, '<em>$1</em>')

  // 목록 - item / * item / 1. item — 단순 처리
  html = html.replace(/^[-*] (.+)$/gm, '<li>$1</li>')
  html = html.replace(/(<li>[\s\S]*?<\/li>)(\n(?!<li>))/g, '<ul>$1</ul>$2')

  // 빈 줄 → 단락
  html = html.split(/\n{2,}/).map((para) => {
    if (para.match(/^<(h\d|pre|ul|ol|hr|blockquote|table)/)) return para
    return '<p>' + para.replace(/\n/g, '<br/>') + '</p>'
  }).join('\n')

  return html
}
