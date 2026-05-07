import { useEffect, useState } from 'react'
import { FaKey, FaPlus, FaTrash, FaCopy, FaCheck, FaShieldAlt, FaTimes } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'
import { copyToClipboard } from '../../utils/clipboard'

/**
 * v4.7.x — #M3 Platform: API Key 관리 페이지 (ADMIN 전용).
 *
 * 기능:
 * - 신규 키 발급 (이름 + role + rate limit + TTL)
 * - 발급 직후 plaintext 1회 노출 + 클립보드 복사 + 모달 닫으면 사라짐
 * - 대장 (목록 + 사용 통계 + 회수)
 *
 * 백엔드 endpoint: /api/v1/admin/api-keys (POST/GET) + /{id}/revoke (POST)
 */

interface ApiKeyEntry {
  id: number
  name: string
  keyPrefix: string
  createdBy: string
  createdAt: string
  expiresAt?: string | null
  lastUsedAt?: string | null
  revoked: boolean
  role: string
  rateLimitPerMinute: number
  totalCalls: number
  usable: boolean
}

interface IssueResponse {
  success: boolean
  plaintext?: string
  warning?: string
  entity?: ApiKeyEntry
  error?: string
}

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKeyEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm] = useState({ name: '', role: 'READ_ONLY', rateLimitPerMinute: 60, ttlDays: 90 })
  const [issued, setIssued] = useState<IssueResponse | null>(null)
  const [copied, setCopied] = useState(false)
  const toast = useToast()

  const reload = async () => {
    setLoading(true)
    try {
      const res = await fetch('/api/v1/admin/api-keys', { credentials: 'include' })
      const d = await res.json()
      if (d?.success) setKeys(d.keys as ApiKeyEntry[])
    } catch { toast.error('목록 로드 실패') }
    setLoading(false)
  }

  useEffect(() => { reload() }, [])

  const issue = async () => {
    if (!form.name.trim()) { toast.error('이름 필수'); return }
    try {
      const res = await fetch('/api/v1/admin/api-keys', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          name: form.name.trim(),
          role: form.role,
          rateLimitPerMinute: form.rateLimitPerMinute,
          ttlDays: form.ttlDays > 0 ? form.ttlDays : null,
        }),
      })
      const d = await res.json() as IssueResponse
      if (d.success) {
        setIssued(d)
        setShowCreate(false)
        setForm({ name: '', role: 'READ_ONLY', rateLimitPerMinute: 60, ttlDays: 90 })
        reload()
      } else {
        toast.error(d.error || '발급 실패')
      }
    } catch { toast.error('발급 요청 실패') }
  }

  const revoke = async (id: number, name: string) => {
    if (!confirm(`키 '${name}' 을 회수하시겠습니까?\n이후 이 키로 호출하면 401 응답.`)) return
    try {
      const res = await fetch(`/api/v1/admin/api-keys/${id}/revoke`, {
        method: 'POST', credentials: 'include',
      })
      const d = await res.json()
      if (d.success) {
        toast.success('회수됨')
        reload()
      } else {
        toast.error(d.error || '회수 실패')
      }
    } catch { toast.error('요청 실패') }
  }

  const copyPlaintext = async () => {
    if (!issued?.plaintext) return
    const ok = await copyToClipboard(issued.plaintext)
    if (ok) {
      setCopied(true)
      toast.success('클립보드 복사됨')
      setTimeout(() => setCopied(false), 3000)
    } else {
      toast.error('복사 실패 — 텍스트를 직접 선택해서 복사하세요')
    }
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ fontSize: 18, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 }}>
          <FaKey style={{ color: '#f59e0b' }} /> API Key 관리
        </h2>
        <button onClick={() => setShowCreate(true)}
          style={{
            display: 'flex', alignItems: 'center', gap: 6, padding: '8px 16px',
            borderRadius: 8, background: 'var(--accent)', color: '#fff', border: 'none',
            fontSize: 13, cursor: 'pointer',
          }}>
          <FaPlus /> 새 키 발급
        </button>
      </div>

      <div style={{
        padding: '10px 14px', marginBottom: 14,
        background: 'rgba(245,158,11,0.08)', border: '1px solid #f59e0b',
        borderRadius: 8, fontSize: 12, lineHeight: 1.6,
      }}>
        <strong style={{ color: '#f59e0b' }}>ℹ️ API Key 는 외부 호출 (CLI / MCP Server / SDK / curl) 인증용입니다.</strong>
        <br />
        브라우저에서 도구 사용 시엔 *세션 로그인* 으로 충분 — 별도 키 불필요. CLI / IDE 통합엔 키 발급 후 환경변수
        <code> CTK_API_KEY </code>로 사용. 자세한 가이드: <code>docs/mcp-setup.md</code>
      </div>

      {loading ? (
        <div style={{ padding: 30, textAlign: 'center', color: 'var(--text-muted)' }}>로딩 중...</div>
      ) : keys.length === 0 ? (
        <div style={{ padding: 60, textAlign: 'center', color: 'var(--text-muted)' }}>
          발급된 API Key 가 없습니다.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {keys.map(k => (
            <div key={k.id} style={{
              display: 'flex', alignItems: 'center', gap: 12, padding: '14px 16px',
              background: 'var(--bg-secondary)',
              border: `1px solid ${k.usable ? 'var(--border-color)' : 'var(--red, #ef4444)'}`,
              borderRadius: 10, opacity: k.usable ? 1 : 0.6,
            }}>
              <FaKey style={{ color: k.usable ? '#10b981' : 'var(--text-muted)', fontSize: 16 }} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: 14, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                  {k.name}
                  <code style={{ fontSize: 11, color: 'var(--text-muted)' }}>{k.keyPrefix}…</code>
                  <span style={{
                    fontSize: 10, padding: '1px 7px', borderRadius: 10, fontWeight: 700,
                    background: k.role === 'ADMIN' ? '#ef4444' : k.role === 'WRITE' ? '#3b82f6' : '#10b981',
                    color: '#fff',
                  }}>{k.role}</span>
                  {k.revoked && <span style={{ fontSize: 10, color: 'var(--red, #ef4444)' }}>● 회수됨</span>}
                  {!k.revoked && k.expiresAt && new Date(k.expiresAt) < new Date()
                    && <span style={{ fontSize: 10, color: 'var(--red, #ef4444)' }}>● 만료됨</span>}
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                  발급: {k.createdBy} · {k.createdAt}
                  {k.expiresAt && ` · 만료: ${k.expiresAt}`}
                  {k.lastUsedAt ? ` · 마지막 사용: ${k.lastUsedAt}` : ' · 미사용'}
                  {' · '}호출 {k.totalCalls.toLocaleString()}회
                  {' · '}분당 {k.rateLimitPerMinute}회
                </div>
              </div>
              {!k.revoked && (
                <button onClick={() => revoke(k.id, k.name)}
                  title="회수" style={{
                    background: 'none', border: 'none', cursor: 'pointer',
                    color: 'var(--red, #ef4444)', fontSize: 14,
                  }}>
                  <FaTrash />
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {/* 발급 모달 */}
      {showCreate && (
        <div style={overlayStyle} onClick={() => setShowCreate(false)}>
          <div style={modalStyle} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h3 style={{ fontSize: 16, fontWeight: 700, margin: 0 }}>새 API Key 발급</h3>
              <button onClick={() => setShowCreate(false)} style={iconBtn}><FaTimes /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <Field label="이름 (사용처 식별용)">
                <input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })}
                  placeholder="예: Claude Code MCP / Jenkins CI" style={inputStyle} autoFocus />
              </Field>
              <Field label="권한 (Role)">
                <div style={{ display: 'flex', gap: 4 }}>
                  {['READ_ONLY', 'WRITE', 'ADMIN'].map(r => (
                    <button key={r} onClick={() => setForm({ ...form, role: r })}
                      style={{
                        padding: '5px 14px', borderRadius: 16, fontSize: 12, cursor: 'pointer',
                        border: `1px solid ${form.role === r ? 'var(--accent)' : 'var(--border-color)'}`,
                        background: form.role === r ? 'var(--accent-subtle)' : 'transparent',
                        color: form.role === r ? 'var(--accent)' : 'var(--text-sub)',
                      }}>
                      {r}
                    </button>
                  ))}
                </div>
              </Field>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                <Field label="분당 호출 한도">
                  <input type="number" min={1} max={1000}
                    value={form.rateLimitPerMinute}
                    onChange={e => setForm({ ...form, rateLimitPerMinute: Number(e.target.value) })}
                    style={inputStyle} />
                </Field>
                <Field label="TTL (일) — 0=무기한">
                  <input type="number" min={0} max={3650}
                    value={form.ttlDays}
                    onChange={e => setForm({ ...form, ttlDays: Number(e.target.value) })}
                    style={inputStyle} />
                </Field>
              </div>
              <button onClick={issue} style={primaryBtn}>발급</button>
            </div>
          </div>
        </div>
      )}

      {/* plaintext 1회 노출 모달 */}
      {issued && issued.plaintext && (
        <div style={overlayStyle}>
          <div style={modalStyle} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
              <FaShieldAlt style={{ color: '#f59e0b', fontSize: 22 }} />
              <h3 style={{ fontSize: 16, fontWeight: 700, margin: 0 }}>API Key 발급 완료 — 1회만 표시</h3>
            </div>
            <div style={{
              background: 'rgba(245,158,11,0.08)', border: '1px solid #f59e0b',
              borderRadius: 6, padding: '10px 12px', fontSize: 12, marginBottom: 12,
            }}>
              <strong style={{ color: '#f59e0b' }}>⚠️ 이 키는 다시 표시되지 않습니다</strong>
              <div style={{ marginTop: 4, color: 'var(--text-default)' }}>
                지금 즉시 복사 + 안전한 곳 (환경변수 / 비밀 매니저) 에 보관하세요. 잃어버리면 회수 후 새 키 발급해야 합니다.
              </div>
            </div>
            <div style={{ position: 'relative', marginBottom: 12 }}>
              <pre style={{
                background: 'var(--bg-default)', border: '1px solid var(--border-color)',
                borderRadius: 6, padding: '12px 50px 12px 12px', margin: 0,
                fontFamily: 'monospace', fontSize: 13, wordBreak: 'break-all',
                whiteSpace: 'pre-wrap',
              }}>{issued.plaintext}</pre>
              <button onClick={copyPlaintext} title="클립보드 복사" style={{
                position: 'absolute', top: 8, right: 8, padding: '6px 8px',
                background: 'var(--accent)', color: '#fff', border: 'none',
                borderRadius: 4, cursor: 'pointer', fontSize: 11,
              }}>{copied ? <FaCheck /> : <FaCopy />}</button>
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 12 }}>
              <strong>사용법:</strong>
              <pre style={{
                background: 'var(--bg-default)', padding: '6px 10px',
                margin: '4px 0', borderRadius: 4, fontSize: 11,
              }}>{`curl -H "X-Api-Key: ${issued.plaintext.substring(0, 16)}..." \\
     /api/v1/analyze`}</pre>
            </div>
            <button onClick={() => setIssued(null)} style={primaryBtn}>닫기 — 키를 안전하게 저장했음</button>
          </div>
        </div>
      )}
    </>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 11, color: 'var(--text-muted)', marginBottom: 3, fontWeight: 600 }}>{label}</label>
      {children}
    </div>
  )
}

const overlayStyle: React.CSSProperties = {
  position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
  display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500,
}
const modalStyle: React.CSSProperties = {
  background: 'var(--bg-secondary)', borderRadius: 16,
  border: '1px solid var(--border-color)', padding: 24,
  width: 'min(540px, 90vw)',
}
const inputStyle: React.CSSProperties = { width: '100%', padding: '8px 10px', fontSize: 13 }
const iconBtn: React.CSSProperties = {
  background: 'none', border: 'none', color: 'var(--text-muted)',
  cursor: 'pointer', fontSize: 16,
}
const primaryBtn: React.CSSProperties = {
  width: '100%', padding: '10px 20px', borderRadius: 8, fontSize: 13,
  background: 'var(--accent)', color: '#fff', border: 'none',
  cursor: 'pointer', fontWeight: 600,
}
