import { useLocation, Link } from 'react-router-dom'
import { FaRobot, FaChevronLeft, FaChevronDown } from 'react-icons/fa'
import { useSidebarStore } from '../../stores/sidebarStore'
import { useAuthStore } from '../../stores/authStore'
import { quickLinks, menuSections, footerItems, type MenuItem } from './sidebarMenus'

// 모든 라우트가 React로 전환 완료 — Thymeleaf 전용 경로 없음
const THYMELEAF_ONLY: string[] = []

function SidebarItem({ item, active }: { item: MenuItem; active: boolean }) {
  const closeMobile = useSidebarStore((s) => s.closeMobile)
  const Icon = item.icon

  const isThymeleaf = THYMELEAF_ONLY.some((t) => item.path === t || item.path.startsWith(t + '/'))

  if (!isThymeleaf) {
    return (
      <Link
        to={item.path}
        className={`sidebar-item${active ? ' active' : ''}`}
        onClick={closeMobile}
      >
        <span className="sidebar-item-icon">
          <Icon style={{ color: item.color }} />
        </span>
        <span>{item.label}</span>
      </Link>
    )
  }

  return (
    <a
      href={item.path}
      className={`sidebar-item${active ? ' active' : ''}`}
    >
      <span className="sidebar-item-icon">
        <Icon style={{ color: item.color }} />
      </span>
      <span>{item.label}</span>
    </a>
  )
}

export default function Sidebar() {
  const location = useLocation()
  const { collapsed, mobileOpen, toggleCollapse, closeMobile, sections, toggleSection } =
    useSidebarStore()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  const isReviewer = user?.role === 'REVIEWER' || isAdmin
  const disabledFeatures = user?.disabledFeatures || []

  const isFeatureAllowed = (item: MenuItem) => {
    if (isAdmin) return true
    if (!item.featureKey) return true
    return !disabledFeatures.includes(item.featureKey)
  }

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/' || location.pathname === ''
    return location.pathname.startsWith(path)
  }

  const sidebarClass = [
    'sidebar',
    collapsed ? 'collapsed' : '',
    mobileOpen ? 'mobile-open' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <>
      <nav className={sidebarClass}>
        {/* Brand */}
        <div className="sidebar-brand">
          <FaRobot className="sidebar-brand-icon" />
          <span>Claude Toolkit</span>
          <button className="sidebar-collapse-btn" onClick={() => { toggleCollapse(); closeMobile(); }} title="사이드바 접기">
            <FaChevronLeft />
          </button>
        </div>

        {/* Navigation */}
        <div className="sidebar-nav">
          {/* Quick Links */}
          {quickLinks.filter(isFeatureAllowed).map((item) => (
            <SidebarItem
              key={item.path}
              item={item}
              active={isActive(item.path)}
            />
          ))}

          {/* Sections */}
          {menuSections.map((section) => {
            if (section.adminOnly && !isAdmin) return null

            const isCollapsed = sections[section.key] === true

            return (
              <div key={section.key}>
                <div
                  className="sidebar-section-toggle"
                  onClick={() => toggleSection(section.key)}
                >
                  <span>{section.label}</span>
                  <FaChevronDown
                    className={`sidebar-section-chevron${isCollapsed ? ' collapsed' : ''}`}
                  />
                </div>
                <div
                  className={`sidebar-group${isCollapsed ? ' collapsed' : ''}`}
                  style={{ maxHeight: isCollapsed ? 0 : `${section.items.length * 36}px` }}
                >
                  {section.items.map((item) => {
                    if (item.adminOnly && !isAdmin) return null
                    if (!isFeatureAllowed(item)) return null
                    return (
                      <SidebarItem
                        key={item.path}
                        item={item}
                        active={isActive(item.path)}
                      />
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>

        {/* Footer */}
        <div className="sidebar-footer">
          {footerItems.map((item) => {
            if (item.adminOnly && !isAdmin) return null
            if (item.reviewerOnly && !isReviewer) return null
            return (
              <SidebarItem
                key={item.path}
                item={item}
                active={isActive(item.path)}
              />
            )
          })}

          {user && (
            <div className="sidebar-user-info">
              <span>{user.username}</span>
              <span style={{ color: 'var(--text-muted)', fontSize: '11px' }}>
                ({user.role})
              </span>
            </div>
          )}

          <div className="sidebar-version">v4.0.0 · React</div>
        </div>
      </nav>

      {/* Open tab when collapsed (desktop) */}
      {collapsed && !mobileOpen && (
        <button className="sidebar-open-tab" onClick={toggleCollapse} title="사이드바 열기">
          <FaChevronLeft style={{ transform: 'rotate(180deg)' }} />
        </button>
      )}

      {/* Mobile overlay backdrop */}
      {mobileOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.5)',
            zIndex: 199,
          }}
          onClick={closeMobile}
        />
      )}
    </>
  )
}
