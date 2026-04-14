import { useState, useEffect, useRef } from 'react'
import { FaExchangeAlt, FaPlay, FaSpinner, FaCheckCircle, FaTimesCircle, FaSync } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

const tabs = ['PostgreSQL', 'MySQL', 'Oracle 11g', '백업/복원', '자동 이관']

export default function DbMigrationGuidePage() {
  const [activeTab, setActiveTab] = useState(4) // 자동 이관 탭 기본 선택

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaExchangeAlt style={{ color: '#3b82f6' }} /> DB 마이그레이션 가이드
      </h2>
      <div style={{ display: 'flex', gap: '4px', marginBottom: '16px', flexWrap: 'wrap' }}>
        {tabs.map((t, i) => (
          <button key={t} onClick={() => setActiveTab(i)} style={{
            padding: '6px 16px', borderRadius: '16px', fontSize: '13px', cursor: 'pointer',
            border: `1px solid ${activeTab === i ? 'var(--accent)' : 'var(--border-color)'}`,
            background: activeTab === i ? 'var(--accent-subtle)' : 'transparent',
            color: activeTab === i ? 'var(--accent)' : 'var(--text-sub)',
            fontWeight: activeTab === i ? 600 : 400,
          }}>{t}</button>
        ))}
      </div>
      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px', minHeight: '300px' }}>
        {activeTab === 0 && <GuideSection title="PostgreSQL 마이그레이션" steps={['Docker Compose로 PostgreSQL 실행', 'DB_TYPE=postgresql 환경변수 설정', 'mvn spring-boot:run (자동 스키마 생성)', '자동 이관 탭에서 데이터 복사']} />}
        {activeTab === 1 && <GuideSection title="MySQL 마이그레이션" steps={['MySQL 8.0+ 필요, utf8mb4 문자셋', 'DB_TYPE=mysql 환경변수 설정', 'mvn spring-boot:run (자동 스키마 생성)', '자동 이관 탭에서 데이터 복사']} />}
        {activeTab === 2 && <GuideSection title="Oracle 11g 마이그레이션" steps={['BOOLEAN→NUMBER(1), TEXT→CLOB 변환', 'ojdbc8 드라이버 의존성 추가', 'application-oracle.yml 프로필 설정', 'Oracle10gDialect 사용', '자동 이관 탭에서 데이터 복사']} />}
        {activeTab === 3 && <GuideSection title="백업/복원" steps={['H2: SCRIPT TO 명령으로 덤프', 'ZIP 백업 → 관리 > 백업/복원', 'CSV 형식으로 이력 내보내기', '복원 시 서버 중지 후 파일 교체']} />}
        {activeTab === 4 && <AutoMigrationPanel />}
      </div>
    </>
  )
}

function GuideSection({ title, steps }: { title: string; steps: string[] }) {
  return (
    <>
      <h3 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '14px' }}>{title}</h3>
      <ol style={{ paddingLeft: '20px', fontSize: '14px', color: 'var(--text-sub)', lineHeight: '2' }}>
        {steps.map((s, i) => <li key={i}>{s}</li>)}
      </ol>
    </>
  )
}

function AutoMigrationPanel() {
  const [targetType, setTargetType] = useState('oracle')
  const [host, setHost] = useState('')
  const [port, setPort] = useState('1521')
  const [dbName, setDbName] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [overwrite, setOverwrite] = useState(false)
  const [running, setRunning] = useState(false)
  const [, setJobId] = useState<number | null>(null)
  const [progress, setProgress] = useState('')
  const [testResult, setTestResult] = useState<string | null>(null)
  const [currentDb, setCurrentDb] = useState('')
  const [completed, setCompleted] = useState(false)
  const [overrideActive, setOverrideActive] = useState(false)
  const [switching, setSwitching] = useState(false)
  const [tableNames, setTableNames] = useState<string[]>([])
  const [validateResult, setValidateResult] = useState<null | {
    success: boolean
    error?: string
    existingTables?: number
    totalTables?: number
    totalExistingRows?: number
    conflict?: boolean
    hint?: string
    tables?: { table: string; exists: boolean; rowCount: number }[]
  }>(null)
  const [validating, setValidating] = useState(false)
  const esRef = useRef<EventSource | null>(null)
  const toast = useToast()

  useEffect(() => {
    fetch('/admin/db-migration/current', { credentials: 'include' })
      .then((r) => r.json()).then((d) => { if (d.dbType) setCurrentDb(`${d.dbType} (${d.url || 'H2 파일'})`) })
      .catch(() => {})
    fetch('/admin/db-migration/auto/override-status', { credentials: 'include' })
      .then((r) => r.json()).then((d) => setOverrideActive(!!d.active))
      .catch(() => {})
    fetch('/admin/db-migration/auto/tables', { credentials: 'include' })
      .then((r) => r.json()).then((d) => { if (d.success && d.tables) setTableNames(d.tables) })
      .catch(() => {})
  }, [])

  const validateMigration = async () => {
    if (!host || !dbName || !username) { toast.error('연결 정보를 모두 입력해주세요.'); return }
    setValidating(true); setValidateResult(null)
    try {
      const res = await fetch('/admin/db-migration/auto/validate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ targetType, host, port, dbName, username, password }),
        credentials: 'include',
      })
      const d = await res.json()
      setValidateResult(d)
      if (d.success) {
        if (d.conflict) toast.warning('충돌 가능성이 발견되었습니다 — 결과를 확인하세요.')
        else toast.success('충돌 없음 — 이관이 안전합니다.')
      } else {
        toast.error(d.error || '검증 실패')
      }
    } catch { toast.error('검증 요청 실패') }
    setValidating(false)
  }

  const switchToTarget = async () => {
    if (!confirm('대상 DB 로 전환합니다. 서비스가 3초 후 재시작됩니다. 계속하시겠습니까?')) return
    setSwitching(true)
    try {
      const res = await fetch('/admin/db-migration/auto/switch-target', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ targetType, host, port, dbName, username, password }),
        credentials: 'include',
      })
      const d = await res.json()
      if (d.success) {
        toast.success(d.message || '전환 요청 성공 — 재시작 대기')
      } else {
        toast.error(d.error || '전환 실패')
        setSwitching(false)
      }
    } catch { toast.error('전환 요청 실패'); setSwitching(false) }
  }

  const switchToH2 = async () => {
    if (!confirm('H2 로 복귀합니다. 서비스가 3초 후 재시작됩니다. 계속하시겠습니까?')) return
    setSwitching(true)
    try {
      const res = await fetch('/admin/db-migration/auto/switch-h2', {
        method: 'POST',
        credentials: 'include',
      })
      const d = await res.json()
      if (d.success) {
        toast.success(d.message || 'H2 복귀 요청 성공 — 재시작 대기')
      } else {
        toast.error(d.error || '복귀 실패')
        setSwitching(false)
      }
    } catch { toast.error('복귀 요청 실패'); setSwitching(false) }
  }

  const testConnection = async () => {
    setTestResult(null)
    try {
      const res = await fetch('/admin/db-migration/auto/test-connection', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ targetType, host, port, dbName, username, password }),
        credentials: 'include',
      })
      const d = await res.json()
      setTestResult(d.success ? `ok:연결 성공 — ${d.product || ''}` : `error:${d.error || '연결 실패'}`)
    } catch { setTestResult('error:연결 실패') }
  }

  const startMigration = async () => {
    if (!host || !dbName || !username) { toast.error('모든 필드를 입력해주세요.'); return }
    setRunning(true); setProgress('이관 시작 중...')
    try {
      const res = await fetch('/admin/db-migration/auto/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ targetType, host, port, dbName, username, password, overwrite: String(overwrite) }),
        credentials: 'include',
      })
      const d = await res.json()
      if (d.success && d.jobId) {
        setJobId(d.jobId)
        // SSE 진행률 구독
        const es = new EventSource(`/admin/db-migration/auto/jobs/${d.jobId}/stream`, { withCredentials: true })
        esRef.current = es
        es.onmessage = (e) => {
          setProgress(e.data)
          if (e.data.includes('완료') || e.data.includes('COMPLETED')) {
            es.close(); esRef.current = null; setRunning(false); setCompleted(true)
            toast.success('이관 완료! 이제 "대상 DB 로 전환" 버튼을 누르면 운영 DB 가 전환됩니다.')
          }
          if (e.data.includes('실패') || e.data.includes('FAILED')) {
            es.close(); esRef.current = null; setRunning(false)
            toast.error('이관 실패')
          }
        }
        es.onerror = () => { es.close(); esRef.current = null; setRunning(false) }
      } else {
        toast.error(d.error || '이관 시작 실패')
        setRunning(false)
      }
    } catch { toast.error('이관 요청 실패'); setRunning(false) }
  }

  return (
    <>
      <h3 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '6px' }}>H2 → 타겟 DB 자동 이관</h3>
      <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '16px' }}>
        현재 DB: <strong>{currentDb || '확인 중...'}</strong> — 버튼 하나로 모든 테이블 데이터를 이관합니다.
      </p>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxWidth: '500px' }}>
        <div>
          <label style={labelSt}>대상 DB</label>
          <div style={{ display: 'flex', gap: '4px' }}>
            {[{ v: 'oracle', l: 'Oracle 11g' }, { v: 'postgresql', l: 'PostgreSQL' }, { v: 'mysql', l: 'MySQL' }].map((t) => (
              <button key={t.v} onClick={() => { setTargetType(t.v); setPort(t.v === 'oracle' ? '1521' : t.v === 'mysql' ? '3306' : '5432') }}
                style={{ ...chipSt, border: `1px solid ${targetType === t.v ? 'var(--accent)' : 'var(--border-color)'}`, color: targetType === t.v ? 'var(--accent)' : 'var(--text-sub)', background: targetType === t.v ? 'var(--accent-subtle)' : 'transparent', fontWeight: targetType === t.v ? 600 : 400 }}>
                {t.l}
              </button>
            ))}
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '3fr 1fr', gap: '8px' }}>
          <div><label style={labelSt}>호스트</label><input placeholder="192.168.1.100" value={host} onChange={(e) => setHost(e.target.value)} style={inputSt} /></div>
          <div><label style={labelSt}>포트</label><input value={port} onChange={(e) => setPort(e.target.value)} style={inputSt} /></div>
        </div>

        <div><label style={labelSt}>DB 이름 / SID</label><input placeholder={targetType === 'oracle' ? 'ORCL' : 'claude_toolkit'} value={dbName} onChange={(e) => setDbName(e.target.value)} style={inputSt} /></div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
          <div><label style={labelSt}>사용자명</label><input value={username} onChange={(e) => setUsername(e.target.value)} style={inputSt} /></div>
          <div><label style={labelSt}>비밀번호</label><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} style={inputSt} /></div>
        </div>

        <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'var(--text-sub)' }}>
          <input type="checkbox" checked={overwrite} onChange={(e) => setOverwrite(e.target.checked)} />
          기존 데이터 덮어쓰기 (타겟 DB에 데이터가 있으면 삭제 후 이관)
        </label>

        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <button onClick={testConnection} style={outlineBtn}><FaSync /> 연결 테스트</button>
          <button onClick={validateMigration} disabled={validating || running} style={{ ...outlineBtn, opacity: (validating || running) ? 0.5 : 1 }}>
            {validating ? <><FaSpinner className="spin" /> 검증 중...</> : <><FaCheckCircle /> 사전 검증</>}
          </button>
          <button onClick={startMigration} disabled={running} style={{ ...primaryBtn, opacity: running ? 0.5 : 1 }}>
            {running ? <><FaSpinner className="spin" /> 이관 중...</> : <><FaPlay /> 이관 시작</>}
          </button>
        </div>

        {/* 사전 검증 결과 */}
        {validateResult && validateResult.success && (
          <div style={{
            padding: '12px', borderRadius: '8px',
            background: validateResult.conflict ? 'rgba(245,158,11,0.08)' : 'rgba(16,185,129,0.08)',
            border: `1px solid ${validateResult.conflict ? 'rgba(245,158,11,0.3)' : 'rgba(16,185,129,0.3)'}`,
            fontSize: '12px',
          }}>
            <div style={{ fontWeight: 600, marginBottom: '4px', color: validateResult.conflict ? '#f59e0b' : 'var(--green)' }}>
              {validateResult.conflict ? '⚠ 충돌 가능성 발견' : '✓ 충돌 없음'}
            </div>
            <div style={{ color: 'var(--text-sub)', marginBottom: '6px' }}>
              {validateResult.hint} (총 {validateResult.totalTables}개 중 {validateResult.existingTables}개 이미 존재 · 기존 데이터 {validateResult.totalExistingRows?.toLocaleString()}건)
            </div>
            {validateResult.tables && validateResult.tables.filter((t) => t.exists).length > 0 && (
              <details style={{ marginTop: '6px' }}>
                <summary style={{ cursor: 'pointer', fontSize: '11px', color: 'var(--text-muted)' }}>충돌 테이블 보기</summary>
                <div style={{ marginTop: '6px', maxHeight: '160px', overflowY: 'auto', fontFamily: 'monospace', fontSize: '11px' }}>
                  {validateResult.tables.filter((t) => t.exists).map((t) => (
                    <div key={t.table} style={{ padding: '2px 0' }}>
                      • <strong>{t.table}</strong> — {t.rowCount.toLocaleString()}건
                    </div>
                  ))}
                </div>
              </details>
            )}
          </div>
        )}

        {/* 이관 대상 테이블 목록 */}
        {tableNames.length > 0 && (
          <details style={{ padding: '10px 12px', borderRadius: '8px', background: 'var(--bg-primary)', border: '1px solid var(--border-color)' }}>
            <summary style={{ cursor: 'pointer', fontSize: '12px', fontWeight: 600, color: 'var(--text-sub)' }}>
              📋 자동 이관 대상 테이블 — 총 {tableNames.length}개 (대상 DB 와 이름이 겹치는지 확인)
            </summary>
            <div style={{
              marginTop: '8px',
              display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: '4px 10px',
              fontFamily: 'monospace', fontSize: '11px', color: 'var(--text-muted)',
            }}>
              {tableNames.map((t, i) => <div key={t}>{i + 1}. {t}</div>)}
            </div>
          </details>
        )}

        {testResult && (
          <div style={{ fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px', color: testResult.startsWith('ok') ? 'var(--green)' : 'var(--red)' }}>
            {testResult.startsWith('ok') ? <FaCheckCircle /> : <FaTimesCircle />} {testResult.split(':')[1]}
          </div>
        )}

        {progress && (
          <div style={{ padding: '12px', borderRadius: '8px', background: 'var(--bg-primary)', border: '1px solid var(--border-color)', fontSize: '13px', whiteSpace: 'pre-wrap' }}>
            {progress}
          </div>
        )}

        {/* 런타임 DB 전환 패널 */}
        <div style={{ marginTop: '12px', padding: '14px', borderRadius: '10px', background: 'var(--bg-primary)', border: '1px dashed var(--border-color)' }}>
          <div style={{ fontSize: '13px', fontWeight: 600, marginBottom: '8px' }}>🔁 운영 DB 런타임 전환</div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '10px', lineHeight: 1.6 }}>
            이관이 완료된 후 "<strong>대상 DB 로 전환</strong>" 을 누르면 서비스가 재시작되며 앞으로의 모든 요청은 대상 DB 를 사용합니다.<br/>
            언제든 "<strong>H2 로 복귀</strong>" 버튼으로 다시 로컬 H2 로 되돌릴 수 있습니다. (재시작 정책 필요 — Docker compose 권장)
            {overrideActive && <div style={{ marginTop: '6px', color: 'var(--accent)' }}>현재 대상 DB 오버라이드가 활성화 되어 있습니다.</div>}
          </div>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <button
              onClick={switchToTarget}
              disabled={!completed || switching || running || !host || !dbName || !username}
              title={!completed ? '먼저 이관을 완료하세요' : ''}
              style={{ ...primaryBtn, opacity: (!completed || switching || running) ? 0.5 : 1 }}>
              <FaExchangeAlt /> 대상 DB 로 전환
            </button>
            <button
              onClick={switchToH2}
              disabled={switching || running}
              style={{ ...outlineBtn, opacity: (switching || running) ? 0.5 : 1 }}>
              <FaSync /> H2 로 복귀
            </button>
          </div>
        </div>
      </div>
    </>
  )
}

const labelSt: React.CSSProperties = { display: 'block', fontSize: '12px', fontWeight: 500, color: 'var(--text-muted)', marginBottom: '3px' }
const inputSt: React.CSSProperties = { width: '100%', padding: '8px 10px', fontSize: '13px' }
const chipSt: React.CSSProperties = { padding: '5px 14px', borderRadius: '16px', fontSize: '12px', cursor: 'pointer' }
const outlineBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '5px', padding: '8px 16px', borderRadius: '8px', fontSize: '13px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-sub)', cursor: 'pointer' }
const primaryBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '5px', padding: '8px 20px', borderRadius: '8px', fontSize: '13px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontWeight: 600 }
