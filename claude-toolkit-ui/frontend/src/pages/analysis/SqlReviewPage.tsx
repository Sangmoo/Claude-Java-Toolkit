import { FaDatabase } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function SqlReviewPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: 'SQL 리뷰',
        icon: FaDatabase,
        iconColor: '#3b82f6',
        description: 'SQL 쿼리를 분석하여 성능, 보안, 코딩 스타일을 리뷰합니다.',
        feature: 'sql_review',
        inputLabel: 'SQL 입력',
        sourceMode: 'sql',
        inputPlaceholder: 'SQL 쿼리를 입력하세요...',
        options: [
          {
            name: 'reviewType',
            label: '리뷰 유형',
            type: 'select',
            defaultValue: 'review',
            options: [
              { value: 'review', label: '종합 리뷰' },
              { value: 'security', label: '보안 감사' },
              { value: 'performance', label: '성능 분석' },
            ],
          },
        ],
      }}
    />
  )
}
