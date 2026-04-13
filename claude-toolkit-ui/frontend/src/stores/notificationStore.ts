import { create } from 'zustand'

interface Notification {
  id: number
  message: string
  link?: string
  isRead: boolean
  createdAt: string
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
  startSSE: () => void
  stopSSE: () => void
}

let es: EventSource | null = null

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
        set({ notifications: Array.isArray(data) ? data : [] })
      }
    } catch {
      // silent
    }
  },

  markRead: async (id: number) => {
    await fetch(`/notifications/${id}/read`, { method: 'POST', credentials: 'include' })
    set((s) => ({
      notifications: s.notifications.map((n) => n.id === id ? { ...n, isRead: true } : n),
      unreadCount: Math.max(0, s.unreadCount - 1),
    }))
  },

  markAllRead: async () => {
    await fetch('/notifications/read-all', { method: 'POST', credentials: 'include' })
    set((s) => ({
      notifications: s.notifications.map((n) => ({ ...n, isRead: true })),
      unreadCount: 0,
    }))
  },

  startSSE: () => {
    if (es) return
    try {
      es = new EventSource('/notifications/stream', { withCredentials: true })
      es.onmessage = () => {
        get().fetchUnreadCount()
      }
      es.onerror = () => {
        es?.close()
        es = null
        // 폴링 fallback
        setTimeout(() => get().fetchUnreadCount(), 60000)
      }
    } catch {
      // 폴링 fallback
    }
    // 초기 카운트
    get().fetchUnreadCount()
  },

  stopSSE: () => {
    es?.close()
    es = null
  },
}))
