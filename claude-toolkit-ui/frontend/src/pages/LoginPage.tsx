import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { FaRobot, FaSun, FaMoon } from 'react-icons/fa'
import { useAuthStore } from '../stores/authStore'
import { useThemeStore } from '../stores/themeStore'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const { error, login } = useAuthStore()
  const { theme, toggleTheme } = useThemeStore()
  const navigate = useNavigate()
  const [params] = useSearchParams()

  const loggedOut = params.get('logout') === 'true'
  const expired = params.get('expired') === 'true'

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    const ok = await login(username, password)
    setSubmitting(false)
    if (ok) {
      navigate('/')
    }
  }

  return (
    <div className="login-page">
      <button
        className="top-bar-btn"
        onClick={toggleTheme}
        style={{ position: 'fixed', top: 16, right: 16 }}
      >
        {theme === 'dark' ? <FaSun /> : <FaMoon />}
        <span>{theme === 'dark' ? 'Light' : 'Dark'}</span>
      </button>

      <div className="login-card">
        <div className="login-brand">
          <div className="login-brand-icon"><FaRobot /></div>
          <h2>Claude Java Toolkit</h2>
          <p>AI-powered tools for Oracle DB & Java/Spring</p>
        </div>

        {error && <div className="alert alert-danger">{error}</div>}
        {loggedOut && <div className="alert alert-success">로그아웃되었습니다.</div>}
        {expired && <div className="alert alert-warning">세션이 만료되었습니다. 다시 로그인해주세요.</div>}

        <form className="login-form" onSubmit={handleSubmit}>
          <input
            type="text"
            placeholder="admin"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            autoFocus
            autoComplete="username"
          />
          <input
            type="password"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
          />
          <button type="submit" disabled={submitting}>
            {submitting ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <div className="login-hint">초기 계정: admin / admin1234</div>
      </div>
    </div>
  )
}
