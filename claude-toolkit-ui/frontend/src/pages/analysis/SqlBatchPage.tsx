import { FaLayerGroup } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function SqlBatchPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '배치 SQL 분석',
        icon: FaLayerGroup,
        iconColor: '#3b82f6',
        description: '여러 SQL 쿼리를 한번에 분석합니다.',
        feature: 'sql_review',
        inputLabel: 'SQL 목록',
        sourceMode: 'sql',
        inputPlaceholder: '여러 SQL 쿼리를 입력하세요 (세미콜론으로 구분)...',
      }}
    />
  )
}
