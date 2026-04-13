import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { FaShieldAlt, FaKey, FaQrcode } from 'react-icons/fa'
import { useAuthStore } from '../stores/authStore'
import { useToast } from '../hooks/useToast'

export default function TwoFactorPage() {
  const [code, setCode] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [isSetup, setIsSetup] = useState(false)
  const [secret] = useState('')
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const checkAuth = useAuthStore((s) => s.checkAuth)
  const toast = useToast()

  useEffect(() => {
    // 페이지 진입 시 2FA 상태 확인 — 세션에 setup secret이 있으면 신규 등록 모드
    fetch('/login/2fa', { credentials: 'include', redirect: 'manual' })
      .then((res) => {
        if (res.type === 'opaqueredirect' || res.status === 302) {
          // 2FA가 필요 없는 경우 → 홈으로
          navigate('/')
        }
      })
      .catch(() => {})
  }, [navigate])

  const verify = async () => {
    if (!code.trim() || code.length !== 6) {
      setError('6자리 코드를 입력해주세요.')
      return
    }
    setSubmitting(true)
    setError(null)

    const endpoint = isSetup ? '/login/2fa/setup' : '/login/2fa/verify'
    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ code }),
        credentials: 'include',
      })
      const data = await res.json()
      if (data.success) {
        toast.success('2FA 인증 성공')
        await checkAuth()
        navigate('/')
      } else {
        setError(data.error || '인증 코드가 올바르지 않습니다.')
      }
    } catch {
      setError('서버 오류가 발생했습니다.')
    }
    setSubmitting(false)
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-primary)', padding: '20px' }}>
      <div style={{ width: 'min(420px, 90vw)', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '16px', padding: '40px 32px' }}>
        <div style={{ textAlign: 'center', marginBottom: '28px' }}>
          <FaShieldAlt style={{ fontSize: '40px', color: 'var(--accent)', marginBottom: '12px' }} />
          <h2 style={{ fontSize: '20px', fontWeight: 700, marginBottom: '6px' }}>2단계 인증</h2>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
            {isSetup ? 'Authenticator 앱에서 QR 코드를 스캔하고 코드를 입력하세요.' : 'Authenticator 앱의 6자리 코드를 입력하세요.'}
          </p>
        </div>

        {isSetup && secret && (
          <div style={{ textAlign: 'center', marginBottom: '20px', padding: '16px', background: 'var(--bg-primary)', borderRadius: '10px', border: '1px solid var(--border-color)' }}>
            <FaQrcode style={{ fontSize: '24px', color: 'var(--text-muted)', marginBottom: '8px' }} />
            <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '8px' }}>아래 키를 Authenticator 앱에 수동 입력:</p>
            <code style={{ fontSize: '14px', fontWeight: 600, wordBreak: 'break-all' }}>{secret}</code>
          </div>
        )}

        {error && <div className="alert alert-danger" style={{ marginBottom: '12px' }}>{error}</div>}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <input
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            maxLength={6}
            placeholder="000000"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            autoFocus
            onKeyDown={(e) => e.key === 'Enter' && verify()}
            style={{ width: '100%', textAlign: 'center', fontSize: '24px', letterSpacing: '8px', fontWeight: 700, padding: '12px' }}
          />
          <button
            onClick={verify}
            disabled={submitting || code.length !== 6}
            style={{ width: '100%', padding: '12px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '15px', fontWeight: 600, opacity: submitting || code.length !== 6 ? 0.5 : 1 }}
          >
            <FaKey style={{ marginRight: '6px' }} />
            {submitting ? '확인 중...' : '인증 확인'}
          </button>
        </div>

        <div style={{ textAlign: 'center', marginTop: '16px' }}>
          <button
            onClick={() => setIsSetup(!isSetup)}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '12px', textDecoration: 'underline' }}
          >
            {isSetup ? '이미 등록했습니다 → 인증' : '처음 등록 → 설정'}
          </button>
        </div>
      </div>
    </div>
  )
}
