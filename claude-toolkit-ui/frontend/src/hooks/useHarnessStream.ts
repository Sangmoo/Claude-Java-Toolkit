import { useRef, useState, useCallback } from 'react'

/**
 * Phase D — 하네스 SSE 스트리밍 + stage 마커 파싱 공통 훅.
 *
 * <p>백엔드 HarnessOrchestrator가 emit하는 {@code [[HARNESS_STAGE:N]]} sentinel을
 * 파싱하여 stage별 버퍼에 청크를 분배합니다. CodeReviewPage의 처리 로직을 일반화한 것.
 *
 * <p>사용 예 (Phase D — 4 stage):
 * <pre>
 * const { stages, streaming, activeStage, startStream, cancel, reset } =
 *   useHarnessStream(['analyst','builder','reviewer','verifier'])
 *
 * await startStream({
 *   initUrl: '/api/v1/log-rca/stream-init',
 *   streamUrlPrefix: '/api/v1/log-rca/stream/',
 *   body: new URLSearchParams({ error_log: '...', timeline: '...' }),
 * })
 * </pre>
 */
export type HarnessStageKey = string

export interface StreamOptions {
  /** POST 엔드포인트 — body를 받아 {streamId} 응답 */
  initUrl: string
  /** GET SSE 엔드포인트 prefix — `${prefix}${streamId}` */
  streamUrlPrefix: string
  /** init POST body (URLSearchParams 권장) */
  body: URLSearchParams | FormData
  /** init 응답에서 streamId를 꺼낼 키 (기본: streamId) */
  streamIdKey?: string
}

export interface UseHarnessStreamResult<K extends string> {
  stages: Record<K, string>
  streaming: boolean
  activeStage: number  // 1-based; 0 = 미시작/완료
  error: string | null
  startStream: (opts: StreamOptions) => Promise<void>
  cancel: () => void
  reset: () => void
}

const STAGE_MARKER = '[[HARNESS_STAGE:'

export function useHarnessStream<K extends string>(stageKeys: readonly K[]): UseHarnessStreamResult<K> {
  const emptyBuffers = useCallback((): Record<K, string> => {
    const obj = {} as Record<K, string>
    stageKeys.forEach((k) => { obj[k] = '' })
    return obj
  }, [stageKeys])

  const [stages, setStages]           = useState<Record<K, string>>(emptyBuffers)
  const [streaming, setStreaming]     = useState(false)
  const [activeStage, setActiveStage] = useState(0)
  const [error, setError]             = useState<string | null>(null)

  // ref 기반 — state 갱신 race 방지
  const stageRef   = useRef<number>(1)
  const buffersRef = useRef<Record<K, string>>(emptyBuffers())
  const accumRef   = useRef<string>('')
  const esRef      = useRef<EventSource | null>(null)

  const stageKeyByNum = (num: number): K | null => {
    if (num < 1 || num > stageKeys.length) return null
    return stageKeys[num - 1]
  }

  const appendToCurrentStage = (text: string) => {
    if (!text) return
    const key = stageKeyByNum(stageRef.current)
    if (key === null) return
    buffersRef.current = { ...buffersRef.current, [key]: buffersRef.current[key] + text }
    setStages({ ...buffersRef.current })
  }

  /**
   * 청크에서 [[HARNESS_STAGE:N]] 마커를 분리.
   * 마커가 chunk 경계에 걸칠 수 있어 accumRef로 누적하며 처리.
   */
  const processChunk = (chunk: string) => {
    accumRef.current += chunk
    let buf = accumRef.current

    // eslint-disable-next-line no-constant-condition
    while (true) {
      const markerIdx = buf.indexOf(STAGE_MARKER)
      if (markerIdx === -1) {
        // 마커 없음 — 마지막 줄바꿈까지만 안전 flush (마크다운 부분 렌더링 방지)
        const lastNewline = buf.lastIndexOf('\n')
        let safeLen = 0
        if (lastNewline >= 0) safeLen = lastNewline + 1
        else if (buf.length > 200) safeLen = buf.length - 24
        if (safeLen > 0) {
          appendToCurrentStage(buf.substring(0, safeLen))
          buf = buf.substring(safeLen)
        }
        break
      }
      // 마커 앞 텍스트는 현재 stage에
      if (markerIdx > 0) appendToCurrentStage(buf.substring(0, markerIdx))
      // 마커 끝 ']]' 찾기
      const endIdx = buf.indexOf(']]', markerIdx)
      if (endIdx === -1) {
        buf = buf.substring(markerIdx)
        break
      }
      const marker = buf.substring(markerIdx, endIdx + 2)
      const stageNum = parseInt(marker.replace(/[^\d]/g, ''), 10)
      if (stageNum >= 1 && stageNum <= stageKeys.length) {
        stageRef.current = stageNum
        setActiveStage(stageNum)
      }
      let after = endIdx + 2
      if (buf.charAt(after) === '\n') after += 1
      buf = buf.substring(after)
    }
    accumRef.current = buf
  }

  const reset = useCallback(() => {
    esRef.current?.close()
    esRef.current = null
    buffersRef.current = emptyBuffers()
    accumRef.current = ''
    stageRef.current = 1
    setStages(emptyBuffers())
    setActiveStage(0)
    setStreaming(false)
    setError(null)
  }, [emptyBuffers])

  const cancel = useCallback(() => {
    esRef.current?.close()
    esRef.current = null
    setStreaming(false)
  }, [])

  const startStream = useCallback(async (opts: StreamOptions) => {
    // 초기화
    buffersRef.current = emptyBuffers()
    accumRef.current   = ''
    stageRef.current   = 1
    setStages(emptyBuffers())
    setActiveStage(1)
    setStreaming(true)
    setError(null)

    try {
      const headers: HeadersInit = opts.body instanceof URLSearchParams
        ? { 'Content-Type': 'application/x-www-form-urlencoded' }
        : {} // FormData는 boundary 자동 설정
      const res = await fetch(opts.initUrl, {
        method: 'POST',
        headers,
        body: opts.body,
        credentials: 'include',
      })
      const data = await res.json()
      const idKey = opts.streamIdKey || 'streamId'
      if (!data.success || !data[idKey]) {
        const msg = data.error || '스트림 초기화 실패'
        setError(msg)
        setStreaming(false)
        return
      }

      const es = new EventSource(`${opts.streamUrlPrefix}${data[idKey]}`, { withCredentials: true })
      esRef.current = es

      es.onmessage = (e) => {
        if (e.data === '[DONE]' || e.data === 'done') {
          if (accumRef.current.length > 0) { appendToCurrentStage(accumRef.current); accumRef.current = '' }
          es.close(); esRef.current = null; setStreaming(false); return
        }
        processChunk(e.data + '\n')
      }
      es.addEventListener('done', () => {
        if (accumRef.current.length > 0) { appendToCurrentStage(accumRef.current); accumRef.current = '' }
        es.close(); esRef.current = null; setStreaming(false)
      })
      es.addEventListener('error', (e: MessageEvent) => {
        const msg = (e && e.data) ? e.data : '스트리밍 중 오류'
        setError(msg)
        es.close(); esRef.current = null; setStreaming(false)
      })
      es.onerror = () => { es.close(); esRef.current = null; setStreaming(false) }
    } catch (e: any) {
      setError(e?.message || '요청 실패')
      setStreaming(false)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [emptyBuffers])

  return { stages, streaming, activeStage, error, startStream, cancel, reset }
}
