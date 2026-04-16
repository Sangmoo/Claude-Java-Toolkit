import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { FaShareAlt, FaCopy, FaCheck, FaRobot, FaClock, FaExclamationTriangle } from 'react-icons/fa'
import { markdownCodeComponents } from '../components/common/CopyableCodeBlock'
import { copyToClipboard } from '../utils/clipboard'

/**
 * v4.2.8 — 공유 링크 뷰 페이지 (게스트 read-only).
 *
 * <p>로그인 없이 접근 가능한 public 페이지. `/share/:token` React 라우트로
 * 매칭되며, 내부적으로 `/api/v1/share/{token}` JSON API 를 fetch 해서 렌더.
 *
 * <p>이전 버전은 최소한의 레이아웃이었지만 v4.2.8 에서 전면 리뉴얼:
 * - Claude Java Toolkit 브랜딩 헤더
 * - 제목 / 유형 / 공유 일시 / 만료 정보 표시
 * - 입력 코드 + 분석 결과 2섹션 구분
 * - 복사 버튼 (입력/결과 각각)
 * - 만료 잔여일 배지
 * - 코드 블록 복사 버튼 (markdownCodeComponents 통합)
 */

interface SharedData {
  success:    boolean
  token:      string
  menuName:   string
  title:      string
  inputText:  string
  resultText: string
  sharedAt:   string
  remaining:  string
  expiresAt:  string
  error?:     string
}

export default function ShareViewPage() {
  const { token } = useParams<{ token: string }>()
  const [data, setData]     = useState<SharedData | null>(null)
  const [error, setError]   = useState<string | null>(null)
  const [copiedSection, setCopiedSection] = useState<string | null>(null)

  useEffect(() => {
    // v4.2.8: /api/v1/share/{token} 으로 변경 (이전엔 /share/{token} 이 JSON 으로 직접 나오던 버그)
    fetch(`/api/v1/share/${token}`)
      .then((r) => r.json())
      .then((d) => {
        if (d.success === false) setError(d.error || '공유 링크를 불러올 수 없습니다.')
        else setData(d.data ?? d)
      })
      .catch(() => setError('서버 연결에 실패했습니다.'))
  }, [token])

  const handleCopy = async (text: string, section: string) => {
    const ok = await copyToClipboard(text)
    if (ok) {
      setCopiedSection(section)
      setTimeout(() => setCopiedSection(null), 2000)
    }
  }

  // ── 오류 상태 ──────────────────────────────────────────────────
  if (error) return (
    <div style={pageWrap}>
      <div style={centeredCard}>
        <FaExclamationTriangle style={{ fontSize: '48px', color: 'var(--yellow)', marginBottom: '16px' }} />
        <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '8px' }}>공유 링크 오류</h2>
        <p style={{ color: 'var(--text-muted)', fontSize: '14px', marginBottom: '20px' }}>{error}</p>
        <a href="/" style={homeLinkStyle}>← 홈으로 돌아가기</a>
      </div>
    </div>
  )

  // ── 로딩 상태 ──────────────────────────────────────────────────
  if (!data) return (
    <div style={pageWrap}>
      <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
        <FaShareAlt style={{ fontSize: '36px', opacity: 0.3, marginBottom: '12px' }} />
        <p>공유된 분석 결과를 불러오는 중...</p>
      </div>
    </div>
  )

  // ── 정상 표시 ──────────────────────────────────────────────────
  return (
    <div style={pageWrap}>
      <div style={{ maxWidth: '900px', margin: '0 auto', width: '100%' }}>

        {/* 브랜딩 헤더 */}
        <div style={brandHeader}>
          <FaRobot style={{ fontSize: '20px', color: 'var(--accent)' }} />
          <span style={{ fontSize: '15px', fontWeight: 700 }}>Claude Java Toolkit</span>
          <span style={{ marginLeft: 'auto', fontSize: '11px', color: 'var(--text-muted)' }}>
            공유된 분석 결과
          </span>
        </div>

        {/* 메타 카드 */}
        <div style={metaCard}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <FaShareAlt style={{ color: 'var(--accent)', fontSize: '16px' }} />
            <span style={typeBadge}>{data.menuName}</span>
            <h1 style={{ fontSize: '17px', fontWeight: 700, margin: 0, flex: 1 }}>
              {data.title || '(제목 없음)'}
            </h1>
          </div>
          <div style={{ display: 'flex', gap: '12px', marginTop: '10px', flexWrap: 'wrap', fontSize: '12px', color: 'var(--text-muted)' }}>
            <span>공유일: {data.sharedAt}</span>
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: '4px',
              padding: '2px 8px', borderRadius: '10px',
              background: 'rgba(245,158,11,0.12)', color: '#f59e0b',
              border: '1px solid #f59e0b', fontWeight: 600,
            }}>
              <FaClock style={{ fontSize: '10px' }} /> {data.remaining}
            </span>
          </div>
        </div>

        {/* 입력 섹션 */}
        {data.inputText && (
          <div style={sectionCard}>
            <div style={sectionHeader}>
              <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 700, color: 'var(--text-muted)' }}>
                📝 입력 코드
              </h3>
              <CopyBtn
                onClick={() => handleCopy(data.inputText, 'input')}
                copied={copiedSection === 'input'}
              />
            </div>
            <pre style={codePreStyle}>{data.inputText}</pre>
          </div>
        )}

        {/* 결과 섹션 */}
        <div style={sectionCard}>
          <div style={sectionHeader}>
            <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 700, color: 'var(--text-muted)' }}>
              📊 분석 결과
            </h3>
            <CopyBtn
              onClick={() => handleCopy(data.resultText, 'result')}
              copied={copiedSection === 'result'}
            />
          </div>
          <div className="markdown-body" style={{ padding: '16px', fontSize: '14px' }}>
            <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownCodeComponents}>
              {data.resultText || '_(내용 없음)_'}
            </ReactMarkdown>
          </div>
        </div>

        {/* 푸터 */}
        <div style={footerStyle}>
          <span>이 링크는 <strong>{data.remaining}</strong> 후 자동 만료됩니다.</span>
          <span>Powered by <a href="/" style={{ color: 'var(--accent)', textDecoration: 'none' }}>Claude Java Toolkit</a></span>
        </div>
      </div>
    </div>
  )
}

function CopyBtn({ onClick, copied }: { onClick: () => void; copied: boolean }) {
  return (
    <button onClick={onClick} style={copyBtnStyle}>
      {copied ? <><FaCheck style={{ color: 'var(--green)' }} /> 복사됨</> : <><FaCopy /> 복사</>}
    </button>
  )
}

// ── Styles ────────────────────────────────────────────────────────

const pageWrap: React.CSSProperties = {
  minHeight: '100vh', background: 'var(--bg-primary)', padding: '24px',
  display: 'flex', flexDirection: 'column', alignItems: 'center',
}
const centeredCard: React.CSSProperties = {
  display: 'flex', flexDirection: 'column', alignItems: 'center',
  justifyContent: 'center', minHeight: '50vh',
  textAlign: 'center',
}
const brandHeader: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '10px',
  padding: '14px 20px', marginBottom: '16px',
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '12px',
}
const metaCard: React.CSSProperties = {
  padding: '18px 20px', marginBottom: '16px',
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '12px',
}
const typeBadge: React.CSSProperties = {
  fontSize: '11px', padding: '2px 10px', borderRadius: '6px',
  background: 'var(--accent-subtle)', color: 'var(--accent)',
  fontWeight: 600,
}
const sectionCard: React.CSSProperties = {
  marginBottom: '16px',
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '12px', overflow: 'hidden',
}
const sectionHeader: React.CSSProperties = {
  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
  padding: '12px 16px',
  borderBottom: '1px solid var(--border-color)',
  background: 'var(--bg-tertiary)',
}
const codePreStyle: React.CSSProperties = {
  padding: '16px', margin: 0, fontSize: '13px',
  fontFamily: 'Consolas, Monaco, monospace',
  lineHeight: 1.6, whiteSpace: 'pre-wrap',
  maxHeight: '400px', overflow: 'auto',
  background: 'var(--bg-primary)',
}
const copyBtnStyle: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: '4px',
  padding: '4px 10px', borderRadius: '6px', fontSize: '11px',
  background: 'transparent', color: 'var(--text-sub)',
  border: '1px solid var(--border-color)', cursor: 'pointer',
}
const footerStyle: React.CSSProperties = {
  display: 'flex', justifyContent: 'space-between',
  padding: '16px 0', fontSize: '11px', color: 'var(--text-muted)',
  borderTop: '1px solid var(--border-color)', marginTop: '8px',
  flexWrap: 'wrap', gap: '8px',
}
const homeLinkStyle: React.CSSProperties = {
  color: 'var(--accent)', textDecoration: 'none', fontSize: '13px',
}
