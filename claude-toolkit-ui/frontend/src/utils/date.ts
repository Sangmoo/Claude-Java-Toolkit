/**
 * v4.2.7 — 날짜 포맷 공통 유틸.
 *
 * 기존에 HistoryPage / ReviewRequestsPage 각각에 `function formatDate(s)` 가
 * 복붙돼 있어 변경시 불일치가 발생할 위험이 있었다. 이곳으로 통합.
 *
 * 두 가지 제공:
 *  - {@link formatDate}   — 2자리 년/월/일 + 시/분 (기본 절대 포맷)
 *  - {@link formatRelative} — "방금", "5분 전" 같은 상대 표현 (후속 UX 개선용)
 */

/** yy-mm-dd hh:mm (ko-KR). 입력이 비어있거나 파싱 실패시 빈 문자열/원본 반환. */
export function formatDate(s?: string | null): string {
  if (!s) return ''
  try {
    const d = new Date(s)
    if (isNaN(d.getTime())) return String(s)
    return d.toLocaleString('ko-KR', {
      year:   '2-digit',
      month:  '2-digit',
      day:    '2-digit',
      hour:   '2-digit',
      minute: '2-digit',
    })
  } catch {
    return String(s)
  }
}

/**
 * 상대 시간 표현: "방금", "N분 전", "N시간 전", "어제", "N일 전", 이후엔 절대 날짜.
 * 미래 시각은 그냥 절대 날짜로 폴백.
 */
export function formatRelative(s?: string | null): string {
  if (!s) return ''
  try {
    const d = new Date(s)
    if (isNaN(d.getTime())) return String(s)
    const diffMs = Date.now() - d.getTime()
    if (diffMs < 0)                    return formatDate(s)   // 미래 — 절대 표기
    const sec = Math.floor(diffMs / 1000)
    if (sec < 45)                      return '방금'
    const min = Math.floor(sec / 60)
    if (min < 60)                      return `${min}분 전`
    const hour = Math.floor(min / 60)
    if (hour < 24)                     return `${hour}시간 전`
    const day = Math.floor(hour / 24)
    if (day === 1)                     return '어제'
    if (day < 7)                       return `${day}일 전`
    return formatDate(s)
  } catch {
    return String(s)
  }
}
