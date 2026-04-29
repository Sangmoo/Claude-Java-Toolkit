import { useState, useRef, useCallback, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { FaPaperPlane, FaSpinner, FaCommentDots, FaTrash, FaUser, FaRobot, FaChevronDown, FaChevronUp } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

/**
 * v4.7.x — #4 결과 후속 질문 패널 (Follow-up Q&A).
 *
 * <p>분석 결과 패널 하단에 임베드되어, 사용자가 결과를 본 직후 동일한 컨텍스트로
 * 후속 질문을 던질 수 있게 한다. 새 페이지로 이동하지 않고 같은 화면에서 미니
 * 채팅이 진행됨. 백엔드는 기존 `/chat/send` + `/chat/stream` SSE 인프라를
 * 그대로 재사용 — context 파라미터로 분석 결과를 system prompt 에 주입.
 *
 * <p>각 후속 질문은 새 채팅 세션으로 시작 (분석 결과별로 분리 보관) — 사용자가
 * 추후 `/chat` 페이지에서 동일 세션을 이어서 진행 가능.
 *
 * @param resultText  분석 결과 markdown (Claude 응답 컨텍스트)
 * @param inputText   원본 사용자 입력 (선택 — 결과만으로 문맥 부족할 때 보강)
 * @param featureLabel 분석 페이지 종류 (예: "SQL 리뷰") — 채팅 세션 제목 prefix
 */
interface FollowUpMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
}

interface Props {
  resultText: string
  inputText?: string
  featureLabel?: string
}

export default function FollowUpQAPanel({ resultText, inputText, featureLabel }: Props) {
  const [open, setOpen]               = useState(false)
  const [question, setQuestion]       = useState('')
  const [messages, setMessages]       = useState<FollowUpMessage[]>([])
  const [streaming, setStreaming]     = useState(false)
  const [streamText, setStreamText]   = useState('')
  const [statusText, setStatusText]   = useState('')
  // 같은 결과 페이지 내에선 sessionId 를 유지해서 후속 질문 흐름이 누적되게 함.
  // 결과가 바뀌면 (다시 분석) 자동으로 새 세션으로 리셋.
  const [sessionId, setSessionId]     = useState<number | null>(null)
  const esRef                         = useRef<EventSource | null>(null)
  const messagesEndRef                = useRef<HTMLDivElement>(null)
  const toast                         = useToast()

  // 결과가 변경되면 후속 Q&A 컨텍스트도 리셋 (새 분석 → 새 세션)
  useEffect(() => {
    setMessages([])
    setStreamText('')
    setStatusText('')
    setSessionId(null)
    setQuestion('')
    // 진행 중인 스트림이 있으면 강제 종료
    esRef.current?.close()
    esRef.current = null
    setStreaming(false)
  }, [resultText])

  // 컴포넌트 unmount 시 SSE 정리
  useEffect(() => {
    return () => {
      esRef.current?.close()
      esRef.current = null
    }
  }, [])

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  /**
   * 분석 결과를 ChatController 의 context 파라미터로 전달.
   * 결과가 너무 길 경우 백엔드가 3000자로 자르므로 클라이언트에서 추가 처리 안 함.
   * 다만 사용자 원본 input 도 함께 보내야 "왜 이런 결과가 나왔는지" 류 질문에 답하기 좋음.
   */
  const buildContext = (): string => {
    const parts: string[] = []
    if (featureLabel) parts.push(`[분석 종류] ${featureLabel}`)
    if (inputText && inputText.trim()) {
      const trimmedIn = inputText.length > 1500 ? inputText.slice(0, 1500) + '\n...(생략)' : inputText
      parts.push(`[원본 입력]\n${trimmedIn}`)
    }
    parts.push(`[분석 결과]\n${resultText}`)
    return parts.join('\n\n---\n\n')
  }

  const askFollowUp = async () => {
    const q = question.trim()
    if (!q || streaming) return
    if (!resultText || !resultText.trim()) {
      toast.error('분석 결과가 없습니다.')
      return
    }

    setQuestion('')
    setMessages((prev) => [...prev, { id: Date.now(), role: 'user', content: q }])
    setTimeout(scrollToBottom, 50)
    setStreaming(true)
    setStreamText('')
    setStatusText('연결 중...')

    try {
      // Step 1: POST /chat/send (sessionId 가 있으면 이어서, 없으면 새 세션 — 백엔드가 자동 생성)
      const body = new URLSearchParams({ message: q })
      if (sessionId) body.set('sessionId', String(sessionId))
      // context 는 첫 번째 질문에만 보내면 충분 — 백엔드가 system prompt 에 1회 주입.
      // 이후 같은 세션의 메시지는 history 로 이어지므로 컨텍스트 중복 전송 불필요.
      if (!sessionId) body.set('context', buildContext())

      const sendRes = await fetch('/chat/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
        credentials: 'include',
      })
      const sendData = await sendRes.json().catch(() => null)

      if (!sendRes.ok || !sendData?.success) {
        toast.error(sendData?.error || '질문 전송 실패')
        setStreaming(false)
        setStatusText('')
        return
      }
      if (!sessionId && sendData.sessionId) {
        setSessionId(sendData.sessionId as number)
      }

      // Step 2: SSE 스트리밍
      let acc = ''
      const es = new EventSource('/chat/stream', { withCredentials: true })
      esRef.current = es

      es.onmessage = (e) => {
        const data = e.data
        if (data === '[DONE]' || data === 'done') {
          es.close(); esRef.current = null
          setMessages((prev) => [...prev, { id: Date.now(), role: 'assistant', content: acc }])
          setStreamText(''); setStreaming(false); setStatusText('')
          return
        }
        if (!acc) setStatusText('')
        acc += data
        setStreamText(acc)
        scrollToBottom()
      }

      es.addEventListener('status', (e: MessageEvent) => {
        if (!acc) setStatusText(e.data || '')
      })

      es.addEventListener('error_msg', (e: MessageEvent) => {
        toast.error(e.data || '스트리밍 오류')
        es.close(); esRef.current = null
        setStreaming(false); setStatusText('')
      })

      es.onerror = () => {
        // 정상 종료(서버 close) 의 경우 acc 가 채워져 있을 수 있음 — 내용이 있으면 메시지로 보존
        if (acc) {
          setMessages((prev) => [...prev, { id: Date.now(), role: 'assistant', content: acc }])
          setStreamText('')
        }
        es.close(); esRef.current = null
        setStreaming(false); setStatusText('')
      }
    } catch (err) {
      toast.error('질문 전송 중 오류')
      setStreaming(false); setStatusText('')
    }
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      askFollowUp()
    }
  }

  const clearChat = () => {
    esRef.current?.close()
    esRef.current = null
    setMessages([])
    setStreamText('')
    setStatusText('')
    setSessionId(null)
    setStreaming(false)
  }

  // 결과가 없으면 패널 자체를 노출하지 않음 — AnalysisPageTemplate 가 result 검사 후 렌더하지만
  // 안전장치로 한 번 더.
  if (!resultText || !resultText.trim()) return null

  return (
    <div style={containerStyle}>
      {/* 헤더 — 클릭으로 펼침/접힘 토글 */}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        style={headerBtn}
        title={open ? '후속 질문 접기' : '후속 질문 펼치기'}>
        <FaCommentDots style={{ color: '#8b5cf6' }} />
        <span style={{ flex: 1, textAlign: 'left' }}>
          후속 질문 (Follow-up Q&amp;A)
          {messages.length > 0 && (
            <span style={countChip}>{Math.ceil(messages.length / 2)}</span>
          )}
        </span>
        {messages.length > 0 && open && (
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); clearChat() }}
            style={miniBtn}
            title="대화 초기화">
            <FaTrash style={{ fontSize: 10 }} />
          </button>
        )}
        {open ? <FaChevronUp /> : <FaChevronDown />}
      </button>

      {open && (
        <div style={bodyStyle}>
          {/* 안내 문구 */}
          {messages.length === 0 && !streaming && (
            <div style={hintStyle}>
              💬 분석 결과에 대해 추가로 궁금한 점을 물어보세요. 결과 + 원본 입력이
              컨텍스트로 자동 전달되어, Claude 가 정확한 후속 답변을 제공합니다.
            </div>
          )}

          {/* 메시지 목록 */}
          {messages.length > 0 && (
            <div style={messagesStyle}>
              {messages.map((m) => (
                <div key={m.id} style={m.role === 'user' ? userBubble : aiBubble}>
                  <div style={bubbleHeader}>
                    {m.role === 'user' ? <FaUser style={{ fontSize: 10 }} /> : <FaRobot style={{ fontSize: 10, color: '#8b5cf6' }} />}
                    <span>{m.role === 'user' ? '나' : 'Claude'}</span>
                  </div>
                  {m.role === 'assistant' ? (
                    <div className="markdown-body" style={{ fontSize: 13 }}>
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown>
                    </div>
                  ) : (
                    <div style={{ whiteSpace: 'pre-wrap', fontSize: 13, color: 'var(--text-primary)' }}>{m.content}</div>
                  )}
                </div>
              ))}
              {/* 스트리밍 중 응답 — 부분 텍스트 */}
              {streaming && streamText && (
                <div style={aiBubble}>
                  <div style={bubbleHeader}>
                    <FaRobot style={{ fontSize: 10, color: '#8b5cf6' }} />
                    <span>Claude</span>
                    <FaSpinner className="spin" style={{ fontSize: 10, marginLeft: 4 }} />
                  </div>
                  <div className="markdown-body" style={{ fontSize: 13 }}>
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{streamText}</ReactMarkdown>
                  </div>
                </div>
              )}
              {/* status (chunk 도착 전) */}
              {streaming && !streamText && (
                <div style={{ ...aiBubble, borderStyle: 'dashed' }}>
                  <div style={bubbleHeader}>
                    <FaRobot style={{ fontSize: 10, color: '#8b5cf6' }} />
                    <span>Claude</span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--text-muted)' }}>
                    <FaSpinner className="spin" />
                    <span>{statusText || '준비 중...'}</span>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>
          )}

          {/* 입력 영역 */}
          <div style={inputAreaStyle}>
            <textarea
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={onKeyDown}
              placeholder={messages.length === 0
                ? "예: '왜 이 인덱스를 추천했나요?', 'PostgreSQL 에서는 어떻게 다른가요?'"
                : "추가 질문... (Shift+Enter: 줄바꿈)"}
              rows={2}
              disabled={streaming}
              style={textareaStyle}
            />
            <button
              onClick={askFollowUp}
              disabled={streaming || !question.trim()}
              title="질문 전송 (Enter)"
              style={{
                ...sendBtn,
                opacity: streaming || !question.trim() ? 0.4 : 1,
                cursor: streaming || !question.trim() ? 'not-allowed' : 'pointer',
              }}>
              {streaming ? <FaSpinner className="spin" /> : <FaPaperPlane />}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

// ── styles ─────────────────────────────────────────────────────────────────

const containerStyle: React.CSSProperties = {
  marginTop: 12,
  border: '1px solid var(--border-color)',
  borderRadius: 10,
  background: 'var(--bg-secondary)',
  overflow: 'hidden',
}
const headerBtn: React.CSSProperties = {
  width: '100%',
  display: 'flex', alignItems: 'center', gap: 8,
  padding: '10px 14px',
  background: 'rgba(139,92,246,0.06)',
  border: 'none', cursor: 'pointer',
  fontSize: 13, fontWeight: 600,
  color: 'var(--text-primary)',
}
const countChip: React.CSSProperties = {
  marginLeft: 6,
  fontSize: 10, fontWeight: 700,
  padding: '1px 7px', borderRadius: 10,
  background: '#8b5cf6', color: '#fff',
}
const miniBtn: React.CSSProperties = {
  background: 'transparent',
  border: '1px solid var(--border-color)',
  borderRadius: 5,
  padding: '2px 6px',
  color: 'var(--text-muted)',
  cursor: 'pointer',
  marginRight: 4,
}
const bodyStyle: React.CSSProperties = {
  padding: 12,
  borderTop: '1px solid var(--border-color)',
  display: 'flex', flexDirection: 'column', gap: 10,
}
const hintStyle: React.CSSProperties = {
  padding: '10px 12px',
  background: 'var(--bg-primary)',
  border: '1px dashed var(--border-color)',
  borderRadius: 8,
  fontSize: 12, color: 'var(--text-muted)',
  lineHeight: 1.5,
}
const messagesStyle: React.CSSProperties = {
  display: 'flex', flexDirection: 'column', gap: 8,
  maxHeight: 360, overflowY: 'auto',
  padding: '4px 2px',
}
const userBubble: React.CSSProperties = {
  alignSelf: 'flex-end',
  maxWidth: '85%',
  padding: '8px 12px', borderRadius: 10,
  background: 'var(--accent-subtle)',
  border: '1px solid var(--border-color)',
}
const aiBubble: React.CSSProperties = {
  alignSelf: 'flex-start',
  maxWidth: '95%',
  padding: '8px 12px', borderRadius: 10,
  background: 'var(--bg-primary)',
  border: '1px solid var(--border-color)',
}
const bubbleHeader: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 5,
  fontSize: 11, fontWeight: 600, color: 'var(--text-muted)',
  marginBottom: 4,
}
const inputAreaStyle: React.CSSProperties = {
  display: 'flex', gap: 6, alignItems: 'flex-end',
}
const textareaStyle: React.CSSProperties = {
  flex: 1,
  fontSize: 13, lineHeight: 1.5,
  padding: '8px 10px', borderRadius: 8,
  border: '1px solid var(--border-color)',
  background: 'var(--bg-primary)', color: 'var(--text-primary)',
  resize: 'vertical', minHeight: 44, maxHeight: 120,
  outline: 'none',
  fontFamily: 'inherit',
}
const sendBtn: React.CSSProperties = {
  padding: '10px 14px',
  borderRadius: 8,
  background: '#8b5cf6', color: '#fff', border: 'none',
  fontSize: 13,
}
