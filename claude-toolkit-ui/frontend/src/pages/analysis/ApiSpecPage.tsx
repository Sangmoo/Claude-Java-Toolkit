import { FaCode } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function ApiSpecPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: 'API 명세 생성',
        icon: FaCode,
        iconColor: '#10b981',
        description: 'Spring Controller 코드에서 REST API 명세를 자동 생성합니다.',
        feature: 'api_spec',
        inputLabel: 'Controller 코드',
        sourceMode: 'both',
        allowFileUpload: true,
        inputPlaceholder: 'Spring Controller 소스를 입력하세요...',
        inputLanguage: 'java',
      }}
    />
  )
}
