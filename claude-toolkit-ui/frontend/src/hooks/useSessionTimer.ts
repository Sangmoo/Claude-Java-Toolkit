import { useState, useEffect, useCallback, useRef } from 'react'

const SESSION_TIMEOUT_MS = 60 * 60 * 1000 // 60분

export function useSessionTimer() {
  const [remaining, setRemaining] = useState(SESSION_TIMEOUT_MS)
  const expiryRef = useRef(Date.now() + SESSION_TIMEOUT_MS)

  const refresh = useCallback(() => {
    expiryRef.current = Date.now() + SESSION_TIMEOUT_MS
    setRemaining(SESSION_TIMEOUT_MS)
    // 서버 세션도 갱신
    fetch('/api/v1/auth/me', { credentials: 'include' }).catch(() => {})
  }, [])

  useEffect(() => {
    // 사용자 활동 시 리셋
    const onActivity = () => {
      expiryRef.current = Date.now() + SESSION_TIMEOUT_MS
    }
    window.addEventListener('click', onActivity, { passive: true })
    window.addEventListener('keydown', onActivity, { passive: true })

    const interval = setInterval(() => {
      const left = Math.max(0, expiryRef.current - Date.now())
      setRemaining(left)
      if (left <= 0) {
        window.location.href = '/login?expired=true'
      }
    }, 1000)

    return () => {
      clearInterval(interval)
      window.removeEventListener('click', onActivity)
      window.removeEventListener('keydown', onActivity)
    }
  }, [])

  const minutes = Math.floor(remaining / 60000)
  const seconds = Math.floor((remaining % 60000) / 1000)
  const display = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`

  return { remaining, display, refresh }
}
