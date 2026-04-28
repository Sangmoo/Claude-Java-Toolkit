import { useEffect, useRef, useState } from 'react'
import { FaRobot, FaSun, FaMoon, FaBell, FaSignOutAlt, FaBars, FaSyncAlt, FaCheckDouble, FaTimes, FaTrash, FaGlobe, FaSearch } from 'react-icons/fa'
import { useThemeStore } from '../../stores/themeStore'
import { useAuthStore } from '../../stores/authStore'
import { useSidebarStore } from '../../stores/sidebarStore'
import { useNotificationStore } from '../../stores/notificationStore'
import { useSessionTimer } from '../../hooks/useSessionTimer'
import { formatDate, formatRelative } from '../../utils/date'
import { useLocation, useNavigate, Link } from 'react-router-dom'
import i18n, { LANGUAGE_OPTIONS, type SupportedLang } from '../../i18n'

const pathMap: Record<string, string> = {
  '': '홈', chat: 'AI 채팅', advisor: 'SQL 리뷰', workspace: '통합 워크스페이스',
  pipelines: '분석 파이프라인', docgen: '기술 문서', history: '리뷰 이력',
  favorites: '즐겨찾기', settings: '설정', admin: '관리', search: '검색',
  harness: '코드 리뷰', erd: 'ERD 분석', complexity: '복잡도', explain: '실행계획',
}

function Breadcrumb() {
  const location = useLocation()
  const parts = location.pathname.replace(/^\//, '').split('/').filter(Boolean)
  if (parts.length === 0) return null
  return (
    <div className="breadcrumb-bar">
      <Link to="/">홈</Link>
      {parts.map((p, i) => (
        <span key={i}>
          <span style={{ margin: '0 4px', color: 'var(--text-muted)' }}>/</span>
          {i === parts.length - 1 ? (
            <span className="bc-current">{pathMap[p] || p}</span>
          ) : (
            <Link to={'/' + parts.slice(0, i + 1).join('/')}>{pathMap[p] || p}</Link>
          )}
        </span>
      ))}
    </div>
  )
}

function NotificationDropdown() {
  const { notifications, unreadCount, dropdownOpen, toggleDropdown, closeDropdown, markRead, markAllRead, deleteNotification, deleteAllNotifications, startSSE } =
    useNotificationStore()
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => { startSSE() }, [startSSE])

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) closeDropdown()
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [closeDropdown])

  return (
    <div className="noti-bell-wrap" ref={dropdownRef}>
      <button className="top-bar-btn" onClick={toggleDropdown} title="알림">
        <FaBell />
      </button>
      {unreadCount > 0 && <span className="noti-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}

      {dropdownOpen && (
        <div className="noti-dropdown">
          <div className="noti-dropdown-header">
            <span>알림</span>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
              {unreadCount > 0 && (
                <button
                  onClick={markAllRead}
                  style={{ background: 'none', border: 'none', color: 'var(--accent)', cursor: 'pointer', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '4px' }}
                >
                  <FaCheckDouble /> 모두 읽음
                </button>
              )}
              {notifications.length > 0 && (
                <button
                  onClick={() => {
                    if (confirm('모든 알림을 삭제하시겠습니까?')) deleteAllNotifications()
                  }}
                  title="전체 삭제"
                  style={{ background: 'none', border: 'none', color: 'var(--red)', cursor: 'pointer', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '4px' }}
                >
                  <FaTrash /> 전체 삭제
                </button>
              )}
            </div>
          </div>
          {notifications.length === 0 ? (
            <div className="noti-empty">새로운 알림이 없습니다.</div>
          ) : (
            notifications.slice(0, 10).map((n) => (
              <div
                key={n.id}
                className={`noti-item${n.isRead ? '' : ' unread'}`}
                onClick={() => { markRead(n.id); if (n.link) window.location.href = n.link }}
                style={{ position: 'relative', paddingRight: '28px' }}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                  {n.title && (
                    <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-primary)', lineHeight: 1.3 }}>
                      {n.title}
                    </div>
                  )}
                  <div style={{ fontSize: '11px', color: 'var(--text-sub)', lineHeight: 1.4, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                    {n.message}
                  </div>
                  <div
                    style={{ fontSize: '10px', color: 'var(--text-muted)', marginTop: '2px' }}
                    title={n.createdAtIso ? formatDate(n.createdAtIso) : n.createdAt}
                  >
                    {n.createdAtIso ? formatRelative(n.createdAtIso) : n.createdAt}
                  </div>
                </div>
                {/* v4.2.7: 알림 삭제 — 클릭 이벤트 전파 방지 */}
                <button
                  type="button"
                  onClick={(e) => { e.stopPropagation(); deleteNotification(n.id) }}
                  title="알림 삭제"
                  style={{
                    position: 'absolute', top: '6px', right: '6px',
                    background: 'none', border: 'none',
                    color: 'var(--text-muted)', cursor: 'pointer',
                    padding: '4px', borderRadius: '4px',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '11px',
                  }}
                  onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.color = 'var(--red)' }}
                  onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.color = 'var(--text-muted)' }}
                >
                  <FaTimes />
                </button>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}

/**
 * 상단 바 가운데 영역의 글로벌 검색.
 * Enter 또는 버튼 클릭 → /search?q=... 로 이동 (SearchPage 가 URL 파라미터로 자동 검색).
 */
function TopBarSearch() {
  const navigate = useNavigate()
  const location = useLocation()
  const [q, setQ] = useState('')

  // /search 경로로 진입 시 URL 의 q 와 동기화
  useEffect(() => {
    if (location.pathname === '/search') {
      const params = new URLSearchParams(location.search)
      setQ(params.get('q') || '')
    }
  }, [location.pathname, location.search])

  const submit = () => {
    const v = q.trim()
    if (!v) return
    navigate(`/search?q=${encodeURIComponent(v)}`)
  }

  return (
    <form
      onSubmit={(e) => { e.preventDefault(); submit() }}
      role="search"
      style={{
        flex: 1, display: 'flex', justifyContent: 'center',
        maxWidth: 560, minWidth: 0, margin: '0 16px',
      }}
    >
      <div style={{
        position: 'relative', display: 'flex', alignItems: 'center',
        width: '100%', maxWidth: 460,
        // 테두리는 Settings 에서 선택한 팔레트 accent 컬러 사용 (테마별 자동 변경)
        background: 'var(--bg-secondary)', border: '1px solid var(--accent)',
        borderRadius: 8, padding: '0 8px',
        boxShadow: '0 0 0 2px var(--accent-subtle)',
      }}>
        <FaSearch style={{ color: 'var(--accent)', fontSize: 12, marginRight: 6, flexShrink: 0 }} />
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="메뉴, 이력 검색…"
          aria-label="전역 검색"
          style={{
            flex: 1, minWidth: 0,
            background: 'transparent', border: 'none', outline: 'none',
            padding: '7px 0', fontSize: 13, color: 'var(--text-primary)',
          }}
        />
        {q && (
          <button
            type="button"
            onClick={() => setQ('')}
            aria-label="검색어 지우기"
            style={{
              background: 'none', border: 'none', cursor: 'pointer',
              color: 'var(--text-muted)', padding: '4px', fontSize: 11,
            }}
          ><FaTimes /></button>
        )}
      </div>
    </form>
  )
}

/** v4.3.0 — 언어 선택 드롭다운 (5개 언어: ko/en/ja/zh/de) */
function LanguageSwitcher() {
  const [open, setOpen] = useState(false)
  const [current, setCurrent] = useState<SupportedLang>(
    (localStorage.getItem('language') || 'ko') as SupportedLang
  )
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const switchTo = (code: SupportedLang) => {
    if (code === current) { setOpen(false); return }
    i18n.changeLanguage(code)
    localStorage.setItem('language', code)
    setCurrent(code)
    setOpen(false)
    // v4.3.0: 백엔드에 사용자 locale 저장 (선택 — 실패 무시)
    fetch('/api/v1/auth/locale', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ locale: code }),
    }).catch(() => {})
    // v4.4.x — 대부분 컴포넌트가 하드코딩 한국어 → useTranslation 미사용.
    //          i18n.changeLanguage 만으론 즉시 변경 안 보이므로 강제 새로고침.
    //          (React 18 + i18next v4 환경에서 가장 안전)
    setTimeout(() => window.location.reload(), 80)
  }

  const currentOpt = LANGUAGE_OPTIONS.find((o) => o.code === current) || LANGUAGE_OPTIONS[0]

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button className="top-bar-btn" onClick={() => setOpen(!open)} title="언어 / Language">
        <FaGlobe />
        <span>{currentOpt.flag} {currentOpt.code.toUpperCase()}</span>
      </button>
      {open && (
        <div style={{
          position: 'absolute', top: '100%', right: 0, marginTop: '6px',
          // v4.4.x — 기존 var(--bg-card) 미정의로 투명하게 보이던 이슈 수정.
          //          theme.css 의 --bg-secondary (어떤 테마에서도 불투명) 사용.
          background: 'var(--bg-secondary)',
          border: '1px solid var(--border-color)',
          borderRadius: '8px', minWidth: '180px', zIndex: 1000,
          boxShadow: '0 8px 24px rgba(0,0,0,0.35)',
          backdropFilter: 'blur(8px)',
          padding: '4px',
          overflow: 'hidden',
        }}>
          {LANGUAGE_OPTIONS.map((opt) => {
            const active = opt.code === current
            return (
              <button key={opt.code}
                onClick={() => switchTo(opt.code)}
                style={{
                  display: 'flex', alignItems: 'center', gap: '10px',
                  width: '100%', padding: '8px 12px', textAlign: 'left',
                  background: active ? 'var(--accent)' : 'transparent',
                  color: active ? '#fff' : 'var(--text-sub, #cbd5e1)',
                  border: 'none', cursor: 'pointer', fontSize: '13px',
                  borderRadius: '6px', fontWeight: active ? 600 : 400,
                  transition: 'background .12s, color .12s',
                }}
                onMouseEnter={(e) => {
                  if (!active) e.currentTarget.style.background = 'var(--bg-primary, rgba(255,255,255,0.06))'
                }}
                onMouseLeave={(e) => {
                  if (!active) e.currentTarget.style.background = 'transparent'
                }}>
                <span style={{ fontSize: '16px' }}>{opt.flag}</span>
                <span style={{ flex: 1 }}>{opt.label}</span>
                {active && <span>✓</span>}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default function TopBar() {
  const { theme, toggleTheme } = useThemeStore()
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const { collapsed, toggleCollapse, toggleMobile } = useSidebarStore()
  const { display, refresh } = useSessionTimer()

  return (
    <div className="top-bar">
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexShrink: 0 }}>
        {collapsed && (
          <button className="top-bar-btn" onClick={toggleCollapse} title="사이드바 열기">
            <FaBars />
          </button>
        )}
        <button
          className="top-bar-btn"
          onClick={toggleMobile}
          style={{ display: 'none' }}
          id="mobileMenuBtn"
        >
          <FaBars />
        </button>
        <span className="top-bar-title">
          <FaRobot style={{ color: 'var(--accent)' }} />
          Claude Java Toolkit
        </span>
        <Breadcrumb />
      </div>

      <TopBarSearch />

      <div className="top-bar-actions">
        <LanguageSwitcher />
        <button className="top-bar-btn" onClick={toggleTheme} title="테마 전환">
          {theme === 'dark' ? <FaSun /> : <FaMoon />}
          <span>{theme === 'dark' ? 'Light' : 'Dark'}</span>
        </button>

        <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
          {display}
        </span>
        <button className="top-bar-btn" onClick={refresh} title="세션 갱신" style={{ padding: '5px 6px' }}>
          <FaSyncAlt />
        </button>

        <NotificationDropdown />

        {user && (
          <>
            <span style={{ fontSize: '13px', color: 'var(--text-sub)' }}>
              {user.username}
            </span>
            <button className="top-bar-btn" onClick={logout} title="로그아웃">
              <FaSignOutAlt />
              <span>로그아웃</span>
            </button>
          </>
        )}
      </div>
    </div>
  )
}
