import { FaChartBar } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function ComplexityPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '복잡도 분석',
        icon: FaChartBar,
        iconColor: '#3b82f6',
        description: '코드의 순환 복잡도, 인지 복잡도 등 품질 메트릭을 분석합니다.',
        feature: 'complexity',
        inputLabel: '코드 입력',
        sourceMode: 'both',
        allowFileUpload: true,
        inputPlaceholder: 'Java/SQL 코드를 입력하세요...',
        inputLanguage: 'java',
      }}
    />
  )
}
