import { useEffect, useState, useCallback } from 'react'
import { FaUserLock, FaSave } from 'react-icons/fa'
import { useApi } from '../../hooks/useApi'
import { useToast } from '../../hooks/useToast'

interface User { id: number; username: string; role: string; enabled: boolean }

// AdminPermissionController의 FEATURE_PATHS와 동일한 목록 (ADMIN 전용 메뉴 제외)
const FEATURES = [
  {
    category: '채팅',
    items: [{ key: 'chat', label: 'AI 채팅' }],
  },
  {
    category: '분석',
    items: [
      { key: 'workspace', label: '통합 워크스페이스' },
      { key: 'pipelines', label: '분석 파이프라인' },
      { key: 'advisor', label: 'SQL 리뷰' },
      { key: 'sql-translate', label: 'SQL DB 번역' },
      { key: 'sql-batch', label: '배치 SQL 분석' },
      { key: 'erd', label: 'ERD 분석' },
      { key: 'complexity', label: '복잡도 분석' },
      { key: 'explain', label: '실행계획 분석' },
      { key: 'harness', label: '코드 리뷰 하네스' },
      { key: 'codereview', label: '코드 리뷰' },
    ],
  },
  {
    category: '생성',
    items: [
      { key: 'docgen', label: '기술 문서' },
      { key: 'testgen', label: '테스트 생성' },
      { key: 'apispec', label: 'API 명세' },
      { key: 'converter', label: '코드 변환' },
      { key: 'mockdata', label: 'Mock 데이터' },
      { key: 'migration', label: 'DB 마이그레이션' },
      { key: 'batch', label: 'Batch 처리' },
      { key: 'depcheck', label: '의존성 분석' },
      { key: 'migrate', label: 'Spring 마이그레이션' },
    ],
  },
  {
    category: '기록',
    items: [
      { key: 'history', label: '리뷰 이력' },
      { key: 'favorites', label: '즐겨찾기' },
      { key: 'usage', label: '사용량 모니터링' },
      { key: 'roi-report', label: 'ROI 리포트' },
      { key: 'schedule', label: '분석 스케줄링' },
      { key: 'review-requests', label: '팀 리뷰 요청' },
    ],
  },
  {
    category: '도구',
    items: [
      { key: 'loganalyzer', label: '로그 분석기' },
      { key: 'regex', label: '정규식 생성기' },
      { key: 'commitmsg', label: '커밋 메시지' },
      { key: 'maskgen', label: '마스킹 스크립트' },
      { key: 'input-masking', label: '민감정보 마스킹' },
      { key: 'github-pr', label: 'GitHub PR 리뷰' },
      { key: 'git-diff', label: 'Git Diff 분석' },
    ],
  },
  {
    category: '기타',
    items: [
      { key: 'prompts', label: '프롬프트 템플릿' },
      { key: 'search', label: '글로벌 검색' },
    ],
  },
]

export default function AdminPermissionsPage() {
  const [users, setUsers] = useState<User[]>([])
  const [selectedUser, setSelectedUser] = useState<User | null>(null)
  const [permissions, setPermissions] = useState<Record<string, boolean>>({})
  const [saving, setSaving] = useState(false)
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    api.get('/api/v1/admin/users').then((data) => {
      if (data) {
        const list = (data as User[]).filter((u) => u.role !== 'ADMIN')
        setUsers(list)
        if (list.length > 0 && !selectedUser) setSelectedUser(list[0])
      }
    })
  }, [])

  const loadPermissions = useCallback(async (userId: number) => {
    try {
      const res = await fetch(`/admin/permissions/${userId}`, { credentials: 'include' })
      if (res.ok) {
        const data = await res.json()
        const perms: Record<string, boolean> = {}
        Object.keys(data).forEach((k) => { perms[k] = data[k] === 'true' || data[k] === true })
        setPermissions(perms)
      }
    } catch { toast.error('권한 로드 실패') }
  }, [toast])

  useEffect(() => {
    if (selectedUser) loadPermissions(selectedUser.id)
  }, [selectedUser, loadPermissions])

  const toggleFeature = (key: string) => {
    setPermissions((prev) => ({ ...prev, [key]: !(prev[key] ?? true) }))
  }

  const toggleAll = (category: string, enabled: boolean) => {
    const cat = FEATURES.find((c) => c.category === category)
    if (!cat) return
    const next = { ...permissions }
    cat.items.forEach((item) => { next[item.key] = enabled })
    setPermissions(next)
  }

  const save = async () => {
    if (!selectedUser) return
    setSaving(true)
    try {
      const params = new URLSearchParams()
      Object.entries(permissions).forEach(([k, v]) => { params.append(k, String(v)) })
      const res = await fetch(`/admin/permissions/${selectedUser.id}/save`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params,
        credentials: 'include',
      })
      if (res.ok) toast.success('권한이 저장되었습니다.')
      else toast.error('저장 실패')
    } catch { toast.error('오류') }
    setSaving(false)
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaUserLock style={{ color: '#ef4444' }} /> 사용자 권한 관리
        <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400, marginLeft: '8px' }}>
          (ADMIN 외 사용자의 기능별 접근 권한)
        </span>
      </h2>

      <div style={{ display: 'grid', gridTemplateColumns: '250px 1fr', gap: '16px' }}>
        {/* 사용자 목록 */}
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '12px' }}>
          <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px', textTransform: 'uppercase' }}>
            사용자 ({users.length}명)
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            {users.map((u) => (
              <button key={u.id} onClick={() => setSelectedUser(u)} style={{
                display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 10px',
                borderRadius: '6px', fontSize: '13px', cursor: 'pointer', textAlign: 'left',
                border: '1px solid transparent',
                background: selectedUser?.id === u.id ? 'var(--accent-subtle)' : 'transparent',
                color: selectedUser?.id === u.id ? 'var(--accent)' : 'var(--text-sub)',
                fontWeight: selectedUser?.id === u.id ? 600 : 400,
              }}>
                <span style={{ flex: 1 }}>{u.username}</span>
                <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '8px', background: 'var(--bg-primary)' }}>
                  {u.role}
                </span>
              </button>
            ))}
            {users.length === 0 && <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>등록된 사용자 없음</div>}
          </div>
        </div>

        {/* 권한 토글 */}
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '20px' }}>
          {selectedUser ? (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <h3 style={{ fontSize: '15px', fontWeight: 700 }}>{selectedUser.username} 님의 권한</h3>
                <button onClick={save} disabled={saving} style={{
                  display: 'flex', alignItems: 'center', gap: '6px',
                  padding: '8px 16px', borderRadius: '8px',
                  background: 'var(--accent)', color: '#fff', border: 'none',
                  cursor: 'pointer', fontSize: '13px', fontWeight: 600,
                }}>
                  <FaSave /> {saving ? '저장 중...' : '저장'}
                </button>
              </div>

              {FEATURES.map((cat) => (
                <div key={cat.category} style={{ marginBottom: '20px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                    <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>{cat.category}</span>
                    <button onClick={() => toggleAll(cat.category, true)} style={smallToggleBtn}>전체 허용</button>
                    <button onClick={() => toggleAll(cat.category, false)} style={smallToggleBtn}>전체 차단</button>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '6px' }}>
                    {cat.items.map((item) => {
                      const allowed = permissions[item.key] ?? true
                      return (
                        <label key={item.key} style={{
                          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                          padding: '8px 12px', borderRadius: '8px', cursor: 'pointer',
                          border: `1px solid ${allowed ? 'var(--border-color)' : 'rgba(239,68,68,0.3)'}`,
                          background: allowed ? 'var(--bg-primary)' : 'rgba(239,68,68,0.05)',
                          fontSize: '12px',
                        }}>
                          <span style={{ color: allowed ? 'var(--text-primary)' : 'var(--text-muted)' }}>{item.label}</span>
                          <ToggleSwitch checked={allowed} onChange={() => toggleFeature(item.key)} />
                        </label>
                      )
                    })}
                  </div>
                </div>
              ))}
            </>
          ) : (
            <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
              좌측에서 사용자를 선택하세요.
            </div>
          )}
        </div>
      </div>
    </>
  )
}

function ToggleSwitch({ checked, onChange }: { checked: boolean; onChange: () => void }) {
  return (
    <div onClick={onChange} style={{
      width: '32px', height: '18px', borderRadius: '10px', cursor: 'pointer', position: 'relative',
      background: checked ? 'var(--accent)' : 'var(--border-color)', transition: 'all 0.2s',
    }}>
      <div style={{
        width: '14px', height: '14px', borderRadius: '50%', background: '#fff',
        position: 'absolute', top: '2px', left: checked ? '16px' : '2px', transition: 'all 0.2s',
      }} />
    </div>
  )
}

const smallToggleBtn: React.CSSProperties = {
  background: 'none', border: '1px solid var(--border-color)', borderRadius: '4px',
  padding: '2px 8px', fontSize: '11px', color: 'var(--text-muted)', cursor: 'pointer',
}
