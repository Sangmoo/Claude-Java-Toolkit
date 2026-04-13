import { FaLanguage } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function SqlTranslatePage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: 'SQL DB 번역',
        icon: FaLanguage,
        iconColor: '#3b82f6',
        description: 'SQL 쿼리를 다른 데이터베이스 문법으로 변환합니다.',
        feature: 'sql_translate',
        inputLabel: 'SQL 입력',
        sourceMode: 'sql',
        inputPlaceholder: 'SQL 쿼리를 입력하세요...',
        options: [
          {
            name: 'sourceDb',
            label: '원본 DB',
            type: 'select',
            defaultValue: 'oracle',
            options: [
              { value: 'oracle', label: 'Oracle' },
              { value: 'mysql', label: 'MySQL' },
              { value: 'postgresql', label: 'PostgreSQL' },
              { value: 'mssql', label: 'MS SQL Server' },
            ],
          },
          {
            name: 'targetDb',
            label: '대상 DB',
            type: 'select',
            defaultValue: 'postgresql',
            options: [
              { value: 'oracle', label: 'Oracle' },
              { value: 'mysql', label: 'MySQL' },
              { value: 'postgresql', label: 'PostgreSQL' },
              { value: 'mssql', label: 'MS SQL Server' },
            ],
          },
        ],
      }}
    />
  )
}
