import { useEffect, useState } from 'react'
import { FaCog, FaSave, FaCheckCircle, FaTimesCircle } from 'react-icons/fa'
import { useToast } from '../hooks/useToast'

export default function SettingsPage() {
  const [claudeModel, setClaudeModel] = useState('claude-sonnet-4-20250514')
  const [claudeApiKey, setClaudeApiKey] = useState('')
  const [dbUrl, setDbUrl] = useState('')
  const [dbUsername, setDbUsername] = useState('')
  const [dbPassword, setDbPassword] = useState('')
  const [scanPath, setScanPath] = useState('')
  const [projectContext, setProjectContext] = useState('')
  const [accentColor, setAccentColor] = useState('')
  const [saving, setSaving] = useState(false)
  const [testResult, setTestResult] = useState<string | null>(null)
  const toast = useToast()

  useEffect(() => {
    // 기존 설정을 Health API에서 로드
    fetch('/api/v1/health', { credentials: 'include' })
      .then((r) => r.json())
      .then((json) => {
        if (json.data) {
          setClaudeModel(json.data.claudeModel || 'claude-sonnet-4-20250514')
        }
      })
      .catch(() => {})
  }, [])

  const save = async () => {
    setSaving(true)
    try {
      const params = new URLSearchParams({
        claudeModel, claudeApiKey, dbUrl, dbUsername, dbPassword,
        scanPath, projectContext, accentColor,
      })
      const res = await fetch('/settings/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params,
        credentials: 'include',
      })
      if (res.ok || res.redirected) {
        toast.success('설정이 저장되었습니다.')
      } else {
        toast.error('저장 실패')
      }
    } catch {
      toast.error('저장 중 오류')
    }
    setSaving(false)
  }

  const testDb = async () => {
    setTestResult(null)
    try {
      const res = await fetch('/settings/test-db', { method: 'POST', credentials: 'include' })
      const text = await res.text()
      setTestResult(text)
      toast.info(text.startsWith('ok') ? 'DB 연결 성공' : 'DB 연결 실패')
    } catch {
      setTestResult('error:연결 테스트 실패')
    }
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaCog style={{ color: '#64748b' }} /> 설정
      </h2>

      <div style={{ maxWidth: '700px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
        {/* Claude API */}
        <Section title="Claude API">
          <Field label="모델">
            <select value={claudeModel} onChange={(e) => setClaudeModel(e.target.value)} style={inputStyle}>
              <option value="claude-sonnet-4-20250514">Claude Sonnet 4</option>
              <option value="claude-opus-4-20250514">Claude Opus 4</option>
              <option value="claude-haiku-4-20250514">Claude Haiku 4</option>
            </select>
          </Field>
          <Field label="API 키 (변경 시만 입력)">
            <input type="password" placeholder="sk-ant-..." value={claudeApiKey}
              onChange={(e) => setClaudeApiKey(e.target.value)} style={inputStyle} />
          </Field>
        </Section>

        {/* Oracle DB */}
        <Section title="Oracle DB 연결 (선택)">
          <Field label="JDBC URL">
            <input placeholder="jdbc:oracle:thin:@//host:1521/ORCL" value={dbUrl}
              onChange={(e) => setDbUrl(e.target.value)} style={inputStyle} />
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
            <Field label="사용자명">
              <input placeholder="DB Username" value={dbUsername}
                onChange={(e) => setDbUsername(e.target.value)} style={inputStyle} />
            </Field>
            <Field label="비밀번호">
              <input type="password" placeholder="DB Password" value={dbPassword}
                onChange={(e) => setDbPassword(e.target.value)} style={inputStyle} />
            </Field>
          </div>
          <button onClick={testDb} style={outlineBtn}>DB 연결 테스트</button>
          {testResult && (
            <div style={{ fontSize: '13px', marginTop: '6px', display: 'flex', alignItems: 'center', gap: '6px', color: testResult.startsWith('ok') ? 'var(--green)' : 'var(--red)' }}>
              {testResult.startsWith('ok') ? <FaCheckCircle /> : <FaTimesCircle />}
              {testResult.split(':')[1]}
            </div>
          )}
        </Section>

        {/* Project */}
        <Section title="프로젝트">
          <Field label="프로젝트 스캔 경로">
            <input placeholder="C:/workspace/project/src/main/java" value={scanPath}
              onChange={(e) => setScanPath(e.target.value)} style={inputStyle} />
          </Field>
          <Field label="프로젝트 컨텍스트 메모">
            <textarea placeholder="모든 AI 요청에 자동으로 추가될 메모..." value={projectContext}
              onChange={(e) => setProjectContext(e.target.value)} style={{ ...inputStyle, minHeight: '100px' }} />
            <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>이 메모는 모든 분석/채팅 요청에 자동 포함됩니다.</p>
          </Field>
        </Section>

        {/* UI */}
        <Section title="UI">
          <Field label="액센트 색상">
            <input placeholder="#f97316" value={accentColor}
              onChange={(e) => setAccentColor(e.target.value)} style={{ ...inputStyle, width: '150px' }} />
          </Field>
        </Section>

        <button onClick={save} disabled={saving} style={saveBtn}>
          <FaSave /> {saving ? '저장 중...' : '설정 저장'}
        </button>
      </div>
    </>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '20px' }}>
      <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '14px', color: 'var(--text-sub)' }}>{title}</h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>{children}</div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, color: 'var(--text-muted)', marginBottom: '4px' }}>{label}</label>
      {children}
    </div>
  )
}

const inputStyle: React.CSSProperties = { width: '100%', padding: '8px 12px', fontSize: '13px' }
const outlineBtn: React.CSSProperties = { padding: '6px 14px', borderRadius: '6px', fontSize: '13px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-sub)', cursor: 'pointer' }
const saveBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '6px', justifyContent: 'center', padding: '12px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', fontSize: '14px', fontWeight: 600, cursor: 'pointer' }
