import { FaCubes } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function DepCheckPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '의존성 분석',
        icon: FaCubes,
        iconColor: '#10b981',
        description: 'pom.xml을 분석하여 취약점, 충돌, 업그레이드 권고를 제공합니다.',
        feature: 'dep_check',
        inputLabel: 'pom.xml 입력',
        inputPlaceholder: 'pom.xml 내용을 입력하세요...',
      }}
    />
  )
}
