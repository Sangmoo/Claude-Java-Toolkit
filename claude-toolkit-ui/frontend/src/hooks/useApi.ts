import { useState, useCallback, useRef } from 'react'
import { useToastStore } from '../stores/toastStore'

interface ApiOptions {
  showError?: boolean
}

interface ApiState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

export function useApi<T = unknown>(opts: ApiOptions = {}) {
  const { showError = true } = opts
  const [state, setState] = useState<ApiState<T>>({
    data: null,
    loading: false,
    error: null,
  })
  const showToast = useToastStore((s) => s.show)
  const abortRef = useRef(false)

  const request = useCallback(
    async (url: string, init?: RequestInit): Promise<T | null> => {
      if (abortRef.current) return null
      setState({ data: null, loading: true, error: null })
      try {
        const res = await fetch(url, {
          credentials: 'include',
          ...init,
        })

        if (res.status === 401 || res.status === 403) {
          abortRef.current = true
          window.location.href = '/login'
          return null
        }

        // 응답이 HTML인 경우 (SPA 포워딩) — API 에러가 아님
        const contentType = res.headers.get('content-type') || ''
        if (contentType.includes('text/html')) {
          setState({ data: null, loading: false, error: null })
          return null
        }

        if (!res.ok) {
          const text = await res.text()
          let msg: string
          try {
            const json = JSON.parse(text)
            msg = json.error || json.message || `HTTP ${res.status}`
          } catch {
            msg = `HTTP ${res.status}`
          }
          throw new Error(msg)
        }

        const json = await res.json()
        const data = (json.data ?? json) as T
        setState({ data, loading: false, error: null })
        return data
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Unknown error'
        setState({ data: null, loading: false, error: message })
        if (showError) {
          showToast(message, 'error')
        }
        return null
      }
    },
    [showError, showToast]
  )

  const get = useCallback(
    (url: string) => request(url),
    [request]
  )

  const post = useCallback(
    (url: string, body?: unknown) =>
      request(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: body ? JSON.stringify(body) : undefined,
      }),
    [request]
  )

  return { ...state, get, post, request }
}
