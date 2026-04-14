import { useState } from 'react'
import { FaSlidersH, FaSave, FaUndo } from 'react-icons/fa'
import { useToast } from '../hooks/useToast'

/** 프롬프트 템플릿 (Thymeleaf 버전 feature codes 유지) */
const FEATURE_CODES = [
  { code: 'SQL_REVIEW', label: 'SQL 성능·품질 리뷰', color: '#3b82f6' },
  { code: 'SQL_SECURITY', label: 'SQL 보안 취약점 검사', color: '#ef4444' },
  { code: 'SQL_EXPLAIN', label: 'Oracle 실행계획 AI 분석', color: '#f59e0b' },
  { code: 'CODE_REVIEW', label: 'Java/Spring 코드 리뷰', color: '#3b82f6' },
  { code: 'CODE_SECURITY', label: 'Java/Spring 보안 감사', color: '#ef4444' },
  { code: 'DOC_GENERATE', label: '소스코드 기술 문서 생성', color: '#10b981' },
  { code: 'ERD_ANALYZE', label: 'ERD 분석', color: '#8b5cf6' },
]

export default function PromptsPage() {
  const [selectedCode, setSelectedCode] = useState(FEATURE_CODES[0].code)
  const [promptText, setPromptText] = useState('')
  const [saving, setSaving] = useState(false)
  const toast = useToast()

  const save = async () => {
    setSaving(true)
    try {
      const res = await fetch('/prompts/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ featureCode: selectedCode, promptText }),
        credentials: 'include',
      })
      if (res.ok) toast.success('프롬프트 저장됨')
      else toast.error('저장 실패')
    } catch { toast.error('오류') }
    setSaving(false)
  }

  const reset = async () => {
    if (!confirm('기본 프롬프트로 되돌리시겠습니까?')) return
    try {
      await fetch('/prompts/reset', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ featureCode: selectedCode }),
        credentials: 'include',
      })
      toast.success('기본값으로 복원됨')
      setPromptText('')
    } catch { toast.error('오류') }
  }

  const current = FEATURE_CODES.find((f) => f.code === selectedCode)!

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaSlidersH style={{ color: '#f97316' }} /> 프롬프트 템플릿
        <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400, marginLeft: '8px' }}>
          (기능별 AI 프롬프트 오버라이드)
        </span>
      </h2>

      <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: '16px' }}>
        {/* 기능 목록 */}
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '12px' }}>
          <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-muted)', marginBottom: '8px', textTransform: 'uppercase' }}>
            기능
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
            {FEATURE_CODES.map((f) => (
              <button key={f.code} onClick={() => setSelectedCode(f.code)} style={{
                display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 10px',
                borderRadius: '6px', fontSize: '12px', cursor: 'pointer', textAlign: 'left',
                border: '1px solid transparent',
                background: selectedCode === f.code ? 'var(--accent-subtle)' : 'transparent',
                color: selectedCode === f.code ? 'var(--accent)' : 'var(--text-sub)',
                fontWeight: selectedCode === f.code ? 600 : 400,
              }}>
                <span style={{ width: '4px', height: '16px', borderRadius: '2px', background: f.color }} />
                <span style={{ flex: 1 }}>{f.label}</span>
              </button>
            ))}
          </div>
        </div>

        {/* 편집기 */}
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
            <h3 style={{ fontSize: '15px', fontWeight: 700 }}>{current.label}</h3>
            <div style={{ display: 'flex', gap: '6px' }}>
              <button onClick={reset} style={outlineBtn}><FaUndo /> 기본값</button>
              <button onClick={save} disabled={saving} style={primaryBtn}><FaSave /> {saving ? '저장 중...' : '저장'}</button>
            </div>
          </div>

          <div style={{ padding: '10px 14px', background: 'rgba(59,130,246,0.08)', border: '1px solid rgba(59,130,246,0.2)', borderRadius: '6px', fontSize: '12px', color: 'var(--blue)', marginBottom: '12px' }}>
            💡 이 페이지는 기능별 AI 프롬프트를 오버라이드합니다. 비워두면 기본 프롬프트가 사용됩니다.
            <br />
            더 세밀한 분석 유형별 프롬프트는 <strong>"AI 프롬프트 관리"</strong> 메뉴를 이용하세요.
          </div>

          <textarea
            value={promptText}
            onChange={(e) => setPromptText(e.target.value)}
            placeholder={`${current.label}에 사용할 커스텀 시스템 프롬프트를 입력하세요...\n\n예시:\n당신은 Java/Spring Boot 전문가입니다...`}
            style={{ width: '100%', minHeight: '450px', fontFamily: "'Consolas', monospace", fontSize: '13px', lineHeight: '1.6' }}
          />
          <p style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '6px' }}>
            저장 시 이 프롬프트가 {current.label} 기능에 적용됩니다. 기본값 버튼으로 복원 가능.
          </p>
        </div>
      </div>
    </>
  )
}

const outlineBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '5px', padding: '6px 14px', borderRadius: '6px', fontSize: '12px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-sub)', cursor: 'pointer' }
const primaryBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '5px', padding: '6px 14px', borderRadius: '6px', fontSize: '12px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontWeight: 600 }
