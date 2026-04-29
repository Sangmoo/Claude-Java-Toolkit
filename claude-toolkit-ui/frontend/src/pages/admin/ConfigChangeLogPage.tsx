import { useEffect, useMemo, useState } from 'react'
import {
  FaShieldAlt, FaSpinner, FaTimes, FaLock, FaSearch, FaTimesCircle,
} from 'react-icons/fa'
import { useApi } from '../../hooks/useApi'

interface LogEntry {
  id: number
  configKey: string
  configLabel: string
  category: string
  oldValue: string | null
  newValue: string | null
  sensitive: boolean
  operation: 'CREATE' | 'UPDATE' | 'DELETE'
  changedBy: string
  changedAt: string
  ipAddress: string | null
}

interface ListResponse {
  page: number
  size: number
  totalPages: number
  totalElements: number
  entries: LogEntry[]
}

interface MetaItem { username?: string; category?: string; count: number }
interface MetaResponse {
  users: MetaItem[]
  categories: MetaItem[]
  total: number
}

const CATEGORY_LABEL: Record<string, string> = {
  SETTINGS:    'Settings',
  PERMISSION:  '권한 관리',
  DB_PROFILE:  'DB 프로필',
  SECURITY:    '보안 설정',
  ROI:         'ROI 설정',
}

const CATEGORY_COLOR: Record<string, string> = {
  SETTINGS:    '#3b82f6',
  PERMISSION:  '#ef4444',
  DB_PROFILE:  '#f59e0b',
  SECURITY:    '#8b5cf6',
  ROI:         '#10b981',
}

const OPERATION_COLOR: Record<string, string> = {
  CREATE: '#10b981',
  UPDATE: '#3b82f6',
  DELETE: '#ef4444',
}

/**
 * v4.7.x — Settings/권한/DB프로필/보안 변경 감사 로그 페이지 (ADMIN 전용).
 */
export default function ConfigChangeLogPage() {
  const [page, setPage] = useState(0)
  const [size] = useState(50)
  const [data, setData] = useState<ListResponse | null>(null)
  const [meta, setMeta] = useState<MetaResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [filterCategory, setFilterCategory] = useState('')
  const [filterUser, setFilterUser]         = useState('')
  const [filterFrom, setFilterFrom]         = useState('')
  const [filterTo, setFilterTo]             = useState('')
  const [selected, setSelected] = useState<LogEntry | null>(null)
  const api = useApi()

  // 메타 (사용자 / 카테고리 옵션) — 첫 마운트 1회 + 데이터 갱신 시 같이
  const loadMeta = async () => {
    const m = await api.get('/api/v1/admin/config-changes/meta') as MetaResponse | null
    if (m) setMeta(m)
  }

  const loadData = async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      params.set('page', String(page))
      params.set('size', String(size))
      if (filterCategory) params.set('category', filterCategory)
      if (filterUser)     params.set('user', filterUser)
      if (filterFrom)     params.set('from', filterFrom)
      if (filterTo)       params.set('to', filterTo)
      const d = await api.get(`/api/v1/admin/config-changes?${params}`) as ListResponse | null
      setData(d)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadMeta()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  useEffect(() => {
    loadData()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, filterCategory, filterUser, filterFrom, filterTo])

  const filterCount = useMemo(() => {
    return (filterCategory ? 1 : 0) + (filterUser ? 1 : 0)
         + (filterFrom ? 1 : 0) + (filterTo ? 1 : 0)
  }, [filterCategory, filterUser, filterFrom, filterTo])

  const clearFilters = () => {
    setFilterCategory(''); setFilterUser(''); setFilterFrom(''); setFilterTo(''); setPage(0)
  }

  return (
    <>
      <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
        <FaShieldAlt style={{ color: '#8b5cf6' }} /> Settings 변경 감사 로그
        <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 400 }}>
          ADMIN 전용 · 외부감사 대응용
        </span>
      </h2>
      <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 16 }}>
        Settings · 권한 · DB 프로필 · 보안 등 시스템 설정 변경 이력을 기록합니다.
        민감 값(API Key, 비밀번호, Webhook URL token) 은 자동 마스킹 — 실제 값은 보존되지 않습니다.
      </p>

      {/* 필터 행 */}
      <div style={{
        display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center',
        padding: '10px 14px', marginBottom: 16,
        background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
        borderRadius: 8,
      }}>
        <FilterField label="카테고리">
          <select value={filterCategory} onChange={(e) => { setFilterCategory(e.target.value); setPage(0) }} style={selectStyle}>
            <option value="">전체</option>
            {meta?.categories?.map((c) => (
              <option key={c.category} value={c.category}>
                {CATEGORY_LABEL[c.category!] || c.category} ({c.count})
              </option>
            ))}
          </select>
        </FilterField>
        <FilterField label="변경자">
          <select value={filterUser} onChange={(e) => { setFilterUser(e.target.value); setPage(0) }} style={selectStyle}>
            <option value="">전체</option>
            {meta?.users?.map((u) => (
              <option key={u.username} value={u.username}>{u.username} ({u.count})</option>
            ))}
          </select>
        </FilterField>
        <FilterField label="시작일">
          <input type="date" value={filterFrom} onChange={(e) => { setFilterFrom(e.target.value); setPage(0) }} style={selectStyle} />
        </FilterField>
        <FilterField label="종료일">
          <input type="date" value={filterTo} onChange={(e) => { setFilterTo(e.target.value); setPage(0) }} style={selectStyle} />
        </FilterField>
        {filterCount > 0 && (
          <button onClick={clearFilters} style={{
            padding: '6px 12px', borderRadius: 6, fontSize: 12,
            background: 'transparent', color: 'var(--text-muted)',
            border: '1px solid var(--border-color)', cursor: 'pointer',
            display: 'flex', alignItems: 'center', gap: 4,
          }}>
            <FaTimesCircle /> 필터 초기화 ({filterCount})
          </button>
        )}
        <span style={{ marginLeft: 'auto', fontSize: 11, color: 'var(--text-muted)' }}>
          {meta && `누적 ${meta.total.toLocaleString()}건`}
          {data && data.totalElements !== meta?.total && ` · 필터 결과 ${data.totalElements}건`}
        </span>
      </div>

      {/* 표 */}
      <div style={{
        background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
        borderRadius: 8, overflow: 'hidden',
      }}>
        {loading && !data && (
          <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-muted)' }}>
            <FaSpinner className="spin" /> 불러오는 중...
          </div>
        )}
        {data && data.entries.length === 0 && (
          <div style={{ padding: 60, textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
            <FaSearch style={{ fontSize: 28, marginBottom: 10, color: 'var(--text-muted)' }} />
            <div>변경 이력이 없습니다.</div>
            <small>Settings 페이지에서 설정을 변경하면 여기에 기록됩니다.</small>
          </div>
        )}
        {data && data.entries.length > 0 && (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border-color)', background: 'var(--bg-card)' }}>
                <th style={th}>시각</th>
                <th style={th}>변경자</th>
                <th style={th}>카테고리</th>
                <th style={th}>변경 항목</th>
                <th style={th}>유형</th>
                <th style={th}>이전 → 이후</th>
              </tr>
            </thead>
            <tbody>
              {data.entries.map((e) => (
                <tr key={e.id}
                    onClick={() => setSelected(e)}
                    style={{
                      cursor: 'pointer',
                      borderBottom: '1px solid var(--border-color)',
                      transition: 'background 0.1s',
                    }}
                    onMouseEnter={(ev) => { (ev.currentTarget as HTMLElement).style.background = 'var(--bg-primary)' }}
                    onMouseLeave={(ev) => { (ev.currentTarget as HTMLElement).style.background = 'transparent' }}
                    title="클릭하여 상세 보기"
                >
                  <td style={{ ...td, whiteSpace: 'nowrap', fontFamily: 'monospace', fontSize: 11 }}>{e.changedAt}</td>
                  <td style={td}>
                    <strong>{e.changedBy}</strong>
                    {e.ipAddress && (
                      <div style={{ fontSize: 10, color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                        {e.ipAddress}
                      </div>
                    )}
                  </td>
                  <td style={td}>
                    <span style={{
                      display: 'inline-block', fontSize: 10, padding: '2px 6px',
                      background: (CATEGORY_COLOR[e.category] || '#64748b') + '22',
                      color: CATEGORY_COLOR[e.category] || '#64748b',
                      borderRadius: 3, fontWeight: 600,
                    }}>{CATEGORY_LABEL[e.category] || e.category}</span>
                  </td>
                  <td style={td}>
                    {e.sensitive && <FaLock style={{ color: 'var(--text-muted)', fontSize: 9, marginRight: 4 }} />}
                    {e.configLabel}
                    <div style={{ fontSize: 10, color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                      {e.configKey}
                    </div>
                  </td>
                  <td style={td}>
                    <span style={{
                      display: 'inline-block', fontSize: 10, padding: '2px 6px',
                      background: OPERATION_COLOR[e.operation] + '22',
                      color: OPERATION_COLOR[e.operation],
                      borderRadius: 3, fontWeight: 700,
                    }}>{e.operation}</span>
                  </td>
                  <td style={{ ...td, fontFamily: 'monospace', fontSize: 11, maxWidth: 360 }}>
                    <DiffPreview oldVal={e.oldValue} newVal={e.newValue} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 페이지네이션 */}
      {data && data.totalPages > 1 && (
        <div style={{ marginTop: 16, display: 'flex', justifyContent: 'center', gap: 6 }}>
          <button onClick={() => setPage(0)} disabled={page === 0} style={pageBtn}>« 처음</button>
          <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} style={pageBtn}>‹ 이전</button>
          <span style={{ alignSelf: 'center', fontSize: 12, color: 'var(--text-muted)' }}>
            {data.page + 1} / {data.totalPages}
          </span>
          <button onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))} disabled={page >= data.totalPages - 1} style={pageBtn}>다음 ›</button>
          <button onClick={() => setPage(data.totalPages - 1)} disabled={page >= data.totalPages - 1} style={pageBtn}>마지막 »</button>
        </div>
      )}

      {/* 상세 모달 */}
      {selected && (
        <div onClick={() => setSelected(null)} style={modalOverlay}>
          <div onClick={(e) => e.stopPropagation()} style={modalDialog}>
            <div style={modalHeader}>
              <div>
                <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{selected.changedAt}</div>
                <div style={{ fontSize: 14, fontWeight: 700, marginTop: 2, display: 'flex', alignItems: 'center', gap: 6 }}>
                  {selected.sensitive && <FaLock style={{ color: 'var(--text-muted)', fontSize: 11 }} />}
                  {selected.configLabel}
                </div>
              </div>
              <button onClick={() => setSelected(null)} style={{
                background: 'transparent', border: 'none', color: 'var(--text-muted)',
                cursor: 'pointer', fontSize: 14, padding: 4,
              }}><FaTimes /></button>
            </div>
            <div style={modalBody}>
              <KvRow k="변경 키"     v={<code style={kvCode}>{selected.configKey}</code>} />
              <KvRow k="카테고리"    v={
                <span style={{ color: CATEGORY_COLOR[selected.category] || '#64748b', fontWeight: 600 }}>
                  {CATEGORY_LABEL[selected.category] || selected.category}
                </span>
              } />
              <KvRow k="유형"       v={
                <span style={{ color: OPERATION_COLOR[selected.operation], fontWeight: 700 }}>
                  {selected.operation}
                </span>
              } />
              <KvRow k="변경자"     v={<><strong>{selected.changedBy}</strong>{selected.ipAddress && <span style={{ marginLeft: 8, fontSize: 11, color: 'var(--text-muted)', fontFamily: 'monospace' }}>{selected.ipAddress}</span>}</>} />
              {selected.sensitive && (
                <div style={{
                  margin: '12px 0', padding: '8px 12px', fontSize: 11,
                  background: 'rgba(139,92,246,0.12)', border: '1px solid rgba(139,92,246,0.3)',
                  borderRadius: 4, color: '#8b5cf6',
                }}>
                  🔒 민감 값 — 자동 마스킹 적용됨. 원본 값은 시스템에 저장되지 않습니다.
                </div>
              )}
              <div style={{ marginTop: 16 }}>
                <DiffPanel label="이전 값" value={selected.oldValue} />
                <DiffPanel label="이후 값" value={selected.newValue} highlight />
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

// ── helpers / sub components ─────────────────────────────────────────────

function FilterField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 3, fontSize: 11, color: 'var(--text-muted)' }}>
      <span>{label}</span>
      {children}
    </label>
  )
}

function DiffPreview({ oldVal, newVal }: { oldVal: string | null; newVal: string | null }) {
  const o = (oldVal || '').slice(0, 80)
  const n = (newVal || '').slice(0, 80)
  return (
    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
      <span style={{ color: '#ef4444', textDecoration: oldVal ? 'line-through' : undefined, opacity: 0.8 }}>
        {o || '(없음)'}{oldVal && oldVal.length > 80 ? '…' : ''}
      </span>
      <span style={{ color: 'var(--text-muted)' }}>→</span>
      <span style={{ color: '#10b981', fontWeight: 600 }}>
        {n || '(없음)'}{newVal && newVal.length > 80 ? '…' : ''}
      </span>
    </div>
  )
}

function KvRow({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', gap: 12, padding: '6px 0', borderBottom: '1px dashed var(--border-color)' }}>
      <div style={{ width: 90, fontSize: 11, color: 'var(--text-muted)', flexShrink: 0 }}>{k}</div>
      <div style={{ flex: 1, fontSize: 13 }}>{v}</div>
    </div>
  )
}

function DiffPanel({ label, value, highlight }: { label: string; value: string | null; highlight?: boolean }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600, marginBottom: 4 }}>{label}</div>
      <pre style={{
        margin: 0, padding: 10, fontFamily: 'monospace', fontSize: 12,
        background: highlight ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.05)',
        border: '1px solid ' + (highlight ? 'rgba(16,185,129,0.25)' : 'rgba(239,68,68,0.15)'),
        borderRadius: 4, whiteSpace: 'pre-wrap', wordBreak: 'break-all',
        color: 'var(--text-primary)',
        maxHeight: 240, overflowY: 'auto',
      }}>{value || '(없음)'}</pre>
    </div>
  )
}

const th: React.CSSProperties = {
  textAlign: 'left', padding: '10px 8px', fontSize: 11, fontWeight: 700,
  color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4,
}
const td: React.CSSProperties = {
  padding: '8px 8px', verticalAlign: 'top',
}
const selectStyle: React.CSSProperties = {
  padding: '5px 8px', fontSize: 13,
  borderRadius: 6, border: '1px solid var(--border-color)',
  background: 'var(--bg-card)', color: 'var(--text-primary)',
  minWidth: 140,
}
const pageBtn: React.CSSProperties = {
  padding: '5px 10px', borderRadius: 6, fontSize: 12,
  background: 'var(--bg-card)', color: 'var(--text-primary)',
  border: '1px solid var(--border-color)', cursor: 'pointer',
}
const modalOverlay: React.CSSProperties = {
  position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)',
  display: 'flex', justifyContent: 'center', alignItems: 'center',
  zIndex: 1000, padding: 24,
}
const modalDialog: React.CSSProperties = {
  background: 'var(--bg-secondary)', borderRadius: 10,
  border: '1px solid var(--border-color)',
  width: 'min(800px, 96vw)', maxHeight: '88vh',
  display: 'flex', flexDirection: 'column',
  boxShadow: '0 10px 40px rgba(0,0,0,0.35)',
}
const modalHeader: React.CSSProperties = {
  display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
  padding: '12px 16px', borderBottom: '1px solid var(--border-color)',
  background: 'var(--bg-card)', borderTopLeftRadius: 10, borderTopRightRadius: 10,
}
const modalBody: React.CSSProperties = {
  flex: 1, overflowY: 'auto', padding: '16px 20px', fontSize: 13,
}
const kvCode: React.CSSProperties = {
  background: 'var(--bg-primary)', padding: '2px 6px',
  borderRadius: 3, fontFamily: 'monospace', fontSize: 11,
}
