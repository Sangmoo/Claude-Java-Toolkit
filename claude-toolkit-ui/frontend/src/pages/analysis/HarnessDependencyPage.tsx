import { FaProjectDiagram } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function HarnessDependencyPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '의존성 분석',
        icon: FaProjectDiagram,
        iconColor: '#8b5cf6',
        description: '클래스 간 의존성 관계를 분석합니다.',
        feature: 'harness_review',
        inputLabel: '코드 입력',
        inputPlaceholder: 'Java 클래스 소스를 입력하세요...',
        inputLanguage: 'java',
      }}
    />
  )
}
