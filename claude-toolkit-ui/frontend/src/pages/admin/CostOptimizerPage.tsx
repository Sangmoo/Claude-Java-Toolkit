import { useEffect, useState } from 'react'
import { FaCoins, FaArrowDown, FaInfoCircle } from 'react-icons/fa'
import {
  ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, Legend,
} from 'recharts'

/**
 * v4.3.0 — AI 모델 비용 옵티마이저.
 *
 * 분석 유형별 토큰 사용량 + 모델 추천 + 절감 가능 금액을 시각화.
 * 백엔드: ModelCostService.analyze() / GET /api/v1/admin/cost-optimizer
 */

interface TypeRow {
  type: string
  count: number
  totalInputTokens: number
  totalOutputTokens: number
  avgInputTokens: number
  avgOutputTokens: number
  acceptedCount: number
  rejectedCount: number
  acceptRate: number
  currentCostUsd: number
  recommendedModelKey: string
  recommendedModelLabel: string
  recommendedCostUsd: number
  monthlySavingUsd: number
  savingPercent: number
  rationale: string
}

interface PricingRow {
  model: string
  label: string
  inputPer1M: number
  outputPer1M: number
}

interface CostData {
  days: number
  currentModel: string
  currentModelLabel: string
  totalAnalyses: number
  totalInputTokens: number
  totalOutputTokens: number
  totalCurrentCostUsd: number
  totalRecommendedCostUsd: number
  totalMonthlySavingUsd: number
  totalSavingPercent: number
  byType: TypeRow[]
  pricingTable: PricingRow[]
}

const PRESETS = [
  { label: '7일',  value: 7 },
  { label: '30일', value: 30 },
  { label: '90일', value: 90 },
]

function fmt$(v: number): string {
  if (v === 0) return '$0.00'
  if (Math.abs(v) < 0.01) return `$${v.toFixed(4)}`
  return `$${v.toFixed(2)}`
}

function fmtN(v: number): string {
  return v.toLocaleString('en-US')
}

function modelColor(key: string): string {
  if (key.includes('haiku')) return '#10b981'
  if (key.includes('sonnet')) return '#3b82f6'
  if (key.includes('opus')) return '#8b5cf6'
  return '#64748b'
}

export default function CostOptimizerPage() {
  const [days, setDays] = useState(30)
  const [data, setData] = useState<CostData | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    fetch(`/api/v1/admin/cost-optimizer?days=${days}`, { credentials: 'include' })
      .then(async (r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`)
        return r.json()
      })
      .then((j) => setData((j?.data ?? j) as CostData))
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false))
  }, [days])

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaCoins style={{ color: '#f59e0b' }} /> AI 모델 비용 옵티마이저
          {data && (
            <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 400 }}>
              · 현재 모델: <code>{data.currentModelLabel}</code>
            </span>
          )}
        </h2>
        <div style={{ display: 'flex', gap: '6px' }}>
          {PRESETS.map((p) => (
            <button key={p.value}
              onClick={() => setDays(p.value)}
              style={{
                padding: '6px 12px', fontSize: '13px',
                background: days === p.value ? 'var(--accent)' : 'var(--bg-card)',
                color: days === p.value ? '#fff' : 'var(--text-default)',
                border: '1px solid var(--border-color)', borderRadius: '6px', cursor: 'pointer',
              }}>
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {loading && <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>로딩 중...</div>}
      {error && <div style={{ padding: '20px', color: 'var(--red)' }}>오류: {error}</div>}

      {data && !loading && (
        <>
          {/* ── 요약 카드 ─────────────────────────────────────── */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '12px', marginBottom: '20px' }}>
            <SummaryCard label="총 분석 건수" value={fmtN(data.totalAnalyses)} hint={`최근 ${data.days}일`} color="#3b82f6" />
            <SummaryCard label="현재 모델 비용" value={fmt$(data.totalCurrentCostUsd)} hint={data.currentModelLabel} color="#ef4444" />
            <SummaryCard label="추천 모델 비용" value={fmt$(data.totalRecommendedCostUsd)} hint="유형별 최적 모델" color="#10b981" />
            <SummaryCard
              label="절감 가능액"
              value={fmt$(data.totalMonthlySavingUsd)}
              hint={`${data.totalSavingPercent}% 절감`}
              color="#f59e0b"
              icon={<FaArrowDown />}
            />
          </div>

          {/* ── 비용 비교 차트 ─────────────────────────────────── */}
          {data.byType.length > 0 && (
            <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '16px', marginBottom: '20px' }}>
              <div style={{ marginBottom: '10px', fontWeight: 600 }}>유형별 비용 비교 (USD)</div>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={data.byType.map((r) => ({
                  type: r.type,
                  current: r.currentCostUsd,
                  recommended: r.recommendedCostUsd,
                }))}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="type" angle={-15} textAnchor="end" height={80} />
                  <YAxis />
                  <Tooltip formatter={(v: number) => fmt$(v)} />
                  <Legend />
                  <Bar dataKey="current"     name="현재 모델"   fill="#ef4444" />
                  <Bar dataKey="recommended" name="추천 모델"   fill="#10b981" />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* ── 유형별 추천 테이블 ──────────────────────────────── */}
          <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '16px', marginBottom: '20px' }}>
            <div style={{ marginBottom: '12px', fontWeight: 600 }}>유형별 모델 추천 (절감액 큰 순)</div>
            {data.byType.length === 0 ? (
              <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)' }}>
                토큰 정보가 있는 분석 이력이 없습니다. 분석을 몇 건 수행한 뒤 다시 확인하세요.
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                  <thead>
                    <tr style={{ borderBottom: '2px solid var(--border-color)', textAlign: 'left' }}>
                      <th style={{ padding: '8px' }}>분석 유형</th>
                      <th style={{ padding: '8px', textAlign: 'right' }}>건수</th>
                      <th style={{ padding: '8px', textAlign: 'right' }}>평균 입력</th>
                      <th style={{ padding: '8px', textAlign: 'right' }}>승인률</th>
                      <th style={{ padding: '8px', textAlign: 'right' }}>현재 비용</th>
                      <th style={{ padding: '8px' }}>추천 모델</th>
                      <th style={{ padding: '8px', textAlign: 'right' }}>추천 비용</th>
                      <th style={{ padding: '8px', textAlign: 'right' }}>절감액</th>
                      <th style={{ padding: '8px' }}>근거</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.byType.map((r) => (
                      <tr key={r.type} style={{ borderBottom: '1px solid var(--border-color)' }}>
                        <td style={{ padding: '8px', fontWeight: 600 }}>{r.type}</td>
                        <td style={{ padding: '8px', textAlign: 'right' }}>{fmtN(r.count)}</td>
                        <td style={{ padding: '8px', textAlign: 'right' }}>{fmtN(r.avgInputTokens)}</td>
                        <td style={{ padding: '8px', textAlign: 'right' }}>{r.acceptRate}%</td>
                        <td style={{ padding: '8px', textAlign: 'right', color: 'var(--red)' }}>{fmt$(r.currentCostUsd)}</td>
                        <td style={{ padding: '8px' }}>
                          <span style={{
                            padding: '2px 8px', borderRadius: '4px', fontSize: '12px',
                            background: modelColor(r.recommendedModelKey) + '22',
                            color: modelColor(r.recommendedModelKey),
                            fontWeight: 600,
                          }}>{r.recommendedModelLabel}</span>
                        </td>
                        <td style={{ padding: '8px', textAlign: 'right', color: 'var(--green)' }}>{fmt$(r.recommendedCostUsd)}</td>
                        <td style={{ padding: '8px', textAlign: 'right', fontWeight: 700, color: r.monthlySavingUsd > 0 ? '#10b981' : 'var(--text-muted)' }}>
                          {fmt$(r.monthlySavingUsd)}
                          {r.savingPercent > 0 && <span style={{ fontSize: '10px', marginLeft: 4, color: 'var(--text-muted)' }}>({r.savingPercent}%)</span>}
                        </td>
                        <td style={{ padding: '8px', fontSize: '11px', color: 'var(--text-muted)' }}>{r.rationale}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* ── 모델 단가표 ─────────────────────────────────────── */}
          <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '16px', marginBottom: '20px' }}>
            <div style={{ marginBottom: '10px', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <FaInfoCircle style={{ color: 'var(--text-muted)' }} /> Anthropic 공식 단가표 (per 1M tokens)
            </div>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid var(--border-color)', textAlign: 'left' }}>
                    <th style={{ padding: '6px 8px' }}>모델</th>
                    <th style={{ padding: '6px 8px' }}>식별자</th>
                    <th style={{ padding: '6px 8px', textAlign: 'right' }}>입력 / 1M</th>
                    <th style={{ padding: '6px 8px', textAlign: 'right' }}>출력 / 1M</th>
                  </tr>
                </thead>
                <tbody>
                  {data.pricingTable.map((p) => (
                    <tr key={p.model} style={{ borderBottom: '1px solid var(--border-color)' }}>
                      <td style={{ padding: '6px 8px', fontWeight: 600, color: modelColor(p.model) }}>{p.label}</td>
                      <td style={{ padding: '6px 8px', fontFamily: 'monospace', fontSize: '11px', color: 'var(--text-muted)' }}>{p.model}</td>
                      <td style={{ padding: '6px 8px', textAlign: 'right' }}>${p.inputPer1M.toFixed(2)}</td>
                      <td style={{ padding: '6px 8px', textAlign: 'right' }}>${p.outputPer1M.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ marginTop: '10px', fontSize: '11px', color: 'var(--text-muted)' }}>
              💡 단가는 2026-04 기준이며, Anthropic 가격 정책 변경 시 백엔드 ModelCostService 의 PRICING 맵을 갱신해야 합니다.
            </div>
          </div>
        </>
      )}
    </>
  )
}

function SummaryCard({ label, value, hint, color, icon }: {
  label: string
  value: string
  hint?: string
  color: string
  icon?: React.ReactNode
}) {
  return (
    <div style={{
      background: 'var(--bg-card)', border: '1px solid var(--border-color)',
      borderLeft: `4px solid ${color}`, borderRadius: '8px', padding: '14px',
    }}>
      <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>{label}</div>
      <div style={{ fontSize: '24px', fontWeight: 700, color, display: 'flex', alignItems: 'center', gap: '6px' }}>
        {icon}{value}
      </div>
      {hint && <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px' }}>{hint}</div>}
    </div>
  )
}
