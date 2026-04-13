import { useEffect, useRef } from 'react'
import { FaRobot, FaSun, FaMoon, FaBell, FaSignOutAlt, FaBars, FaSyncAlt, FaCheckDouble } from 'react-icons/fa'
import { useThemeStore } from '../../stores/themeStore'
import { useAuthStore } from '../../stores/authStore'
import { useSidebarStore } from '../../stores/sidebarStore'
import { useNotificationStore } from '../../stores/notificationStore'
import { useSessionTimer } from '../../hooks/useSessionTimer'
import { useLocation, Link } from 'react-router-dom'

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
  const { notifications, unreadCount, dropdownOpen, toggleDropdown, closeDropdown, markRead, markAllRead, startSSE } =
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
            {unreadCount > 0 && (
              <button
                onClick={markAllRead}
                style={{ background: 'none', border: 'none', color: 'var(--accent)', cursor: 'pointer', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '4px' }}
              >
                <FaCheckDouble /> 모두 읽음
              </button>
            )}
          </div>
          {notifications.length === 0 ? (
            <div className="noti-empty">새로운 알림이 없습니다.</div>
          ) : (
            notifications.slice(0, 10).map((n) => (
              <div
                key={n.id}
                className={`noti-item${n.isRead ? '' : ' unread'}`}
                onClick={() => { markRead(n.id); if (n.link) window.location.href = n.link }}
              >
                <span>{n.message}</span>
              </div>
            ))
          )}
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
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
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

      <div className="top-bar-actions">
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
