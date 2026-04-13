import { FaRocket } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function MigratePage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: 'Spring 마이그레이션',
        icon: FaRocket,
        iconColor: '#10b981',
        description: 'Spring Boot 2.x → 3.x 마이그레이션 체크리스트를 자동 생성합니다.',
        feature: 'spring_migrate',
        inputLabel: '프로젝트 설정',
        sourceMode: 'both',
        allowFileUpload: true,
        inputPlaceholder: 'pom.xml 또는 프로젝트 구조를 입력하세요...',
      }}
    />
  )
}
