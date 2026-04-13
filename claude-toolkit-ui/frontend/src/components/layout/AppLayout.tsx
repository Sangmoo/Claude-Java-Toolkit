import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import TopBar from './TopBar'
import MobileBottomNav from './MobileBottomNav'
import ToastContainer from '../common/ToastContainer'
import { useSidebarStore } from '../../stores/sidebarStore'

export default function AppLayout() {
  const collapsed = useSidebarStore((s) => s.collapsed)

  return (
    <div className="layout">
      <Sidebar />
      <main className={`main-area${collapsed ? ' sidebar-collapsed' : ''}`}>
        <TopBar />
        <div className="main-content">
          <Outlet />
        </div>
      </main>
      <MobileBottomNav />
      <ToastContainer />
    </div>
  )
}
