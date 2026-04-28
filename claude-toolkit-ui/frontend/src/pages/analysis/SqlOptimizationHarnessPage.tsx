import { useState, useEffect } from 'react'
import {
  FaPlay, FaSpinner, FaEraser, FaSearch, FaWrench, FaClipboardCheck, FaShieldAlt,
  FaTachometerAlt,
} from 'react-icons/fa'
import HarnessStagePanels, { type StageDef } from '../../components/common/HarnessStagePanels'
import SourceSelector from '../../components/common/SourceSelector'
import CostHint from '../../components/common/CostHint'
import { useHarnessStream } from '../../hooks/useHarnessStream'
import { useToast } from '../../hooks/useToast'

/**
 * Phase C — Oracle SQL 성능 최적화 하네스 페이지.
 *
 * <p>좌측: 쿼리 (필수) + 선택 입력 5개 (실행계획 / 통계 / 인덱스 / 데이터 볼륨 / 변경 불가 제약)
 *        + "소스 선택" 버튼으로 DB의 PROCEDURE/FUNCTION/PACKAGE 본문도 가져올 수 있음
 * <p>우측: {@link HarnessStagePanels} 4-stage 결과
 *
 * <p>SSE 엔드포인트: {@code POST /api/v1/sql-optimization/stream-init} → {@code GET /stream/{id}}
 * <p>권한: feature key {@code sql-optimization-harness}
 */
type StageKey = 'analyst' | 'builder' | 'reviewer' | 'verifier'

const STAGES: StageDef<StageKey>[] = [
  { key: 'analyst',  num: 1, title: '병목 분석',           icon: <FaSearch />,         color: '#3b82f6', desc: 'Analyst — 병목·카디널리티·인덱스·안티패턴 분석' },
  { key: 'builder',  num: 2, title: '개선 후보 N개',       icon: <FaWrench />,         color: '#10b981', desc: 'Builder — rewrite/DDL/힌트 후보 + 비용·리스크' },
  { key: 'reviewer', num: 3, title: '동등성·우선순위',     icon: <FaClipboardCheck />, color: '#f59e0b', desc: 'Reviewer — 결과 동등성·다른 쿼리 영향·점수표' },
  { key: 'verifier', num: 4, title: '검증·Rollout Plan', icon: <FaShieldAlt />,      color: '#8b5cf6', desc: 'Verifier — 정적 검증·단계별 적용 계획·롤백' },
]

export default function SqlOptimizationHarnessPage() {
  const [query, setQuery]                     = useState('')
  const [executionPlan, setExecutionPlan]     = useState('')
  const [tableStats, setTableStats]           = useState('')
  const [existingIndexes, setExistingIndexes] = useState('')
  const [dataVolume, setDataVolume]           = useState('')
  const [constraints, setConstraints]         = useState('')

  const { stages, streaming, activeStage, error, startStream, reset } =
    useHarnessStream<StageKey>(['analyst', 'builder', 'reviewer', 'verifier'])
  const toast = useToast()

  useEffect(() => {
    if (error) toast.error(error)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [error])

  /** SourceSelector 콜백 — DB 객체(SP/Function 등) 본문을 쿼리 영역에 채움. */
  const handleSourceSelect = (code: string) => {
    setQuery(code)
    toast.success('DB 객체 본문을 쿼리 영역에 로드했습니다')
  }

  const start = async () => {
    if (streaming) return
    if (!query.trim()) { toast.error('쿼리는 필수입니다'); return }

    const body = new URLSearchParams({
      query:            query.trim(),
      execution_plan:   executionPlan.trim(),
      table_stats:      tableStats.trim(),
      existing_indexes: existingIndexes.trim(),
      data_volume:      dataVolume.trim(),
      constraints:      constraints.trim(),
    })
    await startStream({
      initUrl: '/api/v1/sql-optimization/stream-init',
      streamUrlPrefix: '/api/v1/sql-optimization/stream/',
      body,
    })
  }

  const clearAll = () => {
    setQuery(''); setExecutionPlan(''); setTableStats('')
    setExistingIndexes(''); setDataVolume(''); setConstraints('')
    reset()
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
        <FaTachometerAlt style={{ fontSize: '20px', color: '#f59e0b' }} />
        <div>
          <h2 style={{ fontSize: '17px', fontWeight: 700, margin: 0 }}>SQL 최적화 하네스</h2>
          <p style={{ fontSize: '12px', color: 'var(--text-muted)', margin: 0 }}>
            4단계 파이프라인: 병목 분석 → 후보 N개 → 동등성·우선순위 → Rollout Plan
          </p>
        </div>
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 480px), 1fr))',
        gap: '14px',
        height: 'calc(100vh - 200px)',
        minHeight: '520px',
      }}>
        {/* ── 좌측 ── */}
        <div style={panelStyle}>
          <div style={panelHeaderStyle}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>최적화 입력</span>
            <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              <SourceSelector
                mode="sql"
                buttonLabel="소스선택하기"
                buttonTitle="Settings에 연결된 DB 의 SP/FUNCTION/PACKAGE/TRIGGER 선택"
                onSelect={handleSourceSelect}
              />
              <button style={smallBtn} onClick={clearAll} title="초기화" disabled={streaming}>
                <FaEraser /> 초기화
              </button>
            </div>
          </div>

          <div style={{ flex: 1, overflowY: 'auto', padding: '14px', display: 'flex', flexDirection: 'column', gap: '10px', minHeight: 0 }}>
            <Field
              label="쿼리"
              hint="느린 SELECT/UPDATE/MERGE 또는 SP 본문"
              value={query}
              onChange={setQuery}
              rows={8}
              required
              disabled={streaming}
              placeholder={"SELECT /*+ ... */ p.PRDT_NO, p.PRDT_NM, ...\n  FROM PRODUCT p, PRODUCT_HIST h\n WHERE p.PRDT_NO = h.PRDT_NO\n   AND ..."}
            />
            <Field
              label="실행계획 (선택, 강력 권장)"
              hint="EXPLAIN PLAN 결과 또는 DBMS_XPLAN.DISPLAY_CURSOR"
              value={executionPlan}
              onChange={setExecutionPlan}
              rows={6}
              disabled={streaming}
              placeholder={"Plan hash value: ...\n--------------------------------\n| Id | Operation | Name | Rows | Cost | ...\n--------------------------------"}
            />
            <Field
              label="테이블 통계 (선택)"
              hint="USER_TABLES.NUM_ROWS / LAST_ANALYZED / 컬럼별 NDV"
              value={tableStats}
              onChange={setTableStats}
              rows={4}
              disabled={streaming}
              placeholder={"PRODUCT_HIST: 4500만 행, 마지막 통계 2025-12, NDV(PRDT_NO)=12000"}
            />
            <Field
              label="기존 인덱스 (선택)"
              hint="CREATE INDEX 문 또는 USER_INDEXES 출력"
              value={existingIndexes}
              onChange={setExistingIndexes}
              rows={4}
              disabled={streaming}
              placeholder={"CREATE INDEX IDX_PRDT_HIST_PK ON PRODUCT_HIST(PRDT_NO);\nCREATE INDEX IDX_PRDT_HIST_DT ON PRODUCT_HIST(REG_DT);"}
            />
            <Field
              label="데이터 볼륨 (선택)"
              hint="테이블별 행 수, 데이터 분포, skew"
              value={dataVolume}
              onChange={setDataVolume}
              rows={3}
              disabled={streaming}
              placeholder={"PRODUCT 30만, PRODUCT_HIST 4500만 (90% 최근 6개월)"}
            />
            <Field
              label="변경 불가 제약 (선택, 강력 권장)"
              hint="건드리면 안 되는 테이블/인덱스/SP, 운영 시간대 등"
              value={constraints}
              onChange={setConstraints}
              rows={3}
              disabled={streaming}
              placeholder={"T_INV는 절대 인덱스 추가 금지 (다른 시스템 INSERT 영향)\n야간 02-04시만 DDL 허용"}
            />
          </div>

          <div style={{ padding: '10px 14px', borderTop: '1px solid var(--border-color)', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 12 }}>
            <CostHint
              inputText={[query, executionPlan, tableStats, existingIndexes, dataVolume, constraints].filter(Boolean).join('\n')}
              outputRatio={2.5}
            />
            <button
              onClick={start}
              disabled={streaming || !query.trim()}
              style={{ ...analyzeBtn, opacity: streaming || !query.trim() ? 0.5 : 1 }}
            >
              {streaming
                ? <><FaSpinner className="spin" /> 최적화 분석 중 (4단계)...</>
                : <><FaPlay /> 4단계 최적화 시작</>}
            </button>
          </div>
        </div>

        {/* ── 우측 ── */}
        <HarnessStagePanels<StageKey>
          stages={STAGES}
          buffers={stages}
          streaming={streaming}
          activeStreamingStage={activeStage}
          filePrefix="sql-optimization"
          emptyMessage="쿼리를 입력하고 '최적화 시작' 버튼을 누르세요. 실행계획·통계가 있으면 분석 정확도가 크게 올라갑니다."
        />
      </div>
    </>
  )
}

function Field({ label, hint, value, onChange, rows, required, disabled, placeholder }: {
  label: string
  hint: string
  value: string
  onChange: (v: string) => void
  rows: number
  required?: boolean
  disabled?: boolean
  placeholder?: string
}) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: '4px' }}>
        <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-primary)' }}>
          {label} {required && <span style={{ color: 'var(--red, #ef4444)' }}>*</span>}
        </label>
        <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>{hint}</span>
      </div>
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        rows={rows}
        disabled={disabled}
        placeholder={placeholder}
        style={{
          width: '100%', boxSizing: 'border-box',
          fontFamily: 'Consolas, Monaco, monospace', fontSize: '12px', lineHeight: '1.5',
          padding: '6px 8px', border: '1px solid var(--border-color)', borderRadius: '6px',
          background: 'var(--bg-primary)', color: 'var(--text-primary)', resize: 'vertical',
          opacity: disabled ? 0.6 : 1,
        }}
      />
    </div>
  )
}

const panelStyle: React.CSSProperties = { background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', display: 'flex', flexDirection: 'column', overflow: 'hidden' }
const panelHeaderStyle: React.CSSProperties = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: '1px solid var(--border-color)' }
const smallBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '4px', background: 'none', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '4px 8px', color: 'var(--text-sub)', cursor: 'pointer', fontSize: '12px' }
const analyzeBtn: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 20px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '13px', fontWeight: 600 }
