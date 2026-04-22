import { useEffect, useState } from 'react'
import { FaCog, FaSave, FaCheckCircle, FaTimesCircle, FaSync, FaSpinner } from 'react-icons/fa'
import { useToast } from '../hooks/useToast'

const ACCENT_COLORS = [
  { value: '#f97316', label: '오렌지', bg: '#f97316' },
  { value: '#3b82f6', label: '블루', bg: '#3b82f6' },
  { value: '#8b5cf6', label: '퍼플', bg: '#8b5cf6' },
  { value: '#10b981', label: '그린', bg: '#10b981' },
  { value: '#ef4444', label: '레드', bg: '#ef4444' },
]

export default function SettingsPage() {
  const [claudeModel, setClaudeModel] = useState('claude-sonnet-4-20250514')
  const [claudeApiKey, setClaudeApiKey] = useState('')
  const [dbUrl, setDbUrl] = useState('')
  const [dbUsername, setDbUsername] = useState('')
  const [dbPassword, setDbPassword] = useState('')
  const [scanPath, setScanPath] = useState('')
  // v4.4.x — Flow Analysis MiPlatform 설정
  const [miplatformRoot, setMiplatformRoot]         = useState('')
  const [miplatformPatterns, setMiplatformPatterns] = useState('')
  const [projectContext, setProjectContext] = useState('')
  const [accentColor, setAccentColor] = useState('#f97316')
  const [slackWebhookUrl, setSlackWebhookUrl] = useState('')
  const [teamsWebhookUrl, setTeamsWebhookUrl] = useState('')
  // SMTP / Email
  const [emailHost, setEmailHost] = useState('smtp.gmail.com')
  const [emailPort, setEmailPort] = useState('587')
  const [emailUsername, setEmailUsername] = useState('')
  const [emailPassword, setEmailPassword] = useState('')
  const [emailFrom, setEmailFrom] = useState('')
  const [emailTls, setEmailTls] = useState(true)
  const [emailPasswordSet, setEmailPasswordSet] = useState(false)
  const [emailTestTo, setEmailTestTo] = useState('')
  const [emailTest, setEmailTest] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [apiTest, setApiTest] = useState<string | null>(null)
  const [dbTest, setDbTest] = useState<string | null>(null)
  const [scanTest, setScanTest] = useState<string | null>(null)
  const toast = useToast()

  useEffect(() => {
    // 저장된 전체 설정 로드
    fetch('/api/v1/settings', { credentials: 'include' })
      .then((r) => r.json())
      .then((j) => {
        if (j.data) {
          const d = j.data
          if (d.claudeModel) setClaudeModel(d.claudeModel)
          if (d.dbUrl) setDbUrl(d.dbUrl)
          if (d.dbUsername) setDbUsername(d.dbUsername)
          if (d.scanPath) setScanPath(d.scanPath)
          if (d.miplatformRoot)     setMiplatformRoot(d.miplatformRoot)
          if (d.miplatformPatterns) setMiplatformPatterns(d.miplatformPatterns)
          if (d.projectContext) setProjectContext(d.projectContext)
          if (d.accentColor) setAccentColor(d.accentColor)
          if (d.slackWebhookUrl) setSlackWebhookUrl(d.slackWebhookUrl)
          if (d.teamsWebhookUrl) setTeamsWebhookUrl(d.teamsWebhookUrl)
          if (d.emailHost) setEmailHost(d.emailHost)
          if (d.emailPort) setEmailPort(String(d.emailPort))
          if (d.emailUsername) setEmailUsername(d.emailUsername)
          if (d.emailFrom) setEmailFrom(d.emailFrom)
          if (typeof d.emailTls === 'boolean') setEmailTls(d.emailTls)
          if (d.emailPasswordSet) setEmailPasswordSet(true)
        }
      })
      .catch(() => {})
  }, [])

  const save = async () => {
    setSaving(true)
    try {
      const p = new URLSearchParams({
        claudeModel, claudeApiKey, dbUrl, dbUsername, dbPassword,
        scanPath, projectContext, accentColor, slackWebhookUrl, teamsWebhookUrl,
        emailHost, emailPort, emailUsername, emailPassword, emailFrom,
        emailTls: String(emailTls),
        miplatformRoot, miplatformPatterns,
      })
      await fetch('/settings/save', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: p, credentials: 'include' })
      toast.success('설정이 저장되었습니다.')
      if (emailPassword) { setEmailPasswordSet(true); setEmailPassword('') }
    } catch { toast.error('저장 실패') }
    setSaving(false)
  }

  const testEmail = async () => {
    setEmailTest('info:테스트 발송 중...')
    try {
      const res = await fetch('/email/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ to: emailTestTo }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (d?.success) setEmailTest(`ok:${d.sentTo} 로 발송 성공`)
      else setEmailTest(`error:${d?.error || '발송 실패'}`)
    } catch { setEmailTest('error:발송 요청 실패') }
  }

  const testApi = async () => {
    setApiTest(null)
    try {
      const res = await fetch('/api/v1/health', { credentials: 'include' })
      const j = await res.json()
      setApiTest(j.data?.apiKeySet ? 'ok:API 키 유효' : 'error:API 키 미설정')
    } catch { setApiTest('error:연결 실패') }
  }

  const testDb = async () => {
    setDbTest(null)
    try {
      const res = await fetch('/settings/test-db', { method: 'POST', credentials: 'include' })
      setDbTest(await res.text())
    } catch { setDbTest('error:연결 실패') }
  }

  const testScan = async () => {
    setScanTest('info:캐시 상태 조회 중...')
    try {
      // 1차 조회 — 캐시가 아직 로드되지 않았으면 백그라운드 스캔 트리거 + 폴링
      let d = await (await fetch(`/harness/cache/files?q=.java`, { credentials: 'include' })).json()

      // 부팅 직후엔 백그라운드 스캔 중 — loaded=false 또는 refreshing=true 면 폴링
      if (d.refreshing || !d.loaded) {
        setScanTest('info:프로젝트 스캔 중... 잠시 기다려주세요')
        // 수동 refresh 트리거 (이미 진행중이면 무시됨)
        try {
          await fetch('/harness/cache/refresh', { method: 'POST', credentials: 'include' })
        } catch { /* noop */ }
        // 최대 30초 폴링 (1초 간격)
        for (let i = 0; i < 30; i++) {
          await new Promise((r) => setTimeout(r, 1000))
          d = await (await fetch(`/harness/cache/files?q=.java`, { credentials: 'include' })).json()
          if (!d.refreshing && d.loaded) break
        }
      }

      if (d.totalCount > 0) {
        setScanTest(`ok:${d.totalCount}개 Java 파일 발견`)
      } else if (!d.loaded || d.refreshing) {
        setScanTest('error:스캔 시간 초과 — 잠시 후 다시 시도하거나 경로를 확인하세요')
      } else {
        setScanTest('error:Java 파일 없음 — 경로가 올바른지 확인하세요')
      }
    } catch { setScanTest('error:확인 실패') }
  }

  return (
    <>
      <h2 style={{ fontSize: '20px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaCog style={{ color: '#64748b' }} /> 설정
      </h2>

      <div style={{ maxWidth: '720px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
        {/* Claude API */}
        <Card title="🤖 Claude API">
          <Field label="모델">
            <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
              {['claude-sonnet-4-20250514', 'claude-opus-4-20250514', 'claude-haiku-4-20250514'].map((m) => (
                <Chip key={m} active={claudeModel === m} onClick={() => setClaudeModel(m)}>{m.replace('claude-', '').replace('-20250514', '')}</Chip>
              ))}
            </div>
          </Field>
          <Field label="API 키 (변경 시만 입력)">
            <div style={{ display: 'flex', gap: '8px' }}>
              <input type="password" placeholder="sk-ant-..." value={claudeApiKey} onChange={(e) => setClaudeApiKey(e.target.value)} style={{ ...inputSt, flex: 1 }} />
              <button onClick={testApi} style={testBtn}><FaSync /> 테스트</button>
            </div>
            <TestResult result={apiTest} />
          </Field>
        </Card>

        {/* Oracle DB */}
        <Card title="🗄️ Oracle DB 연결">
          <Field label="JDBC URL"><input placeholder="jdbc:oracle:thin:@//host:1521/ORCL" value={dbUrl} onChange={(e) => setDbUrl(e.target.value)} style={inputSt} /></Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
            <Field label="사용자명"><input placeholder="Username" value={dbUsername} onChange={(e) => setDbUsername(e.target.value)} style={inputSt} /></Field>
            <Field label="비밀번호"><input type="password" placeholder="Password" value={dbPassword} onChange={(e) => setDbPassword(e.target.value)} style={inputSt} /></Field>
          </div>
          <button onClick={testDb} style={testBtn}><FaSync /> DB 연결 테스트</button>
          <TestResult result={dbTest} />
        </Card>

        {/* 프로젝트 */}
        <Card title="📁 프로젝트">
          <Field label="Java 소스 스캔 경로">
            <div style={{ display: 'flex', gap: '8px' }}>
              <input placeholder="C:/workspace/project/src/main/java" value={scanPath} onChange={(e) => setScanPath(e.target.value)} style={{ ...inputSt, flex: 1 }} />
              <button onClick={testScan} style={testBtn}><FaSync /> 확인</button>
            </div>
            <TestResult result={scanTest} />
          </Field>
          <Field label="프로젝트 컨텍스트 메모">
            <textarea placeholder="모든 AI 요청에 자동 포함될 메모..." value={projectContext} onChange={(e) => setProjectContext(e.target.value)} style={{ ...inputSt, minHeight: '80px' }} />
          </Field>
          {/* v4.4.x — Flow Analysis MiPlatform */}
          <Field label="MiPlatform 디렉토리 (선택)">
            <input placeholder="비워두면 scanPath/src/main/webapp/miplatform 자동 감지"
                   value={miplatformRoot} onChange={(e) => setMiplatformRoot(e.target.value)}
                   style={inputSt} />
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
              표준 디렉토리 구조가 아닌 경우 직접 지정. WAS 재기동 시 인덱싱이 자동 포함됨.
            </div>
          </Field>
          <Field label="MiPlatform URL 추출 패턴 (고급, 선택)">
            <textarea placeholder={'사이트별 비표준 호출 패턴 — 콤마/줄바꿈 구분, 그룹1 캡처\n예) callService\\s*\\(\\s*[\'"]([^\'"]+)[\'"]\n예) xajaxRequest\\s*\\(\\s*[\'"]([^\'"]+)[\'"]'}
                      value={miplatformPatterns} onChange={(e) => setMiplatformPatterns(e.target.value)}
                      style={{ ...inputSt, minHeight: '70px', fontFamily: 'monospace', fontSize: 11 }} />
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
              기본 정규식 (Transaction url=, transaction("svc::/...")) 외에 추가 적용. 저장 후 데이터 흐름 분석 페이지에서 "인덱스 재빌드" 클릭.
            </div>
          </Field>
        </Card>

        {/* 알림 */}
        <Card title="🔔 알림 연동">
          <Field label="Slack Webhook URL"><input placeholder="https://hooks.slack.com/services/..." value={slackWebhookUrl} onChange={(e) => setSlackWebhookUrl(e.target.value)} style={inputSt} /></Field>
          <Field label="Teams Webhook URL"><input placeholder="https://outlook.office.com/webhook/..." value={teamsWebhookUrl} onChange={(e) => setTeamsWebhookUrl(e.target.value)} style={inputSt} /></Field>
        </Card>

        {/* SMTP / 이메일 발송 */}
        <Card title="✉️ 이메일 (SMTP) — 분석 결과 발송">
          <div style={{ display: 'grid', gridTemplateColumns: '3fr 1fr', gap: '8px' }}>
            <Field label="SMTP 호스트">
              <input placeholder="smtp.gmail.com" value={emailHost} onChange={(e) => setEmailHost(e.target.value)} style={inputSt} />
            </Field>
            <Field label="포트">
              <input placeholder="587" value={emailPort} onChange={(e) => setEmailPort(e.target.value)} style={inputSt} />
            </Field>
          </div>
          <Field label="사용자 (Gmail 주소)">
            <input placeholder="your.account@gmail.com" value={emailUsername} onChange={(e) => setEmailUsername(e.target.value)} style={inputSt} />
          </Field>
          <Field label={`비밀번호 (Gmail 앱 비밀번호 권장)${emailPasswordSet ? ' — 저장됨, 변경 시에만 입력' : ''}`}>
            <input type="password" placeholder={emailPasswordSet ? '••••••••••• (변경 시에만 입력)' : '앱 비밀번호 16자'} value={emailPassword} onChange={(e) => setEmailPassword(e.target.value)} style={inputSt} />
          </Field>
          <Field label="발신자 표시 (선택)">
            <input placeholder="Claude Toolkit <noreply@example.com>" value={emailFrom} onChange={(e) => setEmailFrom(e.target.value)} style={inputSt} />
          </Field>
          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-sub)' }}>
            <input type="checkbox" checked={emailTls} onChange={(e) => setEmailTls(e.target.checked)} />
            STARTTLS 사용 (Gmail/대부분의 SMTP 는 활성화 필요)
          </label>
          <div style={{ background: 'var(--bg-primary)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '10px', fontSize: '11px', color: 'var(--text-muted)', lineHeight: 1.6 }}>
            💡 <strong>Gmail 앱 비밀번호:</strong> 일반 계정 비밀번호는 거부됩니다. Google 계정 → 보안 → 2단계 인증 활성화 → "앱 비밀번호" 에서 16자 비밀번호 발급 후 사용하세요.
            <br/>호스트 <code>smtp.gmail.com</code>, 포트 <code>587</code>, STARTTLS 가 표준입니다.
          </div>
          <div style={{ display: 'flex', gap: '6px', alignItems: 'center', flexWrap: 'wrap' }}>
            <input
              type="email"
              placeholder="테스트 발송 받을 이메일 (비워두면 발신자에게 발송)"
              value={emailTestTo}
              onChange={(e) => setEmailTestTo(e.target.value)}
              style={{ ...inputSt, flex: 1, minWidth: '180px' }}
            />
            <button onClick={testEmail} style={testBtn}><FaSync /> 테스트 발송</button>
          </div>
          <TestResult result={emailTest} />
        </Card>

        {/* UI */}
        <Card title="🎨 UI 설정">
          <Field label="액센트 색상">
            <div style={{ display: 'flex', gap: '8px' }}>
              {ACCENT_COLORS.map((c) => (
                <button key={c.value} onClick={() => setAccentColor(c.value)} title={c.label}
                  style={{
                    width: '36px', height: '36px', borderRadius: '50%', border: accentColor === c.value ? '3px solid var(--text-primary)' : '2px solid var(--border-color)',
                    background: c.bg, cursor: 'pointer', transition: 'all 0.15s',
                  }} />
              ))}
            </div>
          </Field>
        </Card>

        <button onClick={save} disabled={saving} style={saveBtn}>
          <FaSave /> {saving ? '저장 중...' : '설정 저장'}
        </button>
      </div>
    </>
  )
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '20px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 600, marginBottom: '14px' }}>{title}</h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>{children}</div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: 'block', fontSize: '13px', fontWeight: 500, color: 'var(--text-muted)', marginBottom: '4px' }}>{label}</label>{children}</div>
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick} style={{
      padding: '5px 14px', borderRadius: '16px', fontSize: '12px', cursor: 'pointer',
      border: `1px solid ${active ? 'var(--accent)' : 'var(--border-color)'}`,
      background: active ? 'var(--accent-subtle)' : 'transparent',
      color: active ? 'var(--accent)' : 'var(--text-sub)', fontWeight: active ? 600 : 400,
    }}>{children}</button>
  )
}

function TestResult({ result }: { result: string | null }) {
  if (!result) return null
  const kind = result.startsWith('ok') ? 'ok' : result.startsWith('info') ? 'info' : 'error'
  const color = kind === 'ok' ? 'var(--green)' : kind === 'info' ? 'var(--blue)' : 'var(--red)'
  const message = result.substring(result.indexOf(':') + 1)
  return (
    <div style={{ fontSize: '12px', marginTop: '4px', display: 'flex', alignItems: 'center', gap: '4px', color }}>
      {kind === 'ok' ? <FaCheckCircle /> : kind === 'info' ? <FaSpinner className="spin" /> : <FaTimesCircle />} {message}
    </div>
  )
}

const inputSt: React.CSSProperties = { width: '100%', padding: '8px 12px', fontSize: '13px' }
const testBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '4px', padding: '6px 14px', borderRadius: '6px', fontSize: '12px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-sub)', cursor: 'pointer', whiteSpace: 'nowrap' }
const saveBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '6px', justifyContent: 'center', padding: '14px', borderRadius: '10px', background: 'var(--accent)', color: '#fff', border: 'none', fontSize: '15px', fontWeight: 600, cursor: 'pointer' }
