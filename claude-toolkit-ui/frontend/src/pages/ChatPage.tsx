import { useEffect, useState, useRef, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaPlus, FaPaperPlane, FaTrash, FaPen, FaEraser, FaDownload,
  FaComments, FaCopy, FaCheck, FaTimes,
} from 'react-icons/fa'

import { useToast } from '../hooks/useToast'
import { copyToClipboard } from '../utils/clipboard'

interface ChatSession {
  id: number
  title: string
  updatedAt: string
}

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

export default function ChatPage() {
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [sessionSearch, setSessionSearch] = useState('')
  const [activeId, setActiveId] = useState<number | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [streamText, setStreamText] = useState('')
  // v4.4.x — 백엔드 SSE "status" 이벤트로 받는 진행 상태 ("프로젝트 코드 탐색 중..." 등).
  // streamText 가 비어있을 때만 표시 → 첫 응답 chunk 가 오면 자동으로 사라짐.
  const [statusText, setStatusText] = useState('')
  const [copiedId, setCopiedId] = useState<number | null>(null)
  const [renamingId, setRenamingId] = useState<number | null>(null)
  const [renameTitle, setRenameTitle] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const esRef = useRef<EventSource | null>(null)
  const toast = useToast()
  const [params] = useSearchParams()

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  const loadSessions = useCallback(async () => {
    try {
      const res = await fetch('/chat/sessions', { credentials: 'include' })
      if (res.ok) { const data = await res.json(); if (Array.isArray(data)) setSessions(data) }
    } catch { /* silent */ }
  }, [])

  const loadMessages = useCallback(async (sid: number) => {
    try {
      const res = await fetch(`/chat/sessions/${sid}/messages`, { credentials: 'include' })
      if (res.ok) { const data = await res.json(); if (Array.isArray(data)) { setMessages(data); setTimeout(scrollToBottom, 100) } }
    } catch { /* silent */ }
  }, [scrollToBottom])

  useEffect(() => {
    loadSessions()
  }, [loadSessions])

  // Handle context from URL (analysis result → chat)
  useEffect(() => {
    const ctx = params.get('context')
    if (ctx) {
      setInput(ctx)
    }
  }, [params])

  const switchSession = useCallback((id: number) => {
    setActiveId(id)
    setMessages([])
    setStreamText('')
    loadMessages(id)
  }, [loadMessages])

  // Auto-select first session
  useEffect(() => {
    if (sessions.length > 0 && !activeId) {
      switchSession(sessions[0].id)
    }
  }, [sessions, activeId, switchSession])

  const createSession = async () => {
    const res = await fetch("/chat/sessions/new", { method: "POST", credentials: "include" }); const data = res.ok ? await res.json() : null
    if (data) {
      await loadSessions()
      switchSession(data.id)
    }
  }

  const deleteSession = async (id: number) => {
    await fetch(`/chat/sessions/${id}/delete`, { method: "POST", credentials: "include" })
    if (activeId === id) {
      setActiveId(null)
      setMessages([])
    }
    await loadSessions()
  }

  const startRename = (s: ChatSession) => {
    setRenamingId(s.id)
    setRenameTitle(s.title)
  }

  const confirmRename = async () => {
    if (renamingId && renameTitle.trim()) {
      
      // rename expects form param, use URLSearchParams
      await fetch(`/chat/sessions/${renamingId}/rename`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ title: renameTitle }),
        credentials: 'include',
      })
      setRenamingId(null)
      await loadSessions()
    }
  }

  const clearSession = async () => {
    if (activeId) {
      await fetch(`/chat/sessions/${activeId}/clear`, { method: "POST", credentials: "include" })
      setMessages([])
    }
  }

  const sendMessage = async () => {
    if (!input.trim() || streaming) return

    const userMsg = input.trim()
    setInput('')
    setMessages((prev) => [...prev, { id: Date.now(), role: 'user', content: userMsg, createdAt: '' }])
    setTimeout(scrollToBottom, 50)

    setStreaming(true)
    setStreamText('')
    setStatusText('연결 중...')

    try {
      // Step 1: POST /chat/send
      const body = new URLSearchParams({ message: userMsg })
      if (activeId) body.set('sessionId', String(activeId))
      const context = params.get('context')
      if (context) body.set('context', context)

      const sendRes = await fetch('/chat/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
        credentials: 'include',
      })
      const sendData = await sendRes.json()

      if (!sendData.success) {
        toast.error(sendData.error || '메시지 전송 실패')
        setStreaming(false)
        return
      }

      if (!activeId && sendData.sessionId) {
        setActiveId(sendData.sessionId)
        loadSessions()
      }

      // Step 2: SSE stream
      let accumulated = ''
      const es = new EventSource('/chat/stream', { withCredentials: true })
      esRef.current = es

      es.onmessage = (e) => {
        const data = e.data
        if (data === '[DONE]' || data === 'done') {
          es.close()
          esRef.current = null
          setStreaming(false)
          setMessages((prev) => [
            ...prev,
            { id: Date.now(), role: 'assistant', content: accumulated, createdAt: '' },
          ])
          setStreamText('')
          setStatusText('')
          loadSessions() // refresh titles
          return
        }
        // 첫 chunk 도착 → 진행 상태 메시지 제거
        if (!accumulated) setStatusText('')
        // v4.4.x — 가짜 \n 제거 (이전: data + '\n').
        //   원인: 모든 chunk 끝에 강제로 \n 을 붙여서
        //   "🔍 분" + "\n" + "석 결과" + "\n" → 한글 단어가 줄바꿈됨
        //   ("🔍 분\n석 결과" 처럼 "분석"이 갈라져 마크다운이 깨짐)
        //   SseStreamController.sendSseData 가 이미 원본 newline 을
        //   data: 라인 분할로 보존하므로 클라이언트에서 추가 X.
        accumulated += data
        setStreamText(accumulated)
        scrollToBottom()
      }

      es.addEventListener('error_msg', (e: MessageEvent) => {
        toast.error(e.data || '스트리밍 오류')
        es.close()
        esRef.current = null
        setStreaming(false)
        setStatusText('')
      })

      // v4.4.x — 백엔드가 enricher 진행 단계를 알려주는 status 이벤트
      es.addEventListener('status', (e: MessageEvent) => {
        if (!accumulated) setStatusText(e.data || '')
      })

      es.onerror = () => {
        if (accumulated) {
          setMessages((prev) => [
            ...prev,
            { id: Date.now(), role: 'assistant', content: accumulated, createdAt: '' },
          ])
          setStreamText('')
        }
        es.close()
        esRef.current = null
        setStreaming(false)
        setStatusText('')
      }
    } catch {
      toast.error('메시지 전송 중 오류가 발생했습니다.')
      setStreaming(false)
      setStatusText('')
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  // v4.4.x — 채팅 말풍선 우측 상단 복사 버튼
  // 변경 사항:
  //  - navigator.clipboard 직접 호출 → copyToClipboard 유틸 사용
  //    (HTTP 환경에서도 execCommand fallback 으로 동작)
  //  - 성공/실패 토스트 메시지 추가 ("복사됨!" / "복사 실패")
  //  - 아이콘 체크로 변경 + 3초 후 원복 (이전: 2초)
  const copyMessage = async (msg: ChatMessage) => {
    if (!msg.content || !msg.content.trim()) {
      toast.error('복사할 내용이 없습니다.')
      return
    }
    const ok = await copyToClipboard(msg.content)
    if (ok) {
      setCopiedId(msg.id)
      toast.success('복사됨!')
      setTimeout(() => {
        // 3초 사이에 다른 메시지를 복사했다면 그것을 덮지 않도록 가드
        setCopiedId((cur) => (cur === msg.id ? null : cur))
      }, 3000)
    } else {
      toast.error('복사 실패 — 브라우저 권한을 확인해주세요.')
    }
  }

  const exportChat = () => {
    const lines = messages.map((m) =>
      `### ${m.role === 'user' ? '사용자' : 'AI'}\n\n${m.content}\n`
    )
    const md = `# 대화 내보내기\n\n${lines.join('\n---\n\n')}`
    const blob = new Blob([md], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `chat_${new Date().toISOString().replace(/[:.]/g, '').slice(0, 15)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 60px)', gap: 0 }}>
      {/* Session List */}
      <div style={styles.sessionPanel}>
        <button style={styles.newBtn} onClick={createSession}>
          <FaPlus /> 새 대화
        </button>
        <div style={{ padding: '0 12px 8px' }}>
          <input
            type="text"
            placeholder="세션 검색..."
            value={sessionSearch}
            onChange={(e) => setSessionSearch(e.target.value)}
            style={{ width: '100%', fontSize: '12px', padding: '6px 10px' }}
          />
        </div>
        <div style={{ flex: 1, overflowY: 'auto' }}>
          {sessions
            .filter((s) => !sessionSearch || s.title.toLowerCase().includes(sessionSearch.toLowerCase()))
            .map((s) => (
            <div
              key={s.id}
              style={{
                ...styles.sessionItem,
                ...(s.id === activeId ? styles.sessionActive : {}),
              }}
              onClick={() => switchSession(s.id)}
            >
              {renamingId === s.id ? (
                <div style={{ display: 'flex', gap: '4px', width: '100%' }}>
                  <input
                    style={{ flex: 1, fontSize: '12px', padding: '2px 6px' }}
                    value={renameTitle}
                    onChange={(e) => setRenameTitle(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && confirmRename()}
                    onClick={(e) => e.stopPropagation()}
                    autoFocus
                  />
                  <button style={styles.iconBtn} onClick={(e) => { e.stopPropagation(); confirmRename() }}><FaCheck /></button>
                  <button style={styles.iconBtn} onClick={(e) => { e.stopPropagation(); setRenamingId(null) }}><FaTimes /></button>
                </div>
              ) : (
                <>
                  <FaComments style={{ flexShrink: 0, color: 'var(--text-muted)' }} />
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {s.title}
                  </span>
                  <div style={{ display: 'flex', gap: '2px', opacity: 0.6 }} className="session-actions">
                    <button style={styles.iconBtn} onClick={(e) => { e.stopPropagation(); startRename(s) }} title="이름 변경"><FaPen /></button>
                    <button style={styles.iconBtn} onClick={(e) => { e.stopPropagation(); deleteSession(s.id) }} title="삭제"><FaTrash /></button>
                  </div>
                </>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Chat Area */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        {/* Chat Header */}
        <div style={styles.chatHeader}>
          <span style={{ fontWeight: 600, fontSize: '14px' }}>
            {sessions.find((s) => s.id === activeId)?.title ?? 'AI 채팅'}
          </span>
          <div style={{ display: 'flex', gap: '6px' }}>
            <button style={styles.headerBtn} onClick={clearSession} title="대화 초기화"><FaEraser /></button>
            <button style={styles.headerBtn} onClick={exportChat} title="내보내기"><FaDownload /></button>
          </div>
        </div>

        {/* Messages */}
        <div style={styles.messagesArea}>
          {messages.length === 0 && !streaming && (
            <div style={styles.emptyState}>
              <FaComments style={{ fontSize: '40px', color: 'var(--accent)', opacity: 0.3 }} />
              <p>Claude에게 질문해보세요</p>
            </div>
          )}
          {messages.map((msg) => (
            <div key={msg.id} style={{ ...styles.bubble, ...(msg.role === 'user' ? styles.userBubble : styles.aiBubble) }}>
              <div style={styles.bubbleHeader}>
                <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>
                  {msg.role === 'user' ? '사용자' : 'AI'}
                </span>
                <button style={styles.iconBtn} onClick={() => copyMessage(msg)} title="복사">
                  {copiedId === msg.id ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
                </button>
              </div>
              {msg.role === 'assistant' ? (
                <div className="markdown-body">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                </div>
              ) : (
                <pre style={styles.userText}>{msg.content}</pre>
              )}
            </div>
          ))}
          {streaming && streamText && (
            <div style={{ ...styles.bubble, ...styles.aiBubble }}>
              <div style={styles.bubbleHeader}>
                <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>AI</span>
                <span style={styles.streamingDot} />
              </div>
              <pre style={{ ...styles.userText, color: 'var(--text-sub)' }}>{streamText}</pre>
            </div>
          )}
          {/* v4.4.x — 응답 chunk 가 오기 전 진행 상태. enricher 가 길어질 때 빈 화면을 막는다. */}
          {streaming && !streamText && (
            <div style={{ ...styles.bubble, ...styles.aiBubble, ...styles.statusBubble }}>
              <div style={styles.bubbleHeader}>
                <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>AI</span>
                <span style={styles.streamingDot} />
              </div>
              <div style={styles.statusRow}>
                <span style={styles.statusSpinner} />
                <span style={styles.statusText}>{statusText || '준비 중...'}</span>
                <span style={styles.statusDots}>
                  <span style={{ ...styles.dot, animationDelay: '0s'   }}>●</span>
                  <span style={{ ...styles.dot, animationDelay: '0.2s' }}>●</span>
                  <span style={{ ...styles.dot, animationDelay: '0.4s' }}>●</span>
                </span>
              </div>
              <div style={styles.statusHint}>
                대규모 프로젝트일수록 시간이 더 걸릴 수 있습니다 (약 5–30 초).
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Input */}
        <div style={styles.inputArea}>
          <textarea
            ref={textareaRef}
            style={styles.textarea}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="메시지를 입력하세요... (Shift+Enter: 줄바꿈)"
            rows={3}
            disabled={streaming}
          />
          <button
            style={{ ...styles.sendBtn, opacity: streaming || !input.trim() ? 0.4 : 1 }}
            onClick={sendMessage}
            disabled={streaming || !input.trim()}
          >
            <FaPaperPlane />
          </button>
        </div>
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  sessionPanel: {
    width: '240px',
    borderRight: '1px solid var(--border-color)',
    background: 'var(--bg-secondary)',
    display: 'flex',
    flexDirection: 'column',
    flexShrink: 0,
  },
  newBtn: {
    display: 'flex', alignItems: 'center', gap: '8px', justifyContent: 'center',
    margin: '12px', padding: '8px', borderRadius: '8px',
    background: 'var(--accent)', color: '#fff', border: 'none', fontSize: '13px', fontWeight: 600,
    cursor: 'pointer',
  },
  sessionItem: {
    display: 'flex', alignItems: 'center', gap: '8px',
    padding: '8px 12px', margin: '2px 8px', borderRadius: '6px',
    fontSize: '13px', cursor: 'pointer', color: 'var(--text-sub)',
    transition: 'all 0.15s',
  },
  sessionActive: {
    background: 'var(--accent-subtle)', color: 'var(--accent)',
  },
  iconBtn: {
    background: 'none', border: 'none', color: 'var(--text-muted)',
    cursor: 'pointer', padding: '2px', fontSize: '11px',
  },
  chatHeader: {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '10px 16px', borderBottom: '1px solid var(--border-color)',
    background: 'var(--bg-secondary)',
  },
  headerBtn: {
    background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px',
    color: 'var(--text-sub)', padding: '5px 8px', cursor: 'pointer', fontSize: '12px',
  },
  messagesArea: {
    flex: 1, overflowY: 'auto', padding: '16px',
    display: 'flex', flexDirection: 'column', gap: '12px',
  },
  emptyState: {
    flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center',
    justifyContent: 'center', gap: '12px', color: 'var(--text-muted)', fontSize: '14px',
  },
  bubble: {
    padding: '12px 16px', borderRadius: '12px', maxWidth: '85%',
    border: '1px solid var(--border-color)',
  },
  userBubble: {
    alignSelf: 'flex-end', background: 'var(--accent-subtle)',
  },
  aiBubble: {
    alignSelf: 'flex-start', background: 'var(--bg-secondary)',
  },
  bubbleHeader: {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    marginBottom: '6px',
  },
  userText: {
    margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
    fontFamily: 'inherit', fontSize: '14px', background: 'transparent', border: 'none',
    color: 'var(--text-primary)',
  },
  streamingDot: {
    width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent)',
    animation: 'pulse 1s infinite',
  },
  // ── v4.4.x AI 진행 표시 (응답 chunk 도착 전) ───────────────────────────
  statusBubble: {
    background: 'var(--bg-secondary)',
    border: '1px dashed var(--border-color)',
  },
  statusRow: {
    display: 'flex', alignItems: 'center', gap: '10px', padding: '4px 0',
  },
  statusSpinner: {
    width: '14px', height: '14px', borderRadius: '50%',
    border: '2px solid var(--border-color)',
    borderTopColor: 'var(--accent)',
    animation: 'spin 0.8s linear infinite',
    display: 'inline-block',
  },
  statusText: {
    fontSize: '13px', fontWeight: 500, color: 'var(--text-primary)',
  },
  statusDots: {
    display: 'inline-flex', gap: '2px', fontSize: '10px', color: 'var(--accent)',
    marginLeft: '4px',
  },
  dot: {
    animation: 'blink 1.2s infinite',
  },
  statusHint: {
    marginTop: '6px', fontSize: '11px', color: 'var(--text-muted)',
  },
  inputArea: {
    display: 'flex', gap: '8px', padding: '12px 16px',
    borderTop: '1px solid var(--border-color)', background: 'var(--bg-secondary)',
  },
  textarea: {
    flex: 1, resize: 'none', borderRadius: '10px', padding: '10px 14px',
    fontSize: '14px', lineHeight: '1.5',
  },
  sendBtn: {
    alignSelf: 'flex-end', padding: '10px 14px', borderRadius: '10px',
    background: 'var(--accent)', color: '#fff', border: 'none',
    cursor: 'pointer', fontSize: '14px',
  },
}
