import { FaLayerGroup } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function BatchPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: 'Batch 처리',
        icon: FaLayerGroup,
        iconColor: '#10b981',
        description: '대량 코드를 배치로 문서화/변환합니다.',
        feature: 'doc_gen',
        inputLabel: '코드 입력',
        inputPlaceholder: '배치 처리할 코드를 입력하세요...',
      }}
    />
  )
}
