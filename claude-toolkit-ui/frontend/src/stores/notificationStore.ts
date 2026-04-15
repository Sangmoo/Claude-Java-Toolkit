import { create } from 'zustand'

interface Notification {
  id: number
  type?: string
  title?: string
  message: string
  link?: string
  isRead: boolean
  createdAt: string
  createdAtIso?: string | null  // v4.2.7: formatRelative 용 원본 ISO
}

interface NotificationState {
  notifications: Notification[]
  unreadCount: number
  dropdownOpen: boolean
  toggleDropdown: () => void
  closeDropdown: () => void
  fetchUnreadCount: () => Promise<void>
  fetchNotifications: () => Promise<void>
  markRead: (id: number) => Promise<void>
  markAllRead: () => Promise<void>
  deleteNotification: (id: number) => Promise<void>
  deleteAllNotifications: () => Promise<void>
  startSSE: () => void
  stopSSE: () => void
}

let es: EventSource | null = null
// v4.2.7: SSE 연결 실패 시 60 초 주기로 미읽음 카운트를 폴링하는 타이머 핸들.
// 이전엔 setTimeout 한 번만 돌아서 한 번 실패한 뒤로는 아무 갱신도 안 되던 버그 수정.
let pollTimer: ReturnType<typeof setInterval> | null = null

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,
  dropdownOpen: false,

  toggleDropdown: () => {
    const open = !get().dropdownOpen
    set({ dropdownOpen: open })
    if (open) get().fetchNotifications()
  },

  closeDropdown: () => set({ dropdownOpen: false }),

  fetchUnreadCount: async () => {
    try {
      const res = await fetch('/notifications/unread-count', { credentials: 'include' })
      if (res.ok) {
        const data = await res.json()
        set({ unreadCount: data.count ?? 0 })
      }
    } catch {
      // silent
    }
  },

  fetchNotifications: async () => {
    try {
      const res = await fetch('/notifications', { credentials: 'include' })
      if (res.ok) {
        const data = await res.json()
        const list = Array.isArray(data) ? data : []
        // v4.2.7: 목록을 받아올 때 unreadCount 도 같이 재동기화 — 여러 탭/기기 간 drift 방지
        const unread = list.filter((n: Notification) => !n.isRead).length
        set({ notifications: list, unreadCount: unread })
      }
    } catch {
      // silent
    }
  },

  markRead: async (id: number) => {
    await fetch(`/notifications/${id}/read`, { method: 'POST', credentials: 'include' })
    set((s) => {
      // v4.2.7: 이미 읽은 알림을 다시 클릭한 경우 카운트를 잘못 감산하지 않도록
      // wasUnread 체크 후에만 unreadCount 를 줄인다.
      const target = s.notifications.find((n) => n.id === id)
      const wasUnread = target != null && !target.isRead
      return {
        notifications: s.notifications.map((n) => n.id === id ? { ...n, isRead: true } : n),
        unreadCount: wasUnread ? Math.max(0, s.unreadCount - 1) : s.unreadCount,
      }
    })
  },

  markAllRead: async () => {
    await fetch('/notifications/read-all', { method: 'POST', credentials: 'include' })
    set((s) => ({
      notifications: s.notifications.map((n) => ({ ...n, isRead: true })),
      unreadCount: 0,
    }))
  },

  // v4.2.7: 본인 수신 알림 전체 삭제 — 드롭다운 헤더의 "전체 삭제" 버튼에서 호출
  deleteAllNotifications: async () => {
    try {
      const res = await fetch('/notifications/delete-all', { method: 'POST', credentials: 'include' })
      const d   = await res.json().catch(() => null)
      if (d?.success) {
        set({ notifications: [], unreadCount: 0 })
      }
    } catch {
      // silent — 다음 fetch 에서 복구
    }
  },

  // v4.2.7: 단일 알림 삭제 — 드롭다운 각 항목의 'x' 버튼에서 호출
  deleteNotification: async (id: number) => {
    try {
      const res = await fetch(`/notifications/${id}/delete`, { method: 'POST', credentials: 'include' })
      const d   = await res.json().catch(() => null)
      if (d?.success) {
        set((s) => {
          const target = s.notifications.find((n) => n.id === id)
          const wasUnread = target != null && !target.isRead
          return {
            notifications: s.notifications.filter((n) => n.id !== id),
            unreadCount: wasUnread ? Math.max(0, s.unreadCount - 1) : s.unreadCount,
          }
        })
      }
    } catch {
      // silent — 실패시 다음 fetch 에서 복구
    }
  },

  startSSE: () => {
    if (es) return
    // 이전 폴링 타이머가 남아있으면 정리 (중복 방지)
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
    try {
      es = new EventSource('/notifications/stream', { withCredentials: true })
      // v4.2.7 BUG FIX:
      // 백엔드 NotificationPublisher 는 SseEmitter.event().name("notification").data(payload) 로
      // 이름이 붙은 이벤트를 발행한다. EventSource.onmessage 는 이름 없는 이벤트(또는 'message')
      // 만 잡기 때문에 기존 코드는 실시간 push 를 못 받고 있었고, 결과적으로 종 아이콘의
      // 미읽음 카운트가 새 알림 즉시 증가하지 않는 문제가 있었다.
      // addEventListener('notification', ...) 로 직접 구독하여 해결.
      es.addEventListener('notification', () => {
        get().fetchUnreadCount()
        // 드롭다운이 열려있으면 목록도 즉시 갱신해서 새 알림을 표시
        if (get().dropdownOpen) get().fetchNotifications()
      })
      // 초기 'connected' 확인 이벤트도 참고용으로 수신 (NOOP)
      es.addEventListener('connected', () => { /* ignore */ })
      // 혹시 이름 없는 이벤트가 들어올 경우를 위한 호환성 핸들러
      es.onmessage = () => { get().fetchUnreadCount() }
      es.onerror = () => {
        // v4.2.7: SSE 연결 실패시 60초 간격 "지속 폴링" 으로 전환.
        // 이전 구현은 setTimeout 한 번만 돌아 이후 영원히 갱신이 멈췄음.
        es?.close()
        es = null
        if (!pollTimer) {
          pollTimer = setInterval(() => {
            get().fetchUnreadCount()
            // 주기적으로 SSE 재연결도 시도 — 성공하면 자기 자신이 pollTimer 를 해제
            if (!es) {
              try { get().startSSE() } catch { /* ignore */ }
            }
          }, 60000)
        }
      }
    } catch {
      // EventSource 자체 생성 실패 (CSP 등) — 폴링 모드로 진입
      if (!pollTimer) {
        pollTimer = setInterval(() => get().fetchUnreadCount(), 60000)
      }
    }
    // 초기 카운트
    get().fetchUnreadCount()
  },

  stopSSE: () => {
    es?.close()
    es = null
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  },
}))
