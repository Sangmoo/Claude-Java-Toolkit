import { useEffect, useState } from 'react'
import {
  FaHeartbeat, FaServer, FaDatabase, FaMemory, FaStethoscope, FaSpinner,
  FaSitemap, FaLayerGroup, FaShieldAlt, FaBug, FaRobot, FaPlug, FaNetworkWired,
} from 'react-icons/fa'
import { useApi } from '../../hooks/useApi'

// v4.7.x — #G3 Phase 5: Live DB 통계 카드
interface LiveDbProfileStats {
  profileId: number
  profileName: string
  success: number
  failure: number
  timeout: number
  totalCalls: number
  avgLatencyMs: number
  lastCallMillis: number
  circuitOpen: boolean
  circuitSecondsUntilHalfOpen: number
}
interface LiveDbStatsResponse {
  success: boolean
  config?: {
    enabled: boolean
    defaultTimeoutSeconds: number
    maxRows: number
    maxCallsPerMinute: number
  }
  byProfile?: LiveDbProfileStats[]
  total?: { success: number; failure: number; timeout: number; totalCalls: number }
}

interface HealthData {
  jvmHeapUsed: string; jvmHeapMax: string; heapUsagePercent: number
  uptime: string; threadCount: number; javaVersion: string; osName: string
  dbFileSize: string; diskFreeSpace: string; apiStatus: string; userCount: number
  // 자동 이관 + 런타임 전환 반영용 (v4.2.x)
  dbType?: string
  dbProduct?: string
  dbVersion?: string
  dbUrl?: string
  dbUsername?: string
  dbConnected?: boolean
  dbError?: string
  dbOverrideActive?: boolean
}

// v4.6.x — /api/v1/admin/health/summary 응답
interface IndexerStat {
  name: string
  ready: boolean
  count: number
  unit: string
  lastScanMs: number
  lastScanFiles: number
}
interface ClaudeApiStat {
  model: string
  keyConfigured: boolean
  lastInputTokens: number
  lastOutputTokens: number
  error?: string
}
interface CacheStat {
  analysis?:     { size?: number; activeSize?: number; expired?: number; ttlMinutes?: number; storage?: string; error?: string }
  harnessFiles?: { loaded?: boolean; refreshing?: boolean; count?: number; lastRefresh?: number; error?: string }
  harnessDb?:    { loaded?: boolean; refreshing?: boolean; count?: number; configured?: boolean; lastRefresh?: number; error?: string }
}
interface AuditStat {
  totalCount?: number
  last24h?: number
  last1h?: number
  error?: string
}
interface ErrorLogStat {
  totalCount?: number
  unresolvedCount?: number
  error?: string
}
interface HealthSummary {
  indexers:  IndexerStat[]
  claudeApi: ClaudeApiStat
  cache:     CacheStat
  audit:     AuditStat
  errorLog:  ErrorLogStat
}

interface McpStatus {
  available: boolean
  autoStart?: boolean
  running?: boolean
  pid?: number | null
  port?: number
  reason?: string
}

export default function AdminHealthPage() {
  const [data, setData] = useState<HealthData | null>(null)
  const [summary, setSummary] = useState<HealthSummary | null>(null)
  const [liveDbStats, setLiveDbStats] = useState<LiveDbStatsResponse | null>(null)
  const [mcpStatus, setMcpStatus] = useState<McpStatus | null>(null)
  const [diagReport, setDiagReport] = useState<string>('')
  const [diagLoading, setDiagLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const api = useApi()

  // v4.7.x — #G3 보강: 런타임 글로벌 ON/OFF 토글
  const toggleGlobalLiveDb = async (next: boolean) => {
    if (!confirm(next
      ? 'Live DB 를 글로벌 활성화하시겠습니까?\n\n주의: 현재 인스턴스에만 적용됩니다 — 재시작 후엔 application.yml / 환경변수 가 우선합니다.'
      : 'Live DB 를 글로벌 비활성화하시겠습니까?')) return
    try {
      const res = await fetch(`/api/v1/admin/livedb/enabled?value=${next}`, {
        method: 'POST', credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (d?.success) {
        alert(d.message + (d.warning ? '\n\n⚠️ ' + d.warning : ''))
        // 즉시 재조회
        const fresh = await fetch('/api/v1/admin/livedb/stats', { credentials: 'include' })
        if (fresh.ok) setLiveDbStats(await fresh.json())
      } else {
        alert(d?.error || '실패')
      }
    } catch { alert('요청 실패') }
  }

  // v4.7.x — Phase 5: Live DB 회로차단 강제 복구
  const forceCloseBreaker = async (profileId: number, profileName: string) => {
    if (!confirm(`프로필 '${profileName}' 회로차단기를 강제 복구하시겠습니까?`)) return
    try {
      const res = await fetch(`/api/v1/admin/livedb/breaker/${profileId}/close`, {
        method: 'POST', credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (d?.success) {
        alert(`프로필 '${profileName}' 회로차단 강제 복구됨`)
        // 즉시 재조회
        const fresh = await fetch('/api/v1/admin/livedb/stats', { credentials: 'include' })
        if (fresh.ok) setLiveDbStats(await fresh.json())
      } else {
        alert(d?.error || '복구 실패')
      }
    } catch { alert('요청 실패') }
  }

  const runDiagnose = async () => {
    setDiagLoading(true)
    setDiagReport('')
    try {
      const res = await fetch('/admin/health/claude-api-diagnose', { credentials: 'include' })
      const d = await res.json()
      setDiagReport(d.success ? (d.report || '(empty)') : ('오류: ' + (d.error || 'unknown')))
    } catch (e) {
      setDiagReport('요청 실패: ' + (e instanceof Error ? e.message : String(e)))
    }
    setDiagLoading(false)
  }

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      setRefreshing(true)
      try {
        const [d, s] = await Promise.all([
          api.get('/api/v1/admin/health/data') as Promise<HealthData | null>,
          api.get('/api/v1/admin/health/summary') as Promise<HealthSummary | null>,
        ])
        if (cancelled) return
        if (d) setData(d)
        if (s) setSummary(s)
        // v4.7.x — Phase 5: Live DB 통계는 별도 호출 (404 시 silent — 미설치 환경 호환)
        try {
          const r = await fetch('/api/v1/admin/livedb/stats', { credentials: 'include' })
          if (r.ok) {
            const j = await r.json() as LiveDbStatsResponse
            if (!cancelled) setLiveDbStats(j)
          }
        } catch { /* silent */ }
        try {
          const r = await fetch('/api/v1/admin/health/mcp', { credentials: 'include' })
          if (r.ok) {
            const j = await r.json() as McpStatus
            if (!cancelled) setMcpStatus(j)
          }
        } catch { /* silent */ }
      } finally {
        if (!cancelled) setRefreshing(false)
      }
    }
    load()
    const interval = setInterval(load, 30000)
    return () => { cancelled = true; clearInterval(interval) }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!data) return <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>로딩 중...</div>

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaHeartbeat style={{ color: '#ef4444' }} /> 시스템 헬스
          <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 400, marginLeft: 8 }}>
            30초 자동 갱신
            {refreshing && <FaSpinner className="spin" style={{ marginLeft: 6, fontSize: 10 }} />}
          </span>
        </h2>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(290px, 1fr))', gap: '14px' }}>
        {/* 기존 4개 카드 — 서버 / JVM / DB / 시스템 정보 */}
        <Card icon={<FaServer style={{ color: 'var(--green)' }} />} title="서버 상태">
          <Stat label="업타임" value={data.uptime} />
          <Stat label="스레드" value={String(data.threadCount)} />
          <Stat label="API 상태" value={data.apiStatus} />
        </Card>
        <Card icon={<FaMemory style={{ color: 'var(--blue)' }} />} title="JVM 메모리">
          <Stat label="사용" value={data.jvmHeapUsed} />
          <Stat label="최대" value={data.jvmHeapMax} />
          <div style={{ background: 'var(--bg-primary)', borderRadius: '4px', height: '6px', marginTop: '8px', overflow: 'hidden' }}>
            <div style={{ height: '100%', width: `${data.heapUsagePercent}%`, background: data.heapUsagePercent > 80 ? 'var(--red)' : 'var(--blue)', borderRadius: '4px' }} />
          </div>
        </Card>
        <Card icon={<FaDatabase style={{ color: '#f59e0b' }} />} title="데이터베이스">
          <Stat label="유형" value={dbBadge(data)} />
          {data.dbProduct && <Stat label="제품" value={data.dbProduct} />}
          {data.dbUrl && <Stat label="URL" value={data.dbUrl} />}
          {data.dbUsername && <Stat label="계정" value={data.dbUsername} />}
          <Stat label="DB 파일/크기" value={data.dbFileSize} />
          <Stat label="디스크 여유" value={data.diskFreeSpace} />
          <Stat label="등록 사용자" value={String(data.userCount)} />
          {data.dbOverrideActive && (
            <BadgeBox color="#8b5cf6" text="🔁 자동 이관으로 전환된 운영 DB 사용 중" />
          )}
          {data.dbConnected === false && (
            <BadgeBox color="#ef4444" text={`⚠ DB 연결 실패: ${data.dbError || 'unknown'}`} />
          )}
        </Card>
        <Card icon={<FaServer style={{ color: 'var(--text-muted)' }} />} title="시스템 정보">
          <Stat label="Java" value={data.javaVersion} />
          <Stat label="OS" value={data.osName} />
        </Card>

        {/* v4.6.x 신규 — 인덱서 5종 */}
        {summary && (
          <Card icon={<FaSitemap style={{ color: '#06b6d4' }} />} title="인덱서">
            {summary.indexers.map((idx) => (
              <div key={idx.name} style={{ marginBottom: 6, fontSize: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ width: 6, height: 6, borderRadius: '50%', background: idx.ready ? '#10b981' : '#ef4444' }} />
                    <span style={{ color: 'var(--text-muted)' }}>{idx.name}</span>
                  </span>
                  <span style={{ fontWeight: 600 }}>
                    {idx.count.toLocaleString()} <small style={{ color: 'var(--text-muted)' }}>{idx.unit}</small>
                  </span>
                </div>
                {(idx.lastScanFiles > 0 || idx.lastScanMs > 0) && (
                  <div style={{ fontSize: 10, color: 'var(--text-muted)', paddingLeft: 12 }}>
                    스캔 {idx.lastScanFiles > 0 ? `${idx.lastScanFiles}개 파일 · ` : ''}{(idx.lastScanMs / 1000).toFixed(1)}s
                  </div>
                )}
              </div>
            ))}
          </Card>
        )}

        {/* v4.6.x 신규 — 캐시 3종 */}
        {summary && (
          <Card icon={<FaLayerGroup style={{ color: '#10b981' }} />} title="캐시">
            <div style={{ marginBottom: 8 }}>
              <div style={subTitle}>분석 응답 캐시</div>
              <Stat label="활성/전체" value={summary.cache.analysis
                ? `${summary.cache.analysis.activeSize ?? 0} / ${summary.cache.analysis.size ?? 0}`
                : '-'} />
              <Stat label="만료" value={String(summary.cache.analysis?.expired ?? 0)} />
              <Stat label="TTL" value={summary.cache.analysis?.ttlMinutes ? `${summary.cache.analysis.ttlMinutes}분` : '-'} />
            </div>
            <div style={{ marginBottom: 8 }}>
              <div style={subTitle}>하네스 — Java 파일</div>
              <Stat label="로드된 파일" value={String(summary.cache.harnessFiles?.count ?? 0)} />
              <Stat label="상태" value={
                summary.cache.harnessFiles?.refreshing ? '갱신 중'
                  : summary.cache.harnessFiles?.loaded ? '준비됨' : '미로드'} />
              <Stat label="마지막 갱신" value={fmtTime(summary.cache.harnessFiles?.lastRefresh)} />
            </div>
            <div>
              <div style={subTitle}>하네스 — DB 객체</div>
              <Stat label="로드된 객체" value={String(summary.cache.harnessDb?.count ?? 0)} />
              <Stat label="상태" value={
                summary.cache.harnessDb?.refreshing ? '갱신 중'
                  : !summary.cache.harnessDb?.configured ? 'DB 미설정'
                  : summary.cache.harnessDb?.loaded ? '준비됨' : '미로드'} />
              {summary.cache.harnessDb?.error && (
                <BadgeBox color="#ef4444" text={`⚠ ${summary.cache.harnessDb.error}`} />
              )}
            </div>
          </Card>
        )}

        {/* v4.6.x 신규 — Claude API */}
        {summary && (
          <Card icon={<FaRobot style={{ color: '#8b5cf6' }} />} title="Claude API">
            <Stat label="모델" value={summary.claudeApi.model} />
            <Stat label="API Key" value={summary.claudeApi.keyConfigured ? '설정됨' : '미설정'} />
            <Stat label="마지막 입력 토큰" value={summary.claudeApi.lastInputTokens > 0
              ? summary.claudeApi.lastInputTokens.toLocaleString() : '-'} />
            <Stat label="마지막 출력 토큰" value={summary.claudeApi.lastOutputTokens > 0
              ? summary.claudeApi.lastOutputTokens.toLocaleString() : '-'} />
            {summary.claudeApi.error && (
              <BadgeBox color="#ef4444" text={`⚠ ${summary.claudeApi.error}`} />
            )}
          </Card>
        )}

        {/* v4.6.x 신규 — 감사 로그 */}
        {summary && (
          <Card icon={<FaShieldAlt style={{ color: '#f59e0b' }} />} title="감사 로그">
            <Stat label="최근 1시간" value={String(summary.audit.last1h ?? 0)} />
            <Stat label="최근 24시간" value={String(summary.audit.last24h ?? 0)} />
            <Stat label="누적" value={String(summary.audit.totalCount ?? 0)} />
            {summary.audit.error && (
              <BadgeBox color="#ef4444" text={`⚠ ${summary.audit.error}`} />
            )}
          </Card>
        )}

        {/* v4.6.x 신규 — 오류 로그 */}
        {summary && (
          <Card icon={<FaBug style={{ color: '#ef4444' }} />} title="오류 로그 (Sentry-style)">
            <Stat label="미해결" value={String(summary.errorLog.unresolvedCount ?? 0)}
                  highlight={(summary.errorLog.unresolvedCount ?? 0) > 0 ? '#ef4444' : undefined} />
            <Stat label="누적 그룹" value={String(summary.errorLog.totalCount ?? 0)} />
            {(summary.errorLog.unresolvedCount ?? 0) > 0 && (
              <a href="/admin/error-log" style={{
                display: 'inline-block', marginTop: 6, fontSize: 11, color: '#ef4444',
                textDecoration: 'underline',
              }}>
                미해결 오류 보기 →
              </a>
            )}
            {summary.errorLog.error && (
              <BadgeBox color="#ef4444" text={`⚠ ${summary.errorLog.error}`} />
            )}
          </Card>
        )}
        {/* MCP Server 상태 카드 */}
        {mcpStatus && (
          <Card icon={<FaNetworkWired style={{ color: mcpStatus.running ? '#10b981' : mcpStatus.autoStart === false ? 'var(--text-muted)' : '#ef4444' }} />} title="MCP Server">
            {!mcpStatus.available ? (
              <BadgeBox color="var(--text-muted)" text={mcpStatus.reason || '미설치'} />
            ) : (
              <>
                <Stat label="Auto-Start" value={mcpStatus.autoStart ? 'enabled' : 'disabled'} />
                <Stat label="상태" value={mcpStatus.running ? '✓ 실행 중' : '✗ 중단됨'}
                      highlight={mcpStatus.running ? '#10b981' : mcpStatus.autoStart ? '#ef4444' : undefined} />
                {mcpStatus.running && mcpStatus.port && (
                  <Stat label="포트" value={String(mcpStatus.port)} />
                )}
                {mcpStatus.running && mcpStatus.pid != null && (
                  <Stat label="PID" value={String(mcpStatus.pid)} />
                )}
                {!mcpStatus.running && mcpStatus.autoStart && (
                  <BadgeBox color="#ef4444" text="⚠ MCP 프로세스 종료 — 서버 재시작 필요" />
                )}
              </>
            )}
          </Card>
        )}
      </div>

      {/* Live DB 통계 카드 */}
      {liveDbStats && liveDbStats.success && (
        <div style={{
          marginTop: 24, background: 'var(--bg-secondary)',
          border: '1px solid var(--border-color)', borderRadius: 12, padding: 18,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
            <FaPlug style={{ color: '#10b981' }} />
            <strong style={{ fontSize: 14 }}>Live DB 호출 통계</strong>
            <span style={{
              marginLeft: 'auto', fontSize: 11,
              padding: '2px 8px', borderRadius: 10,
              background: liveDbStats.config?.enabled ? 'rgba(16,185,129,0.12)' : 'rgba(100,116,139,0.12)',
              color:      liveDbStats.config?.enabled ? '#10b981' : 'var(--text-muted)',
              fontWeight: 700,
            }}>
              {liveDbStats.config?.enabled ? '✓ ENABLED' : '○ DISABLED'}
            </span>
            {/* v4.7.x — #G3 보강: 즉시 토글 버튼 */}
            <button
              onClick={() => toggleGlobalLiveDb(!liveDbStats.config?.enabled)}
              style={{
                padding: '4px 10px', fontSize: 11, cursor: 'pointer',
                background: liveDbStats.config?.enabled ? 'transparent' : '#10b981',
                color:      liveDbStats.config?.enabled ? 'var(--text-muted)' : '#fff',
                border: `1px solid ${liveDbStats.config?.enabled ? 'var(--border-color)' : '#10b981'}`,
                borderRadius: 4, fontWeight: 600,
              }}>
              {liveDbStats.config?.enabled ? '⏸ 비활성화' : '▶ 활성화'}
            </button>
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 10 }}>
            한도: 분당 {liveDbStats.config?.maxCallsPerMinute ?? 0} 호출 ·
            timeout {liveDbStats.config?.defaultTimeoutSeconds ?? 0}s ·
            max rows {liveDbStats.config?.maxRows ?? 0}
          </div>

          {/* v4.7.x — #G3 보강: DISABLED 상태일 때 사유 + 활성화 가이드 안내 박스 */}
          {!liveDbStats.config?.enabled && (
            <div style={{
              background: 'rgba(245,158,11,0.08)', border: '1px solid #f59e0b',
              borderRadius: 6, padding: '10px 12px', fontSize: 12, marginBottom: 12,
            }}>
              <strong style={{ color: '#f59e0b' }}>ℹ️ 왜 DISABLED 인가?</strong>
              <div style={{ marginTop: 6, lineHeight: 1.7, color: 'var(--text-default)' }}>
                Live DB 활성화는 <strong>3-layer</strong> 모두 ON 이어야 동작합니다:
                <ol style={{ margin: '4px 0 4px', paddingLeft: 20 }}>
                  <li><strong>Layer 1 (현재 OFF ❌)</strong> — 글로벌 feature flag.
                    위 <strong>"▶ 활성화"</strong> 버튼으로 즉시 ON (현재 인스턴스만).
                    영구하려면 <code>application.yml</code> 의 <code>toolkit.livedb.enabled: true</code>
                    또는 환경변수 <code>TOOLKIT_LIVEDB_ENABLED=true</code> + 재시작</li>
                  <li><strong>Layer 2</strong> — DbProfile 별 토글: <code>/db-profiles</code>
                    페이지에서 각 프로필의 🔌 Live: ON 클릭</li>
                  <li><strong>Layer 3</strong> — 분석 페이지 chip 에서 프로필 선택 후 분석 시작</li>
                </ol>
                현재 Layer 1 만 OFF 라서 Layer 2/3 활성화도 동작 안 함 (kill switch 우선).
              </div>
            </div>
          )}

          {/* 글로벌 합계 */}
          {liveDbStats.total && (
            <div style={{ display: 'flex', gap: 8, marginBottom: 10, flexWrap: 'wrap' }}>
              <BadgeBox color="#10b981" text={`✓ 성공 ${liveDbStats.total.success}`} />
              <BadgeBox color="#ef4444" text={`✗ 실패 ${liveDbStats.total.failure}`} />
              <BadgeBox color="#f59e0b" text={`⏱ timeout ${liveDbStats.total.timeout}`} />
              <BadgeBox color="var(--text-muted)" text={`총 ${liveDbStats.total.totalCalls}건`} />
            </div>
          )}

          {/* 프로필별 표 */}
          {liveDbStats.byProfile && liveDbStats.byProfile.length > 0 ? (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ background: 'var(--bg-primary)', textAlign: 'left' }}>
                    <th style={tableTh}>프로필</th>
                    <th style={tableTh}>성공</th>
                    <th style={tableTh}>실패</th>
                    <th style={tableTh}>timeout</th>
                    <th style={tableTh}>총 호출</th>
                    <th style={tableTh}>평균 latency</th>
                    <th style={tableTh}>회로 상태</th>
                  </tr>
                </thead>
                <tbody>
                  {liveDbStats.byProfile.map((p) => (
                    <tr key={p.profileId} style={{ borderTop: '1px solid var(--border-color)' }}>
                      <td style={tableTd}><strong>{p.profileName}</strong></td>
                      <td style={{ ...tableTd, color: '#10b981' }}>{p.success}</td>
                      <td style={{ ...tableTd, color: p.failure > 0 ? '#ef4444' : 'inherit' }}>{p.failure}</td>
                      <td style={{ ...tableTd, color: p.timeout > 0 ? '#f59e0b' : 'inherit' }}>{p.timeout}</td>
                      <td style={tableTd}>{p.totalCalls}</td>
                      <td style={tableTd}>{p.avgLatencyMs}ms</td>
                      <td style={tableTd}>
                        {p.circuitOpen ? (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                            <span style={{ color: '#ef4444' }}>🔴 OPEN</span>
                            <span style={{ color: 'var(--text-muted)', fontSize: 10 }}>
                              ({p.circuitSecondsUntilHalfOpen}s 후 자동 복구)
                            </span>
                            <button
                              onClick={() => forceCloseBreaker(p.profileId, p.profileName)}
                              title="강제 복구"
                              style={{
                                padding: '2px 6px', fontSize: 10, cursor: 'pointer',
                                background: '#ef4444', color: '#fff', border: 'none',
                                borderRadius: 3,
                              }}>
                              강제 복구
                            </button>
                          </span>
                        ) : (
                          <span style={{ color: '#10b981' }}>🟢 CLOSED</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'center', padding: '12px' }}>
              아직 Live DB 호출 기록이 없습니다.
            </div>
          )}
        </div>
      )}

      {/* Claude API 연결 진단 */}
      <div style={{ marginTop: '24px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '18px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '14px', fontWeight: 600 }}>
            <FaStethoscope style={{ color: '#8b5cf6' }} /> Claude API 연결 진단
          </div>
          <button onClick={runDiagnose} disabled={diagLoading} style={{
            display: 'flex', alignItems: 'center', gap: '6px',
            padding: '6px 14px', borderRadius: '6px',
            background: 'var(--accent)', color: '#fff', border: 'none',
            fontSize: '12px', fontWeight: 600, cursor: diagLoading ? 'not-allowed' : 'pointer',
            opacity: diagLoading ? 0.6 : 1,
          }}>
            {diagLoading ? <><FaSpinner className="spin" /> 진단 중...</> : '진단 실행'}
          </button>
        </div>
        <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '8px', lineHeight: 1.6 }}>
          TLS 핸드셰이크·DNS·프록시 경로를 종합 확인합니다. <code>handshake_failure</code> 가 나오면
          사내망 프록시(<code>CLAUDE_PROXY_HOST/PORT</code>) 또는 베이스 URL(<code>CLAUDE_BASE_URL</code>)
          설정이 필요할 수 있습니다.
        </p>
        {diagReport && (
          <pre style={{
            background: 'var(--bg-primary)', border: '1px solid var(--border-color)',
            borderRadius: '6px', padding: '12px', fontSize: '11.5px',
            fontFamily: 'Consolas, Monaco, monospace', whiteSpace: 'pre-wrap',
            maxHeight: '280px', overflowY: 'auto', margin: 0,
          }}>{diagReport}</pre>
        )}
      </div>
    </>
  )
}

function Card({ icon, title, children }: { icon: React.ReactNode; title: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '18px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px', fontSize: '14px', fontWeight: 600 }}>{icon} {title}</div>
      {children}
    </div>
  )
}

function dbBadge(d: HealthData): string {
  const t = (d.dbType || 'unknown').toUpperCase()
  if (d.dbConnected === false) return `${t} (연결 실패)`
  if (d.dbVersion) return `${t} ${d.dbVersion.split(/\s+/)[0]}`
  return t
}

function Stat({ label, value, highlight }: { label: string; value: string; highlight?: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px', gap: '8px' }}>
      <span style={{ color: 'var(--text-muted)', flexShrink: 0 }}>{label}</span>
      <span
        style={{
          fontWeight: highlight ? 700 : 500, textAlign: 'right',
          color: highlight,
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}
        title={value}
      >{value}</span>
    </div>
  )
}

function BadgeBox({ color, text }: { color: string; text: string }) {
  return (
    <div style={{
      marginTop: '6px', padding: '6px 10px',
      background: `${color}1f`, border: `1px solid ${color}55`,
      borderRadius: '6px', fontSize: '11px', color,
    }}>{text}</div>
  )
}

const subTitle: React.CSSProperties = {
  fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase',
  letterSpacing: 0.5, marginBottom: 4, paddingBottom: 2,
  borderBottom: '1px dashed var(--border-color)',
}

function fmtTime(ms: number | undefined | null): string {
  if (!ms || ms === 0) return '-'
  const diff = Date.now() - ms
  if (diff < 60_000)        return `${Math.floor(diff / 1000)}초 전`
  if (diff < 3_600_000)     return `${Math.floor(diff / 60_000)}분 전`
  if (diff < 86_400_000)    return `${Math.floor(diff / 3_600_000)}시간 전`
  return new Date(ms).toLocaleString()
}

// v4.7.x — Phase 5: 테이블 스타일 (livedb 통계 카드 전용)
const tableTh: React.CSSProperties = {
  padding: '6px 10px', fontWeight: 600, fontSize: 11,
  color: 'var(--text-muted)', borderBottom: '1px solid var(--border-color)',
}
const tableTd: React.CSSProperties = {
  padding: '6px 10px', fontSize: 12,
}
