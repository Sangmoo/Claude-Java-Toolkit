import { FaLayerGroup } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function HarnessBatchPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '배치 분석',
        icon: FaLayerGroup,
        iconColor: '#8b5cf6',
        description: '여러 파일을 한번에 코드 리뷰합니다.',
        feature: 'harness_review',
        inputLabel: '코드 입력',
        inputPlaceholder: '여러 Java 파일을 파일명과 함께 입력하세요...',
        inputLanguage: 'java',
      }}
    />
  )
}
