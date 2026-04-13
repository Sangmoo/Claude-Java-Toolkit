import { useEffect, useState } from 'react'
import { FaChartLine } from 'react-icons/fa'
import { useApi } from '../hooks/useApi'

export default function RoiReportPage() {
  const [report, setReport] = useState<string>('')
  const api = useApi()

  useEffect(() => {
    const load = async () => {
      const data = await api.get('/roi-report?format=json')
      if (data) setReport(JSON.stringify(data, null, 2))
    }
    load()
  }, [])

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaChartLine style={{ color: '#f59e0b' }} /> ROI 리포트
      </h2>
      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px' }}>
        {report ? (
          <pre style={{ fontSize: '13px', whiteSpace: 'pre-wrap' }}>{report}</pre>
        ) : (
          <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>리포트를 불러오는 중...</div>
        )}
      </div>
    </>
  )
}
