import { useState, useEffect } from 'react'
import {
  FaPlay, FaSpinner, FaEraser, FaSearch, FaWrench, FaClipboardCheck, FaShieldAlt,
  FaExchangeAlt, FaDatabase,
} from 'react-icons/fa'
import HarnessStagePanels, { type StageDef } from '../../components/common/HarnessStagePanels'
import SourceSelector from '../../components/common/SourceSelector'
import CostHint from '../../components/common/CostHint'
import { useHarnessStream } from '../../hooks/useHarnessStream'
import { useToast } from '../../hooks/useToast'

/**
 * Phase B — Oracle SP → Java/Spring + MyBatis 마이그레이션 하네스 페이지.
 *
 * <p>좌측: SP 본문 (필수) + 선택 입력 (테이블 DDL / 인덱스 DDL / 호출 예시 / 비즈니스 컨텍스트)
 *        + "소스 선택" 버튼으로 DB의 PROCEDURE/FUNCTION/PACKAGE/TRIGGER에서 자동 로드
 * <p>우측: {@link HarnessStagePanels}로 4-stage 결과
 *
 * <p>SSE 엔드포인트: {@code POST /api/v1/sp-migration/stream-init} → {@code GET /api/v1/sp-migration/stream/{id}}
 * <p>권한: feature key {@code sp-migration-harness} — Admin 권한 화면에서 ON/OFF
 */
type StageKey = 'analyst' | 'builder' | 'reviewer' | 'verifier'

const STAGES: StageDef<StageKey>[] = [
  { key: 'analyst',  num: 1, title: 'SP 의미 분석',     icon: <FaSearch />,         color: '#3b82f6', desc: 'Analyst — 입출력·DB 부수효과·트랜잭션·루프·위험 포인트' },
  { key: 'builder',  num: 2, title: 'Java/MyBatis 변환', icon: <FaWrench />,         color: '#10b981', desc: 'Builder — Service · Mapper · XML · DTO · 단위 테스트' },
  { key: 'reviewer', num: 3, title: '행위 동등성 검증',  icon: <FaClipboardCheck />, color: '#f59e0b', desc: 'Reviewer — 동등성 항목·N+1 위험·데이터 의미 보존' },
  { key: 'verifier', num: 4, title: '정적 검증',         icon: <FaShieldAlt />,      color: '#8b5cf6', desc: 'Verifier — 컴파일·MyBatis XML·위험 변경 감지' },
]

export default function SpMigrationHarnessPage() {
  const [spSource, setSpSource]               = useState('')
  const [spName, setSpName]                   = useState('')
  const [spType, setSpType]                   = useState<'PROCEDURE' | 'FUNCTION' | 'PACKAGE' | 'TRIGGER'>('PROCEDURE')
  const [tableDdl, setTableDdl]               = useState('')
  const [indexDdl, setIndexDdl]               = useState('')
  const [callExample, setCallExample]         = useState('')
  const [businessContext, setBusinessContext] = useState('')

  const { stages, streaming, activeStage, error, startStream, reset } =
    useHarnessStream<StageKey>(['analyst', 'builder', 'reviewer', 'verifier'])
  const toast = useToast()

  // 에러 toast (렌더마다 호출 방지)
  useEffect(() => {
    if (error) toast.error(error)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [error])

  /** SourceSelector 콜백 — DB 객체 본문을 SP 본문 영역에 채움. */
  const handleSourceSelect = (code: string) => {
    setSpSource(code)
    toast.success('DB 객체 본문을 SP 본문 영역에 로드했습니다')
  }

  const start = async () => {
    if (streaming) return
    if (!spSource.trim() && !spName.trim()) {
      toast.error('SP 본문 또는 SP 이름 중 하나는 필수입니다')
      return
    }
    const body = new URLSearchParams({
      sp_source:        spSource.trim(),
      sp_name:          spName.trim(),
      sp_type:          spType,
      table_ddl:        tableDdl.trim(),
      index_ddl:        indexDdl.trim(),
      call_example:     callExample.trim(),
      business_context: businessContext.trim(),
    })
    await startStream({
      initUrl: '/api/v1/sp-migration/stream-init',
      streamUrlPrefix: '/api/v1/sp-migration/stream/',
      body,
    })
  }

  const clearAll = () => {
    setSpSource(''); setSpName(''); setSpType('PROCEDURE')
    setTableDdl(''); setIndexDdl(''); setCallExample(''); setBusinessContext('')
    reset()
  }

  return (
    <>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
        <FaExchangeAlt style={{ fontSize: '20px', color: '#10b981' }} />
        <div>
          <h2 style={{ fontSize: '17px', fontWeight: 700, margin: 0 }}>SP → Java 마이그레이션 하네스</h2>
          <p style={{ fontSize: '12px', color: 'var(--text-muted)', margin: 0 }}>
            4단계 파이프라인: SP 의미 분석 → Java/MyBatis 변환 → 행위 동등성 검증 → 정적 검증
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
        {/* ── 좌측: 다중 입력 ── */}
        <div style={panelStyle}>
          <div style={panelHeaderStyle}>
            <span style={{ fontWeight: 600, fontSize: '13px' }}>마이그레이션 입력</span>
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

          {/* SP 식별자 + 타입 */}
          <div style={{ padding: '8px 14px', display: 'flex', gap: '12px', alignItems: 'center', fontSize: '13px', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <FaDatabase style={{ color: '#3b82f6', fontSize: '12px' }} />
              <label style={{ color: 'var(--text-muted)' }}>SP 이름 (선택)</label>
              <input
                value={spName}
                onChange={(e) => setSpName(e.target.value)}
                placeholder="OWNER.SP_NAME"
                disabled={streaming}
                style={{ fontSize: '12px', padding: '3px 8px', width: '180px' }}
              />
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <label style={{ color: 'var(--text-muted)' }}>타입</label>
              <select
                value={spType}
                onChange={(e) => setSpType(e.target.value as any)}
                disabled={streaming}
                style={{ fontSize: '12px', padding: '3px 6px' }}
              >
                <option value="PROCEDURE">PROCEDURE</option>
                <option value="FUNCTION">FUNCTION</option>
                <option value="PACKAGE">PACKAGE</option>
                <option value="TRIGGER">TRIGGER</option>
              </select>
            </div>
            <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>
              본문 비우고 이름만 입력하면 ALL_SOURCE에서 자동 조회
            </span>
          </div>

          {/* 입력 필드들 */}
          <div style={{ flex: 1, overflowY: 'auto', padding: '0 14px 14px', display: 'flex', flexDirection: 'column', gap: '10px', minHeight: 0 }}>
            <Field
              label="SP 본문"
              hint="CREATE PROCEDURE/FUNCTION 전체 또는 PACKAGE BODY"
              value={spSource}
              onChange={setSpSource}
              rows={10}
              required={!spName.trim()}
              disabled={streaming}
              placeholder={"CREATE OR REPLACE PROCEDURE SP_WMS_DELV_SALE\n  (P_DELV_NO IN VARCHAR2, P_RESULT OUT NUMBER)\nIS\nBEGIN\n  ...\nEND;"}
            />
            <Field
              label="관련 테이블 DDL (선택)"
              hint="SP가 다루는 테이블의 CREATE TABLE 문"
              value={tableDdl}
              onChange={setTableDdl}
              rows={4}
              disabled={streaming}
              placeholder="CREATE TABLE WMS_DELV ( ... );"
            />
            <Field
              label="관련 인덱스 DDL (선택)"
              hint="해당 테이블의 인덱스 정의"
              value={indexDdl}
              onChange={setIndexDdl}
              rows={3}
              disabled={streaming}
              placeholder="CREATE INDEX IX_WMS_DELV_01 ON WMS_DELV(...);"
            />
            <Field
              label="호출 예시 (선택)"
              hint="실제 호출 시 파라미터 값 예시"
              value={callExample}
              onChange={setCallExample}
              rows={3}
              disabled={streaming}
              placeholder="EXEC SP_WMS_DELV_SALE('D2026010001', :result);"
            />
            <Field
              label="비즈니스 컨텍스트 (선택)"
              hint="이 SP의 비즈니스 의도, 호출자 정보, 기존 Java 구현 등"
              value={businessContext}
              onChange={setBusinessContext}
              rows={4}
              disabled={streaming}
              placeholder="출고 처리 후 매출 확정 + 재고 차감. 야간 배치에서 호출됨."
            />
          </div>

          <div style={{ padding: '10px 14px', borderTop: '1px solid var(--border-color)', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 12 }}>
            <CostHint
              inputText={[spSource, tableDdl, indexDdl, callExample, businessContext].filter(Boolean).join('\n')}
              outputRatio={2.5}
            />
            <button
              onClick={start}
              disabled={streaming || (!spSource.trim() && !spName.trim())}
              style={{ ...analyzeBtn, opacity: streaming || (!spSource.trim() && !spName.trim()) ? 0.5 : 1 }}
            >
              {streaming
                ? <><FaSpinner className="spin" /> 마이그레이션 분석 중 (4단계)...</>
                : <><FaPlay /> 4단계 마이그레이션 시작</>}
            </button>
          </div>
        </div>

        {/* ── 우측: 4-stage 결과 ── */}
        <HarnessStagePanels<StageKey>
          stages={STAGES}
          buffers={stages}
          streaming={streaming}
          activeStreamingStage={activeStage}
          filePrefix="sp-migration"
          emptyMessage="SP 본문을 입력하거나 'SP 이름' + '소스 선택' 으로 로드한 뒤 시작 버튼을 누르세요"
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
