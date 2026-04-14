import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  FaPlus, FaPlay, FaEdit, FaTrash, FaClock, FaProjectDiagram,
  FaSpinner,
} from 'react-icons/fa'
import { useToast } from '../hooks/useToast'
import SourceSelector from '../components/common/SourceSelector'

interface Pipeline {
  id: number
  name: string
  description: string
  inputLanguage: string
  isBuiltin: boolean
  scheduleCron?: string
  scheduleEnabled?: boolean
}

export default function PipelinePage() {
  const [pipelines, setPipelines] = useState<Pipeline[]>([])
  const [runModal, setRunModal] = useState<Pipeline | null>(null)
  const [runInput, setRunInput] = useState('')
  const [running, setRunning] = useState(false)
  const toast = useToast()
  const navigate = useNavigate()

  useEffect(() => {
    // Load pipelines from the page — fallback to fetching DOM
    loadPipelines()
  }, [])

  const loadPipelines = async () => {
    try {
      const res = await fetch('/api/v1/pipelines', { credentials: 'include' })
      if (res.ok) {
        const json = await res.json()
        const data = json.data ?? json
        if (Array.isArray(data)) {
          setPipelines(data)
          return
        }
      }
    } catch {
      // ignore
    }
    // Fallback: fetch pipeline list page and extract data
    toast.info('파이프라인 목록을 불러오는 중...')
  }

  const openRunModal = (p: Pipeline) => {
    setRunModal(p)
    setRunInput(p.isBuiltin ? '' : '')
  }

  const runPipeline = async () => {
    if (!runModal || !runInput.trim()) return
    setRunning(true)
    try {
      const body = new URLSearchParams({
        input: runInput,
        language: runModal.inputLanguage || 'java',
      })
      const res = await fetch(`/pipelines/${runModal.id}/run`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Accept': 'application/json',
        },
        body,
        credentials: 'include',
      })

      // 응답 본문을 텍스트로 먼저 받아서 JSON 파싱 실패도 안전하게 처리
      const raw = await res.text()
      let data: { success?: boolean; error?: string; executionId?: number } | null = null
      try { data = JSON.parse(raw) } catch { /* HTML 응답 등 */ }

      if (!res.ok || !data) {
        const hint = !data
          ? `서버가 JSON 을 반환하지 않음 (HTTP ${res.status}). 인증/권한 또는 인터셉터 차단 가능성.`
          : `HTTP ${res.status}`
        toast.error(`실행 시작 실패 — ${hint}`)
        return
      }
      if (data.success === false) {
        toast.error(data.error || '실행 시작 실패 — 알 수 없는 오류')
        return
      }
      toast.success('파이프라인 실행을 시작했습니다.')
      setRunModal(null)
      if (data.executionId) {
        navigate(`/pipelines/execution/${data.executionId}`)
      }
    } catch (e) {
      toast.error('실행 요청 실패: ' + (e instanceof Error ? e.message : String(e)))
    } finally {
      setRunning(false)
    }
  }

  const deletePipeline = async (id: number) => {
    if (!confirm('이 파이프라인을 삭제하시겠습니까?')) return
    await fetch(`/pipelines/${id}/delete`, { method: 'POST', credentials: 'include' })
    toast.success('삭제되었습니다.')
    loadPipelines()
  }

  const builtins = pipelines.filter((p) => p.isBuiltin)
  const customs = pipelines.filter((p) => !p.isBuiltin)

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ fontSize: '20px', fontWeight: 700 }}>
          <FaProjectDiagram style={{ color: 'var(--purple)', marginRight: '8px' }} />
          분석 파이프라인
        </h2>
        <a href="/pipelines/new" style={styles.createBtn}>
          <FaPlus /> 새 파이프라인
        </a>
      </div>

      {/* Built-in Pipelines */}
      <h3 style={styles.sectionTitle}>내장 파이프라인</h3>
      <div style={styles.grid}>
        {builtins.map((p) => (
          <PipelineCard key={p.id} pipeline={p} onRun={openRunModal} onDelete={deletePipeline} />
        ))}
      </div>

      {/* User Pipelines */}
      {customs.length > 0 && (
        <>
          <h3 style={{ ...styles.sectionTitle, marginTop: '28px' }}>사용자 정의</h3>
          <div style={styles.grid}>
            {customs.map((p) => (
              <PipelineCard key={p.id} pipeline={p} onRun={openRunModal} onDelete={deletePipeline} />
            ))}
          </div>
        </>
      )}

      {pipelines.length === 0 && (
        <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
          <FaProjectDiagram style={{ fontSize: '40px', opacity: 0.3, marginBottom: '12px' }} />
          <p>파이프라인을 불러오는 중이거나 등록된 파이프라인이 없습니다.</p>
          <p style={{ fontSize: '13px' }}>서버에서 파이프라인 데이터를 가져옵니다.</p>
        </div>
      )}

      {/* Run Modal */}
      {runModal && (
        <div style={styles.modalOverlay} onClick={() => setRunModal(null)}>
          <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
              <h3 style={{ fontSize: '16px', margin: 0 }}>
                <FaPlay style={{ color: 'var(--accent)', marginRight: '8px' }} />
                {runModal.name} 실행
              </h3>
              <SourceSelector
                mode={runModal.inputLanguage === 'sql' ? 'sql' : 'both'}
                onSelect={(code) => setRunInput((prev) => prev ? prev + '\n\n' + code : code)}
              />
            </div>
            <textarea
              style={{ ...styles.runTextarea }}
              value={runInput}
              onChange={(e) => setRunInput(e.target.value)}
              placeholder="분석할 코드 또는 SQL을 입력하거나, 위 '소스 선택' 버튼으로 파일/DB 객체를 불러오세요..."
              rows={12}
              autoFocus
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '12px' }}>
              <button style={styles.cancelBtn} onClick={() => setRunModal(null)}>취소</button>
              <button style={styles.runBtn} onClick={runPipeline} disabled={running || !runInput.trim()}>
                {running ? <><FaSpinner className="spin" /> 실행 중...</> : <><FaPlay /> 실행</>}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function PipelineCard({
  pipeline: p,
  onRun,
  onDelete,
}: {
  pipeline: Pipeline
  onRun: (p: Pipeline) => void
  onDelete: (id: number) => void
}) {
  return (
    <div style={styles.card}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
        <FaProjectDiagram style={{ color: p.isBuiltin ? 'var(--purple)' : 'var(--accent)', fontSize: '16px' }} />
        <span style={{ fontSize: '14px', fontWeight: 600, flex: 1 }}>{p.name}</span>
        {p.scheduleEnabled && (
          <span style={{ fontSize: '11px', color: 'var(--green)', display: 'flex', alignItems: 'center', gap: '3px' }}>
            <FaClock /> 스케줄
          </span>
        )}
      </div>
      <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '12px', minHeight: '36px' }}>
        {p.description || '설명 없음'}
      </p>
      <div style={{ display: 'flex', gap: '6px' }}>
        <span style={styles.langBadge}>{p.inputLanguage || 'java'}</span>
        <div style={{ flex: 1 }} />
        <button style={styles.cardBtn} onClick={() => onRun(p)} title="실행">
          <FaPlay />
        </button>
        <a href={`/pipelines/${p.id}`} style={styles.cardBtn} title="편집">
          <FaEdit />
        </a>
        {!p.isBuiltin && (
          <button style={{ ...styles.cardBtn, color: 'var(--red)' }} onClick={() => onDelete(p.id)} title="삭제">
            <FaTrash />
          </button>
        )}
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  grid: {
    display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '16px',
  },
  card: {
    background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
    borderRadius: '12px', padding: '18px',
  },
  sectionTitle: {
    fontSize: '14px', fontWeight: 700, color: 'var(--text-muted)',
    textTransform: 'uppercase' as const, letterSpacing: '0.5px', marginBottom: '12px',
  },
  createBtn: {
    display: 'flex', alignItems: 'center', gap: '6px',
    padding: '8px 16px', borderRadius: '8px',
    background: 'var(--accent)', color: '#fff', fontSize: '13px', fontWeight: 600,
    textDecoration: 'none',
  },
  langBadge: {
    fontSize: '11px', padding: '2px 8px', borderRadius: '4px',
    background: 'var(--accent-subtle)', color: 'var(--accent)',
  },
  cardBtn: {
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    width: '28px', height: '28px', borderRadius: '6px',
    border: '1px solid var(--border-color)', background: 'transparent',
    color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px',
    textDecoration: 'none',
  },
  modalOverlay: {
    position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.6)',
    display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 500,
  },
  modal: {
    background: 'var(--bg-secondary)', borderRadius: '16px',
    border: '1px solid var(--border-color)', padding: '24px',
    width: 'min(600px, 90vw)', maxHeight: '80vh',
  },
  runTextarea: {
    width: '100%', minHeight: '200px', resize: 'vertical' as const,
    fontFamily: 'monospace', fontSize: '13px',
  },
  cancelBtn: {
    padding: '8px 16px', borderRadius: '8px',
    border: '1px solid var(--border-color)', background: 'transparent',
    color: 'var(--text-sub)', cursor: 'pointer', fontSize: '13px',
  },
  runBtn: {
    padding: '8px 20px', borderRadius: '8px',
    background: 'var(--accent)', color: '#fff', border: 'none',
    cursor: 'pointer', fontSize: '13px', fontWeight: 600,
    display: 'flex', alignItems: 'center', gap: '6px',
  },
}
