import { useEffect, useRef, useCallback, useState } from 'react'

interface UseSSEOptions {
  onMessage: (data: string) => void
  onError?: () => void
  onOpen?: () => void
  /** 재연결 최대 횟수 (기본 3) */
  maxRetries?: number
  /** 초기 재연결 간격(ms, exponential backoff 적용, 기본 2000) */
  retryDelay?: number
}

/**
 * 네트워크 끊김 시 자동 재연결을 지원하는 SSE 훅.
 * Exponential backoff: 2s → 4s → 8s (maxRetries 번까지)
 */
export function useSSE(url: string | null, options: UseSSEOptions) {
  const { onMessage, onError, onOpen, maxRetries = 3, retryDelay = 2000 } = options
  const esRef = useRef<EventSource | null>(null)
  const retriesRef = useRef(0)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!url) return
    let cancelled = false

    const connect = () => {
      if (cancelled) return
      const es = new EventSource(url, { withCredentials: true })
      esRef.current = es

      es.onopen = () => {
        retriesRef.current = 0
        setConnected(true)
        onOpen?.()
      }

      es.onmessage = (e) => onMessage(e.data)

      es.onerror = () => {
        setConnected(false)
        es.close()
        esRef.current = null
        onError?.()
        if (retriesRef.current < maxRetries && !cancelled) {
          const delay = retryDelay * Math.pow(2, retriesRef.current)
          retriesRef.current++
          setTimeout(connect, delay)
        }
      }
    }

    connect()

    return () => {
      cancelled = true
      esRef.current?.close()
      esRef.current = null
      setConnected(false)
    }
  }, [url, onMessage, onError, onOpen, maxRetries, retryDelay])

  const close = useCallback(() => {
    esRef.current?.close()
    esRef.current = null
    setConnected(false)
  }, [])

  return { connected, close }
}
