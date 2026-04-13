import { useEffect, useState } from 'react'
import { FaCog, FaSave } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'

interface Settings {
  claudeModel: string
  projectContext: string
  maxTokens: number
}

export default function SettingsPage() {
  const [settings, setSettings] = useState<Settings>({
    claudeModel: '',
    projectContext: '',
    maxTokens: 8192,
  })
  const [saving, setSaving] = useState(false)
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/settings?format=json') as Settings | null
      if (data) setSettings(data)
    }
    load()
  }, [api])

  const save = async () => {
    setSaving(true)
    try {
      await fetch('/settings/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          claudeModel: settings.claudeModel,
          projectContext: settings.projectContext,
          maxTokens: String(settings.maxTokens),
        }),
        credentials: 'include',
      })
      toast.success('설정이 저장되었습니다.')
    } catch {
      toast.error('저장 실패')
    }
    setSaving(false)
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaCog style={{ color: '#64748b' }} /> 설정
      </h2>

      <div style={{ maxWidth: '600px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
        <div>
          <label style={labelStyle}>Claude 모델</label>
          <select
            style={{ width: '100%', padding: '8px 12px' }}
            value={settings.claudeModel}
            onChange={(e) => setSettings({ ...settings, claudeModel: e.target.value })}
          >
            <option value="claude-sonnet-4-20250514">Claude Sonnet 4</option>
            <option value="claude-opus-4-20250514">Claude Opus 4</option>
            <option value="claude-haiku-4-20250514">Claude Haiku 4</option>
          </select>
        </div>

        <div>
          <label style={labelStyle}>최대 토큰 수</label>
          <input
            type="number"
            style={{ width: '100%' }}
            value={settings.maxTokens}
            onChange={(e) => setSettings({ ...settings, maxTokens: Number(e.target.value) })}
            min={1024}
            max={32768}
          />
        </div>

        <div>
          <label style={labelStyle}>프로젝트 컨텍스트 메모</label>
          <textarea
            style={{ width: '100%', minHeight: '120px', fontFamily: 'inherit' }}
            value={settings.projectContext}
            onChange={(e) => setSettings({ ...settings, projectContext: e.target.value })}
            placeholder="모든 AI 요청에 자동으로 추가될 프로젝트 컨텍스트 메모..."
          />
          <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
            이 메모는 모든 분석 및 채팅 요청에 자동으로 포함됩니다.
          </p>
        </div>

        <button
          onClick={save}
          disabled={saving}
          style={{
            display: 'flex', alignItems: 'center', gap: '6px', justifyContent: 'center',
            padding: '10px', borderRadius: '8px',
            background: 'var(--accent)', color: '#fff', border: 'none',
            fontSize: '14px', fontWeight: 600, cursor: 'pointer',
            opacity: saving ? 0.6 : 1,
          }}
        >
          <FaSave /> {saving ? '저장 중...' : '설정 저장'}
        </button>
      </div>
    </>
  )
}

const labelStyle: React.CSSProperties = {
  display: 'block', fontSize: '13px', fontWeight: 600,
  color: 'var(--text-sub)', marginBottom: '6px',
}
