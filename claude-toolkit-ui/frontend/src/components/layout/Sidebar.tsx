import { useLocation, Link, useNavigate } from 'react-router-dom'
import { FaRobot, FaChevronLeft, FaChevronDown, FaUserEdit, FaKey } from 'react-icons/fa'
import { useSidebarStore } from '../../stores/sidebarStore'
import { useAuthStore } from '../../stores/authStore'
import { quickLinks, menuSections, footerItems, type MenuItem } from './sidebarMenus'

// 모든 라우트가 React로 전환 완료 — Thymeleaf 전용 경로 없음
const THYMELEAF_ONLY: string[] = []

function SidebarItem({ item, active }: { item: MenuItem; active: boolean }) {
  const closeMobile = useSidebarStore((s) => s.closeMobile)
  const Icon = item.icon

  const isThymeleaf = THYMELEAF_ONLY.some((t) => item.path === t || item.path.startsWith(t + '/'))

  // v4.4.0: 외부 링크 (Swagger UI 등) — 새 탭에서 열기
  if (item.externalLink) {
    return (
      <a
        href={item.path}
        target="_blank"
        rel="noopener noreferrer"
        className={`sidebar-item${active ? ' active' : ''}`}
      >
        <span className="sidebar-item-icon">
          <Icon style={{ color: item.color }} />
        </span>
        <span>{item.label} ↗</span>
      </a>
    )
  }

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
  const navigate = useNavigate()
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

  // 모든 사이드바 경로를 수집 (더 구체적인 경로 우선 매칭용)
  const allPaths: string[] = [
    ...quickLinks.map((i) => i.path),
    ...menuSections.flatMap((s) => s.items.map((i) => i.path)),
    ...footerItems.map((i) => i.path),
  ]

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/' || location.pathname === ''
    const cur = location.pathname
    if (cur === path) return true
    // 하위 경로(/path/...)인 경우 active
    if (!cur.startsWith(path + '/')) return false
    // 단, 더 구체적인(긴) 매칭 path가 존재하면 이 path는 active 아님
    // 예: /settings와 /settings/prompts 둘 다 있을 때 /settings/prompts만 active
    const moreSpecific = allPaths.some((p) => p !== path && p.startsWith(path + '/') && (cur === p || cur.startsWith(p + '/')))
    return !moreSpecific
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
                  style={{
                    // padding(8px) + line-height(20px) = 36px 이지만 일부 환경에서 마지막
                    // 항목이 잘리는 현상이 있어 40px + 여유 8px 로 계산
                    maxHeight: isCollapsed ? 0 : `${section.items.length * 40 + 8}px`,
                  }}
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
            <div className="sidebar-user-info" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                <span style={{ fontSize: '12px', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{user.username}</span>
                <span style={{ color: 'var(--text-muted)', fontSize: '10px' }}>{user.role}</span>
              </div>
              <button
                onClick={() => { navigate('/account'); closeMobile() }}
                title="내 정보 수정"
                style={userIconBtn}
              >
                <FaUserEdit />
              </button>
              <button
                onClick={() => { navigate('/security'); closeMobile() }}
                title="비밀번호 변경"
                style={userIconBtn}
              >
                <FaKey />
              </button>
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

const userIconBtn: React.CSSProperties = {
  background: 'none',
  border: '1px solid var(--border-color)',
  borderRadius: '6px',
  padding: '4px 6px',
  color: 'var(--text-muted)',
  cursor: 'pointer',
  fontSize: '11px',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
}
