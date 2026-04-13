import { useEffect, useRef, useCallback, useState } from 'react'

interface UseSSEOptions {
  onMessage: (data: string) => void
  onError?: () => void
  onOpen?: () => void
}

export function useSSE(url: string | null, options: UseSSEOptions) {
  const { onMessage, onError, onOpen } = options
  const esRef = useRef<EventSource | null>(null)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!url) return

    const es = new EventSource(url, { withCredentials: true })
    esRef.current = es

    es.onopen = () => {
      setConnected(true)
      onOpen?.()
    }

    es.onmessage = (e) => {
      onMessage(e.data)
    }

    es.onerror = () => {
      setConnected(false)
      onError?.()
    }

    return () => {
      es.close()
      esRef.current = null
      setConnected(false)
    }
  }, [url, onMessage, onError, onOpen])

  const close = useCallback(() => {
    esRef.current?.close()
    esRef.current = null
    setConnected(false)
  }, [])

  return { connected, close }
}
