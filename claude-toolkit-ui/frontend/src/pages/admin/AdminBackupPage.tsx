import { FaDatabase, FaDownload, FaUpload } from 'react-icons/fa'
import { useToast } from '../../hooks/useToast'

export default function AdminBackupPage() {
  const toast = useToast()

  const downloadBackup = async () => {
    try {
      const res = await fetch('/admin/backup/download', { credentials: 'include' })
      if (res.ok) {
        const blob = await res.blob()
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `backup_${new Date().toISOString().slice(0, 10)}.zip`
        a.click()
        URL.revokeObjectURL(url)
        toast.success('백업 다운로드 완료')
      } else {
        toast.error('백업 다운로드 실패')
      }
    } catch { toast.error('백업 오류') }
  }

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaDatabase style={{ color: '#10b981' }} /> 백업 / 복원
      </h2>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px', textAlign: 'center' }}>
          <FaDownload style={{ fontSize: '32px', color: 'var(--accent)', marginBottom: '12px' }} />
          <h3 style={{ fontSize: '15px', marginBottom: '8px' }}>DB 백업 다운로드</h3>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '16px' }}>현재 H2 데이터베이스를 ZIP 파일로 다운로드합니다.</p>
          <button onClick={downloadBackup} style={{ padding: '10px 24px', borderRadius: '8px', background: 'var(--accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '14px', fontWeight: 600 }}>
            <FaDownload /> 백업 다운로드
          </button>
        </div>
        <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px', textAlign: 'center' }}>
          <FaUpload style={{ fontSize: '32px', color: '#3b82f6', marginBottom: '12px' }} />
          <h3 style={{ fontSize: '15px', marginBottom: '8px' }}>CSV 이력 내보내기</h3>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '16px' }}>리뷰 이력을 CSV 형식으로 내보냅니다.</p>
          <button onClick={() => { window.location.href = '/admin/backup/csv'; toast.info('CSV 내보내기 시작') }} style={{ padding: '10px 24px', borderRadius: '8px', background: '#3b82f6', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '14px', fontWeight: 600 }}>
            <FaDownload /> CSV 내보내기
          </button>
        </div>
      </div>
    </>
  )
}
