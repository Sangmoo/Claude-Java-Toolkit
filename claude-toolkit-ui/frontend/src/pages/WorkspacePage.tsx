import { FaLayerGroup } from 'react-icons/fa'
import AnalysisPageTemplate from '../components/common/AnalysisPageTemplate'

export default function WorkspacePage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '통합 워크스페이스',
        icon: FaLayerGroup,
        iconColor: '#f97316',
        description: '코드를 입력하면 여러 분석을 한번에 수행합니다.',
        feature: 'code_review',
        inputLabel: '코드 입력',
        inputPlaceholder: 'Java/SQL 코드를 입력하세요...',
        inputLanguage: 'java',
      }}
    />
  )
}
