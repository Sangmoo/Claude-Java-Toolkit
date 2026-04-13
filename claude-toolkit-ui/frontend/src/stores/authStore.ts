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
      const res = await fetch('/api/v1/auth/me', {
        credentials: 'include',
        redirect: 'manual',
      })
      if (res.ok) {
        const data = await res.json()
        set({ user: data.data ?? data, loading: false, error: null })
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
      const body = new URLSearchParams({ username, password })
      const res = await fetch('/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
        credentials: 'include',
        redirect: 'manual',
      })
      // Spring Security returns 302 on both success and failure
      // Success: redirect to / , Failure: redirect to /login?error=true
      // With redirect: 'manual', we get type 'opaqueredirect'
      // After POST, try to fetch /api/v1/auth/me to confirm login
      const check = await fetch('/api/v1/auth/me', {
        credentials: 'include',
        redirect: 'manual',
      })
      if (check.ok) {
        const data = await check.json()
        set({ user: data.data ?? data, error: null })
        return true
      }
      // If still not authenticated, check if response indicates error
      if (res.type === 'opaqueredirect' || res.status === 302) {
        set({ error: '아이디 또는 비밀번호가 올바르지 않습니다.' })
      } else {
        set({ error: '로그인에 실패했습니다.' })
      }
      return false
    } catch {
      set({ error: '서버에 연결할 수 없습니다.' })
      return false
    }
  },

  logout: async () => {
    try {
      await fetch('/logout', { credentials: 'include' })
    } catch {
      // ignore
    }
    set({ user: null })
  },
}))
