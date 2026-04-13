import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FaRobot, FaKey, FaCheck, FaArrowRight } from 'react-icons/fa'
import { useToast } from '../hooks/useToast'

export default function SetupPage() {
  const [step, setStep] = useState(0)
  const [apiKey, setApiKey] = useState('')
  const [testing, setTesting] = useState(false)
  const toast = useToast()
  const navigate = useNavigate()

  const testApiKey = async () => {
    if (!apiKey.trim()) return
    setTesting(true)
    try {
      const res = await fetch('/setup/save-api-key', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ apiKey }),
        credentials: 'include',
      })
      if (res.ok) { toast.success('API 키 확인 완료'); setStep(1) }
      else toast.error('API 키가 유효하지 않습니다.')
    } catch { toast.error('연결 실패') }
    setTesting(false)
  }

  const finish = () => {
    toast.success('설정이 완료되었습니다!')
    navigate('/')
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-primary)', padding: '20px' }}>
      <div style={{ width: 'min(500px, 90vw)', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '16px', padding: '40px 32px' }}>
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <FaRobot style={{ fontSize: '40px', color: 'var(--accent)', marginBottom: '12px' }} />
          <h2 style={{ fontSize: '22px', fontWeight: 700 }}>초기 설정</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>단계 {step + 1} / 2</p>
        </div>

        {step === 0 && (
          <div>
            <h3 style={{ fontSize: '15px', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}><FaKey style={{ color: 'var(--accent)' }} /> Claude API 키</h3>
            <input type="password" placeholder="sk-ant-..." value={apiKey} onChange={(e) => setApiKey(e.target.value)} style={{ width: '100%', marginBottom: '12px', padding: '10px 14px' }} />
            <button onClick={testApiKey} disabled={testing || !apiKey.trim()} style={{ width: '100%', padding: '10px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '14px', fontWeight: 600 }}>
              {testing ? '확인 중...' : 'API 키 확인'}
            </button>
            <button onClick={() => setStep(1)} style={{ width: '100%', marginTop: '8px', padding: '8px', background: 'transparent', border: '1px solid var(--border-color)', borderRadius: '8px', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '13px' }}>나중에 설정하기</button>
          </div>
        )}

        {step === 1 && (
          <div style={{ textAlign: 'center' }}>
            <FaCheck style={{ fontSize: '48px', color: 'var(--green)', marginBottom: '16px' }} />
            <h3 style={{ fontSize: '16px', marginBottom: '8px' }}>설정 완료!</h3>
            <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '20px' }}>추가 설정은 관리자 설정 페이지에서 변경할 수 있습니다.</p>
            <button onClick={finish} style={{ padding: '10px 24px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '14px', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
              시작하기 <FaArrowRight />
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
