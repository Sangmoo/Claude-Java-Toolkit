import { useEffect, useState } from 'react'
import { FaServer, FaPlus, FaTrash, FaCheck, FaTimes, FaSync, FaPlug, FaShieldAlt } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'
import { useAuthStore } from '../stores/authStore'

interface DbProfile {
  id: number
  name: string
  // 백엔드가 entity 그대로 반환할 때 호환: dbType/host/port/dbName 가 없으면 maskedUrl 사용
  dbType?: string
  host?: string
  port?: number
  dbName?: string
  description?: string
  maskedUrl?: string
  username?: string
  active: boolean
  // v4.7.x — #G3: Live 분석 토글 상태 + 옵션 user
  readOnlyForLiveAnalysis?: boolean
  liveAnalysisUser?: string
  liveQueryTimeoutSeconds?: number
}

export default function DbProfilesPage() {
  const [profiles, setProfiles] = useState<DbProfile[]>([])
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ name: '', dbType: 'oracle', host: '', port: '1521', dbName: '', username: '', password: '' })
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<string | null>(null)
  // v4.7.x — #G3: Live 분석 활성화 확인 모달 (보안 게이트 — ADMIN 명시적 동의)
  const [liveConfirm, setLiveConfirm] = useState<DbProfile | null>(null)
  const api = useApi()
  const toast = useToast()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'

  const reload = () => {
    api.get('/api/v1/db-profiles').then((data) => { if (data) setProfiles(data as DbProfile[]) })
  }

  useEffect(() => { reload() }, [])

  const activate = async (id: number) => {
    await fetch(`/db-profiles/${id}/apply`, { method: 'POST', credentials: 'include' })
    setProfiles((prev) => prev.map((p) => ({ ...p, active: p.id === id })))
    toast.success('DB 프로필 활성화')
  }

  const remove = async (id: number) => {
    if (!confirm('이 프로필을 삭제하시겠습니까?')) return
    await fetch(`/db-profiles/${id}/delete`, { method: 'POST', credentials: 'include' })
    setProfiles((prev) => prev.filter((p) => p.id !== id))
    toast.success('삭제됨')
  }

  /**
   * v4.7.x — #G3: Live 분석 토글.
   * - 활성화 (false → true): 보안 확인 모달 노출 → 사용자 명시적 동의 후만 토글
   * - 비활성화 (true → false): 즉시 (위험 없음)
   * - ADMIN 만 가능
   */
  const toggleLiveAnalysis = async (p: DbProfile, nextValue: boolean) => {
    if (!isAdmin) {
      toast.error('ADMIN 권한이 필요합니다.')
      return
    }
    if (nextValue) {
      // 활성화 — 확인 모달
      setLiveConfirm(p)
      return
    }
    // 비활성화 — 바로 진행
    await applyLiveAnalysisToggle(p.id, false)
  }

  const applyLiveAnalysisToggle = async (id: number, enabled: boolean) => {
    try {
      const res = await fetch(`/db-profiles/${id}/live-analysis?enabled=${enabled}`, {
        method: 'POST',
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (res.ok && d?.success) {
        setProfiles((prev) =>
          prev.map((q) => (q.id === id ? { ...q, readOnlyForLiveAnalysis: enabled } : q))
        )
        toast.success(enabled ? '✅ Live 분석 활성화됨' : '⏸ Live 분석 비활성화됨')
      } else {
        toast.error(d?.error || '토글 실패')
      }
    } catch {
      toast.error('요청 실패')
    } finally {
      setLiveConfirm(null)
    }
  }

  const testConnection = async () => {
    setTesting(true); setTestResult(null)
    try {
      const res = await fetch('/db-profiles/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(form),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      // 응답에 url 도 함께 포함되어 디버깅에 유용
      const urlSuffix = d?.url ? `\n[조립된 URL] ${d.url}` : ''
      if (d?.success) {
        setTestResult('ok:연결 성공 — ' + (d.message || 'OK') + urlSuffix)
      } else {
        setTestResult('error:' + (d?.error || `HTTP ${res.status}`) + urlSuffix)
      }
    } catch (e) {
      setTestResult('error:요청 실패 — ' + String(e))
    }
    setTesting(false)
  }

  const saveProfile = async () => {
    try {
      const res = await fetch('/db-profiles/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(form),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      // 백엔드가 200 OK + success:false 를 반환할 수 있어 양쪽 모두 검사
      if (res.ok && d?.success) {
        toast.success('프로필이 추가되었습니다.')
        setShowModal(false)
        setForm({ name: '', dbType: 'oracle', host: '', port: '1521', dbName: '', username: '', password: '' })
        setTestResult(null)
        reload()
      } else {
        toast.error(d?.error || `저장 실패 (HTTP ${res.status})`)
      }
    } catch (e) {
      toast.error('저장 오류 — ' + String(e))
    }
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaServer style={{ color: '#3b82f6' }} /> DB 프로필
        </h2>
        <button onClick={() => setShowModal(true)} style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', fontSize: '13px', cursor: 'pointer' }}>
          <FaPlus /> 프로필 추가
        </button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {profiles.map((p) => {
          const liveOn = !!p.readOnlyForLiveAnalysis
          // dbType/host 가 없을 때는 maskedUrl 사용 (백엔드 응답 호환)
          const subtitle = p.dbType && p.host
            ? `${p.dbType} · ${p.host}:${p.port}/${p.dbName}`
            : (p.maskedUrl || p.username || '(URL)')
          return (
          <div key={p.id} style={{
            display: 'flex', alignItems: 'center', gap: '12px',
            padding: '14px 16px', background: 'var(--bg-secondary)',
            border: `1px solid ${p.active ? 'var(--green)' : 'var(--border-color)'}`,
            borderRadius: '10px',
          }}>
            <FaServer style={{ color: p.active ? 'var(--green)' : 'var(--text-muted)', fontSize: '18px' }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 600, fontSize: '14px', display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                {p.name}
                {p.active && <span style={{ fontSize: '11px', color: 'var(--green)' }}>(활성)</span>}
                {/* v4.7.x — #G3: Live 분석 활성화 배지 */}
                {liveOn && (
                  <span title="이 프로필은 Live DB 분석에 사용 가능 — SQL 분석 페이지의 🔌 chip 에 노출됩니다"
                        style={{
                          fontSize: 10, padding: '2px 8px', borderRadius: 10,
                          background: 'rgba(16,185,129,0.12)', color: '#10b981', fontWeight: 700,
                          display: 'inline-flex', alignItems: 'center', gap: 3,
                        }}>
                    <FaPlug style={{ fontSize: 9 }} /> Live 분석 ON
                  </span>
                )}
              </div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {subtitle}
              </div>
            </div>

            {/* v4.7.x — #G3: Live 분석 토글 (ADMIN 만) */}
            {isAdmin && (
              <button
                onClick={() => toggleLiveAnalysis(p, !liveOn)}
                title={liveOn
                  ? 'Live 분석 비활성화 — SQL 페이지 chip dropdown 에서 제거'
                  : 'Live 분석 활성화 — read-only 권한 확인 후 진행'}
                style={{
                  display: 'flex', alignItems: 'center', gap: 6,
                  padding: '5px 12px', borderRadius: 16, fontSize: 11, fontWeight: 600,
                  cursor: 'pointer',
                  border: `1px solid ${liveOn ? '#10b981' : 'var(--border-color)'}`,
                  background: liveOn ? 'rgba(16,185,129,0.12)' : 'transparent',
                  color: liveOn ? '#10b981' : 'var(--text-muted)',
                }}>
                <FaPlug style={{ fontSize: 10 }} />
                Live: {liveOn ? 'ON' : 'OFF'}
              </button>
            )}

            {!p.active && <button onClick={() => activate(p.id)} style={{ background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 10px', fontSize: '12px', cursor: 'pointer', color: 'var(--green)' }}><FaCheck /> 활성화</button>}
            <button onClick={() => remove(p.id)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--red)', fontSize: '14px' }}><FaTrash /></button>
          </div>
          )
        })}
        {profiles.length === 0 && <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>등록된 DB 프로필이 없습니다.</div>}
      </div>

      {/* v4.7.x — #G3: Live 분석 활성화 보안 확인 모달 */}
      {liveConfirm && (
        <div style={overlayStyle} onClick={() => setLiveConfirm(null)}>
          <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
              <FaShieldAlt style={{ color: '#f59e0b', fontSize: 22 }} />
              <h3 style={{ fontSize: 16, fontWeight: 700, margin: 0 }}>
                Live 분석 활성화 — 보안 확인
              </h3>
            </div>
            <div style={{ fontSize: 13, lineHeight: 1.7, color: 'var(--text-default)' }}>
              <p style={{ margin: '0 0 12px' }}>
                프로필 <strong style={{ color: 'var(--accent)' }}>{liveConfirm.name}</strong> 을 Live 분석에 사용 가능하도록 활성화합니다.
              </p>
              <p style={{ margin: '0 0 8px', fontWeight: 600 }}>활성화 후 동작:</p>
              <ul style={{ margin: '0 0 12px', paddingLeft: 20, fontSize: 12, color: 'var(--text-sub)' }}>
                <li>SQL 분석 페이지 (/advisor, /explain, /sql/index-advisor 등 6개) 의 🔌 Live DB chip 에 이 프로필이 선택 가능</li>
                <li>분석 시 백엔드가 자동으로 EXPLAIN PLAN + 테이블 통계 + 인덱스 메타를 수집</li>
                <li>SqlClassifier 가 SELECT/EXPLAIN/DESC/WITH 만 통과시키지만, DB 권한도 read-only 인 것이 권장 (이중 차단)</li>
              </ul>
              <div style={{
                background: 'rgba(245,158,11,0.08)', border: '1px solid #f59e0b',
                borderRadius: 6, padding: '10px 12px', fontSize: 12, marginBottom: 12,
              }}>
                <strong style={{ color: '#f59e0b' }}>⚠️ 운영자 확인 사항</strong>
                <ul style={{ margin: '6px 0 0', paddingLeft: 18, color: 'var(--text-default)' }}>
                  <li>이 프로필의 user (<code>{liveConfirm.username || '(?)'}</code>) 가 read-only 권한만 가지고 있나요?</li>
                  <li>인덱스 시뮬레이션을 사용할 거라면 staging schema 의 CREATE/DROP INDEX 권한이 필요합니다.</li>
                  <li>비활성화는 언제든 가능합니다 (이 페이지에서 토글 OFF).</li>
                </ul>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button onClick={() => setLiveConfirm(null)} style={outlineBtn}>
                취소
              </button>
              <button
                onClick={() => applyLiveAnalysisToggle(liveConfirm.id, true)}
                style={{ ...primaryBtn, flex: 'none', padding: '8px 18px', background: '#10b981' }}>
                <FaCheck /> 확인하고 활성화
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 추가 모달 */}
      {showModal && (
        <div style={overlayStyle} onClick={() => setShowModal(false)}>
          <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: 700 }}>DB 프로필 추가</h3>
              <button onClick={() => setShowModal(false)} style={iconBtn}><FaTimes /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <Field label="프로필 이름">
                <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="예: 개발 DB" style={inputStyle} />
              </Field>
              <Field label="DB 유형">
                <div style={{ display: 'flex', gap: '4px' }}>
                  {['oracle', 'mysql', 'postgresql'].map((t) => (
                    <button key={t} onClick={() => setForm({ ...form, dbType: t, port: t === 'oracle' ? '1521' : t === 'mysql' ? '3306' : '5432' })}
                      style={{
                        padding: '5px 14px', borderRadius: '16px', fontSize: '12px', cursor: 'pointer',
                        border: `1px solid ${form.dbType === t ? 'var(--accent)' : 'var(--border-color)'}`,
                        background: form.dbType === t ? 'var(--accent-subtle)' : 'transparent',
                        color: form.dbType === t ? 'var(--accent)' : 'var(--text-sub)',
                      }}>
                      {t}
                    </button>
                  ))}
                </div>
              </Field>
              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '8px' }}>
                <Field label="호스트">
                  <input value={form.host} onChange={(e) => setForm({ ...form, host: e.target.value })} placeholder="192.168.1.100" style={inputStyle} />
                </Field>
                <Field label="포트">
                  <input value={form.port} onChange={(e) => setForm({ ...form, port: e.target.value })} style={inputStyle} />
                </Field>
              </div>
              <Field label="DB 이름 / SID">
                <input value={form.dbName} onChange={(e) => setForm({ ...form, dbName: e.target.value })} placeholder="ORCL" style={inputStyle} />
              </Field>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                <Field label="사용자명">
                  <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} style={inputStyle} />
                </Field>
                <Field label="비밀번호">
                  <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} style={inputStyle} />
                </Field>
              </div>
              {testResult && (
                <div style={{
                  fontSize: '12px',
                  color: testResult.startsWith('ok') ? 'var(--green)' : 'var(--red)',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  background: 'var(--bg-primary)',
                  border: '1px solid var(--border-color)',
                  borderRadius: 6,
                  padding: '8px 10px',
                }}>
                  {testResult.substring(testResult.indexOf(':') + 1)}
                </div>
              )}
              <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                <button onClick={testConnection} disabled={testing} style={outlineBtn}><FaSync /> 연결 테스트</button>
                <button onClick={saveProfile} style={primaryBtn}>저장</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: 'block', fontSize: '11px', color: 'var(--text-muted)', marginBottom: '3px', fontWeight: 600 }}>{label}</label>{children}</div>
}

const overlayStyle: React.CSSProperties = { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500 }
const modalStyle: React.CSSProperties = { background: 'var(--bg-secondary)', borderRadius: '16px', border: '1px solid var(--border-color)', padding: '24px', width: 'min(500px, 90vw)' }
const inputStyle: React.CSSProperties = { width: '100%', padding: '8px 10px', fontSize: '13px' }
const iconBtn: React.CSSProperties = { background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '16px' }
const outlineBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '5px', padding: '8px 16px', borderRadius: '8px', fontSize: '13px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-sub)', cursor: 'pointer' }
const primaryBtn: React.CSSProperties = { flex: 1, padding: '8px 20px', borderRadius: '8px', fontSize: '13px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontWeight: 600 }
