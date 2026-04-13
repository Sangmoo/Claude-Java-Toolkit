import { create } from 'zustand'

interface User {
  username: string
  role: string
}

interface AuthState {
  user: User | null
  loading: boolean
  error: string | null
  checkAuth: () => Promise<void>
  login: (username: string, password: string) => Promise<boolean>
  logout: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  loading: true,
  error: null,

  checkAuth: async () => {
    try {
      const res = await fetch('/api/v1/auth/me', { credentials: 'include' })
      if (res.ok) {
        const json = await res.json()
        set({ user: json.data ?? json, loading: false, error: null })
      } else {
        set({ user: null, loading: false, error: null })
      }
    } catch {
      set({ user: null, loading: false, error: null })
    }
  },

  login: async (username: string, password: string) => {
    set({ error: null })
    try {
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
        credentials: 'include',
      })
      const json = await res.json()
      if (res.ok && json.success) {
        set({ user: json.data, error: null })
        return true
      }
      set({ error: json.error || '로그인에 실패했습니다.' })
      return false
    } catch {
      set({ error: '서버에 연결할 수 없습니다.' })
      return false
    }
  },

  logout: async () => {
    try {
      await fetch('/api/v1/auth/logout', {
        method: 'POST',
        credentials: 'include',
      })
    } catch {
      // ignore
    }
    set({ user: null })
  },
}))
