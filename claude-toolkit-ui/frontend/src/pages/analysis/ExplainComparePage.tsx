import { FaColumns } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function ExplainComparePage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '실행계획 비교',
        icon: FaColumns,
        iconColor: '#3b82f6',
        description: '두 SQL 실행계획을 비교 분석합니다.',
        feature: 'explain_plan',
        inputLabel: '실행계획 입력',
        sourceMode: 'sql',
        inputPlaceholder: '비교할 두 실행계획을 구분선(---)으로 나누어 입력하세요...',
      }}
    />
  )
}
