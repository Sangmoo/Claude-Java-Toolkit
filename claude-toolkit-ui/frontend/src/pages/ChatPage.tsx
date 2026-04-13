import { useEffect, useState, useRef, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaPlus, FaPaperPlane, FaTrash, FaPen, FaEraser, FaDownload,
  FaComments, FaCopy, FaCheck, FaTimes,
} from 'react-icons/fa'

import { useToast } from '../hooks/useToast'

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
  const [activeId, setActiveId] = useState<number | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [streamText, setStreamText] = useState('')
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
          loadSessions() // refresh titles
          return
        }
        accumulated += data + '\n'
        setStreamText(accumulated)
        scrollToBottom()
      }

      es.addEventListener('error_msg', (e: MessageEvent) => {
        toast.error(e.data || '스트리밍 오류')
        es.close()
        esRef.current = null
        setStreaming(false)
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
      }
    } catch {
      toast.error('메시지 전송 중 오류가 발생했습니다.')
      setStreaming(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const copyMessage = (msg: ChatMessage) => {
    navigator.clipboard.writeText(msg.content)
    setCopiedId(msg.id)
    setTimeout(() => setCopiedId(null), 2000)
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
        <div style={{ flex: 1, overflowY: 'auto' }}>
          {sessions.map((s) => (
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
