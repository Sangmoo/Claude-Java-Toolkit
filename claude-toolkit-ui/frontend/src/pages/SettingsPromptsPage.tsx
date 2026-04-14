import { useEffect, useState, useCallback } from 'react'
import { FaMagic, FaSave, FaUndo, FaCheck } from 'react-icons/fa'
import { useToast } from '../hooks/useToast'

/** 편집 가능한 분석 유형 — 백엔드 AnalysisType enum과 1:1 매칭 */
const ANALYSIS_TYPES = [
  { key: 'AI_CHAT', label: 'AI 채팅', color: '#8b5cf6', desc: 'Claude AI 어시스턴트의 기본 시스템 프롬프트' },
  { key: 'CODE_REVIEW', label: '코드 리뷰', color: '#3b82f6', desc: 'Java/Spring 코드 리뷰 프롬프트' },
  { key: 'SECURITY_AUDIT', label: '보안 감사', color: '#ef4444', desc: 'OWASP Top 10 기반 보안 취약점 검사' },
  { key: 'TEST_GENERATION', label: '테스트 생성', color: '#10b981', desc: 'JUnit 테스트 코드 생성' },
  { key: 'JAVADOC', label: 'Javadoc', color: '#06b6d4', desc: 'Javadoc 주석 자동 생성' },
  { key: 'REFACTOR', label: '리팩터링', color: '#8b5cf6', desc: '코드 리팩터링 제안' },
  { key: 'SQL_REVIEW', label: 'SQL 리뷰', color: '#3b82f6', desc: 'SQL 성능/품질 리뷰' },
  { key: 'SQL_SECURITY', label: 'SQL 보안', color: '#ef4444', desc: 'SQL Injection 등 보안 검사' },
  { key: 'SQL_TRANSLATE', label: 'SQL DB 번역', color: '#3b82f6', desc: 'DB 방언 간 SQL 변환' },
  { key: 'HARNESS', label: '코드 리뷰 하네스', color: '#8b5cf6', desc: '4단계 심층 리뷰 파이프라인' },
]

interface PromptData {
  defaultPrompt: string
  customPrompt?: string
  customId?: number
  promptName?: string
  hasCustom: boolean
}

export default function SettingsPromptsPage() {
  const [selectedType, setSelectedType] = useState(ANALYSIS_TYPES[0].key)
  const [promptData, setPromptData] = useState<PromptData | null>(null)
  const [editContent, setEditContent] = useState('')
  const [editName, setEditName] = useState('')
  const [saving, setSaving] = useState(false)
  const toast = useToast()

  const load = useCallback(async (type: string) => {
    try {
      const res = await fetch(`/settings/prompts/default/${type}`, { credentials: 'include' })
      if (res.ok) {
        const data = await res.json()
        if (data.success) {
          setPromptData(data)
          setEditContent(data.customPrompt || data.defaultPrompt || '')
          setEditName(data.promptName || '')
        }
      }
    } catch { toast.error('프롬프트 로드 실패') }
  }, [toast])

  useEffect(() => { load(selectedType) }, [selectedType, load])

  const save = async () => {
    setSaving(true)
    try {
      const res = await fetch('/settings/prompts/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          analysisType: selectedType,
          promptName: editName || `커스텀 - ${selectedType}`,
          systemPrompt: editContent,
        }),
        credentials: 'include',
      })
      const d = await res.json()
      if (d.success) {
        toast.success('프롬프트가 저장되었습니다.')
        load(selectedType)
      } else toast.error(d.error || '저장 실패')
    } catch { toast.error('오류') }
    setSaving(false)
  }

  const reset = async () => {
    if (!confirm('기본 프롬프트로 되돌리시겠습니까?')) return
    try {
      await fetch(`/settings/prompts/reset/${selectedType}`, { method: 'POST', credentials: 'include' })
      toast.success('기본 프롬프트로 복원됨')
      load(selectedType)
    } catch { toast.error('복원 실패') }
  }

  const current = ANALYSIS_TYPES.find((t) => t.key === selectedType)!

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaMagic style={{ color: '#f97316' }} /> AI 프롬프트 관리
        <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400, marginLeft: '8px' }}>
          (분석 유형별 시스템 프롬프트 커스터마이징)
        </span>
      </h2>

      <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: '16px' }}>
        {/* 분석 유형 목록 */}
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '12px' }}>
          <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-muted)', marginBottom: '8px', textTransform: 'uppercase' }}>
            분석 유형
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
            {ANALYSIS_TYPES.map((t) => (
              <button key={t.key} onClick={() => setSelectedType(t.key)} style={{
                display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 10px',
                borderRadius: '6px', fontSize: '13px', cursor: 'pointer', textAlign: 'left',
                border: '1px solid transparent',
                background: selectedType === t.key ? 'var(--accent-subtle)' : 'transparent',
                color: selectedType === t.key ? 'var(--accent)' : 'var(--text-sub)',
                fontWeight: selectedType === t.key ? 600 : 400,
              }}>
                <span style={{ width: '4px', height: '16px', borderRadius: '2px', background: t.color }} />
                <span style={{ flex: 1 }}>{t.label}</span>
                {promptData?.hasCustom && selectedType === t.key && <FaCheck style={{ color: 'var(--green)', fontSize: '10px' }} />}
              </button>
            ))}
          </div>
        </div>

        {/* 프롬프트 편집기 */}
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '14px' }}>
            <div>
              <h3 style={{ fontSize: '15px', fontWeight: 700 }}>{current.label}</h3>
              <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>{current.desc}</p>
            </div>
            <div style={{ display: 'flex', gap: '6px' }}>
              {promptData?.hasCustom && <button onClick={reset} style={outlineBtn}><FaUndo /> 기본값</button>}
              <button onClick={save} disabled={saving} style={primaryBtn}><FaSave /> {saving ? '저장 중...' : '저장'}</button>
            </div>
          </div>

          {promptData?.hasCustom && (
            <div style={{ padding: '8px 12px', background: 'rgba(34,197,94,0.08)', border: '1px solid rgba(34,197,94,0.2)', borderRadius: '6px', fontSize: '12px', color: 'var(--green)', marginBottom: '10px' }}>
              ✓ 커스텀 프롬프트 활성화됨 (저장 시 이 프롬프트가 실제 분석에 사용됩니다)
            </div>
          )}

          <div style={{ marginBottom: '10px' }}>
            <label style={labelSt}>프롬프트 이름</label>
            <input value={editName} onChange={(e) => setEditName(e.target.value)} placeholder={`커스텀 - ${current.label}`} style={inputSt} />
          </div>

          <label style={labelSt}>시스템 프롬프트</label>
          <textarea value={editContent} onChange={(e) => setEditContent(e.target.value)}
            style={{ width: '100%', minHeight: '400px', fontFamily: "'Consolas', monospace", fontSize: '13px', lineHeight: '1.6' }}
            placeholder="기본 프롬프트 로딩 중..."
          />
          <p style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '6px' }}>
            * 저장 시 이 프롬프트가 자동으로 활성화되어 실제 분석에 사용됩니다.
          </p>
        </div>
      </div>
    </>
  )
}

const labelSt: React.CSSProperties = { display: 'block', fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '4px', textTransform: 'uppercase' }
const inputSt: React.CSSProperties = { width: '100%', padding: '8px 10px', fontSize: '13px' }
const outlineBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '5px', padding: '6px 14px', borderRadius: '6px', fontSize: '12px', border: '1px solid var(--border-color)', background: 'transparent', color: 'var(--text-sub)', cursor: 'pointer' }
const primaryBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '5px', padding: '6px 14px', borderRadius: '6px', fontSize: '12px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontWeight: 600 }
