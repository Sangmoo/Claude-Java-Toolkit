import { useLocation } from 'react-router-dom'
import { FaHome, FaComments, FaSearch, FaHistory, FaBars } from 'react-icons/fa'
import { useSidebarStore } from '../../stores/sidebarStore'

const navItems = [
  { label: '홈', path: '/', icon: FaHome },
  { label: '채팅', path: '/chat', icon: FaComments },
  { label: '검색', path: '/search', icon: FaSearch },
  { label: '이력', path: '/history', icon: FaHistory },
]

export default function MobileBottomNav() {
  const location = useLocation()
  const toggleMobile = useSidebarStore((s) => s.toggleMobile)

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/'
    return location.pathname.startsWith(path)
  }

  const navigate = (path: string) => {
    // Phase 2: 아직 React 라우트 없는 경로는 Thymeleaf로
    window.location.href = path
  }

  return (
    <div className="mobile-bottom-nav">
      {navItems.map((item) => {
        const Icon = item.icon
        return (
          <button
            key={item.path}
            className={`mobile-nav-item${isActive(item.path) ? ' active' : ''}`}
            onClick={() => navigate(item.path)}
          >
            <Icon className="mobile-nav-item-icon" />
            <span>{item.label}</span>
          </button>
        )
      })}
      <button className="mobile-nav-item" onClick={toggleMobile}>
        <FaBars className="mobile-nav-item-icon" />
        <span>메뉴</span>
      </button>
    </div>
  )
}
