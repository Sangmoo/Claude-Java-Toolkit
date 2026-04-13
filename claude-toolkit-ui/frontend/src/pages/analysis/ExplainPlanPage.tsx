import { FaSitemap } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function ExplainPlanPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '실행계획 분석',
        icon: FaSitemap,
        iconColor: '#3b82f6',
        description: 'SQL 실행계획을 분석하여 최적화 방안을 제안합니다.',
        feature: 'explain_plan',
        inputLabel: '실행계획 입력',
        sourceMode: 'sql',
        inputPlaceholder: 'EXPLAIN PLAN 결과를 입력하세요...',
      }}
    />
  )
}
