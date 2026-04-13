import { FaRobot, FaSun, FaMoon, FaBell, FaSignOutAlt, FaBars } from 'react-icons/fa'
import { useThemeStore } from '../../stores/themeStore'
import { useAuthStore } from '../../stores/authStore'
import { useSidebarStore } from '../../stores/sidebarStore'
import { useLocation, Link } from 'react-router-dom'

const pathMap: Record<string, string> = {
  '': '홈',
  chat: 'AI 채팅',
  advisor: 'SQL 리뷰',
  workspace: '통합 워크스페이스',
  pipelines: '분석 파이프라인',
  docgen: '기술 문서',
  history: '리뷰 이력',
  favorites: '즐겨찾기',
  settings: '설정',
  admin: '관리',
}

function Breadcrumb() {
  const location = useLocation()
  const parts = location.pathname
    .replace(/^\//, '')
    .split('/')
    .filter(Boolean)

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
            <Link to={'/' + parts.slice(0, i + 1).join('/')}>
              {pathMap[p] || p}
            </Link>
          )}
        </span>
      ))}
    </div>
  )
}

export default function TopBar() {
  const { theme, toggleTheme } = useThemeStore()
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const { collapsed, toggleCollapse, toggleMobile } = useSidebarStore()

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

        <div className="noti-bell-wrap">
          <button className="top-bar-btn" title="알림">
            <FaBell />
          </button>
        </div>

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
