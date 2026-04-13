import { useState, useRef, useCallback } from 'react'
import { useToastStore } from '../stores/toastStore'

interface ApiState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

/**
 * API 호출 훅. get/post 함수는 안정적 참조 (무한 루프 방지).
 * showError: false 설정 시 에러 토스트 미표시.
 */
export function useApi<T = unknown>(opts?: { showError?: boolean }) {
  const showError = opts?.showError ?? true
  const [state, setState] = useState<ApiState<T>>({ data: null, loading: false, error: null })
  const showToast = useToastStore((s) => s.show)
  // 안정적 참조를 위해 ref 사용
  const showErrorRef = useRef(showError)
  showErrorRef.current = showError
  const showToastRef = useRef(showToast)
  showToastRef.current = showToast

  const request = useCallback(async (url: string, init?: RequestInit): Promise<T | null> => {
    setState((prev) => ({ ...prev, loading: true, error: null }))
    try {
      const res = await fetch(url, { credentials: 'include', ...init })

      if (res.status === 401 || res.status === 403) {
        // 인증 필요 → 로그인으로
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
        return null
      }

      const ct = res.headers.get('content-type') || ''
      if (ct.includes('text/html')) {
        // HTML 응답 = SPA 포워딩 → API 데이터 없음 (에러 아님)
        setState({ data: null, loading: false, error: null })
        return null
      }

      if (!res.ok) {
        const text = await res.text()
        let msg: string
        try { const j = JSON.parse(text); msg = j.error || j.message || `HTTP ${res.status}` }
        catch { msg = `HTTP ${res.status}` }
        throw new Error(msg)
      }

      const json = await res.json()
      const data = (json.data ?? json) as T
      setState({ data, loading: false, error: null })
      return data
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error'
      setState({ data: null, loading: false, error: msg })
      if (showErrorRef.current) showToastRef.current(msg, 'error')
      return null
    }
  }, []) // 의존성 없음 → 안정적 참조

  const get = useCallback((url: string) => request(url), [request])
  const post = useCallback((url: string, body?: unknown) =>
    request(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined,
    }), [request])

  return { ...state, get, post, request }
}
