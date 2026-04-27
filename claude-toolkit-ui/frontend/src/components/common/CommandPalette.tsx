import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FaSearch } from 'react-icons/fa'
import { quickLinks, menuSections, footerItems, type MenuItem } from '../layout/sidebarMenus'

/**
 * v4.2.8 — Cmd/Ctrl+K 커맨드 팔레트.
 *
 * <p>어디서든 `Cmd+K` (mac) / `Ctrl+K` (win) 로 열어 전 기능/페이지를 즉시 네비게이션할 수 있다.
 * 사이드바의 `sidebarMenus` 를 유일한 source of truth 로 쓰므로 메뉴가 추가되면 자동으로 반영된다.
 *
 * <p>조작:
 * <ul>
 *   <li>↑ / ↓ — 항목 이동</li>
 *   <li>Enter — 선택한 페이지로 이동</li>
 *   <li>Esc — 닫기</li>
 *   <li>배경 클릭 — 닫기</li>
 * </ul>
 *
 * <p>최근 방문한 항목은 `localStorage` 에 저장되어 다음 오픈시 상단에 표시된다.
 */

const RECENT_KEY = 'cmdk-recent'

function getRecentPaths(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.slice(0, 5) : []
  } catch { return [] }
}

function addRecentPath(path: string) {
  try {
    const current = getRecentPaths().filter((p) => p !== path)
    current.unshift(path)
    localStorage.setItem(RECENT_KEY, JSON.stringify(current.slice(0, 5)))
  } catch { /* ignore */ }
}

interface CommandItem {
  label:    string
  path:     string
  section:  string
  icon:     MenuItem['icon']
  color?:   string
}

/** 모든 메뉴를 flat 하게 CommandItem[] 으로 변환 */
function getAllCommands(): CommandItem[] {
  const out: CommandItem[] = []
  quickLinks.forEach((m) => out.push({ label: m.label, path: m.path, section: '빠른 이동', icon: m.icon, color: m.color }))
  menuSections.forEach((section) => {
    section.items.forEach((m) => out.push({ label: m.label, path: m.path, section: section.label, icon: m.icon, color: m.color }))
  })
  footerItems.forEach((m) => out.push({ label: m.label, path: m.path, section: '기타', icon: m.icon, color: m.color }))
  return out
}

/** 퍼지 매칭 — label / section / path 셋 중 어디라도 부분 일치하면 true (대소문자 무시) */
function fuzzyMatch(query: string, label: string, section: string, path: string): boolean {
  if (!query) return true
  const q = query.toLowerCase()
  return label.toLowerCase().includes(q)
      || section.toLowerCase().includes(q)
      || path.toLowerCase().includes(q)
}

export default function CommandPalette() {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [selectedIdx, setSelectedIdx] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()

  // 100ms 디바운스 — 연속 입력 중 불필요한 필터 재계산 방지
  useEffect(() => {
    const id = setTimeout(() => setDebouncedQuery(query), 100)
    return () => clearTimeout(id)
  }, [query])

  // 전역 단축키 — Cmd+K / Ctrl+K 로 열기
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault()
        setOpen((prev) => !prev)
        return
      }
      if (e.key === 'Escape' && open) {
        setOpen(false)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  // 열리면 포커스
  useEffect(() => {
    if (open) {
      setQuery('')
      setSelectedIdx(0)
      setTimeout(() => inputRef.current?.focus(), 0)
    }
  }, [open])

  const allCommands = useMemo(() => getAllCommands(), [])

  const filtered = useMemo(() => {
    const recentPaths = getRecentPaths()
    if (!debouncedQuery) {
      // 최근 항목 상단, 나머지는 원래 순서
      const recent = recentPaths
        .map((p) => allCommands.find((c) => c.path === p))
        .filter(Boolean) as CommandItem[]
      const rest = allCommands.filter((c) => !recentPaths.includes(c.path))
      return [...recent.map((c) => ({ ...c, section: '최근 방문' })), ...rest]
    }
    return allCommands.filter((c) => fuzzyMatch(debouncedQuery, c.label, c.section, c.path))
  }, [debouncedQuery, allCommands])

  // selectedIdx 가 filtered 길이 벗어나면 리셋
  useEffect(() => {
    if (selectedIdx >= filtered.length) setSelectedIdx(Math.max(0, filtered.length - 1))
  }, [filtered.length, selectedIdx])

  const onSelect = (cmd: CommandItem) => {
    addRecentPath(cmd.path)
    setOpen(false)
    navigate(cmd.path)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setSelectedIdx((i) => Math.min(i + 1, filtered.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setSelectedIdx((i) => Math.max(i - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const cmd = filtered[selectedIdx]
      if (cmd) onSelect(cmd)
    }
  }

  if (!open) return null

  // 섹션별 그룹화
  const grouped: Array<{ section: string; items: CommandItem[] }> = []
  filtered.forEach((c) => {
    const last = grouped[grouped.length - 1]
    if (last && last.section === c.section) last.items.push(c)
    else grouped.push({ section: c.section, items: [c] })
  })

  return (
    <div
      className="modal-overlay"
      onClick={() => setOpen(false)}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
        display: 'flex', alignItems: 'flex-start', justifyContent: 'center',
        paddingTop: '12vh', zIndex: 2000,
      }}
    >
      <div
        className="modal-body"
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
          borderRadius: '12px', width: 'min(640px, 92vw)', maxHeight: '70vh',
          display: 'flex', flexDirection: 'column', overflow: 'hidden',
          boxShadow: '0 20px 60px rgba(0,0,0,0.5)',
        }}
      >
        {/* 검색 입력 */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: '10px',
          padding: '14px 16px', borderBottom: '1px solid var(--border-color)',
        }}>
          <FaSearch style={{ color: 'var(--text-muted)' }} />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="메뉴/페이지 검색..."
            style={{
              flex: 1, background: 'transparent', border: 'none', outline: 'none',
              fontSize: '15px', color: 'var(--text-primary)',
            }}
          />
          <kbd style={kbdStyle}>ESC</kbd>
        </div>

        {/* 결과 목록 */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '6px' }}>
          {filtered.length === 0 ? (
            <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
              일치하는 메뉴가 없습니다.
            </div>
          ) : (
            grouped.map((g, gi) => (
              <div key={`${g.section}-${gi}`}>
                <div style={{
                  padding: '6px 12px', fontSize: '10px', fontWeight: 700,
                  textTransform: 'uppercase', letterSpacing: '0.06em',
                  color: 'var(--text-muted)',
                }}>
                  {g.section}
                </div>
                {g.items.map((cmd) => {
                  const flatIdx = filtered.indexOf(cmd)
                  const isActive = flatIdx === selectedIdx
                  const Icon = cmd.icon
                  return (
                    <div
                      key={cmd.path + '-' + flatIdx}
                      onClick={() => onSelect(cmd)}
                      onMouseEnter={() => setSelectedIdx(flatIdx)}
                      style={{
                        display: 'flex', alignItems: 'center', gap: '12px',
                        padding: '9px 12px', borderRadius: '8px',
                        background: isActive ? 'var(--accent-subtle)' : 'transparent',
                        cursor: 'pointer',
                        fontSize: '13px',
                        color: isActive ? 'var(--accent)' : 'var(--text-primary)',
                      }}>
                      <Icon style={{ fontSize: '14px', color: cmd.color || 'var(--text-muted)' }} />
                      <span style={{ flex: 1 }}>{cmd.label}</span>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{cmd.path}</span>
                    </div>
                  )
                })}
              </div>
            ))
          )}
        </div>

        {/* 푸터 힌트 */}
        <div style={{
          padding: '8px 16px', borderTop: '1px solid var(--border-color)',
          display: 'flex', gap: '14px', fontSize: '10px', color: 'var(--text-muted)',
          background: 'var(--bg-primary)',
        }}>
          <span><kbd style={kbdStyle}>↑↓</kbd> 이동</span>
          <span><kbd style={kbdStyle}>Enter</kbd> 선택</span>
          <span><kbd style={kbdStyle}>Esc</kbd> 닫기</span>
          <span style={{ marginLeft: 'auto' }}><kbd style={kbdStyle}>⌘K</kbd> / <kbd style={kbdStyle}>Ctrl+K</kbd> 열기</span>
        </div>
      </div>
    </div>
  )
}

const kbdStyle: React.CSSProperties = {
  display: 'inline-block',
  padding: '2px 6px', borderRadius: '4px',
  background: 'var(--bg-primary)',
  border: '1px solid var(--border-color)',
  fontFamily: 'monospace', fontSize: '10px',
  color: 'var(--text-sub)',
}
