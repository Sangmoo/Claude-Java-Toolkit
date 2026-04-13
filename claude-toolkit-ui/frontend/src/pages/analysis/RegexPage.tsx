import { FaCode } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function RegexPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '정규식 생성기',
        icon: FaCode,
        iconColor: '#06b6d4',
        description: '요구사항을 설명하면 정규식을 자동 생성합니다.',
        feature: 'regex_gen',
        inputLabel: '요구사항',
        inputPlaceholder: '정규식으로 매칭하고 싶은 패턴을 설명하세요...',
      }}
    />
  )
}
