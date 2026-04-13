import { FaRandom } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function DbMigrationPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: 'DB 마이그레이션',
        icon: FaRandom,
        iconColor: '#10b981',
        description: 'DDL을 다른 데이터베이스 방언으로 변환합니다.',
        feature: 'db_migration',
        inputLabel: 'DDL 입력',
        inputPlaceholder: 'CREATE TABLE 문을 입력하세요...',
        options: [
          {
            name: 'targetDb',
            label: '대상 DB',
            type: 'select',
            defaultValue: 'postgresql',
            options: [
              { value: 'postgresql', label: 'PostgreSQL' },
              { value: 'mysql', label: 'MySQL' },
              { value: 'oracle', label: 'Oracle' },
            ],
          },
        ],
      }}
    />
  )
}
