import { useState } from 'react'
import { FaExchangeAlt, FaDatabase } from 'react-icons/fa'

const tabs = ['PostgreSQL', 'MySQL', 'Oracle', '백업/복원', '자동 이관']

export default function DbMigrationGuidePage() {
  const [activeTab, setActiveTab] = useState(0)

  return (
    <>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <FaExchangeAlt style={{ color: '#3b82f6' }} /> DB 마이그레이션 가이드
      </h2>
      <div style={{ display: 'flex', gap: '6px', marginBottom: '16px', flexWrap: 'wrap' }}>
        {tabs.map((t, i) => (
          <button key={t} onClick={() => setActiveTab(i)} style={{ padding: '6px 16px', borderRadius: '6px', border: '1px solid var(--border-color)', background: activeTab === i ? 'var(--accent)' : 'transparent', color: activeTab === i ? '#fff' : 'var(--text-sub)', cursor: 'pointer', fontSize: '13px' }}>{t}</button>
        ))}
      </div>
      <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: '12px', padding: '24px', minHeight: '300px' }}>
        {activeTab === 0 && <GuideSection title="PostgreSQL 마이그레이션" steps={['Docker Compose로 PostgreSQL 실행', 'DB_TYPE=postgresql 환경변수 설정', 'mvn spring-boot:run으로 서버 실행 (자동 스키마 생성)', '자동 이관 탭에서 데이터 복사']} />}
        {activeTab === 1 && <GuideSection title="MySQL 마이그레이션" steps={['MySQL 8.0+ 필요, utf8mb4 문자셋 설정', 'DB_TYPE=mysql 환경변수 설정', 'mvn spring-boot:run (자동 스키마 생성)', '자동 이관 탭에서 데이터 복사']} />}
        {activeTab === 2 && <GuideSection title="Oracle 마이그레이션" steps={['BOOLEAN→NUMBER(1), TEXT→CLOB 변환 주의', 'ojdbc8 드라이버 의존성 추가', 'application-oracle.yml 프로필 설정', 'Oracle10gDialect 사용']} />}
        {activeTab === 3 && <GuideSection title="백업/복원" steps={['H2: SCRIPT TO 명령으로 덤프', 'ZIP 백업 다운로드 (관리 탭)', 'CSV 형식으로 이력 내보내기', '복원 시 서버 중지 후 파일 교체']} />}
        {activeTab === 4 && <div><FaDatabase style={{ fontSize: '28px', color: 'var(--accent)', marginBottom: '12px' }} /><p style={{ color: 'var(--text-sub)', fontSize: '14px' }}>H2 → PostgreSQL/MySQL 원클릭 자동 이관 기능입니다. 서버에서 직접 실행해주세요.</p></div>}
      </div>
    </>
  )
}

function GuideSection({ title, steps }: { title: string; steps: string[] }) {
  return (
    <>
      <h3 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '14px' }}>{title}</h3>
      <ol style={{ paddingLeft: '20px', fontSize: '14px', color: 'var(--text-sub)', lineHeight: '2' }}>
        {steps.map((s, i) => <li key={i}>{s}</li>)}
      </ol>
    </>
  )
}
