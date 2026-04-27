import { useEffect, useMemo, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { FaSpinner, FaCopy, FaCheck, FaDownload, FaFilePdf, FaLayerGroup } from 'react-icons/fa'
import type { IconType } from 'react-icons'
import { useToast } from '../../hooks/useToast'
import { copyToClipboard, printAsHtml, markdownToHtml } from '../../utils/clipboard'

/**
 * Phase D — 하네스 4-stage 결과 패널 (재사용 가능 컴포넌트).
 *
 * <p>{@link useHarnessStream} 훅의 stages/streaming/activeStage를 props로 받아
 * 탭 네비 + 단계별 본문 + 액션 버튼(복사/MD 다운로드/PDF) 을 렌더합니다.
 *
 * <p>Phase B(SP→Java), Phase C(SQL 최적화)에서도 동일한 컴포넌트로 재사용됩니다.
 */
export interface StageDef<K extends string = string> {
  key: K
  num: number
  title: string
  icon: React.ReactNode
  color: string
  desc: string
}

export interface HarnessStagePanelsProps<K extends string = string> {
  /** 단계 정의 — 보통 4개 (analyst/builder/reviewer/verifier) */
  stages: StageDef<K>[]
  /** 단계별 누적 텍스트 (useHarnessStream의 stages) */
  buffers: Record<K, string>
  /** 스트리밍 진행 중 여부 */
  streaming: boolean
  /** 현재 활성 stage (1-based, 0=비활성) */
  activeStreamingStage: number
  /** 다운로드/이메일 파일명 prefix (예: "log-rca", "sp-migration") */
  filePrefix: string
  /** "전체 결과" 탭 표시 여부 (기본 true) */
  showAllTab?: boolean
  /** 빈 상태 메시지 커스터마이즈 */
  emptyMessage?: string
}

const ALL_KEY = '__all__' as const
type TabKey<K extends string> = K | typeof ALL_KEY

export default function HarnessStagePanels<K extends string>({
  stages,
  buffers,
  streaming,
  activeStreamingStage,
  filePrefix,
  showAllTab = true,
  emptyMessage = '분석을 시작하면 단계별 결과가 표시됩니다',
}: HarnessStagePanelsProps<K>) {
  const [activeTab, setActiveTab] = useState<TabKey<K>>(stages[0]?.key as K)
  const [copiedKey, setCopiedKey] = useState<TabKey<K> | null>(null)
  const toast = useToast()

  // 스트리밍 중 활성 stage가 바뀌면 자동 탭 전환
  useEffect(() => {
    if (!streaming || activeStreamingStage < 1 || activeStreamingStage > stages.length) return
    const newKey = stages[activeStreamingStage - 1]?.key
    if (newKey && newKey !== activeTab) setActiveTab(newKey)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeStreamingStage, streaming])

  const hasAnyResult = useMemo(
    () => Object.values(buffers).some((v) => (v as string).length > 0),
    [buffers]
  )

  const stageContent = (key: TabKey<K>): string => {
    if (key === ALL_KEY) {
      return stages
        .map((s) => `# [${s.num}] ${s.title}\n\n${(buffers[s.key] || '_(결과 없음)_')}`)
        .join('\n\n---\n\n')
    }
    return buffers[key as K] || ''
  }

  const stageTitle = (key: TabKey<K>): string => {
    if (key === ALL_KEY) return '전체 결과'
    const s = stages.find((x) => x.key === key)
    return s ? `[${s.num}] ${s.title}` : String(key)
  }

  const onCopy = async (key: TabKey<K>) => {
    const text = stageContent(key)
    if (!text) { toast.error('복사할 결과가 없습니다.'); return }
    const ok = await copyToClipboard(text)
    if (ok) {
      setCopiedKey(key)
      setTimeout(() => setCopiedKey((c) => (c === key ? null : c)), 3000)
      toast.success('복사되었습니다.')
    } else {
      toast.error('복사 실패')
    }
  }

  const onDownload = (key: TabKey<K>) => {
    const text = stageContent(key)
    if (!text) { toast.warning('내려받을 결과가 없습니다.'); return }
    const blob = new Blob([text], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const stageLabel = key === ALL_KEY ? 'all' : String(key)
    a.download = `${filePrefix}_${stageLabel}_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  const onPrint = (key: TabKey<K>) => {
    const text = stageContent(key)
    if (!text) { toast.warning('인쇄할 결과가 없습니다.'); return }
    printAsHtml(`<h1>${stageTitle(key)}</h1>` + markdownToHtml(text), `${filePrefix} - ${stageTitle(key)}`)
  }

  const tabs: TabKey<K>[] = showAllTab ? [...stages.map((s) => s.key), ALL_KEY] : stages.map((s) => s.key)

  return (
    <div style={panelStyle}>
      {/* 탭 네비 */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--border-color)', overflowX: 'auto', flexShrink: 0 }}>
        {tabs.map((tabKey) => {
          const isActive    = activeTab === tabKey
          const isAll       = tabKey === ALL_KEY
          const stage       = isAll ? null : stages.find((s) => s.key === tabKey)
          const hasContent  = isAll ? hasAnyResult : (buffers[tabKey as K] || '').length > 0
          const isStreaming = !isAll && streaming && stage?.num === activeStreamingStage
          const tabColor    = isAll ? '#ef4444' : stage?.color || 'var(--accent)'
          const tabTitle    = isAll ? '전체 결과' : stage?.title || ''
          const tabDesc     = isAll ? '4단계 결과 통합' : stage?.desc || ''
          const tabIcon: React.ReactNode = isAll ? <FaLayerGroup /> : stage?.icon
          return (
            <button
              key={String(tabKey)}
              onClick={() => setActiveTab(tabKey)}
              title={tabDesc}
              style={{
                display: 'flex', alignItems: 'center', gap: '6px',
                padding: '10px 14px',
                background: isActive ? 'var(--bg-secondary)' : 'transparent',
                border: 'none',
                borderBottom: isActive ? `2px solid ${tabColor}` : '2px solid transparent',
                color: isActive ? tabColor : (hasContent ? 'var(--text-primary)' : 'var(--text-muted)'),
                cursor: 'pointer', fontSize: '12px', fontWeight: isActive ? 700 : 500,
                whiteSpace: 'nowrap', flexShrink: 0,
              }}
            >
              {isStreaming
                ? <FaSpinner className="spin" style={{ color: tabColor }} />
                : <span style={{ color: hasContent ? tabColor : 'var(--text-muted)' }}>{tabIcon}</span>}
              {!isAll && stage && <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>{stage.num}.</span>}
              <span>{tabTitle}</span>
              {hasContent && !isAll && (
                <span style={{ fontSize: '9px', color: 'var(--text-muted)' }}>
                  ({(buffers[tabKey as K] || '').length}자)
                </span>
              )}
            </button>
          )
        })}
      </div>

      {/* 액션 바 */}
      <div style={{ padding: '8px 14px', borderBottom: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '6px' }}>
        <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
          {activeTab === ALL_KEY ? '4단계 결과 통합' : (stages.find((s) => s.key === activeTab)?.desc || '')}
        </span>
        <div style={{ display: 'flex', gap: '4px' }}>
          <button style={miniBtn} onClick={() => onCopy(activeTab)} title="복사">
            {copiedKey === activeTab
              ? <><FaCheck style={{ color: 'var(--green)' }} /> <span style={{ color: 'var(--green)', fontWeight: 700 }}>복사됨</span></>
              : <><FaCopy /> 복사</>}
          </button>
          <button style={miniBtn} onClick={() => onDownload(activeTab)} title="MD"><FaDownload /> MD</button>
          <button style={miniBtn} onClick={() => onPrint(activeTab)} title="PDF"><FaFilePdf /> PDF</button>
        </div>
      </div>

      {/* 본문 */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '14px', minHeight: 0 }}>
        {(() => {
          const text = stageContent(activeTab)
          if (text) {
            const stage = activeTab === ALL_KEY ? null : stages.find((s) => s.key === activeTab)
            return (
              <div className="markdown-body">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{text}</ReactMarkdown>
                {streaming && stage && stage.num === activeStreamingStage && (
                  <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', color: 'var(--accent)', fontSize: '12px', marginTop: '8px' }}>
                    <FaSpinner className="spin" /> 스트리밍 중...
                  </div>
                )}
              </div>
            )
          }
          if (streaming && activeTab !== ALL_KEY) {
            const stage = stages.find((s) => s.key === activeTab)
            if (stage && stage.num > activeStreamingStage) {
              return <EmptyState icon="⏳" msg={`이전 단계가 완료되면 ${stage.title} 단계가 시작됩니다`} />
            }
            return <EmptyState icon="🔄" msg="AI 응답 대기 중..." />
          }
          if (activeTab === ALL_KEY) {
            return <EmptyState icon="📊" msg="분석을 시작하면 4단계 결과가 통합되어 표시됩니다" />
          }
          return <EmptyState icon="📝" msg={emptyMessage} />
        })()}
      </div>
    </div>
  )
}

function EmptyState({ icon, msg }: { icon: string; msg: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)', fontSize: '13px', flexDirection: 'column', gap: '8px', minHeight: '300px' }}>
      <div style={{ fontSize: '32px', opacity: 0.4 }}>{icon}</div>
      <p>{msg}</p>
    </div>
  )
}

const panelStyle: React.CSSProperties = { background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', display: 'flex', flexDirection: 'column', overflow: 'hidden' }
const miniBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '11px' }

// React 컴포넌트 외부에서 import할 수 있게 export
export { ALL_KEY }

// IconType 사용을 위해 — 외부 단계 정의에서 react-icons 컴포넌트를 직접 넣을 수 있게
export type { IconType }
