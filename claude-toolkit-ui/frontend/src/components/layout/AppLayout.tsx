import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import TopBar from './TopBar'
import MobileBottomNav from './MobileBottomNav'
import ToastContainer from '../common/ToastContainer'
import CommandPalette from '../common/CommandPalette'
import { useSidebarStore } from '../../stores/sidebarStore'
import { useKeyboardShortcuts } from '../../hooks/useKeyboardShortcuts'

const shortcuts = [
  { keys: '?', desc: '단축키 도움말 표시' },
  { keys: 'Esc', desc: '모달/드롭다운 닫기' },
  { keys: 'Ctrl+Enter', desc: '폼 제출 (입력 필드에서)' },
  { keys: 'Shift+Enter', desc: '줄바꿈 (채팅에서)' },
  { keys: 'Enter', desc: '메시지 전송 (채팅에서)' },
]

export default function AppLayout() {
  const collapsed = useSidebarStore((s) => s.collapsed)
  const { showHelp, closeHelp } = useKeyboardShortcuts()

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

      {/* v4.2.8: Cmd+K / Ctrl+K 전역 커맨드 팔레트 */}
      <CommandPalette />

      {/* 키보드 단축키 모달 */}
      {showHelp && (
        <div
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}
          onClick={closeHelp}
        >
          <div
            style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '16px', padding: '24px', width: 'min(400px, 90vw)' }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ marginBottom: '16px', fontSize: '16px', fontWeight: 700 }}>키보드 단축키</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {shortcuts.map((s) => (
                <div key={s.keys} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px' }}>
                  <span style={{ color: 'var(--text-sub)' }}>{s.desc}</span>
                  <kbd style={{
                    background: 'var(--bg-primary)', border: '1px solid var(--border-color)',
                    borderRadius: '4px', padding: '2px 8px', fontSize: '12px', fontFamily: 'monospace',
                  }}>
                    {s.keys}
                  </kbd>
                </div>
              ))}
            </div>
            <p style={{ marginTop: '16px', fontSize: '11px', color: 'var(--text-muted)', textAlign: 'center' }}>
              아무 키나 눌러 닫기
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
