import { useEffect, useState } from 'react'
import { FaCalendarCheck, FaPlay, FaPause } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'

interface ScheduleItem {
  id: number
  pipelineName: string
  scheduleCron: string
  scheduleEnabled: boolean
  lastRun?: string
  nextRun?: string
}

export default function SchedulePage() {
  const [items, setItems] = useState<ScheduleItem[]>([])
  const api = useApi()
  const toast = useToast()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/schedule?format=json') as ScheduleItem[] | null
      if (data) setItems(data)
    }
    load()
  }, [api])

  const toggleEnabled = async (item: ScheduleItem) => {
    await fetch(`/pipelines/${item.id}/schedule`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ enabled: String(!item.scheduleEnabled) }),
      credentials: 'include',
    })
    setItems((prev) => prev.map((i) => i.id === item.id ? { ...i, scheduleEnabled: !i.scheduleEnabled } : i))
    toast.success(item.scheduleEnabled ? '스케줄 비활성화' : '스케줄 활성화')
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaCalendarCheck style={{ color: '#f59e0b' }} /> 분석 스케줄링
      </h2>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {items.map((item) => (
          <div key={item.id} style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '14px 16px', background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '10px' }}>
            <button
              onClick={() => toggleEnabled(item)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '16px', color: item.scheduleEnabled ? 'var(--green)' : 'var(--text-muted)' }}
            >
              {item.scheduleEnabled ? <FaPlay /> : <FaPause />}
            </button>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: '14px', fontWeight: 600 }}>{item.pipelineName}</div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>cron: {item.scheduleCron}</div>
            </div>
            {item.lastRun && <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>마지막: {item.lastRun}</span>}
          </div>
        ))}
        {items.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
            <FaCalendarCheck style={{ fontSize: '36px', opacity: 0.3, marginBottom: '12px' }} />
            <p>스케줄된 파이프라인이 없습니다.</p>
          </div>
        )}
      </div>
    </>
  )
}
