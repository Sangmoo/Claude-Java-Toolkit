import { Component, type ReactNode } from 'react'
import { FaExclamationTriangle } from 'react-icons/fa'

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback

      return (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
          padding: '60px 24px', color: 'var(--text-muted)', textAlign: 'center',
        }}>
          <FaExclamationTriangle style={{ fontSize: '40px', color: 'var(--red)', marginBottom: '16px', opacity: 0.6 }} />
          <h3 style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '8px' }}>
            페이지 로딩 오류
          </h3>
          <p style={{ fontSize: '13px', marginBottom: '16px', maxWidth: '400px' }}>
            {this.state.error?.message || '알 수 없는 오류가 발생했습니다.'}
          </p>
          <button
            onClick={() => { this.setState({ hasError: false, error: null }); window.location.reload() }}
            style={{
              padding: '8px 20px', borderRadius: '8px',
              background: 'var(--accent)', color: '#fff', border: 'none',
              cursor: 'pointer', fontSize: '13px', fontWeight: 600,
            }}
          >
            새로고침
          </button>
        </div>
      )
    }

    return this.props.children
  }
}
