import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react'

/**
 * v4.2.7 — 댓글용 @멘션 자동완성 입력.
 *
 * 사용자가 `@` 를 입력하면 커서 바로 뒤에 활성 토큰을 추적하여
 * 후보 사용자 목록을 드롭다운으로 표시한다. ArrowUp/Down 으로 이동,
 * Enter/Tab 으로 선택해 `@username ` 로 치환한다. 드롭다운이 열려있지
 * 않을 때의 Enter 는 `onEnter()` 콜백을 통해 부모(댓글 작성 handler)로
 * 전달된다.
 */
export interface MentionCandidate {
  username: string
  displayName: string
  role: string
}

interface Props {
  value: string
  onChange: (next: string) => void
  candidates: MentionCandidate[]
  onEnter?: () => void
  placeholder?: string
  disabled?: boolean
  style?: CSSProperties
  autoFocus?: boolean
}

const ROLE_COLORS: Record<string, string> = {
  ADMIN:    '#ef4444',
  REVIEWER: '#8b5cf6',
  VIEWER:   '#3b82f6',
}

export default function MentionInput({
  value, onChange, candidates, onEnter, placeholder, disabled, style, autoFocus,
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [open, setOpen] = useState(false)
  // 드롭다운에 사용되는 활성 토큰 시작 위치 (value 문자열 상의 `@` 인덱스)
  const [atIndex, setAtIndex] = useState<number>(-1)
  const [query, setQuery] = useState('')
  const [highlight, setHighlight] = useState(0)

  // 필터된 후보 (최대 8개)
  const filtered = (() => {
    const q = query.toLowerCase()
    const list = q
      ? candidates.filter((c) =>
          c.username.toLowerCase().includes(q) || c.displayName.toLowerCase().includes(q))
      : candidates
    return list.slice(0, 8)
  })()

  // filtered 길이가 바뀌면 highlight 를 클램프
  useEffect(() => {
    if (highlight >= filtered.length) setHighlight(Math.max(0, filtered.length - 1))
  }, [filtered.length, highlight])

  // value 가 외부에서 초기화되면 드롭다운 닫기
  useEffect(() => {
    if (!value) { setOpen(false); setAtIndex(-1); setQuery('') }
  }, [value])

  /**
   * 현재 커서 위치를 기준으로 `@` 멘션 토큰이 진행 중인지 검사.
   * 진행 중이면 {atIndex, query} 반환, 아니면 null.
   * - `@` 이전 문자는 공백이거나 문자열 시작이어야 함
   * - 토큰 문자는 영숫자/언더스코어/점/하이픈만 허용 (공백이나 특수문자가 들어오면 종료)
   */
  const detectMention = (text: string, caret: number): { atIndex: number; query: string } | null => {
    if (caret <= 0) return null
    // 커서 뒤에서 앞으로 거슬러 올라가며 `@` 를 찾는다
    let i = caret - 1
    while (i >= 0) {
      const ch = text.charAt(i)
      if (ch === '@') {
        // `@` 앞 문자는 공백/개행이거나 문자열 시작
        if (i === 0 || /\s/.test(text.charAt(i - 1))) {
          const q = text.substring(i + 1, caret)
          // 토큰에 허용되지 않는 문자가 섞이면 mention 아님
          if (!/^[A-Za-z0-9_.\-]*$/.test(q)) return null
          return { atIndex: i, query: q }
        }
        return null
      }
      // 유효한 토큰 문자가 아니면 종료
      if (!/[A-Za-z0-9_.\-]/.test(ch)) return null
      i -= 1
    }
    return null
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const text  = e.target.value
    const caret = e.target.selectionStart ?? text.length
    onChange(text)
    const m = detectMention(text, caret)
    if (m) {
      setOpen(true)
      setAtIndex(m.atIndex)
      setQuery(m.query)
      setHighlight(0)
    } else {
      setOpen(false)
      setAtIndex(-1)
      setQuery('')
    }
  }

  /** 선택된 후보를 value 에 삽입하고 드롭다운 닫기 */
  const insertMention = (cand: MentionCandidate) => {
    if (atIndex < 0) return
    const before = value.substring(0, atIndex)
    const afterStart = atIndex + 1 + query.length
    const after  = value.substring(afterStart)
    const needsSpace = !(after.startsWith(' ') || after.startsWith('\n'))
    const insert = '@' + cand.username + (needsSpace ? ' ' : '')
    const next   = before + insert + after
    onChange(next)
    setOpen(false); setAtIndex(-1); setQuery('')
    // 커서를 삽입한 텍스트 끝으로 이동
    const newCaret = (before + insert).length
    requestAnimationFrame(() => {
      if (inputRef.current) {
        inputRef.current.focus()
        try { inputRef.current.setSelectionRange(newCaret, newCaret) } catch { /* ignore */ }
      }
    })
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (open && filtered.length > 0) {
      if (e.key === 'ArrowDown') { e.preventDefault(); setHighlight((h) => (h + 1) % filtered.length); return }
      if (e.key === 'ArrowUp')   { e.preventDefault(); setHighlight((h) => (h - 1 + filtered.length) % filtered.length); return }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault()
        insertMention(filtered[highlight])
        return
      }
      if (e.key === 'Escape') { e.preventDefault(); setOpen(false); return }
    }
    // 드롭다운 비활성 상태에서의 Enter 는 외부 핸들러로 (댓글 전송)
    if (e.key === 'Enter' && !open && onEnter) { onEnter() }
  }

  return (
    <div style={{ position: 'relative', flex: 1, minWidth: 0 }}>
      <input
        ref={inputRef}
        value={value}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder={placeholder}
        disabled={disabled}
        autoFocus={autoFocus}
        style={{ width: '100%', ...(style || {}) }}
      />
      {open && filtered.length > 0 && (
        <div
          style={{
            position: 'absolute',
            bottom: '100%',
            left: 0,
            marginBottom: '4px',
            minWidth: '220px',
            maxWidth: '320px',
            maxHeight: '200px',
            overflowY: 'auto',
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-color)',
            borderRadius: '8px',
            boxShadow: '0 6px 20px rgba(0,0,0,0.25)',
            zIndex: 50,
            padding: '4px',
          }}
          // 마우스 다운으로는 blur 가 먼저 발생하므로 무시
          onMouseDown={(e) => e.preventDefault()}
        >
          {filtered.map((c, idx) => {
            const active = idx === highlight
            const roleColor = ROLE_COLORS[c.role] || '#94a3b8'
            return (
              <div
                key={c.username}
                onMouseEnter={() => setHighlight(idx)}
                onClick={() => insertMention(c)}
                style={{
                  display: 'flex', alignItems: 'center', gap: '8px',
                  padding: '6px 10px', borderRadius: '6px', cursor: 'pointer',
                  background: active ? 'var(--accent-subtle)' : 'transparent',
                }}
              >
                <div style={{
                  width: '24px', height: '24px', borderRadius: '50%',
                  background: 'var(--bg-primary)', color: roleColor,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '10px', fontWeight: 700, flexShrink: 0,
                  border: `1px solid ${roleColor}`,
                }}>
                  {c.username.substring(0, 2).toUpperCase()}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    @{c.username}
                  </div>
                  {c.displayName !== c.username && (
                    <div style={{ fontSize: '10px', color: 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {c.displayName}
                    </div>
                  )}
                </div>
                <span style={{
                  fontSize: '9px', padding: '1px 5px', borderRadius: '3px',
                  color: roleColor, border: `1px solid ${roleColor}`,
                  flexShrink: 0,
                }}>
                  {c.role}
                </span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
