import { useEffect, useState } from 'react'
import { FaShareAlt, FaDownload, FaUpload } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'

export default function SharedConfigPage() {
  const [config, setConfig] = useState('')
  const [importing, setImporting] = useState(false)
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/settings/shared?format=json')
      if (data) setConfig(JSON.stringify(data, null, 2))
    }
    load()
  }, [])

  const exportConfig = () => {
    const blob = new Blob([config], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `shared-config_${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    toast.success('설정 내보내기 완료')
  }

  const importConfig = async () => {
    setImporting(true)
    try {
      await fetch('/settings/shared/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: config,
        credentials: 'include',
      })
      toast.success('설정 가져오기 완료')
    } catch {
      toast.error('가져오기 실패')
    }
    setImporting(false)
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaShareAlt style={{ color: '#ef4444' }} /> 팀 설정 공유
      </h2>
      <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '16px' }}>
        팀 전체에 적용할 설정을 JSON 형식으로 관리합니다. 내보내기/가져오기로 팀 간 설정을 공유할 수 있습니다.
      </p>
      <textarea
        value={config}
        onChange={(e) => setConfig(e.target.value)}
        style={{ width: '100%', minHeight: '300px', fontFamily: 'monospace', fontSize: '13px', marginBottom: '12px' }}
        placeholder='{ "settings": {} }'
      />
      <div style={{ display: 'flex', gap: '8px' }}>
        <button onClick={exportConfig} style={btnStyle}><FaDownload /> 내보내기</button>
        <button onClick={importConfig} disabled={importing} style={{ ...btnStyle, background: 'var(--accent)', color: '#fff', border: 'none' }}>
          <FaUpload /> {importing ? '가져오는 중...' : '가져오기'}
        </button>
      </div>
    </>
  )
}

const btnStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '6px',
  padding: '8px 16px', borderRadius: '8px', fontSize: '13px',
  border: '1px solid var(--border-color)', background: 'transparent',
  color: 'var(--text-sub)', cursor: 'pointer',
}
