import { create } from 'zustand'

interface User {
  username: string
  role: string
  disabledFeatures?: string[]
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
        const user = (json.data ?? json) as User
        // ADMIN이 아닌 경우 비활성화 기능 목록 병행 로드
        if (user && user.role !== 'ADMIN') {
          try {
            const pRes = await fetch('/api/v1/auth/my-permissions', { credentials: 'include' })
            if (pRes.ok) {
              const pJson = await pRes.json()
              user.disabledFeatures = pJson.data?.disabledFeatures || []
            }
          } catch { /* ignore */ }
        }
        set({ user, loading: false, error: null })
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
      // 비밀번호 Base64 인코딩 (DevTools payload 난독화)
      const encodedPw = btoa(unescape(encodeURIComponent(password)))
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password: encodedPw, encoded: true }),
        credentials: 'include',
      })
      const json = await res.json()
      if (res.ok && json.success) {
        if (json.data?.require2fa) {
          // ADMIN 2FA 필요 → 2FA 페이지로 이동
          window.location.href = '/login/2fa'
          return false
        }
        // v4.2.6: 로그인 직후 disabledFeatures 즉시 fetch (사이드바가 권한 토글을
        // 새로고침 없이 바로 반영하도록). 이전엔 checkAuth() 안에서만 호출되어
        // 첫 로그인 시 disabledFeatures 가 비어 있어 권한 OFF 메뉴가 그대로 보였음.
        const user = json.data as User
        if (user && user.role !== 'ADMIN') {
          try {
            const pRes = await fetch('/api/v1/auth/my-permissions', { credentials: 'include' })
            if (pRes.ok) {
              const pJson = await pRes.json()
              user.disabledFeatures = pJson.data?.disabledFeatures || []
            }
          } catch { /* ignore */ }
        }
        set({ user, error: null })
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
