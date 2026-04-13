import { useState, useCallback } from 'react'
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

  const request = useCallback(
    async (url: string, init?: RequestInit): Promise<T | null> => {
      setState({ data: null, loading: true, error: null })
      try {
        const res = await fetch(url, {
          credentials: 'include',
          ...init,
        })

        if (res.status === 401 || res.status === 403) {
          window.location.href = '/react/login'
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
